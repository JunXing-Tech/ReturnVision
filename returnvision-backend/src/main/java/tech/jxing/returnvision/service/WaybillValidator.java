package tech.jxing.returnvision.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 【业务逻辑层】运单号语义校验服务
 *
 * 职责：校验运单号前缀与快递公司是否匹配（如 SF 开头应为顺丰）
 * 层级：Service 层
 * 调用方：UploadController（F11 埋点）
 * 关联：docs/04 第 4.6 节 F11
 *
 * 设计说明：
 *   与 ValidatorService 职责分离 -- ValidatorService 做格式校验（正则），
 *   WaybillValidator 做语义校验（前缀 vs 公司匹配）。
 *   规则不全，只做"明显不匹配"提示，不做强校验。
 */
@Service
@Slf4j
public class WaybillValidator {

    /**
     * 快递公司 -> 运单号前缀集合映射
     * 同一快递公司可能有多个前缀（如 EMS 有 EMS 和 1 开头）
     * 前缀统一大写存储，匹配时运单号也转大写
     */
    private static final Map<String, Set<String>> COMPANY_PREFIX_MAP = Map.of(
            "顺丰", Set.of("SF", "SFC"),
            "京东", Set.of("JD"),
            "圆通", Set.of("YT"),
            "中通", Set.of("ZT"),
            "韵达", Set.of("YD"),
            "申通", Set.of("ST"),
            "EMS", Set.of("EMS"),
            "极兔", Set.of("JT")
    );

    /**
     * 校验运单号前缀与快递公司是否匹配
     *
     * 实现步骤：
     *   1. 快递公司或运单号为空时跳过（由 ValidatorService 负责空值校验）
     *   2. 查找快递公司对应的前缀集合
     *   3. 未登记的快递公司跳过（小快递无固定前缀）
     *   4. 检查运单号是否以任一前缀开头
     *   5. 不匹配时返回 warning 描述
     *
     * @param waybillNo      运单号
     * @param expressCompany 快递公司名称（中文）
     * @return null 表示匹配或无法校验；非空字符串表示 warning 描述
     */
    public String validate(String waybillNo, String expressCompany) {
        // 步骤1：空值跳过
        if (waybillNo == null || waybillNo.isEmpty()
                || expressCompany == null || expressCompany.isEmpty()) {
            return null;
        }

        // 步骤2：查找快递公司对应的前缀集合
        Set<String> prefixes = COMPANY_PREFIX_MAP.get(expressCompany);

        // 步骤3：未登记的快递公司跳过（避免误报）
        if (prefixes == null || prefixes.isEmpty()) {
            return null;
        }

        // 步骤4：检查运单号是否以任一前缀开头（大小写不敏感）
        String upperWaybill = waybillNo.toUpperCase();
        for (String prefix : prefixes) {
            if (upperWaybill.startsWith(prefix)) {
                return null;
            }
        }

        // 步骤5：不匹配时返回 warning 描述
        log.warn("[运单校验] 前缀与快递公司不匹配：waybill_no={}, company={}, 期望前缀={}",
                waybillNo, expressCompany, prefixes);
        return "运单号前缀与快递公司不匹配：" + waybillNo + " 开头应为 " + expressCompany
                + "（期望前缀：" + String.join("/", prefixes) + "）";
    }
}
