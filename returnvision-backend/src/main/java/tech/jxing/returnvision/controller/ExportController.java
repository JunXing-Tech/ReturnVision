package tech.jxing.returnvision.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.jxing.returnvision.audit.AuditLog;
import tech.jxing.returnvision.common.alert.AlertLevel;
import tech.jxing.returnvision.common.alert.AlertService;
import tech.jxing.returnvision.common.exception.AuthError;
import tech.jxing.returnvision.export.ExportService;
import tech.jxing.returnvision.security.AuthUser;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 【接口层】数据导出控制器（F02）
 *
 * 职责：提供退货记录导出接口（含审计 + 水印 + 限流 + 告警）
 * 层级：Controller 层
 * 关联：docs/06 第五章数据导出接口
 *
 * 权限：所有登录用户可访问（数据范围按角色过滤，STAFF 只能导出自己的）
 *
 * 防护策略：
 *   1. @AuditLog 强制审计（action=EXPORT_RECORDS）
 *   2. 水印（ExportService 生成 Excel 时加页眉）
 *   3. 限流（ExportService 调 ExportRateLimiter）
 *   4. 告警（导出后飞书群通知主管）
 */
@Slf4j
@RestController
@RequestMapping("/api/records")
public class ExportController {

    private final ExportService exportService;
    private final AlertService alertService;

    public ExportController(ExportService exportService, AlertService alertService) {
        this.exportService = exportService;
        this.alertService = alertService;
    }

    /**
     * 导出退货记录
     *
     * 业务流程：
     *   1. 从 SecurityContext 获取当前用户
     *   2. 调 ExportService.exportReturnRecords（含限流+水印+数量校验）
     *   3. 写入响应流（Excel 文件）
     *   4. 飞书群通知主管（异步）
     *
     * @param request  导出请求（筛选条件）
     * @param httpRequest HttpServletRequest（取 IP）
     * @param httpResponse HttpServletResponse（写 Excel 流）
     */
    @PostMapping("/export")
    @AuditLog(action = "EXPORT_RECORDS", targetType = "return_record", description = "导出退货记录", recordParams = true)
    public void exportRecords(@RequestBody Map<String, String> request,
                               HttpServletRequest httpRequest,
                               HttpServletResponse httpResponse) {
        // 步骤1：获取当前用户
        AuthUser currentUser = getCurrentAuthUser();

        // 步骤2：解析筛选条件
        String status = request.get("status");
        LocalDate startDate = parseDate(request.get("start_date"));
        LocalDate endDate = parseDate(request.get("end_date"));

        // 步骤3：导出（含限流+水印+数量校验）
        byte[] excelBytes = exportService.exportReturnRecords(currentUser, status, startDate, endDate, httpRequest);

        // 步骤4：写入响应流
        try {
            String filename = "return_records_" + java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
            httpResponse.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            httpResponse.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            httpResponse.getOutputStream().write(excelBytes);
            httpResponse.getOutputStream().flush();
        } catch (Exception e) {
            log.error("[导出管控] 写入响应流失败", e);
            throw new RuntimeException("导出失败：" + e.getMessage(), e);
        }

        // 步骤5：飞书群通知主管（异步，失败不影响导出）
        try {
            Map<String, Object> context = new HashMap<>();
            context.put("operator", currentUser.getUsername());
            context.put("rows", excelBytes.length);
            alertService.notify(AlertLevel.WARN, "DATA_EXPORT",
                    "用户 " + currentUser.getUsername() + " 导出了退货记录数据", context);
        } catch (Exception e) {
            log.warn("[导出管控] 飞书通知失败（不影响导出）: {}", e.getMessage());
        }
    }

    private AuthUser getCurrentAuthUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser)) {
            throw AuthError.invalidCredentials();
        }
        return (AuthUser) authentication.getPrincipal();
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            return null;
        }
    }
}
