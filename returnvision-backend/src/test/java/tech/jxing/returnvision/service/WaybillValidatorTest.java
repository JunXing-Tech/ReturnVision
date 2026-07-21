package tech.jxing.returnvision.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 【测试类】WaybillValidator 单元测试
 * <p>
 * 1. 纯逻辑测试，零外部依赖
 * 2. 覆盖 8 家快递公司前缀映射 + 空值跳过 + 未登记跳过 + 大小写不敏感 + 多前缀
 * 3. 对应清单：test-checklists/2026-07-21_ValidatorService-单元测试.md AT-25~AT-36
 * 4. 对应文档：docs/04 第 4.6 节 F11 + 4.6.8 验收要点
 * </p>
 *
 * @author ReturnVision
 */
class WaybillValidatorTest {

    private final WaybillValidator validator = new WaybillValidator();

    // ============ 命中前缀（应返回 null） ============

    @ParameterizedTest(name = "顺丰 + {0} 应匹配（null）")
    @DisplayName("AT-25/AT-31：顺丰 SF / SFC 前缀匹配")
    @ValueSource(strings = {"SF1234", "SFC123456"})
    void shunfengPrefix_shouldMatch(String waybillNo) {
        assertNull(validator.validate(waybillNo, "顺丰"));
    }

    @ParameterizedTest(name = "{0} + {1} 应匹配（null）")
    @DisplayName("AT-27/AT-28/AT-29：京东 / EMS / 极兔前缀匹配")
    @CsvSource({
            "JD1234, 京东",
            "EMS1234, EMS",
            "JT1234, 极兔",
            "YT1234, 圆通",
            "ZT1234, 中通",
            "YD1234, 韵达",
            "ST1234, 申通"
    })
    void registeredCompanyPrefix_shouldMatch(String waybillNo, String company) {
        assertNull(validator.validate(waybillNo, company));
    }

    // ============ 不匹配（应返回非 null warning） ============

    @Test
    @DisplayName("AT-26：顺丰 + JD1234 应返回 warning（docs 4.6.8 \"明显不匹配\"）")
    void mismatchedPrefix_shouldReturnWarning() {
        String warning = validator.validate("JD1234", "顺丰");

        assertNotNull(warning, "不匹配时应返回非 null warning");
        assertTrue(warning.contains("JD1234"), "warning 应包含运单号");
        assertTrue(warning.contains("顺丰"), "warning 应包含快递公司");
        assertTrue(warning.contains("SF"), "warning 应包含期望前缀");
    }

    @ParameterizedTest(name = "{0} + {1} 应返回 warning")
    @DisplayName("AT-26 扩展：其他不匹配场景")
    @CsvSource({
            "SF1234, 中通",
            "JD1234, 顺丰",
            "YT1234, 京东"
    })
    void otherMismatch_shouldReturnWarning(String waybillNo, String company) {
        assertNotNull(validator.validate(waybillNo, company));
    }

    // ============ 大小写不敏感 ============

    @Test
    @DisplayName("AT-30：大小写不敏感（顺丰 + \"sf1234\" 应匹配）")
    void caseInsensitive_shouldMatch() {
        assertNull(validator.validate("sf1234", "顺丰"));
    }

    // ============ 空值跳过（应返回 null，不抛 NPE） ============

    @ParameterizedTest(name = "运单号 [{0}] + 公司 [{1}] 应跳过（null）")
    @DisplayName("AT-33/AT-34/AT-35：空值或 null 跳过，不抛 NPE")
    @CsvSource(value = {
            ", 顺丰",
            "'', 顺丰",
            "SF1234, ",
            "SF1234, ''",
            "null, 顺丰",
            "SF1234, null"
    }, nullValues = {"null"})
    void emptyOrNullInputs_shouldReturnNull(String waybillNo, String company) {
        assertNull(validator.validate(waybillNo, company), "空值应跳过返回 null");
    }

    // ============ 未登记公司跳过 ============

    @ParameterizedTest(name = "未登记公司 [{0}] 应跳过")
    @DisplayName("AT-32：未登记的快递公司跳过（避免误报）")
    @ValueSource(strings = {"小快递", "百世", "天天", "未知快递"})
    void unregisteredCompany_shouldSkip(String company) {
        assertNull(validator.validate("XX1234", company),
                "未登记公司应跳过避免误报");
    }

    // ============ warning 字符串内容契约 ============

    @Test
    @DisplayName("AT-36：不匹配 warning 字符串包含运单号与期望前缀")
    void warningString_shouldContainContext() {
        String warning = validator.validate("JD123456", "顺丰");

        assertNotNull(warning);
        assertTrue(warning.contains("JD123456"), "应含运单号");
        assertTrue(warning.contains("顺丰"), "应含公司名");
        assertTrue(warning.contains("SF/SFC") || warning.contains("SF"),
                "应含期望前缀");
    }
}