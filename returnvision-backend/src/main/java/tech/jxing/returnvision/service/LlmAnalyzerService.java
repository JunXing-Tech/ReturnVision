package tech.jxing.returnvision.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.jxing.returnvision.common.exception.LlmError;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 【业务逻辑层】DeepSeek LLM分析服务
 *
 * 职责：调用 DeepSeek V4 Flash，对OCR结果进行语义校验、退货原因分析、智能分类
 * 层级：Service 层
 * 调用方：UploadController（步骤10）
 *
 * 功能：
 *   1. 语义校验：检查运单号格式、电话格式、地址完整性
 *   2. 退货原因：根据面单信息推断退货原因
 *   3. 智能分类：将退货归类为字典中的标准分类（F08 改造，v2.2）
 *
 * F08 改造要点（v2.2）：
 *   1. 注入 DictService，运行时查活跃字典叶子项
 *   2. prompt 动态注入字典项列表（code + label），LLM 必须从 code 中选
 *   3. LLM 返回的 return_category_code 经 DictService.getItemLabelByCode 反查 label
 *   4. 字典为空 -> prompt 用硬编码 4 类兜底（向后兼容）
 *   5. LLM 越界（code 不在字典中） -> 降级为"其他"，记 warn
 *   6. API Key 未配置 -> 不查字典，直接返回空对象（避免无意义 DB 调用）
 */
@Service
@Slf4j
public class LlmAnalyzerService {

    private final String deepseekApiKey;
    private final String deepseekBaseUrl;
    private final String deepseekModel;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final DictService dictService;

    /** 字典为空时的硬编码兜底分类（向后兼容） */
    private static final String FALLBACK_PROMPT_CATEGORIES = "质量问题、物流问题、尺寸不符、其他";

    /** 退货分析 prompt 模板（F08 改造：分类段落动态注入） */
    private static final String ANALYZE_PROMPT_TEMPLATE = """
            你是退货分析助手。请根据以下快递面单OCR识别结果，完成三件事：
            1. 语义校验：检查运单号、电话、地址是否格式正确、信息完整
            2. 退货原因：根据面单信息推断可能的退货原因
            3. 智能分类：将退货归类为以下标准分类之一，必须从给定 code 中选一个

            可选分类（code 与名称对应，你只能返回其中某个 code）：
            {CATEGORIES_BLOCK}

            返回JSON格式（不要返回其他内容）：
            {"return_reason":"退货原因","return_category_code":"分类code","llm_confidence":0.85,"validation":{"waybill_no":"valid/invalid","phone":"valid/invalid","address":"valid/invalid","notes":"校验备注"}}

            llm_confidence为你对分析结果的把握程度（0-1）。return_category_code 必须是上面列出的 code 之一。只返回JSON。""";

    /**
     * 构造器注入
     *
     * @param deepseekApiKey  DeepSeek API Key
     * @param deepseekBaseUrl DeepSeek API基础URL
     * @param deepseekModel   DeepSeek模型名
     * @param httpClient      OkHttp客户端
     * @param objectMapper    JSON序列化工具
     * @param dictService     F08 字典服务（查活跃字典、反查 label）
     */
    public LlmAnalyzerService(
            @Value("${deepseek.api-key}") String deepseekApiKey,
            @Value("${deepseek.base-url}") String deepseekBaseUrl,
            @Value("${deepseek.model}") String deepseekModel,
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            DictService dictService) {
        this.deepseekApiKey = deepseekApiKey;
        this.deepseekBaseUrl = deepseekBaseUrl;
        this.deepseekModel = deepseekModel;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.dictService = dictService;
    }

    /**
     * 调用DeepSeek分析退货信息
     *
     * 实现步骤：
     *   1. 凭证未配置 -> 降级返回空对象（不查字典，避免无意义 DB 调用）
     *   2. 查活跃字典叶子项，组装 prompt 分类段落（字典为空用硬编码兜底）
     *   3. 组装请求（OCR结果 + 分析prompt）
     *   4. 调用DeepSeek chat/completions API
     *   5. 解析返回的JSON结果
     *   6. return_category_code 反查 label，写入 return_category
     *   7. code 不在字典中 -> 降级为"其他"，记 warn
     *
     * @param ocrData OCR识别结果（waybill_no, rec_name, rec_phone等字段）
     * @return {return_reason, return_category, llm_confidence, validation}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> analyze(Map<String, Object> ocrData) {
        log.info("[DeepSeek] 开始分析，waybill_no={}", ocrData.get("waybill_no"));

        // 步骤1：凭证未配置时优雅降级（不查字典）
        if (deepseekApiKey == null || deepseekApiKey.isEmpty()) {
            log.warn("[DeepSeek] API Key未配置，跳过LLM分析");
            Map<String, Object> result = new HashMap<>();
            result.put("return_reason", "");
            result.put("return_category", "");
            result.put("llm_confidence", 0.0);
            return result;
        }

        // 步骤2：查活跃字典叶子项，组装 prompt 分类段落
        String categoriesBlock = buildCategoriesBlock();
        String prompt = ANALYZE_PROMPT_TEMPLATE.replace("{CATEGORIES_BLOCK}", categoriesBlock);

        try {
            // 步骤3：组装请求
            String ocrJson = objectMapper.writeValueAsString(ocrData);
            Map<String, Object> body = new HashMap<>();
            body.put("model", deepseekModel);
            body.put("messages", new Object[]{
                    Map.of("role", "system", "content", "你是退货分析助手，只返回JSON格式数据。"),
                    Map.of("role", "user", "content", prompt + "\n\nOCR识别结果：\n" + ocrJson)
            });

            // 步骤4：调用DeepSeek API
            RequestBody requestBody = RequestBody.create(
                    objectMapper.writeValueAsString(body),
                    MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(deepseekBaseUrl + "/v1/chat/completions")
                    .addHeader("Authorization", "Bearer " + deepseekApiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                // 步骤5：解析返回结果
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    log.error("[DeepSeek] API调用失败，code={}, body={}", response.code(), errorBody);
                    throw new LlmError("DeepSeek调用失败：HTTP " + response.code());
                }

                String responseBody = response.body().string();
                Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);

                // 从chat/completions响应中提取content
                var choices = (java.util.List<Map<String, Object>>) result.get("choices");
                if (choices == null || choices.isEmpty()) {
                    throw new LlmError("DeepSeek响应无choices");
                }
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                String content = (String) message.get("content");

                // 解析JSON（content可能包含```json```标记，需要清理）
                String jsonStr = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                Map<String, Object> analysisResult = objectMapper.readValue(jsonStr, Map.class);

                // 步骤6：return_category_code 反查 label，写入 return_category
                String categoryCode = getStringValue(analysisResult, "return_category_code");
                String categoryLabel = dictService.getItemLabelByCode(categoryCode);

                // 步骤7：code 不在字典中 -> 降级为"其他"
                if (categoryLabel == null) {
                    log.warn("[DeepSeek] LLM返回分类code [{}] 不在活跃字典中，降级为'其他'", categoryCode);
                    categoryLabel = resolveFallbackLabel();
                }

                analysisResult.put("return_category", categoryLabel);
                analysisResult.remove("return_category_code");

                log.info("[DeepSeek] 分析完成，return_reason={}, return_category={}",
                        analysisResult.get("return_reason"), analysisResult.get("return_category"));
                return analysisResult;
            }
        } catch (LlmError e) {
            throw e;
        } catch (Exception e) {
            log.error("[DeepSeek] 分析异常", e);
            throw new LlmError("DeepSeek分析异常：" + e.getMessage());
        }
    }

    /**
     * 组装 prompt 的分类段落
     *
     * 实现步骤：
     *   1. 查活跃字典叶子项
     *   2. 字典为空 -> 返回硬编码兜底 4 类
     *   3. 字典非空 -> 拼装 "code: label" 列表
     *
     * @return 分类段落字符串
     */
    private String buildCategoriesBlock() {
        List<Map<String, Object>> leafItems = dictService.listActiveLeafItems();
        if (leafItems.isEmpty()) {
            log.info("[DeepSeek] 字典为空，prompt 用硬编码 4 类兜底");
            return FALLBACK_PROMPT_CATEGORIES;
        }
        return leafItems.stream()
                .map(item -> item.get("item_code") + ": " + item.get("item_label"))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 解析降级"其他"的 label
     *
     * 实现步骤：
     *   1. 先从字典查 OTHER code 的 label
     *   2. 查不到 -> 用硬编码"其他"
     *
     * @return "其他"的 label
     */
    private String resolveFallbackLabel() {
        String label = dictService.getItemLabelByCode("OTHER");
        return label != null ? label : "其他";
    }

    /** 安全获取字符串值 */
    private String getStringValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val == null ? "" : val.toString().trim();
    }
}
