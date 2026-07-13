package tech.jxing.returnvision.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 【业务逻辑层】数据校验服务
 *
 * 职责：校验OCR识别结果的完整性和格式正确性，决定是否需要人工确认
 * 层级：Service 层
 * 调用方：UploadController（步骤10）
 *
 * 校验项：
 *   1. 运单号格式（支持顺丰/中通/圆通/韵达/申通/EMS等主流快递）
 *   2. 收件人电话格式（11位手机号或区号-座机）
 *   3. 收件人地址完整性（非空，最少5个字）
 *   4. 收件人姓名非空
 *   5. 快递公司非空
 */
@Service
@Slf4j
public class ValidatorService {

    /** 运单号正则：支持纯数字（8-20位）和字母+数字组合 */
    private static final Pattern WAYBILL_NO_PATTERN = Pattern.compile(
            "^[A-Za-z]{0,3}\\d{8,20}$");

    /** 手机号正则：11位数字，1开头 */
    private static final Pattern MOBILE_PATTERN = Pattern.compile(
            "^1[3-9]\\d{9}$");

    /** 座机正则：区号-号码，如 010-12345678 */
    private static final Pattern LANDLINE_PATTERN = Pattern.compile(
            "^\\d{3,4}-?\\d{7,8}$");

    /** 地址最少长度 */
    private static final int MIN_ADDRESS_LENGTH = 5;

    /**
     * 校验OCR识别结果
     *
     * 实现步骤：
     *   1. 校验运单号格式
     *   2. 校验收件人电话格式
     *   3. 校验收件人地址完整性
     *   4. 校验收件人姓名非空
     *   5. 校验快递公司非空
     *   6. 汇总校验结果，决定是否需要人工确认
     *
     * @param data OCR识别结果
     * @return {passed, errors, warnings, need_manual}
     */
    public Map<String, Object> validate(Map<String, Object> data) {
        log.info("[数据校验] 开始校验，waybill_no={}", data.get("waybill_no"));

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 步骤1：校验运单号格式
        String waybillNo = getString(data, "waybill_no");
        if (waybillNo.isEmpty()) {
            errors.add("运单号为空");
        } else if (!WAYBILL_NO_PATTERN.matcher(waybillNo).matches()) {
            warnings.add("运单号格式异常：" + waybillNo);
        }

        // 步骤2：校验收件人电话格式
        String recPhone = getString(data, "rec_phone");
        if (recPhone.isEmpty()) {
            warnings.add("收件人电话为空");
        } else if (!MOBILE_PATTERN.matcher(recPhone).matches()
                && !LANDLINE_PATTERN.matcher(recPhone).matches()) {
            warnings.add("收件人电话格式异常：" + recPhone);
        }

        // 步骤3：校验收件人地址完整性
        String recAddress = getString(data, "rec_address");
        if (recAddress.isEmpty()) {
            errors.add("收件人地址为空");
        } else if (recAddress.length() < MIN_ADDRESS_LENGTH) {
            warnings.add("收件人地址过短：" + recAddress);
        }

        // 步骤4：校验收件人姓名非空
        String recName = getString(data, "rec_name");
        if (recName.isEmpty()) {
            errors.add("收件人姓名为空");
        }

        // 步骤5：校验快递公司非空
        String expressCompany = getString(data, "express_company");
        if (expressCompany.isEmpty()) {
            warnings.add("快递公司为空");
        }

        // 步骤6：汇总校验结果
        boolean passed = errors.isEmpty();
        // 有错误或运单号格式异常时需要人工确认
        boolean needManual = !passed
                || warnings.stream().anyMatch(w -> w.contains("运单号格式异常"));

        Map<String, Object> result = new HashMap<>();
        result.put("passed", passed);
        result.put("need_manual", needManual);
        result.put("errors", errors);
        result.put("warnings", warnings);

        log.info("[数据校验] 校验完成，passed={}, need_manual={}, errors={}, warnings={}",
                passed, needManual, errors.size(), warnings.size());
        return result;
    }

    /**
     * 安全获取字符串值
     */
    private String getString(Map<String, Object> data, String key) {
        Object val = data.get(key);
        return val == null ? "" : val.toString().trim();
    }
}
