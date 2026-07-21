package tech.jxing.returnvision.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 【测试类】ValidatorService 单元测试
 * <p>
 * 1. 纯逻辑测试，零外部依赖，直接 new ValidatorService() 即可
 * 2. 覆盖 5 项校验的所有分支 + 边界 + 汇总逻辑
 * 3. 对应清单：test-checklists/2026-07-21_ValidatorService-单元测试.md AT-01~AT-24
 * </p>
 *
 * @author ReturnVision
 */
class ValidatorServiceTest {

    private final ValidatorService service = new ValidatorService();

    /** 构造一份合法的 OCR 结果，测试时按需覆盖某字段 */
    private Map<String, Object> validData() {
        Map<String, Object> data = new HashMap<>();
        data.put("waybill_no", "SF12345678");
        data.put("rec_phone", "13812345678");
        data.put("rec_address", "北京市朝阳区某某街");
        data.put("rec_name", "张三");
        data.put("express_company", "顺丰");
        return data;
    }

    @SuppressWarnings("unchecked")
    private List<String> errors(Map<String, Object> result) {
        return (List<String>) result.get("errors");
    }

    @SuppressWarnings("unchecked")
    private List<String> warnings(Map<String, Object> result) {
        return (List<String>) result.get("warnings");
    }

    // ============ happy path 与总体契约 ============

    @Test
    @DisplayName("AT-01：全部字段合法 -> passed=true, need_manual=false, errors=空")
    void allValid_shouldPassWithoutManual() {
        Map<String, Object> result = service.validate(validData());

        assertEquals(true, result.get("passed"), "passed 应为 true");
        assertEquals(false, result.get("need_manual"), "need_manual 应为 false");
        assertTrue(errors(result).isEmpty(), "errors 应为空");
        assertTrue(warnings(result).isEmpty(), "warnings 应为空");
    }

    @Test
    @DisplayName("AT-24：返回 Map 包含 4 个 key（passed/need_manual/errors/warnings）")
    void result_shouldContainFourKeys() {
        Map<String, Object> result = service.validate(validData());

        assertTrue(result.containsKey("passed"));
        assertTrue(result.containsKey("need_manual"));
        assertTrue(result.containsKey("errors"));
        assertTrue(result.containsKey("warnings"));
        assertEquals(4, result.size());
    }

    // ============ 运单号校验 ============

    @Test
    @DisplayName("AT-02：运单号为空 -> errors 含 \"运单号为空\", passed=false")
    void emptyWaybill_shouldAddError() {
        Map<String, Object> data = validData();
        data.put("waybill_no", "");

        Map<String, Object> result = service.validate(data);

        assertEquals(false, result.get("passed"));
        assertTrue(errors(result).stream().anyMatch(e -> e.contains("运单号为空")));
    }

    @Test
    @DisplayName("AT-03：运单号格式异常（5 位短号 12345）-> warnings 含 \"运单号格式异常\", need_manual=true")
    void malformedWaybill_shouldWarningAndNeedManual() {
        Map<String, Object> data = validData();
        data.put("waybill_no", "12345");

        Map<String, Object> result = service.validate(data);

        assertTrue(warnings(result).stream().anyMatch(w -> w.contains("运单号格式异常")));
        assertEquals(true, result.get("need_manual"), "格式异常应触发 need_manual");
    }

    @ParameterizedTest(name = "运单号 {0} 应无 warning（合法边界）")
    @DisplayName("AT-04/AT-05/AT-08：合法运单号边界")
    @ValueSource(strings = {"12345678", "12345678901234567890", "SF12345678"})
    void validWaybillLength_shouldNotWarn(String waybillNo) {
        Map<String, Object> data = validData();
        data.put("waybill_no", waybillNo);

        Map<String, Object> result = service.validate(data);

        assertTrue(warnings(result).stream().noneMatch(w -> w.contains("运单号格式异常")));
    }

    @ParameterizedTest(name = "运单号 {0} 应 warning（越界）")
    @DisplayName("AT-06/AT-07/AT-09：非法运单号边界")
    @ValueSource(strings = {"1234567", "123456789012345678901", "ABCD12345678"})
    void invalidWaybillLength_shouldWarn(String waybillNo) {
        Map<String, Object> data = validData();
        data.put("waybill_no", waybillNo);

        Map<String, Object> result = service.validate(data);

        assertTrue(warnings(result).stream().anyMatch(w -> w.contains("运单号格式异常")));
    }

    // ============ 电话校验 ============

    @Test
    @DisplayName("AT-10：收件人电话为空 -> warnings 含 \"收件人电话为空\"")
    void emptyPhone_shouldWarn() {
        Map<String, Object> data = validData();
        data.put("rec_phone", "");

        Map<String, Object> result = service.validate(data);

        assertTrue(warnings(result).stream().anyMatch(w -> w.contains("收件人电话为空")));
    }

    @ParameterizedTest(name = "电话 {0} 应无 warning（合法）")
    @DisplayName("AT-11/AT-12：合法手机号/座机")
    @ValueSource(strings = {"13812345678", "19912345678", "010-12345678", "0755-1234567"})
    void validPhone_shouldNotWarn(String phone) {
        Map<String, Object> data = validData();
        data.put("rec_phone", phone);

        Map<String, Object> result = service.validate(data);

        assertTrue(warnings(result).stream().noneMatch(w -> w.contains("收件人电话")));
    }

    @ParameterizedTest(name = "电话 {0} 应 warning（非法格式）")
    @DisplayName("AT-13：非法电话格式")
    @ValueSource(strings = {"12345", "abc12345", "0123-4"})
    void invalidPhone_shouldWarn(String phone) {
        Map<String, Object> data = validData();
        data.put("rec_phone", phone);

        Map<String, Object> result = service.validate(data);

        assertTrue(warnings(result).stream().anyMatch(w -> w.contains("收件人电话格式异常")));
    }

    // ============ 地址校验 ============

    @Test
    @DisplayName("AT-14：地址为空 -> errors 含 \"收件人地址为空\", passed=false")
    void emptyAddress_shouldAddError() {
        Map<String, Object> data = validData();
        data.put("rec_address", "");

        Map<String, Object> result = service.validate(data);

        assertEquals(false, result.get("passed"));
        assertTrue(errors(result).stream().anyMatch(e -> e.contains("收件人地址为空")));
    }

    @Test
    @DisplayName("AT-15：地址非空但 <5 字 -> warnings 含 \"收件人地址过短\"")
    void shortAddress_shouldWarn() {
        Map<String, Object> data = validData();
        data.put("rec_address", "北京");

        Map<String, Object> result = service.validate(data);

        assertTrue(warnings(result).stream().anyMatch(w -> w.contains("收件人地址过短")));
    }

    @Test
    @DisplayName("AT-16：地址刚好 5 字 -> 无 warning")
    void exactly5CharAddress_shouldNotWarn() {
        Map<String, Object> data = validData();
        data.put("rec_address", "北京市朝阳区a".substring(0, 5)); // 刚好 5 字

        Map<String, Object> result = service.validate(data);

        assertTrue(warnings(result).stream().noneMatch(w -> w.contains("收件人地址")));
    }

    // ============ 姓名/快递公司 ============

    @Test
    @DisplayName("AT-17：收件人姓名为空 -> errors 含 \"收件人姓名为空\", passed=false")
    void emptyName_shouldAddError() {
        Map<String, Object> data = validData();
        data.put("rec_name", "");

        Map<String, Object> result = service.validate(data);

        assertEquals(false, result.get("passed"));
        assertTrue(errors(result).stream().anyMatch(e -> e.contains("收件人姓名为空")));
    }

    @Test
    @DisplayName("AT-18：快递公司为空 -> warnings 含 \"快递公司为空\"")
    void emptyCompany_shouldWarn() {
        Map<String, Object> data = validData();
        data.put("express_company", "");

        Map<String, Object> result = service.validate(data);

        assertTrue(warnings(result).stream().anyMatch(w -> w.contains("快递公司为空")));
    }

    // ============ null / trim / 多错误聚合 ============

    @Test
    @DisplayName("AT-19：字段值为 null -> getString 兜底为 \"\", 走对应空值分支")
    void nullFields_shouldFallbackToEmptyAndRoute() {
        Map<String, Object> data = new HashMap<>();
        data.put("waybill_no", null);
        data.put("rec_name", null);
        data.put("rec_address", null);

        Map<String, Object> result = service.validate(data);

        assertEquals(false, result.get("passed"));
        assertTrue(errors(result).stream().anyMatch(e -> e.contains("运单号为空")));
        assertTrue(errors(result).stream().anyMatch(e -> e.contains("收件人姓名为空")));
        assertTrue(errors(result).stream().anyMatch(e -> e.contains("收件人地址为空")));
    }

    @Test
    @DisplayName("AT-20：字段值有前后空格（\" SF1234 \"）-> trim 后正常匹配")
    void trimmedFields_shouldBeHandled() {
        Map<String, Object> data = validData();
        data.put("waybill_no", " SF12345678 ");

        Map<String, Object> result = service.validate(data);

        assertTrue(warnings(result).stream().noneMatch(w -> w.contains("运单号格式异常")));
    }

    @Test
    @DisplayName("AT-21：多 error 并存 -> errors.size 反映数量, passed=false")
    void multipleErrors_shouldAggregate() {
        Map<String, Object> data = new HashMap<>();
        data.put("waybill_no", "");
        data.put("rec_address", "");
        data.put("rec_name", "");

        Map<String, Object> result = service.validate(data);

        assertEquals(false, result.get("passed"));
        assertEquals(3, errors(result).size());
    }

    // ============ need_manual 精确逻辑 ============

    @Test
    @DisplayName("AT-22：warnings 含 \"运单号格式异常\" -> need_manual=true")
    void waybillFormatWarning_shouldTriggerManual() {
        Map<String, Object> data = validData();
        data.put("waybill_no", "12345");

        Map<String, Object> result = service.validate(data);

        assertEquals(true, result.get("need_manual"));
    }

    @Test
    @DisplayName("AT-23：warnings 不含 \"运单号格式异常\" -> need_manual=false")
    void nonWaybillWarningsOnly_shouldNotTriggerManual() {
        Map<String, Object> data = validData();
        data.put("rec_phone", "");
        data.put("express_company", "");

        Map<String, Object> result = service.validate(data);

        assertEquals(true, result.get("passed"));
        assertEquals(false, result.get("need_manual"), "电话/公司为空不应触发 need_manual");
        assertFalse(warnings(result).isEmpty(), "应有 warnings");
    }
}