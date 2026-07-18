package tech.jxing.returnvision.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import tech.jxing.returnvision.common.ResponseResult;
import tech.jxing.returnvision.common.exception.AuthError;
import tech.jxing.returnvision.common.exception.BizException;
import tech.jxing.returnvision.model.dto.CreateUserRequest;
import tech.jxing.returnvision.model.dto.UpdateUserRequest;
import tech.jxing.returnvision.security.AuthUser;
import tech.jxing.returnvision.service.AdminUserService;

import java.util.HashMap;
import java.util.Map;

/**
 * 【接口层】用户管理控制器（管理员后台）
 *
 * 职责：提供用户增删改查 + 重置密码 5 个接口
 * 层级：Controller 层
 * 关联：docs/06 第三章用户管理接口
 *
 * 权限：所有接口 @PreAuthorize("hasRole('ADMIN')")，仅管理员可访问
 *
 * 接口列表：
 *   GET    /api/admin/users                       - 用户列表
 *   POST   /api/admin/users                       - 创建用户
 *   PUT    /api/admin/users/{id}                  - 编辑用户
 *   DELETE /api/admin/users/{id}                  - 删除用户
 *   POST   /api/admin/users/{id}/reset-password   - 重置密码
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * 用户列表（含角色）
     *
     * 业务流程：
     *   1. 调 AdminUserService.listUsers
     *   2. 返回用户列表
     */
    @GetMapping
    public ResponseResult<Map<String, Object>> listUsers() {
        Map<String, Object> result = adminUserService.listUsers();
        return ResponseResult.success(result);
    }

    /**
     * 创建用户
     *
     * 业务流程：
     *   1. 校验必填字段
     *   2. 调 AdminUserService.createUser
     *   3. 返回新用户 id + username
     */
    @PostMapping
    public ResponseResult<Map<String, Object>> createUser(@RequestBody CreateUserRequest request) {
        // 步骤1：校验必填字段
        if (request.getUsername() == null || request.getUsername().isEmpty()) {
            throw new BizException(1007, "用户名不能为空");
        }
        if (request.getPassword() == null || request.getPassword().isEmpty()) {
            throw new BizException(1007, "密码不能为空");
        }
        if (request.getRoleCodes() == null || request.getRoleCodes().isEmpty()) {
            throw new BizException(1007, "角色不能为空");
        }

        // 步骤2-3：创建并返回
        Map<String, Object> result = adminUserService.createUser(request);
        return ResponseResult.success(result);
    }

    /**
     * 编辑用户
     *
     * 业务流程：
     *   1. 从 SecurityContext 获取当前操作者
     *   2. 调 AdminUserService.updateUser（含安全约束）
     *   3. 返回成功
     */
    @PutMapping("/{id}")
    public ResponseResult<Map<String, Object>> updateUser(@PathVariable Long id,
                                                           @RequestBody UpdateUserRequest request) {
        // 步骤1：获取当前操作者
        AuthUser currentUser = getCurrentAuthUser();

        // 步骤2：更新
        adminUserService.updateUser(id, request, currentUser);

        // 步骤3：返回
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ResponseResult.success(result);
    }

    /**
     * 删除用户
     *
     * 业务流程：
     *   1. 从 SecurityContext 获取当前操作者
     *   2. 调 AdminUserService.deleteUser（含安全约束）
     *   3. 返回成功
     */
    @DeleteMapping("/{id}")
    public ResponseResult<Map<String, Object>> deleteUser(@PathVariable Long id) {
        // 步骤1：获取当前操作者
        AuthUser currentUser = getCurrentAuthUser();

        // 步骤2：删除
        adminUserService.deleteUser(id, currentUser);

        // 步骤3：返回
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ResponseResult.success(result);
    }

    /**
     * 重置密码
     *
     * 业务流程：
     *   1. 校验新密码非空
     *   2. 调 AdminUserService.resetPassword
     *   3. 返回成功
     */
    @PostMapping("/{id}/reset-password")
    public ResponseResult<Map<String, Object>> resetPassword(@PathVariable Long id,
                                                              @RequestBody Map<String, String> request) {
        // 步骤1：校验
        String newPassword = request.get("new_password");
        if (newPassword == null || newPassword.isEmpty()) {
            throw new BizException(1007, "新密码不能为空");
        }

        // 步骤2：重置
        adminUserService.resetPassword(id, newPassword);

        // 步骤3：返回
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
