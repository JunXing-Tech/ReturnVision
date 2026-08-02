package tech.jxing.returnvision.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import tech.jxing.returnvision.audit.AuditLog;
import tech.jxing.returnvision.common.ResponseResult;
import tech.jxing.returnvision.common.exception.AuthError;
import tech.jxing.returnvision.common.exception.BizException;
import tech.jxing.returnvision.common.util.RegisterCodeGenerator;
import tech.jxing.returnvision.model.entity.RegisterCode;
import tech.jxing.returnvision.model.mapper.RegisterCodeMapper;
import tech.jxing.returnvision.security.AuthUser;
import tech.jxing.returnvision.security.TenantContext;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 【接口层】注册码管理控制器（管理员后台）
 *
 * 职责：提供注册码查询/生成/撤销 3 个接口
 * 层级：Controller 层
 * 关联：docs/06 §8.4.2
 *
 * 权限：所有接口 @PreAuthorize("hasRole('ADMIN')")，仅管理员可访问
 *       生成注册码需公司ADMIN（feishuConfigId 非空），注册码绑当前公司 feishu_config_id
 *
 * 数据范围：
 *   - 平台 ADMIN（feishuConfigId=null）：看所有公司注册码
 *   - 公司 ADMIN（feishuConfigId=非空）：只看自己公司注册码（按 feishu_config_id 过滤）
 *
 * 接口列表：
 *   GET    /api/admin/register-code        - 注册码列表（公司ADMIN只看自己公司）
 *   POST   /api/admin/register-code        - 生成注册码（公司ADMIN，绑自己feishu_config_id）
 *   DELETE /api/admin/register-code/{id}   - 撤销注册码（status=revoked）
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/register-code")
@PreAuthorize("hasRole('ADMIN')")
public class RegisterCodeController {

    private static final DateTimeFormatter DATETIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_REVOKED = "revoked";
    private static final int DEFAULT_MAX_USES = 1;

    private final RegisterCodeMapper registerCodeMapper;

    public RegisterCodeController(RegisterCodeMapper registerCodeMapper) {
        this.registerCodeMapper = registerCodeMapper;
    }

    /**
     * 注册码列表
     *
     * 业务流程：
     *   1. 取当前用户 feishuConfigId
     *   2. 平台ADMIN（null）查全部；公司ADMIN 只查自己公司（按 feishu_config_id 过滤）
     *   3. 返回列表
     */
    @GetMapping
    public ResponseResult<List<Map<String, Object>>> listCodes() {
        // 步骤1：取当前用户 feishuConfigId
        Long currentConfigId = TenantContext.currentFeishuConfigId();

        // 步骤2：按数据范围查询
        LambdaQueryWrapper<RegisterCode> wrapper = new LambdaQueryWrapper<>();
        if (currentConfigId != null) {
            wrapper.eq(RegisterCode::getFeishuConfigId, currentConfigId);
        }
        List<RegisterCode> codes = registerCodeMapper.selectList(wrapper);

        // 步骤3：组装并返回
        List<Map<String, Object>> list = new ArrayList<>();
        for (RegisterCode code : codes) {
            list.add(buildCodeMap(code));
        }
        return ResponseResult.success(list);
    }

    /**
     * 生成注册码
     *
     * 业务流程：
     *   1. 校验当前用户为公司ADMIN（feishuConfigId 非空）
     *   2. 取当前用户 userId 作为 created_by
     *   3. 用 RegisterCodeGenerator.generate() 生成 8 位码
     *   4. 组装 RegisterCode（绑当前 feishu_config_id，used_count=0，status=active）并 insert
     *   5. 返回新注册码信息
     *
     * @param request 含 max_uses（可选，默认1）、expires_at（可选，格式 yyyy-MM-dd HH:mm:ss，空=永不过期）
     */
    @PostMapping
    @AuditLog(action = "CREATE_REGISTER_CODE", targetType = "register_code", description = "生成注册码")
    public ResponseResult<Map<String, Object>> createCode(@RequestBody Map<String, Object> request) {
        // 步骤1：校验公司ADMIN（feishuConfigId 非空）
        Long currentConfigId = TenantContext.currentFeishuConfigId();
        if (currentConfigId == null) {
            throw new BizException(403, "仅公司管理员可操作");
        }

        // 步骤2：取 created_by
        AuthUser currentUser = getCurrentAuthUser();

        // 步骤3：生成 8 位注册码
        String code = RegisterCodeGenerator.generate();

        // 步骤4：组装并插入
        RegisterCode registerCode = new RegisterCode();
        registerCode.setCode(code);
        registerCode.setFeishuConfigId(currentConfigId);
        registerCode.setMaxUses(parseMaxUses(request.get("max_uses")));
        registerCode.setUsedCount(0);
        registerCode.setExpiresAt(parseExpiresAt(request.get("expires_at")));
        registerCode.setStatus(STATUS_ACTIVE);
        registerCode.setCreatedBy(currentUser.getUserId());

        registerCodeMapper.insert(registerCode);
        log.info("[注册码] 生成成功，id={}, code={}, configId={}, 创建人={}",
                registerCode.getId(), code, currentConfigId, currentUser.getUserId());

        // 步骤5：返回
        Map<String, Object> result = new HashMap<>();
        result.put("id", registerCode.getId());
        result.put("code", code);
        result.put("max_uses", registerCode.getMaxUses());
        result.put("expires_at", registerCode.getExpiresAt() != null
                ? registerCode.getExpiresAt().format(DATETIME_FORMATTER) : null);
        return ResponseResult.success(result);
    }

    /**
     * 撤销注册码
     *
     * 业务流程：
     *   1. 查原注册码，不存在抛异常
     *   2. 公司ADMIN 校验只能撤销自己公司的码（平台ADMIN 不限）
     *   3. status 置 revoked
     *   4. updateById 持久化
     *   5. 返回成功
     *
     * @param id 注册码ID
     */
    @DeleteMapping("/{id}")
    @AuditLog(action = "REVOKE_REGISTER_CODE", targetType = "register_code", description = "撤销注册码")
    public ResponseResult<Map<String, Object>> revokeCode(@PathVariable Long id) {
        // 步骤1：查原注册码
        RegisterCode registerCode = registerCodeMapper.selectById(id);
        if (registerCode == null) {
            throw new BizException(1007, "注册码不存在");
        }

        // 步骤2：公司ADMIN 只能撤销自己公司的码（平台ADMIN currentConfigId=null 不受限）
        Long currentConfigId = TenantContext.currentFeishuConfigId();
        if (currentConfigId != null
                && !currentConfigId.equals(registerCode.getFeishuConfigId())) {
            throw new BizException(403, "只能撤销本公司的注册码");
        }

        // 步骤3-4：置 revoked 并持久化
        registerCode.setStatus(STATUS_REVOKED);
        registerCodeMapper.updateById(registerCode);

        log.info("[注册码] 撤销成功，id={}, code={}", id, registerCode.getCode());

        // 步骤5：返回
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

    /**
     * 解析 max_uses（null/非法时取默认值 1）
     */
    private int parseMaxUses(Object value) {
        if (value instanceof Number) {
            int maxUses = ((Number) value).intValue();
            return maxUses > 0 ? maxUses : DEFAULT_MAX_USES;
        }
        return DEFAULT_MAX_USES;
    }

    /**
     * 解析 expires_at（null/空=永不过期，格式 yyyy-MM-dd HH:mm:ss）
     */
    private LocalDateTime parseExpiresAt(Object value) {
        if (value == null || value.toString().isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.toString(), DATETIME_FORMATTER);
        } catch (Exception e) {
            throw new BizException(1007, "expires_at 格式错误，应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    /**
     * 组装注册码 Map（字段名用下划线，与 AdminUserController 响应风格一致）
     */
    private Map<String, Object> buildCodeMap(RegisterCode code) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", code.getId());
        map.put("code", code.getCode());
        map.put("feishu_config_id", code.getFeishuConfigId());
        map.put("max_uses", code.getMaxUses());
        map.put("used_count", code.getUsedCount());
        map.put("expires_at", code.getExpiresAt() != null
                ? code.getExpiresAt().format(DATETIME_FORMATTER) : null);
        map.put("status", code.getStatus());
        map.put("created_by", code.getCreatedBy());
        map.put("created_at", code.getCreatedAt());
        return map;
    }
}
