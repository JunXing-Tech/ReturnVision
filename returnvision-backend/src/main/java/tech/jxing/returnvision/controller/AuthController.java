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
import tech.jxing.returnvision.security.AuthUser;
import tech.jxing.returnvision.security.FeishuOAuthService;
import tech.jxing.returnvision.service.AuthService;

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

    public AuthController(AuthService authService, FeishuOAuthService feishuOAuthService) {
        this.authService = authService;
        this.feishuOAuthService = feishuOAuthService;
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
     * 获取飞书 OAuth 授权 URL
     *
     * 业务流程：
     *   1. 生成 state（防 CSRF）
     *   2. 调 FeishuOAuthService 生成授权 URL
     *   3. 返回 auth_url + state
     */
    @GetMapping("/feishu/url")
    public ResponseResult<Map<String, Object>> feishuAuthUrl() {
        // 步骤1：生成 state
        String state = UUID.randomUUID().toString().replace("-", "");

        // 步骤2：生成授权 URL
        String authUrl = feishuOAuthService.generateAuthUrl(state);

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
        log.info("[鉴权] 飞书 OAuth 回调进入，code 长度={}", code == null ? 0 : code.length());
        if (code == null || code.isEmpty()) {
            log.warn("[鉴权] 飞书回调 code 为空，request keys={}", request.keySet());
            throw new BizException(1004, "飞书授权码不能为空");
        }

        // 步骤2-3：登录并返回
        try {
            Map<String, Object> result = authService.feishuLogin(code);
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
