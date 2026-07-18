package tech.jxing.returnvision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jxing.returnvision.common.exception.AuthError;
import tech.jxing.returnvision.common.exception.BizException;
import tech.jxing.returnvision.model.dto.CreateUserRequest;
import tech.jxing.returnvision.model.dto.UpdateUserRequest;
import tech.jxing.returnvision.model.entity.SysRefreshToken;
import tech.jxing.returnvision.model.entity.SysRole;
import tech.jxing.returnvision.model.entity.SysUser;
import tech.jxing.returnvision.model.entity.SysUserRole;
import tech.jxing.returnvision.model.mapper.SysRefreshTokenMapper;
import tech.jxing.returnvision.model.mapper.SysRoleMapper;
import tech.jxing.returnvision.model.mapper.SysUserMapper;
import tech.jxing.returnvision.model.mapper.SysUserRoleMapper;
import tech.jxing.returnvision.security.AuthUser;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 【业务逻辑层】用户管理服务（管理员后台）
 *
 * 职责：用户的增删改查 + 5 项安全约束 + 重置密码
 * 层级：Service 层
 * 调用方：AdminUserController
 * 关联：docs/04 第 4.7.9 节、docs/06 第三章
 *
 * 5 项安全约束：
 *   1. 不能删除自己
 *   2. 不能禁用自己
 *   3. 不能删最后一个管理员
 *   4. 不能撤销自己的 ADMIN 角色
 *   5. 密码 BCrypt 哈希
 */
@Service
@Slf4j
public class AdminUserService {

    private static final String ADMIN_ROLE_CODE = "ADMIN";
    private static final String STATUS_DISABLED = "disabled";

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRefreshTokenMapper refreshTokenMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(SysUserMapper userMapper,
                            SysRoleMapper roleMapper,
                            SysUserRoleMapper userRoleMapper,
                            SysRefreshTokenMapper refreshTokenMapper,
                            PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 查询用户列表（含角色）
     *
     * 实现步骤：
     *   1. 查所有用户
     *   2. 批量查每个用户的角色
     *   3. 组装返回
     *
     * @return Map 含 users 列表和 total
     */
    public Map<String, Object> listUsers() {
        // 步骤1：查所有用户
        List<SysUser> users = userMapper.selectList(null);

        // 步骤2：批量查角色
        List<Map<String, Object>> userList = new ArrayList<>();
        for (SysUser user : users) {
            List<String> roleCodes = queryUserRoleCodes(user.getId());
            userList.add(buildUserMap(user, roleCodes));
        }

        // 步骤3：组装返回
        Map<String, Object> result = new HashMap<>();
        result.put("users", userList);
        result.put("total", users.size());
        return result;
    }

    /**
     * 创建用户
     *
     * 实现步骤：
     *   1. 校验用户名不重复
     *   2. 校验角色 code 合法
     *   3. BCrypt 哈希密码
     *   4. 插入 sys_user
     *   5. 插入 sys_user_role 关联
     *
     * @param request 创建用户请求
     * @return Map 含 id 和 username
     */
    @Transactional
    public Map<String, Object> createUser(CreateUserRequest request) {
        // 步骤1：校验用户名不重复
        Long existing = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.getUsername()));
        if (existing != null && existing > 0) {
            throw AuthError.usernameExists();
        }

        // 步骤2：校验角色 code 合法
        validateRoleCodes(request.getRoleCodes());

        // 步骤3：BCrypt 哈希密码
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getDisplayName());
        user.setFeishuUserId(request.getFeishuUserId());
        user.setStatus("active");

        // 步骤4：插入 sys_user
        userMapper.insert(user);

        // 步骤5：插入角色关联
        assignRoles(user.getId(), request.getRoleCodes());

        log.info("[用户管理] 创建用户：id={}, username={}, roles={}",
                user.getId(), user.getUsername(), request.getRoleCodes());

        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("username", user.getUsername());
        return result;
    }

    /**
     * 编辑用户（改密/改角色/改状态/改飞书绑定）
     *
     * 实现步骤：
     *   1. 查目标用户
     *   2. 校验不能禁用自己
     *   3. 校验不能撤销自己的 ADMIN 角色
     *   4. 更新字段
     *   5. 若改角色，重新分配
     *   6. 若禁用，删除该用户所有 refresh token
     *
     * @param targetId 目标用户ID
     * @param request  编辑请求
     * @param currentUser 当前操作者（用于安全约束）
     */
    @Transactional
    public void updateUser(Long targetId, UpdateUserRequest request, AuthUser currentUser) {
        // 步骤1：查目标用户
        SysUser user = userMapper.selectById(targetId);
        if (user == null) {
            throw new BizException(1007, "用户不存在");
        }

        // 步骤2：不能禁用自己
        if (STATUS_DISABLED.equals(request.getStatus())
                && targetId.equals(currentUser.getUserId())) {
            throw AuthError.cannotOperateSelf();
        }

        // 步骤3：不能撤销自己的 ADMIN 角色
        if (request.getRoleCodes() != null && targetId.equals(currentUser.getUserId())) {
            List<String> currentRoles = queryUserRoleCodes(targetId);
            if (currentRoles.contains(ADMIN_ROLE_CODE)
                    && !request.getRoleCodes().contains(ADMIN_ROLE_CODE)) {
                throw AuthError.cannotRevokeOwnAdmin();
            }
        }

        // 步骤4：更新字段
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getStatus() != null) {
            user.setStatus(request.getStatus());
        }
        if (request.getFeishuUserId() != null) {
            user.setFeishuUserId(request.getFeishuUserId());
        }
        userMapper.updateById(user);

        // 步骤5：若改角色，重新分配
        if (request.getRoleCodes() != null) {
            validateRoleCodes(request.getRoleCodes());
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                    .eq(SysUserRole::getUserId, targetId));
            assignRoles(targetId, request.getRoleCodes());
        }

        // 步骤6：若禁用，删除 refresh token
        if (STATUS_DISABLED.equals(request.getStatus())) {
            refreshTokenMapper.delete(new LambdaQueryWrapper<SysRefreshToken>()
                    .eq(SysRefreshToken::getUserId, targetId));
            log.info("[用户管理] 禁用用户 {}，已清除其所有 refresh token", targetId);
        }

        log.info("[用户管理] 编辑用户：id={}, 操作者={}", targetId, currentUser.getUsername());
    }

    /**
     * 删除用户
     *
     * 实现步骤：
     *   1. 校验不能删自己
     *   2. 校验不能删最后一个管理员
     *   3. 删除 sys_user_role 关联
     *   4. 删除 sys_user（refresh token 由外键 CASCADE 自动删）
     *
     * @param targetId     目标用户ID
     * @param currentUser  当前操作者
     */
    @Transactional
    public void deleteUser(Long targetId, AuthUser currentUser) {
        // 步骤1：不能删自己
        if (targetId.equals(currentUser.getUserId())) {
            throw AuthError.cannotOperateSelf();
        }

        // 步骤2：不能删最后一个管理员
        SysUser target = userMapper.selectById(targetId);
        if (target == null) {
            throw new BizException(1007, "用户不存在");
        }
        List<String> targetRoles = queryUserRoleCodes(targetId);
        if (targetRoles.contains(ADMIN_ROLE_CODE)) {
            Long adminCount = countAdmins();
            if (adminCount <= 1) {
                throw AuthError.cannotDeleteLastAdmin();
            }
        }

        // 步骤3：删除角色关联
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, targetId));

        // 步骤4：删除用户（refresh token 由外键 CASCADE 自动删）
        userMapper.deleteById(targetId);

        log.info("[用户管理] 删除用户：id={}, username={}, 操作者={}",
                targetId, target.getUsername(), currentUser.getUsername());
    }

    /**
     * 重置密码
     *
     * 实现步骤：
     *   1. 查目标用户
     *   2. BCrypt 哈希新密码
     *   3. 更新 password_hash
     *   4. 删除该用户所有 refresh token，强制重新登录
     *
     * @param targetId    目标用户ID
     * @param newPassword 新密码明文
     */
    @Transactional
    public void resetPassword(Long targetId, String newPassword) {
        // 步骤1：查目标用户
        SysUser user = userMapper.selectById(targetId);
        if (user == null) {
            throw new BizException(1007, "用户不存在");
        }

        // 步骤2-3：哈希并更新
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);

        // 步骤4：删除 refresh token，强制重新登录
        refreshTokenMapper.delete(new LambdaQueryWrapper<SysRefreshToken>()
                .eq(SysRefreshToken::getUserId, targetId));

        log.info("[用户管理] 重置密码：id={}, username={}", targetId, user.getUsername());
    }

    // ==================== 内部方法 ====================

    /**
     * 查用户的角色 code 列表
     */
    private List<String> queryUserRoleCodes(Long userId) {
        List<SysUserRole> userRoles = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId));
        if (userRoles.isEmpty()) {
            return List.of();
        }
        List<Long> roleIds = userRoles.stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        List<SysRole> roles = roleMapper.selectBatchIds(roleIds);
        return roles.stream().map(SysRole::getRoleCode).collect(Collectors.toList());
    }

    /**
     * 统计管理员数量（用于"不能删最后一个管理员"校验）
     */
    private Long countAdmins() {
        SysRole adminRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, ADMIN_ROLE_CODE));
        if (adminRole == null) {
            return 0L;
        }
        return userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, adminRole.getId()));
    }

    /**
     * 校验角色 code 合法（必须存在于 sys_role 表）
     */
    private void validateRoleCodes(List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            throw new BizException(1007, "角色不能为空");
        }
        for (String code : roleCodes) {
            Long count = roleMapper.selectCount(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleCode, code));
            if (count == null || count == 0) {
                throw new BizException(1007, "角色不存在：" + code);
            }
        }
    }

    /**
     * 分配角色（先删后插）
     */
    private void assignRoles(Long userId, List<String> roleCodes) {
        for (String code : roleCodes) {
            SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                    .eq(SysRole::getRoleCode, code));
            if (role != null) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(role.getId());
                userRoleMapper.insert(userRole);
            }
        }
    }

    /**
     * 组装用户信息 Map（含角色）
     */
    private Map<String, Object> buildUserMap(SysUser user, List<String> roleCodes) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("display_name", user.getDisplayName());
        map.put("feishu_user_id", user.getFeishuUserId());
        map.put("status", user.getStatus());
        map.put("last_login_at", user.getLastLoginAt());
        map.put("created_at", user.getCreatedAt());
        map.put("roles", roleCodes);
        return map;
    }
}
