package tech.jxing.returnvision.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.jxing.returnvision.common.ResponseResult;
import tech.jxing.returnvision.security.AuthUser;
import tech.jxing.returnvision.service.ReportService;

import java.util.Map;

/**
 * 【接口层】多维度退货报表控制器（F04）
 *
 * 职责：提供退货分类占比 / 快递公司占比 / 退货原因 TOP10 / N 天趋势 4 个维度的报表
 * 层级：Controller 层
 * 关联：docs/06 第七章报表接口、docs/10 第 7.3 节 F04
 *
 * 权限：所有登录用户可访问（数据范围按角色过滤，STAFF 只能看自己的）
 *
 * 接口列表：
 *   GET /api/reports - 多维度退货报表
 */
@Slf4j
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * 获取多维度退货报表
     *
     * 业务流程：
     *   1. 获取当前登录用户
     *   2. 调 ReportService 查询报表数据
     *   3. 返回 4 个维度数据
     *
     * @param days 统计天数，默认 7，可选 7/30/90
     */
    @GetMapping
    public ResponseResult<Map<String, Object>> getReport(
            @RequestParam(value = "days", defaultValue = "7") int days) {
        // 步骤1：获取当前用户
        AuthUser currentUser = getCurrentAuthUser();

        // 步骤2：查询报表
        Map<String, Object> report = reportService.getReport(days, currentUser);

        // 步骤3：返回
        log.info("[退货报表] 查询完成，user={}, days={}", currentUser.getUsername(), days);
        return ResponseResult.success(report);
    }

    /**
     * 从 SecurityContext 获取当前登录用户
     */
    private AuthUser getCurrentAuthUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser)) {
            throw new IllegalStateException("无法获取当前登录用户");
        }
        return (AuthUser) authentication.getPrincipal();
    }
}