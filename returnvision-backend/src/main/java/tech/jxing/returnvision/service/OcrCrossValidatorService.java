package tech.jxing.returnvision.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * 【业务逻辑层】双引擎交叉验证引擎（核心业务逻辑）
 *
 * 职责：并行调用智谱OCR和阿里云OCR，逐字段比对结果，根据置信度仲裁冲突
 * 层级：Service 层
 * 调用方：UploadController（步骤10）
 *
 * 策略：
 *   1. CompletableFuture 并行调用引擎A（智谱）和引擎B（阿里云）
 *   2. 逐字段比对：一致 -> matched，不一致 -> conflict
 *   3. 冲突字段用阿里云 valueProb 仲裁（≥80 优先阿里云，<80 优先智谱）
 *   4. 单引擎失败 -> 优先采用成功引擎的结果
 *   5. 双引擎失败 -> 转人工
 */
@Service
@Slf4j
public class OcrCrossValidatorService {

    private final OcrZhipuService ocrZhipuService;
    private final OcrAliyunService ocrAliyunService;

    /** 交叉验证的字段列表 */
    private static final List<String> FIELDS = Arrays.asList(
            "waybill_no", "rec_name", "rec_phone", "rec_address",
            "sender_name", "sender_phone", "express_company"
    );

    /** 阿里云置信度仲裁阈值：≥80 优先阿里云 */
    private static final int CONFIDENCE_THRESHOLD = 80;

    /**
     * 构造器注入
     *
     * @param ocrZhipuService  智谱OCR服务（引擎A）
     * @param ocrAliyunService 阿里云OCR服务（引擎B）
     */
    public OcrCrossValidatorService(OcrZhipuService ocrZhipuService, OcrAliyunService ocrAliyunService) {
        this.ocrZhipuService = ocrZhipuService;
        this.ocrAliyunService = ocrAliyunService;
    }

    /**
     * 双引擎并行OCR + 交叉验证
     *
     * 实现步骤：
     *   1. CompletableFuture 并行调用引擎A和引擎B
     *   2. 等待两个引擎返回（或捕获异常）
     *   3. 处理引擎失败情况（双失败/单失败）
     *   4. 逐字段交叉比对两个引擎结果
     *   5. 根据比对结果决定 action：accept/review/manual
     *
     * @param imageUrl COS图片URL
     * @return {action, data, source, confidence, diff_fields, diff_detail}
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> dualEngineOcr(String imageUrl) {
        log.info("[交叉验证] 开始双引擎并行识别，imageUrl={}", imageUrl);

        // 步骤1：并行调用双引擎
        CompletableFuture<Map<String, Object>> zhipuFuture = CompletableFuture.supplyAsync(
                () -> ocrZhipuService.ocrByZhipu(imageUrl));
        CompletableFuture<Map<String, Object>> aliyunFuture = CompletableFuture.supplyAsync(
                () -> ocrAliyunService.ocrByAliyun(imageUrl));

        // 步骤2：分别获取结果，捕获异常
        Map<String, Object> resultZhipu = null;
        Map<String, Object> resultAliyun = null;
        boolean zhipuSuccess = false;
        boolean aliyunSuccess = false;

        try {
            resultZhipu = zhipuFuture.get();
            zhipuSuccess = true;
        } catch (InterruptedException | ExecutionException e) {
            log.warn("[交叉验证] 智谱OCR失败：{}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        }

        try {
            resultAliyun = aliyunFuture.get();
            aliyunSuccess = true;
        } catch (InterruptedException | ExecutionException e) {
            log.warn("[交叉验证] 阿里云OCR失败：{}", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
        }

        // 步骤3：处理引擎失败情况
        if (!zhipuSuccess && !aliyunSuccess) {
            // 双引擎均失败 -> 转人工
            log.warn("[交叉验证] 双引擎均识别失败，转人工");
            Map<String, Object> result = new HashMap<>();
            result.put("action", "manual");
            result.put("reason", "双引擎均识别失败");
            result.put("data", new HashMap<>());
            result.put("confidence", "low");
            return result;
        }

        if (!zhipuSuccess) {
            // 智谱失败，仅阿里云结果
            log.info("[交叉验证] 智谱失败，仅采用阿里云结果");
            Map<String, Object> result = new HashMap<>();
            result.put("action", "accept");
            result.put("data", extractFlatData(resultAliyun));
            result.put("source", "aliyun_only");
            result.put("confidence", "medium");
            result.put("note", "智谱失败，仅阿里云结果");
            return result;
        }

        if (!aliyunSuccess) {
            // 阿里云失败，仅智谱结果
            log.info("[交叉验证] 阿里云失败，仅采用智谱结果");
            Map<String, Object> result = new HashMap<>();
            result.put("action", "accept");
            result.put("data", extractFlatData(resultZhipu));
            result.put("source", "zhipu_only");
            result.put("confidence", "medium");
            result.put("note", "阿里云失败，仅智谱结果");
            return result;
        }

        // 步骤4：双引擎均成功，逐字段交叉比对
        log.info("[交叉验证] 双引擎均成功，开始逐字段比对");
        Map<String, Object> chosenData = new HashMap<>();
        List<String> diffFields = new ArrayList<>();
        Map<String, Object> diffDetail = new HashMap<>();

        // 获取阿里云置信度字典
        Map<String, Object> confidenceMap = (Map<String, Object>) resultAliyun
                .getOrDefault("confidence", Collections.emptyMap());

        for (String field : FIELDS) {
            String valZhipu = getStringValue(resultZhipu, field);
            String valAliyun = getStringValue(resultAliyun, field);

            if (!valZhipu.isEmpty() && !valAliyun.isEmpty() && valZhipu.equals(valAliyun)) {
                // 两引擎一致 -> 直接采用
                chosenData.put(field, valZhipu);
            } else if (!valZhipu.isEmpty() && valAliyun.isEmpty()) {
                // 智谱有值，阿里云为空 -> 采用智谱
                chosenData.put(field, valZhipu);
            } else if (!valAliyun.isEmpty() && valZhipu.isEmpty()) {
                // 阿里云有值，智谱为空 -> 采用阿里云
                chosenData.put(field, valAliyun);
            } else if (!valZhipu.isEmpty() && !valAliyun.isEmpty() && !valZhipu.equals(valAliyun)) {
                // 两引擎不一致 -> 用阿里云 valueProb 仲裁
                int prob = getIntValue(confidenceMap, field + "_prob");
                String chosenValue = prob >= CONFIDENCE_THRESHOLD ? valAliyun : valZhipu;
                chosenData.put(field, chosenValue);
                diffFields.add(field);

                // 记录差异详情
                Map<String, Object> detail = new HashMap<>();
                detail.put("zhipu", valZhipu);
                detail.put("aliyun", valAliyun);
                detail.put("aliyun_prob", prob);
                detail.put("chosen", prob >= CONFIDENCE_THRESHOLD ? "aliyun" : "zhipu");
                diffDetail.put(field, detail);
            } else {
                // 两引擎均为空
                chosenData.put(field, "");
            }
        }

        // 步骤5：根据比对结果决定 action
        boolean waybillNoConflict = diffFields.contains("waybill_no");

        if (waybillNoConflict) {
            // 运单号冲突 -> 转人工（运单号是关键字段，不能自动仲裁）
            log.warn("[交叉验证] 运单号冲突，转人工。智谱={}, 阿里云={}",
                    getStringValue(resultZhipu, "waybill_no"),
                    getStringValue(resultAliyun, "waybill_no"));
            Map<String, Object> result = new HashMap<>();
            result.put("action", "manual");
            result.put("reason", "运单号双引擎结果不一致");
            result.put("data", chosenData);
            result.put("diff_fields", diffFields);
            result.put("diff_detail", diffDetail);
            result.put("confidence", "low");
            return result;
        }

        if (!diffFields.isEmpty()) {
            // 有差异字段（非运单号） -> 需人工复核
            log.info("[交叉验证] 存在差异字段，需复核。diff_fields={}", diffFields);
            Map<String, Object> result = new HashMap<>();
            result.put("action", "review");
            result.put("data", chosenData);
            result.put("source", "cross_validated");
            result.put("diff_fields", diffFields);
            result.put("diff_detail", diffDetail);
            result.put("confidence", "medium");
            return result;
        }

        // 全部一致 -> 自动采用
        log.info("[交叉验证] 全部字段一致，自动采用");
        Map<String, Object> result = new HashMap<>();
        result.put("action", "accept");
        result.put("data", chosenData);
        result.put("source", "cross_validated");
        result.put("confidence", "high");
        return result;
    }

    /**
     * 从OCR结果中提取扁平字段（排除confidence等元数据）
     */
    private Map<String, Object> extractFlatData(Map<String, Object> ocrResult) {
        Map<String, Object> flat = new HashMap<>();
        for (String field : FIELDS) {
            flat.put(field, ocrResult.getOrDefault(field, ""));
        }
        return flat;
    }

    /**
     * 安全获取字符串值
     */
    private String getStringValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val == null ? "" : val.toString().trim();
    }

    /**
     * 安全获取整数值
     */
    private int getIntValue(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        }
        return 0;
    }
}
