package tech.jxing.returnvision.ocrstats;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.jxing.returnvision.model.entity.OcrLog;
import tech.jxing.returnvision.model.mapper.OcrLogMapper;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

/**
 * 【OCR 统计模块】OCR 日志埋点写入工具（F05）
 *
 * 职责：封装 OcrLog 组装+插入，避免污染 OcrCrossValidatorService 主流程
 * 层级：ocrstats 层
 * 关联：docs/04 第 4.10.3 节
 *
 * 设计要点：
 *   - 埋点失败不影响主流程（try-catch 吞异常，只记 warn 日志）
 *   - 每次双引擎调用写 2 条 ocr_log（智谱+阿里云）
 *   - field_confidence 存 JSON 字符串（阿里云有字段级置信度，智谱为空 JSON）
 */
@Component
@Slf4j
public class OcrLogWriter {

    private final OcrLogMapper ocrLogMapper;
    private final ObjectMapper objectMapper;

    public OcrLogWriter(OcrLogMapper ocrLogMapper, ObjectMapper objectMapper) {
        this.ocrLogMapper = ocrLogMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 写入一条 OCR 日志
     *
     * 实现步骤：
     *   1. 组装 OcrLog 实体
     *   2. field_confidence Map 转 JSON 字符串
     *   3. 插入 ocr_log 表
     *   4. 失败只记 warn，不抛异常
     *
     * @param recordId       关联退货记录ID（可为 null）
     * @param engine         引擎名：zhipu_ocr / aliyun_waybill
     * @param durationMs     识别耗时（毫秒）
     * @param success        是否成功
     * @param confidence     表级置信度（0.00-1.00）
     * @param fieldConfidence 字段级置信度 Map（可为空）
     * @param errorMsg       错误信息（可为 null）
     */
    public void writeLog(Long recordId, String engine, long durationMs, boolean success,
                         BigDecimal confidence, Map<String, Object> fieldConfidence,
                         String errorMsg) {
        try {
            // 步骤1：组装 OcrLog 实体
            OcrLog ocrLog = new OcrLog();
            ocrLog.setRecordId(recordId);
            ocrLog.setEngine(engine);
            ocrLog.setDurationMs((int) durationMs);
            ocrLog.setSuccess(success);
            ocrLog.setConfidence(confidence);
            ocrLog.setErrorMsg(errorMsg);

            // 步骤2：field_confidence Map 转 JSON 字符串
            if (fieldConfidence != null && !fieldConfidence.isEmpty()) {
                ocrLog.setFieldConfidence(objectMapper.writeValueAsString(fieldConfidence));
            } else {
                ocrLog.setFieldConfidence("{}");
            }

            // 步骤3：插入 ocr_log 表
            ocrLogMapper.insert(ocrLog);
        } catch (Exception e) {
            // 步骤4：失败只记 warn，不影响主流程
            log.warn("[OCR埋点] 写入 ocr_log 失败（不影响主流程）：engine={}, error={}", engine, e.getMessage());
        }
    }

    /**
     * 便捷方法：写入空 field_confidence 的日志（用于智谱，无字段级置信度）
     */
    public void writeSimpleLog(Long recordId, String engine, long durationMs, boolean success,
                               BigDecimal confidence, String errorMsg) {
        writeLog(recordId, engine, durationMs, success, confidence, Collections.emptyMap(), errorMsg);
    }
}
