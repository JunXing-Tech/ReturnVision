package tech.jxing.returnvision.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.jxing.returnvision.audit.AuditService;
import tech.jxing.returnvision.common.ResponseResult;

import java.time.LocalDate;
import java.util.Map;

/**
 * 【接口层】审计日志查询控制器
 *
 * 职责：提供审计日志查询接口（仅主管/管理员可访问）
 * 层级：Controller 层
 * 关联：docs/06 第四章审计日志接口
 *
 * 权限：@PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")，仅主管/管理员可访问
 */
@Slf4j
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasAnyRole('SUPERVISOR', 'ADMIN')")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * 分页查询审计日志
     *
     * 业务流程：
     *   1. 解析查询参数
     *   2. 调 AuditService.queryLogs
     *   3. 返回结果
     */
    @GetMapping("/logs")
    public ResponseResult<Map<String, Object>> queryLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {

        // 步骤1-2：查询
        Map<String, Object> result = auditService.queryLogs(page, size, userId, action, startDate, endDate);

        // 步骤3：返回
        return ResponseResult.success(result);
    }
}
