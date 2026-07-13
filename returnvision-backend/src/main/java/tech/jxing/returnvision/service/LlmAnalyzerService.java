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
import java.util.Map;

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
 *   3. 智能分类：将退货归类为质量问题/物流问题/尺寸不符/其他
 */
@Service
@Slf4j
public class LlmAnalyzerService {

    private final String deepseekApiKey;
    private final String deepseekBaseUrl;
    private final String deepseekModel;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    /** 退货分析 prompt */
    private static final String ANALYZE_PROMPT = """
            你是退货分析助手。请根据以下快递面单OCR识别结果，完成三件事：
            1. 语义校验：检查运单号、电话、地址是否格式正确、信息完整
            2. 退货原因：根据面单信息推断可能的退货原因
            3. 智能分类：将退货归类为以下之一：质量问题、物流问题、尺寸不符、其他

            返回JSON格式（不要返回其他内容）：
            {"return_reason":"退货原因","return_category":"分类","llm_confidence":0.85,"validation":{"waybill_no":"valid/invalid","phone":"valid/invalid","address":"valid/invalid","notes":"校验备注"}}

            llm_confidence为你对分析结果的把握程度（0-1）。只返回JSON。""";

    /**
     * 构造器注入
     *
     * @param deepseekApiKey  DeepSeek API Key
     * @param deepseekBaseUrl DeepSeek API基础URL
     * @param deepseekModel   DeepSeek模型名
     * @param httpClient      OkHttp客户端
     * @param objectMapper    JSON序列化工具
     */
    public LlmAnalyzerService(
            @Value("${deepseek.api-key}") String deepseekApiKey,
            @Value("${deepseek.base-url}") String deepseekBaseUrl,
            @Value("${deepseek.model}") String deepseekModel,
            OkHttpClient httpClient,
            ObjectMapper objectMapper) {
        this.deepseekApiKey = deepseekApiKey;
        this.deepseekBaseUrl = deepseekBaseUrl;
        this.deepseekModel = deepseekModel;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用DeepSeek分析退货信息
     *
     * 实现步骤：
     *   1. 组装请求（OCR结果 + 分析prompt）
     *   2. 调用DeepSeek chat/completions API
     *   3. 解析返回的JSON结果
     *   4. 返回退货原因、分类、置信度
     *
     * @param ocrData OCR识别结果（waybill_no, rec_name, rec_phone等字段）
     * @return {return_reason, return_category, llm_confidence, validation}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> analyze(Map<String, Object> ocrData) {
        log.info("[DeepSeek] 开始分析，waybill_no={}", ocrData.get("waybill_no"));

        // 凭证未配置时优雅降级
        if (deepseekApiKey == null || deepseekApiKey.isEmpty()) {
            log.warn("[DeepSeek] API Key未配置，跳过LLM分析");
            Map<String, Object> result = new HashMap<>();
            result.put("return_reason", "");
            result.put("return_category", "");
            result.put("llm_confidence", 0.0);
            return result;
        }

        try {
            // 步骤1：组装请求
            String ocrJson = objectMapper.writeValueAsString(ocrData);
            Map<String, Object> body = new HashMap<>();
            body.put("model", deepseekModel);
            body.put("messages", new Object[]{
                    Map.of("role", "system", "content", "你是退货分析助手，只返回JSON格式数据。"),
                    Map.of("role", "user", "content", ANALYZE_PROMPT + "\n\nOCR识别结果：\n" + ocrJson)
            });

            // 步骤2：调用DeepSeek API
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
                // 步骤3：解析返回结果
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

                // 步骤4：解析JSON（content可能包含```json```标记，需要清理）
                String jsonStr = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
                Map<String, Object> analysisResult = objectMapper.readValue(jsonStr, Map.class);

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
}
