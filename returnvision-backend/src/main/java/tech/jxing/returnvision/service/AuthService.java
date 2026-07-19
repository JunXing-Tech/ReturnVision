package tech.jxing.returnvision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tech.jxing.returnvision.common.exception.AuthError;
import tech.jxing.returnvision.model.entity.SysRefreshToken;
import tech.jxing.returnvision.model.entity.SysRole;
import tech.jxing.returnvision.model.entity.SysUser;
import tech.jxing.returnvision.model.entity.SysUserRole;
import tech.jxing.returnvision.model.mapper.SysRefreshTokenMapper;
import tech.jxing.returnvision.model.mapper.SysRoleMapper;
import tech.jxing.returnvision.model.mapper.SysUserMapper;
import tech.jxing.returnvision.model.mapper.SysUserRoleMapper;
import tech.jxing.returnvision.security.FeishuOAuthService;
import tech.jxing.returnvision.security.JwtUtil;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 【业务逻辑层】鉴权服务
 *
 * 职责：登录/登出/刷新token/改密/飞书OAuth登录的业务逻辑
 * 层级：Service 层
 * 调用方：AuthController
 * 关联：docs/04 第 4.7 节、docs/06 第二章
 *
 * 设计要点：
 *   1. access token 用 JWT（无状态），refresh token 用随机字符串存库（有状态，支持主动失效）
 *   2. refresh token 存 SHA-256 哈希，不存明文
 *   3. 飞书 OAuth 首次登录不自动创建账号，需管理员先绑定
 *   4. 初始密码 admin123 登录时 must_change_password=true
 */
@Service
@Slf4j
public class AuthService {

    private static final String INITIAL_PASSWORD = "admin123";

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final FeishuOAuthService feishuOAuthService;
    private final long refreshTokenExpirationSec;

    public AuthService(SysUserMapper userMapper,
                       SysRoleMapper roleMapper,
                       SysUserRoleMapper userRoleMapper,
                       SysRefreshTokenMapper refreshTokenMapper,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       FeishuOAuthService feishuOAuthService,
                       @Value("${jwt.refresh-token-expiration:604800}") long refreshTokenExpirationSec) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.feishuOAuthService = feishuOAuthService;
        this.refreshTokenExpirationSec = refreshTokenExpirationSec;
    }

    /**
     * 账号密码登录
     *
     * 实现步骤：
     *   1. 按 username 查用户
     *   2. 校验账号存在 + 密码匹配 + 账号启用
     *   3. 查用户角色
     *   4. 生成 access token + refresh token
     *   5. 更新 last_login_at
     *   6. 返回登录结果
     *
     * @param username 用户名
     * @param password 明文密码
     * @return Map 含 access_token / refresh_token / expires_in / user
     */
    public Map<String, Object> login(String username, String password) {
        // 步骤1：查用户
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (user == null) {
            throw AuthError.invalidCredentials();
        }

        // 步骤2：校验密码 + 账号状态
        if (user.getPasswordHash() == null || user.getPasswordHash().isEmpty()
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw AuthError.invalidCredentials();
        }
        if ("disabled".equals(user.getStatus())) {
            throw AuthError.accountDisabled();
        }

        // 步骤3：查角色
        List<String> roles = queryUserRoleCodes(user.getId());

        // 步骤4：生成 token
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refreshToken = generateAndStoreRefreshToken(user.getId());

        // 步骤5：更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 步骤6：组装结果
        log.info("[鉴权] 登录成功：username={}, roles={}", username, roles);
        return buildLoginResult(accessToken, refreshToken, user, roles);
    }

    /**
     * 刷新 access token
     *
     * 实现步骤：
     *   1. 对 refresh token 做 SHA-256 哈希
     *   2. 查库比对
     *   3. 校验未过期 + 用户仍启用
     *   4. 签发新 access token
     *
     * @param refreshToken 客户端传入的 refresh token
     * @return Map 含 access_token / expires_in
     */
    public Map<String, Object> refresh(String refreshToken) {
        // 步骤1-2：哈希后查库
        String tokenHash = sha256(refreshToken);
        SysRefreshToken stored = refreshTokenMapper.selectOne(new LambdaQueryWrapper<SysRefreshToken>()
                .eq(SysRefreshToken::getTokenHash, tokenHash));

        if (stored == null) {
            throw AuthError.refreshTokenInvalid();
        }

        // 步骤3：校验过期 + 用户状态
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenMapper.deleteById(stored.getId());
            throw AuthError.refreshTokenInvalid();
        }

        SysUser user = userMapper.selectById(stored.getUserId());
        if (user == null || "disabled".equals(user.getStatus())) {
            refreshTokenMapper.deleteById(stored.getId());
            throw AuthError.accountDisabled();
        }

        // 步骤4：签发新 access token
        List<String> roles = queryUserRoleCodes(user.getId());
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), roles);

        Map<String, Object> result = new HashMap<>();
        result.put("access_token", accessToken);
        result.put("expires_in", jwtUtil.getAccessTokenExpirationSec());
        log.info("[鉴权] 刷新 token 成功：username={}", user.getUsername());
        return result;
    }

    /**
     * 登出（失效当前用户所有 refresh token）
     *
     * @param userId 用户ID
     */
    public void logout(Long userId) {
        refreshTokenMapper.delete(new LambdaQueryWrapper<SysRefreshToken>()
                .eq(SysRefreshToken::getUserId, userId));
        log.info("[鉴权] 登出成功：user_id={}（所有 refresh token 已失效）", userId);
    }

    /**
     * 获取当前用户信息
     *
     * @param userId 用户ID
     * @return Map 含用户信息 + roles + must_change_password
     */
    public Map<String, Object> getCurrentUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw AuthError.invalidCredentials();
        }
        List<String> roles = queryUserRoleCodes(userId);

        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("display_name", user.getDisplayName());
        result.put("roles", roles);
        result.put("last_login_at", user.getLastLoginAt());
        result.put("must_change_password", isInitialPassword(user.getPasswordHash()));
        return result;
    }

    /**
     * 飞书 OAuth 登录
     *
     * 实现步骤：
     *   1. 用 code 换取飞书用户信息（feishu_user_id）
     *   2. 用 feishu_user_id 查 sys_user
     *   3. 未找到 -> 返回 1004 错误
     *   4. 找到 -> 校验启用 + 查角色 + 生成 token
     *
     * @param code 飞书回调的授权码
     * @return Map 含 access_token / refresh_token / expires_in / user
     */
    public Map<String, Object> feishuLogin(String code) throws Exception {
        // 步骤1：换取飞书用户信息
        Map<String, String> feishuInfo = feishuOAuthService.getUserInfoByCode(code);
        if (feishuInfo == null) {
            throw new AuthError(1004, "飞书授权失败");
        }
        String feishuUserId = feishuInfo.get("feishu_user_id");

        // 步骤2：查 sys_user
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getFeishuUserId, feishuUserId));

        // 步骤3：未找到
        if (user == null) {
            log.warn("[鉴权] 飞书账号未绑定：feishu_user_id={}", feishuUserId);
            throw AuthError.feishuNotBound();
        }

        // 步骤4：校验 + 生成 token
        if ("disabled".equals(user.getStatus())) {
            throw AuthError.accountDisabled();
        }

        List<String> roles = queryUserRoleCodes(user.getId());
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getUsername(), roles);
        String refreshToken = generateAndStoreRefreshToken(user.getId());

        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        log.info("[鉴权] 飞书登录成功：username={}, feishu_user_id={}", user.getUsername(), feishuUserId);
        return buildLoginResult(accessToken, refreshToken, user, roles);
    }

    /**
     * 修改密码
     *
     * @param userId      用户ID
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw AuthError.invalidCredentials();
        }

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw AuthError.oldPasswordWrong();
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        log.info("[鉴权] 修改密码成功：user_id={}", userId);
    }

    /**
     * 获取自己的完整信息（个人中心用）
     *
     * 实现步骤：
     *   1. 查用户
     *   2. 查角色
     *   3. 组装完整信息（含 feishu_bound / created_at）
     *
     * @param userId 当前用户ID
     * @return Map 含完整用户信息
     */
    public Map<String, Object> getProfile(Long userId) {
        // 步骤1：查用户
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw AuthError.invalidCredentials();
        }

        // 步骤2：查角色
        List<String> roles = queryUserRoleCodes(userId);

        // 步骤3：组装完整信息
        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        result.put("display_name", user.getDisplayName());
        result.put("feishu_user_id", user.getFeishuUserId());
        result.put("feishu_bound", user.getFeishuUserId() != null && !user.getFeishuUserId().isEmpty());
        result.put("roles", roles);
        result.put("last_login_at", user.getLastLoginAt());
        result.put("created_at", user.getCreatedAt());
        return result;
    }

    /**
     * 修改自己的显示名（个人中心用）
     *
     * 实现步骤：
     *   1. 查用户
     *   2. 更新 display_name
     *
     * @param userId      当前用户ID
     * @param displayName 新显示名
     */
    public void updateProfile(Long userId, String displayName) {
        // 步骤1：查用户
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw AuthError.invalidCredentials();
        }

        // 步骤2：更新 display_name
        user.setDisplayName(displayName);
        userMapper.updateById(user);
        log.info("[鉴权] 修改显示名成功：user_id={}, display_name={}", userId, displayName);
    }

    // ==================== 内部方法 ====================

    /**
     * 查询用户的角色 code 列表
     */
    private List<String> queryUserRoleCodes(Long userId) {
        List<SysUserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).toList();
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
        return roles.stream().map(SysRole::getRoleCode).toList();
    }

    /**
     * 生成 refresh token 并存库
     *
     * @return 明文 refresh token（返回给客户端，库只存哈希）
     */
    private String generateAndStoreRefreshToken(Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        String tokenHash = sha256(token);

        SysRefreshToken entity = new SysRefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(tokenHash);
        entity.setExpiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpirationSec));
        refreshTokenMapper.insert(entity);

        return token;
    }

    /**
     * 组装登录结果
     */
    private Map<String, Object> buildLoginResult(String accessToken, String refreshToken,
                                                  SysUser user, List<String> roles) {
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("username", user.getUsername());
        userMap.put("display_name", user.getDisplayName());
        userMap.put("roles", roles);
        userMap.put("must_change_password", isInitialPassword(user.getPasswordHash()));

        Map<String, Object> result = new HashMap<>();
        result.put("access_token", accessToken);
        result.put("refresh_token", refreshToken);
        result.put("expires_in", jwtUtil.getAccessTokenExpirationSec());
        result.put("user", userMap);
        return result;
    }

    /**
     * 判断是否初始密码（admin123）
     * 用于 must_change_password 标志
     */
    private boolean isInitialPassword(String passwordHash) {
        return passwordHash != null && passwordEncoder.matches(INITIAL_PASSWORD, passwordHash);
    }

    /**
     * SHA-256 哈希
     */
    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 哈希失败", e);
        }
    }
}
