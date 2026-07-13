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
 * 职责：调用智谱OCR layout_parsing API，从快递面单图片中提取结构化字段
 * 层级：Service 层
 * 调用方：OcrCrossValidatorService（步骤6）
 *
 * 特点：准确率94.62%（OmniDocBench v1.5第一），0.01元/次，支持手写体和JSON Schema
 */
@Service
@Slf4j
public class OcrZhipuService {

    private final String zhipuApiKey;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final String ZHIPU_OCR_URL = "https://open.bigmodel.cn/api/paas/v4/layout_parsing";

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
     * 调用智谱OCR解析工具识别快递面单
     *
     * 实现步骤：
     *   1. 组装请求体（模型、图片URL、JSON Schema）
     *   2. 发送HTTP POST请求到智谱API
     *   3. 解析响应，提取结构化字段
     *   4. 返回识别结果
     *
     * @param imageUrl COS图片URL
     * @return 结构化识别结果（waybill_no, rec_name, rec_phone, rec_address, sender_name, sender_phone, express_company）
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> ocrByZhipu(String imageUrl) {
        log.info("[智谱OCR] 开始识别，imageUrl={}", imageUrl);

        try {
            // 步骤1：组装请求体
            Map<String, Object> body = new HashMap<>();
            body.put("model", "glm-ocr");

            Map<String, Object> document = new HashMap<>();
            document.put("type", "image_url");
            document.put("image_url", imageUrl);
            body.put("document", document);

            // 自定义JSON Schema提取结构化字段
            Map<String, Object> schema = new HashMap<>();
            schema.put("type", "object");
            Map<String, Object> properties = new HashMap<>();
            properties.put("waybill_no", Map.of("type", "string", "description", "运单号"));
            properties.put("rec_name", Map.of("type", "string", "description", "收件人姓名"));
            properties.put("rec_phone", Map.of("type", "string", "description", "收件人电话"));
            properties.put("rec_address", Map.of("type", "string", "description", "收件人地址"));
            properties.put("sender_name", Map.of("type", "string", "description", "寄件人姓名"));
            properties.put("sender_phone", Map.of("type", "string", "description", "寄件人电话"));
            properties.put("express_company", Map.of("type", "string", "description", "快递公司"));
            schema.put("properties", properties);
            body.put("schema", schema);

            // 步骤2：发送HTTP POST请求
            RequestBody requestBody = RequestBody.create(
                    objectMapper.writeValueAsString(body),
                    MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(ZHIPU_OCR_URL)
                    .addHeader("Authorization", "Bearer " + zhipuApiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                // 步骤3：解析响应
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    log.error("[智谱OCR] API调用失败，code={}, body={}", response.code(), errorBody);
                    throw new OcrError("智谱OCR调用失败：HTTP " + response.code());
                }

                String responseBody = response.body().string();
                Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);

                // 步骤4：提取结构化字段（智谱返回的content字段含Schema提取结果）
                Map<String, Object> content = (Map<String, Object>) result.getOrDefault("content", result);
                log.info("[智谱OCR] 识别完成，waybill_no={}", content.get("waybill_no"));
                return content;
            }
        } catch (OcrError e) {
            throw e;
        } catch (Exception e) {
            log.error("[智谱OCR] 识别异常", e);
            throw new OcrError("智谱OCR识别异常：" + e.getMessage());
        }
    }
}
