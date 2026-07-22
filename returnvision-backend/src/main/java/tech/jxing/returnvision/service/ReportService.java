package tech.jxing.returnvision.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.jxing.returnvision.model.entity.ReturnRecord;
import tech.jxing.returnvision.model.mapper.ReturnRecordMapper;
import tech.jxing.returnvision.security.AuthUser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 【业务逻辑层】多维度退货报表服务（F04）
 *
 * 职责：聚合 return_records 数据，提供 4 个维度的报表
 * 层级：Service 层
 * 调用方：ReportController
 * 关联：docs/04 第 4.12 节、docs/10 第 7.3 节 F04
 *
 * 报表维度：
 *   1. category_breakdown：退货分类占比（return_category 分组）
 *   2. express_breakdown：快递公司占比（express_company 分组）
 *   3. reason_top10：退货原因 TOP10（return_reason 分组）
 *   4. trend：N 天趋势（DATE(created_at) 分组）
 *
 * 数据范围：
 *   - STAFF：只能看自己处理的记录（created_by = 当前用户）
 *   - SUPERVISOR/ADMIN：看全部记录
 *
 * 状态过滤：只统计 synced（已闭环的退货才有分析价值）
 */
@Service
@Slf4j
public class ReportService {

    private static final String STATUS_SYNCED = "synced";
    private static final String ROLE_STAFF = "STAFF";
    private static final String ROLE_SUPERVISOR = "SUPERVISOR";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final int DEFAULT_DAYS = 7;
    private static final int MAX_DAYS = 90;
    private static final int TOP_N = 10;

    private final ReturnRecordMapper recordMapper;

    public ReportService(ReturnRecordMapper recordMapper) {
        this.recordMapper = recordMapper;
    }

    /**
     * 获取多维度退货报表
     *
     * 实现步骤：
     *   1. 校验 days 参数（越界回退默认 7）
     *   2. 计算起始时间
     *   3. 构建基础查询条件（status=synced + 时间范围 + 数据范围）
     *   4. 查询 4 个维度聚合数据
     *   5. 组装返回
     *
     * @param days        统计天数（默认 7，越界回退）
     * @param currentUser 当前用户（用于数据范围过滤）
     * @return {category_breakdown, express_breakdown, reason_top10, trend}
     */
    public Map<String, Object> getReport(int days, AuthUser currentUser) {
        // 步骤1：校验 days
        if (days < 1 || days > MAX_DAYS) {
            days = DEFAULT_DAYS;
            log.info("[退货报表] days 越界，回退默认 {}", DEFAULT_DAYS);
        }

        // 步骤2：计算起始时间
        LocalDateTime startTime = LocalDateTime.now().minusDays(days);
        log.info("[退货报表] 开始查询，days={}, user={}, isStaff={}",
                days, currentUser.getUsername(), isStaff(currentUser));

        // 步骤3：查询 4 个维度
        List<Map<String, Object>> categoryBreakdown = buildCategoryBreakdown(startTime, currentUser);
        List<Map<String, Object>> expressBreakdown = buildExpressBreakdown(startTime, currentUser);
        List<Map<String, Object>> reasonTop10 = buildReasonTop10(startTime, currentUser);
        List<Map<String, Object>> trend = buildTrend(days, currentUser);

        // 步骤4：组装返回
        Map<String, Object> result = new HashMap<>();
        result.put("category_breakdown", categoryBreakdown);
        result.put("express_breakdown", expressBreakdown);
        result.put("reason_top10", reasonTop10);
        result.put("trend", trend);
        result.put("days", days);
        log.info("[退货报表] 查询完成，分类={}, 快递={}, 原因={}, 趋势={}",
                categoryBreakdown.size(), expressBreakdown.size(), reasonTop10.size(), trend.size());
        return result;
    }

    /**
     * 退货分类占比
     */
    private List<Map<String, Object>> buildCategoryBreakdown(LocalDateTime startTime, AuthUser user) {
        QueryWrapper<ReturnRecord> wrapper = new QueryWrapper<>();
        wrapper.select("IFNULL(NULLIF(return_category, ''), '未分类') as dim", "COUNT(*) as cnt")
                .eq("status", STATUS_SYNCED)
                .ge("created_at", startTime)
                .groupBy("dim")
                .orderByDesc("cnt");
        applyDataScope(wrapper, user);

        List<Map<String, Object>> raw = recordMapper.selectMaps(wrapper);
        return toBreakdown(raw);
    }

    /**
     * 快递公司占比
     */
    private List<Map<String, Object>> buildExpressBreakdown(LocalDateTime startTime, AuthUser user) {
        QueryWrapper<ReturnRecord> wrapper = new QueryWrapper<>();
        wrapper.select("IFNULL(NULLIF(express_company, ''), '未知') as dim", "COUNT(*) as cnt")
                .eq("status", STATUS_SYNCED)
                .ge("created_at", startTime)
                .groupBy("dim")
                .orderByDesc("cnt");
        applyDataScope(wrapper, user);

        List<Map<String, Object>> raw = recordMapper.selectMaps(wrapper);
        return toBreakdown(raw);
    }

    /**
     * 退货原因 TOP10
     */
    private List<Map<String, Object>> buildReasonTop10(LocalDateTime startTime, AuthUser user) {
        QueryWrapper<ReturnRecord> wrapper = new QueryWrapper<>();
        wrapper.select("IFNULL(NULLIF(return_reason, ''), '未标注') as dim", "COUNT(*) as cnt")
                .eq("status", STATUS_SYNCED)
                .ge("created_at", startTime)
                .groupBy("dim")
                .orderByDesc("cnt")
                .last("LIMIT " + TOP_N);
        applyDataScope(wrapper, user);

        List<Map<String, Object>> raw = recordMapper.selectMaps(wrapper);
        return toBreakdown(raw);
    }

    /**
     * N 天趋势
     */
    private List<Map<String, Object>> buildTrend(int days, AuthUser user) {
        LocalDateTime startTime = LocalDateTime.now().minusDays(days);
        QueryWrapper<ReturnRecord> wrapper = new QueryWrapper<>();
        wrapper.select("DATE(created_at) as date", "COUNT(*) as cnt")
                .eq("status", STATUS_SYNCED)
                .ge("created_at", startTime)
                .groupBy("DATE(created_at)")
                .orderByAsc("DATE(created_at)");
        applyDataScope(wrapper, user);

        List<Map<String, Object>> raw = recordMapper.selectMaps(wrapper);
        List<Map<String, Object>> trend = new ArrayList<>();
        for (Map<String, Object> row : raw) {
            Map<String, Object> item = new HashMap<>();
            item.put("date", row.get("date") != null ? row.get("date").toString() : "");
            item.put("count", row.get("cnt") != null ? Long.parseLong(row.get("cnt").toString()) : 0);
            trend.add(item);
        }
        return trend;
    }

    /**
     * 应用数据范围过滤（STAFF 只看自己）
     */
    private void applyDataScope(QueryWrapper<ReturnRecord> wrapper, AuthUser user) {
        if (isStaff(user)) {
            wrapper.eq("created_by", user.getUserId());
        }
    }

    /**
     * 判断是否为纯客服角色（不含 SUPERVISOR/ADMIN）
     */
    private boolean isStaff(AuthUser user) {
        List<String> roles = user.getRoles();
        return roles.contains(ROLE_STAFF)
                && !roles.contains(ROLE_SUPERVISOR)
                && !roles.contains(ROLE_ADMIN);
    }

    /**
     * 将聚合结果转为带 percentage 的 breakdown 列表
     */
    private List<Map<String, Object>> toBreakdown(List<Map<String, Object>> raw) {
        long total = 0;
        for (Map<String, Object> row : raw) {
            total += row.get("cnt") != null ? Long.parseLong(row.get("cnt").toString()) : 0;
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : raw) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", row.get("dim"));
            long count = row.get("cnt") != null ? Long.parseLong(row.get("cnt").toString()) : 0;
            item.put("count", count);
            // percentage 保留 2 位小数
            double pct = total > 0 ? Math.round(count * 10000.0 / total) / 100.0 : 0.0;
            item.put("percentage", pct);
            result.add(item);
        }
        return result;
    }
}