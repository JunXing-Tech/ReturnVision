package tech.jxing.returnvision.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import tech.jxing.returnvision.audit.AuditLog;
import tech.jxing.returnvision.common.ResponseResult;
import tech.jxing.returnvision.common.exception.AuthError;
import tech.jxing.returnvision.common.exception.BizException;
import tech.jxing.returnvision.common.ratelimit.RegisterRateLimiter;
import tech.jxing.returnvision.controller.dto.AdminRegisterRequest;
import tech.jxing.returnvision.controller.dto.StaffRegisterRequest;
import tech.jxing.returnvision.model.entity.FeishuConfig;
import tech.jxing.returnvision.model.mapper.FeishuConfigMapper;
import tech.jxing.returnvision.security.AuthUser;
import tech.jxing.returnvision.security.FeishuOAuthService;
import tech.jxing.returnvision.service.AuthService;
import tech.jxing.returnvision.service.RegisterService;

import jakarta.servlet.http.HttpServletRequest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 【接口层】鉴权控制器
 *
 * 职责：提供登录/登出/刷新/用户信息/飞书OAuth/改密 6 个接口
 * 层级：Controller 层
 * 关联：docs/06 第二章鉴权接口
 *
 * 接口列表：
 *   POST /api/auth/login              - 账号密码登录
 *   POST /api/auth/refresh            - 刷新 access token
 *   POST /api/auth/logout             - 登出
 *   GET  /api/auth/me                 - 获取当前用户信息
 *   GET  /api/auth/feishu/url         - 获取飞书 OAuth 授权 URL
 *   POST /api/auth/feishu/callback    - 飞书 OAuth 回调
 *   POST /api/auth/change-password    - 修改密码
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final FeishuOAuthService feishuOAuthService;
    private final RegisterService registerService;
    private final RegisterRateLimiter registerRateLimiter;
    private final FeishuConfigMapper feishuConfigMapper;

    public AuthController(AuthService authService,
                          FeishuOAuthService feishuOAuthService,
                          RegisterService registerService,
                          RegisterRateLimiter registerRateLimiter,
                          FeishuConfigMapper feishuConfigMapper) {
        this.authService = authService;
        this.feishuOAuthService = feishuOAuthService;
        this.registerService = registerService;
        this.registerRateLimiter = registerRateLimiter;
        this.feishuConfigMapper = feishuConfigMapper;
    }

    /**
     * 账号密码登录
     *
     * 业务流程：
     *   1. 从请求体取 username + password
     *   2. 调 AuthService.login
     *   3. 返回 access_token + refresh_token + user
     */
    @PostMapping("/login")
    public ResponseResult<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        // 步骤1：取参数
        String username = request.get("username");
        String password = request.get("password");
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            throw new BizException(1001, "用户名和密码不能为空");
        }

        // 步骤2-3：登录并返回
        Map<String, Object> result = authService.login(username, password);
        return ResponseResult.success(result);
    }

    /**
     * 刷新 access token
     *
     * 业务流程：
     *   1. 从请求体取 refresh_token
     *   2. 调 AuthService.refresh
     *   3. 返回新 access_token
     */
    @PostMapping("/refresh")
    public ResponseResult<Map<String, Object>> refresh(@RequestBody Map<String, String> request) {
        // 步骤1：取参数
        String refreshToken = request.get("refresh_token");
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw AuthError.refreshTokenInvalid();
        }

        // 步骤2-3：刷新并返回
        Map<String, Object> result = authService.refresh(refreshToken);
        return ResponseResult.success(result);
    }

    /**
     * 登出
     *
     * 业务流程：
     *   1. 从 SecurityContext 获取当前用户
     *   2. 调 AuthService.logout 失效所有 refresh token
     *   3. 返回成功
     */
    @PostMapping("/logout")
    public ResponseResult<Map<String, Object>> logout() {
        // 步骤1：获取当前用户
        AuthUser authUser = getCurrentAuthUser();

        // 步骤2：登出
        authService.logout(authUser.getUserId());

        // 步骤3：返回
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ResponseResult.success(result);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public ResponseResult<Map<String, Object>> me() {
        AuthUser authUser = getCurrentAuthUser();
        Map<String, Object> result = authService.getCurrentUser(authUser.getUserId());
        return ResponseResult.success(result);
    }

    /**
     * 获取自己的完整信息（个人中心用，F01.2）
     *
     * 业务流程：
     *   1. 从 SecurityContext 获取当前用户
     *   2. 调 AuthService.getProfile
     *   3. 返回完整信息
     */
    @GetMapping("/profile")
    public ResponseResult<Map<String, Object>> getProfile() {
        // 步骤1：获取当前用户
        AuthUser authUser = getCurrentAuthUser();

        // 步骤2-3：查并返回
        Map<String, Object> result = authService.getProfile(authUser.getUserId());
        return ResponseResult.success(result);
    }

    /**
     * 修改自己的显示名（个人中心用，F01.2）
     *
     * 业务流程：
     *   1. 从 SecurityContext 获取当前用户
     *   2. 从请求体取 display_name
     *   3. 调 AuthService.updateProfile
     *   4. 返回成功
     */
    @PutMapping("/profile")
    @AuditLog(action = "UPDATE_PROFILE", targetType = "auth", description = "修改自己的显示名")
    public ResponseResult<Map<String, Object>> updateProfile(@RequestBody Map<String, String> request) {
        // 步骤1：获取当前用户
        AuthUser authUser = getCurrentAuthUser();

        // 步骤2：取参数
        String displayName = request.get("display_name");
        if (displayName == null || displayName.isEmpty()) {
            throw new BizException(1006, "显示名不能为空");
        }

        // 步骤3：更新
        authService.updateProfile(authUser.getUserId(), displayName);

        // 步骤4：返回
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ResponseResult.success(result);
    }

    /**
     * 查询可选公司列表（登录页飞书登录前选择公司用）
     *
     * 业务流程：
     *   1. 查 feishu_config 中 status=active 的公司
     *   2. 返回 configId + orgName（不含 app_secret 等敏感信息）
     *   3. 平台级用户不传 config_id 走 .env 默认
     */
    @GetMapping("/orgs")
    public ResponseResult<Map<String, Object>> listOrgs() {
        // 步骤1：查 active 的飞书配置
        java.util.List<FeishuConfig> configs = feishuConfigMapper.selectList(
                new LambdaQueryWrapper<FeishuConfig>()
                        .eq(FeishuConfig::getStatus, "active")
                        .orderByAsc(FeishuConfig::getOrgName));

        // 步骤2：组装（仅 configId + orgName，不回显 app_secret/app_id）
        java.util.List<Map<String, Object>> orgs = new ArrayList<>();
        for (FeishuConfig config : configs) {
            Map<String, Object> org = new HashMap<>();
            org.put("config_id", config.getId());
            org.put("org_name", config.getOrgName());
            orgs.add(org);
        }

        // 步骤3：返回
        Map<String, Object> result = new HashMap<>();
        result.put("orgs", orgs);
        result.put("has_platform", feishuOAuthService.isConfigured());
        return ResponseResult.success(result);
    }

    /**
     * 获取飞书 OAuth 授权 URL
     *
     * 业务流程：
     *   1. 生成 state（防 CSRF）
     *   2. 调 FeishuOAuthService 生成授权 URL
     *   3. 返回 auth_url + state
     */
    @GetMapping("/feishu/url")
    public ResponseResult<Map<String, Object>> feishuAuthUrl(
            @RequestParam(value = "config_id", required = false) Long configId) {
        // 步骤1：生成 state（格式：random:configId，回调时解析出 configId 用对应公司凭证）
        String random = UUID.randomUUID().toString().replace("-", "");
        String state = configId != null ? random + ":" + configId : random;

        // 步骤2：用 configId 对应公司的飞书应用生成授权 URL
        String authUrl = feishuOAuthService.generateAuthUrl(state, configId);

        // 步骤3：返回
        Map<String, Object> result = new HashMap<>();
        result.put("auth_url", authUrl);
        result.put("state", state);
        return ResponseResult.success(result);
    }

    /**
     * 飞书 OAuth 回调
     *
     * 业务流程：
     *   1. 从请求体取 code + state
     *   2. 调 AuthService.feishuLogin
     *   3. 返回登录结果
     */
    @PostMapping("/feishu/callback")
    @AuditLog(action = "FEISHU_LOGIN", targetType = "auth", description = "飞书OAuth登录")
    public ResponseResult<Map<String, Object>> feishuCallback(@RequestBody Map<String, String> request) {
        // 步骤1：取参数
        String code = request.get("code");
        String state = request.get("state");
        log.info("[鉴权] 飞书 OAuth 回调进入，code 长度={}", code == null ? 0 : code.length());
        if (code == null || code.isEmpty()) {
            log.warn("[鉴权] 飞书回调 code 为空，request keys={}", request.keySet());
            throw new BizException(1004, "飞书授权码不能为空");
        }

        // 步骤2：从 state 解析 configId（格式 random:configId），用对应公司凭证登录
        Long configId = parseConfigIdFromState(state);

        // 步骤3：登录并返回
        try {
            Map<String, Object> result = authService.feishuLogin(code, configId);
            log.info("[鉴权] 飞书 OAuth 登录成功，username={}", result.get("username"));
            return ResponseResult.success(result);
        } catch (BizException e) {
            // F01 调试日志：明确打印错误码和消息，便于排查"未绑定/授权失败"
            log.warn("[鉴权] 飞书 OAuth 业务异常：code={}, msg={}", e.getCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[鉴权] 飞书 OAuth 登录异常", e);
            throw new BizException(1004, "飞书授权失败：" + e.getMessage());
        }
    }

    /**
     * 从 state 解析 configId（格式 random:configId）
     *
     * @param state 状态串（无冒号=平台级，有冒号=公司级）
     * @return configId（null=平台级）
     */
    private Long parseConfigIdFromState(String state) {
        if (state == null || state.isEmpty()) {
            return null;
        }
        int idx = state.indexOf(':');
        if (idx < 0 || idx == state.length() - 1) {
            return null;
        }
        try {
            return Long.parseLong(state.substring(idx + 1));
        } catch (NumberFormatException e) {
            log.warn("[鉴权] state 中 configId 解析失败：{}", state);
            return null;
        }
    }

    /**
     * 修改密码
     *
     * 业务流程：
     *   1. 从 SecurityContext 获取当前用户
     *   2. 从请求体取 old_password + new_password
     *   3. 调 AuthService.changePassword
     *   4. 返回成功
     */
    @PostMapping("/change-password")
    public ResponseResult<Map<String, Object>> changePassword(@RequestBody Map<String, String> request) {
        // 步骤1：获取当前用户
        AuthUser authUser = getCurrentAuthUser();

        // 步骤2：取参数
        String oldPassword = request.get("old_password");
        String newPassword = request.get("new_password");
        if (oldPassword == null || oldPassword.isEmpty()
                || newPassword == null || newPassword.isEmpty()) {
            throw new BizException(1006, "旧密码和新密码不能为空");
        }

        // 步骤3：改密
        authService.changePassword(authUser.getUserId(), oldPassword, newPassword);

        // 步骤4：返回
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ResponseResult.success(result);
    }

    /**
     * 公司管理员注册
     *
     * 业务流程（docs/14 §3.6.1）：
     *   1. 限流检查（IP+小时）
     *   2. 调 RegisterService.registerAdmin（飞书验证+建表+创建配置+创建用户）
     *   3. 返回 user_id + org_name
     */
    @PostMapping("/register/admin")
    public ResponseResult registerAdmin(@RequestBody AdminRegisterRequest request,
                                        HttpServletRequest httpRequest) {
        // 步骤1：限流
        String ip = getClientIp(httpRequest);
        if (!registerRateLimiter.tryAcquire(ip)) {
            throw new BizException(1006, "注册过于频繁，请稍后再试");
        }

        log.info("[注册] 管理员注册请求，username={}, orgName={}, ip={}",
                request.getUsername(), request.getOrgName(), ip);

        // 步骤2：注册
        Map<String, Object> result = registerService.registerAdmin(request);

        // 步骤3：返回
        return ResponseResult.success(result);
    }

    /**
     * 普通用户注册
     *
     * 业务流程（docs/14 §3.6.2）：
     *   1. 限流检查（IP+小时）
     *   2. 调 RegisterService.registerStaff（注册码校验+创建用户）
     *   3. 返回 user_id
     */
    @PostMapping("/register/staff")
    public ResponseResult registerStaff(@RequestBody StaffRegisterRequest request,
                                        HttpServletRequest httpRequest) {
        // 步骤1：限流
        String ip = getClientIp(httpRequest);
        if (!registerRateLimiter.tryAcquire(ip)) {
            throw new BizException(1006, "注册过于频繁，请稍后再试");
        }

        log.info("[注册] 员工注册请求，username={}, ip={}", request.getUsername(), ip);

        // 步骤2：注册
        Map<String, Object> result = registerService.registerStaff(request);

        // 步骤3：返回
        return ResponseResult.success(result);
    }

    /**
     * 获取客户端 IP（优先取 X-Forwarded-For，兼容反向代理）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    // ==================== 内部方法 ====================

    /**
     * 从 SecurityContext 获取当前已认证用户
     */
    private AuthUser getCurrentAuthUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser)) {
            throw AuthError.invalidCredentials();
        }
        return (AuthUser) authentication.getPrincipal();
    }
}
