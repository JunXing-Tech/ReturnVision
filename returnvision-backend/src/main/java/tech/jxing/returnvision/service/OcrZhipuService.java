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
import tech.jxing.returnvision.common.exception.OcrError;

import java.util.HashMap;
import java.util.Map;

/**
 * 【业务逻辑层】智谱OCR识别服务（引擎A）
 *
 * 职责：调用智谱 GLM-OCR 识别图片文字，再用 glm-4-flash 提取结构化字段
 * 层级：Service 层
 * 调用方：OcrCrossValidatorService（步骤6）
 *
 * 流程：GLM-OCR(file参数) -> md_results全文 -> glm-4-flash提取字段 -> 结构化JSON
 * 特点：准确率94.62%（OmniDocBench v1.5第一），0.2元/百万tokens
 */
@Service
@Slf4j
public class OcrZhipuService {

    private final String zhipuApiKey;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final String OCR_URL = "https://open.bigmodel.cn/api/paas/v4/layout_parsing";
    private static final String CHAT_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions";

    /** 字段提取 prompt */
    private static final String EXTRACT_PROMPT = """
            你是快递面单信息提取助手。请从以下OCR识别文本中提取快递面单信息，返回JSON格式（不要返回其他内容）：
            {"waybill_no":"运单号","rec_name":"收件人姓名","rec_phone":"收件人电话","rec_address":"收件人地址","sender_name":"寄件人姓名","sender_phone":"寄件人电话","sender_address":"寄件人地址","express_company":"快递公司","goods":"托寄物"}
            找不到的字段返回空字符串。只返回JSON，不要有任何其他文字。""";

    /**
     * 构造器注入
     *
     * @param zhipuApiKey  智谱API Key
     * @param httpClient   OkHttp客户端（由AppConfig提供）
     * @param objectMapper JSON序列化工具（Spring自动提供）
     */
    public OcrZhipuService(
            @Value("${zhipu.api-key}") String zhipuApiKey,
            OkHttpClient httpClient,
            ObjectMapper objectMapper) {
        this.zhipuApiKey = zhipuApiKey;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用智谱GLM-OCR识别快递面单
     *
     * 实现步骤：
     *   1. 调用 GLM-OCR API 识别图片文字
     *   2. 调用 glm-4-flash 从识别文本中提取结构化字段
     *   3. 返回结构化识别结果
     *
     * @param imageUrl COS图片URL
     * @return 结构化识别结果（waybill_no, rec_name, rec_phone, rec_address, sender_name, sender_phone, express_company）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> ocrByZhipu(String imageUrl) {
        log.info("[智谱OCR] 开始识别，imageUrl={}", imageUrl);

        try {
            // 步骤1：调用 GLM-OCR API 识别图片文字
            String mdText = callGlmOcr(imageUrl);
            log.info("[智谱OCR] 文字识别完成，文本长度={}", mdText.length());

            // 步骤2：调用 glm-4-flash 提取结构化字段
            Map<String, Object> fields = extractFields(mdText);
            log.info("[智谱OCR] 字段提取完成，waybill_no={}", fields.get("waybill_no"));

            // 步骤3：返回结构化识别结果
            return fields;
        } catch (OcrError e) {
            throw e;
        } catch (Exception e) {
            log.error("[智谱OCR] 识别异常", e);
            throw new OcrError("智谱OCR识别异常：" + e.getMessage());
        }
    }

    /**
     * 调用 GLM-OCR layout_parsing API，返回 Markdown 格式的识别文本
     */
    @SuppressWarnings("unchecked")
    private String callGlmOcr(String imageUrl) throws Exception {
        // 组装请求体（参数是 file，不是 document）
        Map<String, Object> body = new HashMap<>();
        body.put("model", "glm-ocr");
        body.put("file", imageUrl);

        RequestBody requestBody = RequestBody.create(
                objectMapper.writeValueAsString(body),
                MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(OCR_URL)
                .addHeader("Authorization", "Bearer " + zhipuApiKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "无响应体";
                log.error("[智谱OCR] GLM-OCR调用失败，code={}, body={}", response.code(), errorBody);
                throw new OcrError("智谱OCR调用失败：HTTP " + response.code());
            }

            String responseBody = response.body().string();
            Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);

            // 返回 md_results（Markdown格式的识别文本）
            String mdResults = (String) result.getOrDefault("md_results", "");
            if (mdResults.isEmpty()) {
                log.warn("[智谱OCR] GLM-OCR返回空文本，response={}", responseBody);
            }
            return mdResults;
        }
    }

    /**
     * 调用 glm-4-flash 从OCR文本中提取结构化字段
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractFields(String mdText) throws Exception {
        // 组装请求体
        Map<String, Object> body = new HashMap<>();
        body.put("model", "glm-4-flash");
        body.put("messages", new Object[]{
                Map.of("role", "system", "content", "你是一个信息提取助手，只返回JSON格式数据。"),
                Map.of("role", "user", "content", EXTRACT_PROMPT + "\n\nOCR识别文本：\n" + mdText)
        });

        RequestBody requestBody = RequestBody.create(
                objectMapper.writeValueAsString(body),
                MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(CHAT_URL)
                .addHeader("Authorization", "Bearer " + zhipuApiKey)
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "无响应体";
                log.error("[智谱OCR] 字段提取调用失败，code={}, body={}", response.code(), errorBody);
                throw new OcrError("智谱OCR字段提取失败：HTTP " + response.code());
            }

            String responseBody = response.body().string();
            Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);

            // 从 chat/completions 响应中提取 content
            var choices = (java.util.List<Map<String, Object>>) result.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new OcrError("智谱OCR字段提取失败：响应无choices");
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");

            // 解析JSON（content可能包含```json```标记，需要清理）
            String jsonStr = content.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            return objectMapper.readValue(jsonStr, Map.class);
        }
    }
}
