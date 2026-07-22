package tech.jxing.returnvision.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import tech.jxing.returnvision.model.entity.ReturnRecord;
import tech.jxing.returnvision.model.mapper.ReturnRecordMapper;
import tech.jxing.returnvision.security.AuthUser;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 【测试类】ReportService 集成测试（@SpringBootTest + H2）
 * <p>
 * 1. 用真实 H2 + 测试数据验证 4 个维度的聚合查询
 * 2. 覆盖数据范围权限（STAFF 限自己 / SUPERVISOR/ADMIN 全部）
 * 3. 覆盖边界：空数据 / 时间范围 / TOP10 截断 / 空值兜底
 * 4. 对应清单：test-checklists/2026-07-21_F04-退货报表.md AT-01~AT-16
 * </p>
 *
 * @author ReturnVision
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReportServiceTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private ReturnRecordMapper recordMapper;

    private AuthUser staffUser() {
        return new AuthUser(100L, "staff1", "", true, List.of("STAFF"));
    }

    private AuthUser supervisorUser() {
        return new AuthUser(200L, "sup1", "", true, List.of("SUPERVISOR"));
    }

    private AuthUser adminUser() {
        return new AuthUser(300L, "admin1", "", true, List.of("ADMIN"));
    }

    private ReturnRecord syncedRecord(String waybill, String category, String express,
                                       String reason, Long createdBy) {
        ReturnRecord r = new ReturnRecord();
        r.setWaybillNo(waybill);
        r.setReturnCategory(category);
        r.setExpressCompany(express);
        r.setReturnReason(reason);
        r.setStatus("synced");
        r.setCreatedBy(createdBy);
        r.setCreatedAt(LocalDateTime.now());
        r.setReturnDate(LocalDate.now());
        return r;
    }

    // ============ AT-01~AT-04：4 个维度 ============

    @Test
    @DisplayName("AT-01：退货分类占比 - 返回 return_category 分组 + count + percentage")
    void categoryBreakdown_shouldGroupByCategory() {
        recordMapper.insert(syncedRecord("SF001", "质量问题", "顺丰", "破损", 100L));
        recordMapper.insert(syncedRecord("SF002", "质量问题", "顺丰", "瑕疵", 100L));
        recordMapper.insert(syncedRecord("SF003", "物流问题", "京东", "延误", 100L));

        Map<String, Object> result = reportService.getReport(7, supervisorUser());
        List<Map<String, Object>> breakdown = (List<Map<String, Object>>) result.get("category_breakdown");

        assertTrue(breakdown.size() >= 2);
        Map<String, Object> quality = breakdown.stream()
                .filter(b -> "质量问题".equals(b.get("name"))).findFirst().orElse(null);
        assertNotNull(quality);
        assertEquals(2L, quality.get("count"));
        assertTrue((double) quality.get("percentage") > 0);
    }

    @Test
    @DisplayName("AT-02：快递公司占比 - 返回 express_company 分组")
    void expressBreakdown_shouldGroupByExpress() {
        recordMapper.insert(syncedRecord("SF001", "质量问题", "顺丰", "破损", 100L));
        recordMapper.insert(syncedRecord("JD001", "物流问题", "京东", "延误", 100L));

        Map<String, Object> result = reportService.getReport(7, supervisorUser());
        List<Map<String, Object>> breakdown = (List<Map<String, Object>>) result.get("express_breakdown");

        assertTrue(breakdown.size() >= 2);
        assertTrue(breakdown.stream().anyMatch(b -> "顺丰".equals(b.get("name"))));
        assertTrue(breakdown.stream().anyMatch(b -> "京东".equals(b.get("name"))));
    }

    @Test
    @DisplayName("AT-03：退货原因 TOP10 - 按 return_reason 分组，取前 10")
    void reasonTop10_shouldLimitTo10() {
        for (int i = 0; i < 15; i++) {
            recordMapper.insert(syncedRecord("WB" + i, "质量问题", "顺丰", "原因" + i, 100L));
        }

        Map<String, Object> result = reportService.getReport(7, supervisorUser());
        List<Map<String, Object>> top10 = (List<Map<String, Object>>) result.get("reason_top10");

        assertEquals(10, top10.size(), "应只返回 10 项");
    }

    @Test
    @DisplayName("AT-04：N 天趋势 - 按 DATE(created_at) 分组")
    void trend_shouldGroupByDate() {
        recordMapper.insert(syncedRecord("SF001", "质量问题", "顺丰", "破损", 100L));
        recordMapper.insert(syncedRecord("SF002", "质量问题", "顺丰", "瑕疵", 100L));

        Map<String, Object> result = reportService.getReport(7, supervisorUser());
        List<Map<String, Object>> trend = (List<Map<String, Object>>) result.get("trend");

        assertFalse(trend.isEmpty());
        Map<String, Object> today = trend.get(trend.size() - 1);
        assertNotNull(today.get("date"));
        assertEquals(2L, today.get("count"));
    }

    // ============ AT-05：status 过滤 ============

    @Test
    @DisplayName("AT-05：只统计 synced 状态（confirmed/pending 不算）")
    void report_shouldOnlyCountSynced() {
        ReturnRecord synced = syncedRecord("SF001", "质量问题", "顺丰", "破损", 100L);
        recordMapper.insert(synced);

        ReturnRecord pending = syncedRecord("SF002", "物流问题", "京东", "延误", 100L);
        pending.setStatus("pending");
        recordMapper.insert(pending);

        Map<String, Object> result = reportService.getReport(7, supervisorUser());
        List<Map<String, Object>> breakdown = (List<Map<String, Object>>) result.get("category_breakdown");

        // 只应有"质量问题"1 条（pending 的"物流问题"不算）
        assertEquals(1, breakdown.size());
        assertEquals("质量问题", breakdown.get(0).get("name"));
    }

    // ============ AT-06/AT-07：时间范围 ============

    @Test
    @DisplayName("AT-06：时间范围 7 天筛选生效")
    void report_shouldFilterByDays() {
        // 8 天前的记录不应出现
        ReturnRecord old = syncedRecord("SF_OLD", "质量问题", "顺丰", "破损", 100L);
        old.setCreatedAt(LocalDateTime.now().minusDays(8));
        recordMapper.insert(old);

        // 今天的记录应出现
        recordMapper.insert(syncedRecord("SF_NEW", "物流问题", "京东", "延误", 100L));

        Map<String, Object> result = reportService.getReport(7, supervisorUser());
        List<Map<String, Object>> breakdown = (List<Map<String, Object>>) result.get("category_breakdown");

        // 只应有"物流问题"（8天前的"质量问题"超出范围）
        assertTrue(breakdown.stream().noneMatch(b -> "质量问题".equals(b.get("name"))));
    }

    @Test
    @DisplayName("AT-07：days 越界（<1 或 >90）-> 回退默认 7")
    void report_daysOutOfRange_shouldFallbackTo7() {
        recordMapper.insert(syncedRecord("SF001", "质量问题", "顺丰", "破损", 100L));

        Map<String, Object> result = reportService.getReport(0, supervisorUser());
        assertEquals(7, result.get("days"), "days=0 应回退为 7");

        Map<String, Object> result2 = reportService.getReport(100, supervisorUser());
        assertEquals(7, result2.get("days"), "days=100 应回退为 7");
    }

    // ============ AT-08：无数据兜底 ============

    @Test
    @DisplayName("AT-08：无数据时返回空列表（不报错，不返回 null）")
    void report_noData_shouldReturnEmptyLists() {
        Map<String, Object> result = reportService.getReport(7, supervisorUser());

        assertNotNull(result.get("category_breakdown"));
        assertNotNull(result.get("express_breakdown"));
        assertNotNull(result.get("reason_top10"));
        assertNotNull(result.get("trend"));
        assertTrue(((List<?>) result.get("category_breakdown")).isEmpty());
    }

    // ============ AT-09：percentage 精度 ============

    @Test
    @DisplayName("AT-09：percentage 计算正确（count/total*100，保留 2 位小数）")
    void percentage_shouldBeCorrect() {
        // 3 条记录，2 条质量问题 -> 66.67%
        recordMapper.insert(syncedRecord("SF001", "质量问题", "顺丰", "破损", 100L));
        recordMapper.insert(syncedRecord("SF002", "质量问题", "顺丰", "瑕疵", 100L));
        recordMapper.insert(syncedRecord("SF003", "物流问题", "京东", "延误", 100L));

        Map<String, Object> result = reportService.getReport(7, supervisorUser());
        List<Map<String, Object>> breakdown = (List<Map<String, Object>>) result.get("category_breakdown");

        Map<String, Object> quality = breakdown.stream()
                .filter(b -> "质量问题".equals(b.get("name"))).findFirst().orElse(null);
        assertNotNull(quality);
        assertEquals(66.67, (double) quality.get("percentage"), 0.01);
    }

    // ============ AT-11/AT-12/AT-26：空值兜底 ============

    @Test
    @DisplayName("AT-11：return_category 为空的记录归到'未分类'")
    void emptyCategory_shouldFallbackToUnclassified() {
        ReturnRecord r = syncedRecord("SF001", "", "顺丰", "破损", 100L);
        recordMapper.insert(r);

        Map<String, Object> result = reportService.getReport(7, supervisorUser());
        List<Map<String, Object>> breakdown = (List<Map<String, Object>>) result.get("category_breakdown");

        assertTrue(breakdown.stream().anyMatch(b -> "未分类".equals(b.get("name"))));
    }

    @Test
    @DisplayName("AT-12：express_company 为空的记录归到'未知'")
    void emptyExpress_shouldFallbackToUnknown() {
        ReturnRecord r = syncedRecord("SF001", "质量问题", "", "破损", 100L);
        recordMapper.insert(r);

        Map<String, Object> result = reportService.getReport(7, supervisorUser());
        List<Map<String, Object>> breakdown = (List<Map<String, Object>>) result.get("express_breakdown");

        assertTrue(breakdown.stream().anyMatch(b -> "未知".equals(b.get("name"))));
    }

    @Test
    @DisplayName("AT-26：return_reason 为空的记录归到'未标注'")
    void emptyReason_shouldFallbackToUnlabeled() {
        ReturnRecord r = syncedRecord("SF001", "质量问题", "顺丰", "", 100L);
        recordMapper.insert(r);

        Map<String, Object> result = reportService.getReport(7, supervisorUser());
        List<Map<String, Object>> top10 = (List<Map<String, Object>>) result.get("reason_top10");

        assertTrue(top10.stream().anyMatch(b -> "未标注".equals(b.get("name"))));
    }

    // ============ AT-13~AT-16：数据范围权限 ============

    @Test
    @DisplayName("AT-13：STAFF 只看到自己 created_by 的记录")
    void staff_shouldOnlySeeOwnRecords() {
        recordMapper.insert(syncedRecord("SF001", "质量问题", "顺丰", "破损", 100L));
        recordMapper.insert(syncedRecord("SF002", "物流问题", "京东", "延误", 200L));

        Map<String, Object> result = reportService.getReport(7, staffUser());
        List<Map<String, Object>> breakdown = (List<Map<String, Object>>) result.get("category_breakdown");

        // STAFF(user_id=100) 只看到 1 条"质量问题"
        assertEquals(1, breakdown.size());
        assertEquals("质量问题", breakdown.get(0).get("name"));
    }

    @Test
    @DisplayName("AT-14：SUPERVISOR 看到全部记录")
    void supervisor_shouldSeeAllRecords() {
        recordMapper.insert(syncedRecord("SF001", "质量问题", "顺丰", "破损", 100L));
        recordMapper.insert(syncedRecord("SF002", "物流问题", "京东", "延误", 200L));

        Map<String, Object> result = reportService.getReport(7, supervisorUser());
        List<Map<String, Object>> breakdown = (List<Map<String, Object>>) result.get("category_breakdown");

        assertEquals(2, breakdown.size());
    }

    @Test
    @DisplayName("AT-15：ADMIN 看到全部记录")
    void admin_shouldSeeAllRecords() {
        recordMapper.insert(syncedRecord("SF001", "质量问题", "顺丰", "破损", 100L));
        recordMapper.insert(syncedRecord("SF002", "物流问题", "京东", "延误", 200L));

        Map<String, Object> result = reportService.getReport(7, adminUser());
        List<Map<String, Object>> breakdown = (List<Map<String, Object>>) result.get("category_breakdown");

        assertEquals(2, breakdown.size());
    }

    @Test
    @DisplayName("AT-16：STAFF 同时有 ADMIN 角色时按 ADMIN 处理（看全部）")
    void staffWithAdmin_shouldSeeAll() {
        recordMapper.insert(syncedRecord("SF001", "质量问题", "顺丰", "破损", 100L));
        recordMapper.insert(syncedRecord("SF002", "物流问题", "京东", "延误", 200L));

        AuthUser staffAdmin = new AuthUser(100L, "staff_admin", "", true, Arrays.asList("STAFF", "ADMIN"));
        Map<String, Object> result = reportService.getReport(7, staffAdmin);
        List<Map<String, Object>> breakdown = (List<Map<String, Object>>) result.get("category_breakdown");

        assertEquals(2, breakdown.size(), "STAFF+ADMIN 应按 ADMIN 看全部");
    }

    // ============ AT-21：返回结构 ============

    @Test
    @DisplayName("AT-21：返回的 data 含 4 个维度 key + days")
    void result_shouldContainFourKeys() {
        Map<String, Object> result = reportService.getReport(7, supervisorUser());

        assertTrue(result.containsKey("category_breakdown"));
        assertTrue(result.containsKey("express_breakdown"));
        assertTrue(result.containsKey("reason_top10"));
        assertTrue(result.containsKey("trend"));
        assertTrue(result.containsKey("days"));
        assertEquals(5, result.size());
    }
}