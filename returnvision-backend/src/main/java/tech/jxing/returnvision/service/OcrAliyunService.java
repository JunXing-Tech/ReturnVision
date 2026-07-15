package tech.jxing.returnvision.service;

import com.aliyun.ocr_api20210707.Client;
import com.aliyun.ocr_api20210707.models.RecognizeWaybillRequest;
import com.aliyun.ocr_api20210707.models.RecognizeWaybillResponse;
import com.aliyun.teaopenapi.models.Config;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.jxing.returnvision.common.exception.OcrError;

import java.util.HashMap;
import java.util.Map;

/**
 * 【业务逻辑层】阿里云面单OCR识别服务（引擎B）
 *
 * 职责：调用阿里云 RecognizeWaybill API，从快递面单图片中提取结构化字段 + 逐字段置信度
 * 层级：Service 层
 * 调用方：OcrCrossValidatorService（步骤6）
 *
 * 特点：93%准确率，200次/月免费，返回逐字段valueProb置信度（0-100），专用面单优化
 */
@Service
@Slf4j
public class OcrAliyunService {

    private final ObjectMapper objectMapper;
    private final Client aliyunClient;

    /**
     * 构造器注入阿里云配置，并初始化OCR客户端
     *
     * @param accessKeyId     阿里云 AccessKey ID
     * @param accessKeySecret 阿里云 AccessKey Secret
     * @param objectMapper    JSON序列化工具
     */
    public OcrAliyunService(
            @Value("${aliyun.ocr.access-key-id}") String accessKeyId,
            @Value("${aliyun.ocr.access-key-secret}") String accessKeySecret,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        // 凭证未配置时优雅降级
        if (accessKeyId == null || accessKeyId.isEmpty()
                || accessKeySecret == null || accessKeySecret.isEmpty()) {
            log.warn("[阿里云OCR] 凭证未配置，阿里云OCR功能不可用。请设置 ALIYUN_AK_ID 和 ALIYUN_AK_SECRET 环境变量");
            this.aliyunClient = null;
            return;
        }

        Client client;
        try {
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret);
            config.endpoint = "ocr-api.cn-hangzhou.aliyuncs.com";
            client = new Client(config);
            log.info("[阿里云OCR] 客户端初始化完成");
        } catch (Exception e) {
            log.error("[阿里云OCR] 客户端初始化失败", e);
            client = null;
        }
        this.aliyunClient = client;
    }

    /**
     * 调用阿里云电子面单OCR识别快递单（引擎B）
     *
     * 实现步骤：
     *   1. 检查客户端是否可用
     *   2. 调用阿里云 RecognizeWaybill API
     *   3. 解析返回字段 + 逐字段置信度
     *   4. 返回结构化结果（含confidence字典）
     *
     * @param imageUrl COS图片URL
     * @return 结构化识别结果，含 waybill_no/rec_name 等字段 + confidence 字典
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> ocrByAliyun(String imageUrl) {
        // 步骤1：检查客户端是否可用
        if (aliyunClient == null) {
            throw new OcrError("阿里云OCR客户端未初始化，请检查阿里云凭证配置");
        }

        log.info("[阿里云OCR] 开始识别，imageUrl={}", imageUrl);

        try {
            // 步骤2：调用阿里云 RecognizeWaybill API
            RecognizeWaybillRequest req = new RecognizeWaybillRequest().setUrl(imageUrl);
            RecognizeWaybillResponse response = aliyunClient.recognizeWaybill(req);

            // 步骤3：解析返回字段（阿里云返回JSON字符串，需用ObjectMapper解析）
            String jsonData = response.getBody().getData();
            Map<String, Object> data = objectMapper.readValue(jsonData, Map.class);

            // 步骤4：组装结构化结果 + 逐字段置信度
            Map<String, Object> result = new HashMap<>();
            result.put("waybill_no", data.getOrDefault("waybill_no", ""));
            result.put("rec_name", data.getOrDefault("rec_name", ""));
            result.put("rec_phone", data.getOrDefault("rec_phone", ""));
            result.put("rec_address", data.getOrDefault("rec_address", ""));
            result.put("sender_name", data.getOrDefault("sender_name", ""));
            result.put("sender_phone", data.getOrDefault("sender_phone", ""));
            result.put("sender_address", data.getOrDefault("sender_address", ""));
            result.put("express_company", data.getOrDefault("express_company", ""));
            result.put("goods", data.getOrDefault("goods", ""));

            // 阿里云特有：逐字段置信度（从 prism_keyValueInfo 解析，格式为 0-100）
            Map<String, Object> confidence = new HashMap<>();
            confidence.put("waybill_no_prob", data.getOrDefault("waybill_no_prob", 0));
            confidence.put("rec_name_prob", data.getOrDefault("rec_name_prob", 0));
            confidence.put("rec_phone_prob", data.getOrDefault("rec_phone_prob", 0));
            confidence.put("rec_address_prob", data.getOrDefault("rec_address_prob", 0));
            confidence.put("sender_name_prob", data.getOrDefault("sender_name_prob", 0));
            result.put("confidence", confidence);

            log.info("[阿里云OCR] 识别完成，waybill_no={}", result.get("waybill_no"));
            return result;
        } catch (OcrError e) {
            throw e;
        } catch (Exception e) {
            log.error("[阿里云OCR] 识别异常", e);
            throw new OcrError("阿里云OCR识别异常：" + e.getMessage());
        }
    }
}
