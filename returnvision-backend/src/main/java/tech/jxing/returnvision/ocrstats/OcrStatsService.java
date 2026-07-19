package tech.jxing.returnvision.ocrstats;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.jxing.returnvision.model.entity.OcrLog;
import tech.jxing.returnvision.model.mapper.OcrLogMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 【OCR 统计模块】OCR 准确率统计服务（F05）
 *
 * 职责：聚合 ocr_log 数据，提供仪表盘所需的统计指标
 * 层级：ocrstats 层
 * 调用方：OcrStatsController
 * 关联：docs/04 第 4.10.4 节
 *
 * 统计维度：
 *   - summary：总调用数/成功率/平均耗时/双引擎对比
 *   - field_accuracy：字段级平均置信度
 *   - trend：N 天成功率趋势
 */
@Service
@Slf4j
public class OcrStatsService {

    private static final String ENGINE_ZHIPU = "zhipu_ocr";
    private static final String ENGINE_ALIYUN = "aliyun_waybill";

    private final OcrLogMapper ocrLogMapper;

    public OcrStatsService(OcrLogMapper ocrLogMapper) {
        this.ocrLogMapper = ocrLogMapper;
    }

    /**
     * 获取 OCR 统计数据
     *
     * 实现步骤：
     *   1. 计算 N 天前的起始时间
     *   2. 查询 summary（总数/成功率/耗时/双引擎对比）
     *   3. 查询 field_accuracy（字段级置信度，阿里云）
     *   4. 查询 trend（按日期分组的成功率）
     *   5. 组装返回
     *
     * @param days 统计天数（默认 7）
     * @return {summary, field_accuracy, trend}
     */
    public Map<String, Object> getOcrStats(int days) {
        LocalDateTime startTime = LocalDateTime.now().minusDays(days);

        // 步骤1：summary
        Map<String, Object> summary = buildSummary(startTime);

        // 步骤2：field_accuracy
        List<Map<String, Object>> fieldAccuracy = buildFieldAccuracy(startTime);

        // 步骤3：trend
        List<Map<String, Object>> trend = buildTrend(days);

        // 步骤4：组装返回
        Map<String, Object> result = new HashMap<>();
        result.put("summary", summary);
        result.put("field_accuracy", fieldAccuracy);
        result.put("trend", trend);
        return result;
    }

    /**
     * 构建 summary 统计
     */
    private Map<String, Object> buildSummary(LocalDateTime startTime) {
        Long totalCalls = ocrLogMapper.selectCount(new LambdaQueryWrapper<OcrLog>()
                .ge(OcrLog::getCreatedAt, startTime));

        Long successCount = ocrLogMapper.selectCount(new LambdaQueryWrapper<OcrLog>()
                .ge(OcrLog::getCreatedAt, startTime)
                .eq(OcrLog::getSuccess, true));

        Long zhipuSuccess = ocrLogMapper.selectCount(new LambdaQueryWrapper<OcrLog>()
                .ge(OcrLog::getCreatedAt, startTime)
                .eq(OcrLog::getEngine, ENGINE_ZHIPU)
                .eq(OcrLog::getSuccess, true));

        Long zhipuTotal = ocrLogMapper.selectCount(new LambdaQueryWrapper<OcrLog>()
                .ge(OcrLog::getCreatedAt, startTime)
                .eq(OcrLog::getEngine, ENGINE_ZHIPU));

        Long aliyunSuccess = ocrLogMapper.selectCount(new LambdaQueryWrapper<OcrLog>()
                .ge(OcrLog::getCreatedAt, startTime)
                .eq(OcrLog::getEngine, ENGINE_ALIYUN)
                .eq(OcrLog::getSuccess, true));

        Long aliyunTotal = ocrLogMapper.selectCount(new LambdaQueryWrapper<OcrLog>()
                .ge(OcrLog::getCreatedAt, startTime)
                .eq(OcrLog::getEngine, ENGINE_ALIYUN));

        // 平均耗时（只算成功的）
        QueryWrapper<OcrLog> avgWrapper = new QueryWrapper<>();
        avgWrapper.select("IFNULL(AVG(duration_ms),0) as avg_duration")
                .ge("created_at", startTime)
                .eq("success", 1);
        List<Map<String, Object>> avgResult = ocrLogMapper.selectMaps(avgWrapper);
        double avgDuration = 0;
        if (avgResult != null && !avgResult.isEmpty() && avgResult.get(0).get("avg_duration") != null) {
            avgDuration = Double.parseDouble(avgResult.get(0).get("avg_duration").toString());
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("total_calls", totalCalls);
        summary.put("success_rate", totalCalls > 0 ? (double) successCount / totalCalls : 0);
        summary.put("avg_duration_ms", (long) avgDuration);
        summary.put("zhipu_success_rate", zhipuTotal > 0 ? (double) zhipuSuccess / zhipuTotal : 0);
        summary.put("aliyun_success_rate", aliyunTotal > 0 ? (double) aliyunSuccess / aliyunTotal : 0);
        return summary;
    }

    /**
     * 构建字段级准确率（基于阿里云 field_confidence JSON）
     *
     * 由于 field_confidence 是 JSON 字符串，MySQL 直接聚合较复杂，
     * 这里采用应用层解析：查所有阿里云成功的记录，解析 JSON 后按字段聚合平均值
     */
    private List<Map<String, Object>> buildFieldAccuracy(LocalDateTime startTime) {
        List<OcrLog> logs = ocrLogMapper.selectList(new LambdaQueryWrapper<OcrLog>()
                .ge(OcrLog::getCreatedAt, startTime)
                .eq(OcrLog::getEngine, ENGINE_ALIYUN)
                .eq(OcrLog::getSuccess, true)
                .isNotNull(OcrLog::getFieldConfidence));

        // 应用层聚合每个字段的置信度
        Map<String, double[]> fieldStats = new HashMap<>(); // field -> [sum, count]
        for (OcrLog log : logs) {
            Map<String, Object> fieldConf = parseFieldConfidence(log.getFieldConfidence());
            for (Map.Entry<String, Object> entry : fieldConf.entrySet()) {
                String field = entry.getKey();
                double conf = toDouble(entry.getValue());
                double[] stats = fieldStats.computeIfAbsent(field, k -> new double[2]);
                stats[0] += conf;
                stats[1] += 1;
            }
        }

        // 转换为列表
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (Map.Entry<String, double[]> entry : fieldStats.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("field", entry.getKey());
            item.put("avg_confidence", entry.getValue()[1] > 0 ? entry.getValue()[0] / entry.getValue()[1] : 0);
            item.put("sample_count", (int) entry.getValue()[1]);
            result.add(item);
        }
        return result;
    }

    /**
     * 构建 N 天成功率趋势
     */
    private List<Map<String, Object>> buildTrend(int days) {
        LocalDateTime startTime = LocalDateTime.now().minusDays(days);
        QueryWrapper<OcrLog> wrapper = new QueryWrapper<>();
        wrapper.select("DATE(created_at) as date", "COUNT(*) as total", "SUM(CASE WHEN success=1 THEN 1 ELSE 0 END) as success_count")
                .ge("created_at", startTime)
                .groupBy("DATE(created_at)")
                .orderByAsc("DATE(created_at)");
        List<Map<String, Object>> raw = ocrLogMapper.selectMaps(wrapper);

        List<Map<String, Object>> trend = new java.util.ArrayList<>();
        for (Map<String, Object> row : raw) {
            Map<String, Object> item = new HashMap<>();
            item.put("date", row.get("date") != null ? row.get("date").toString() : "");
            long total = row.get("total") != null ? Long.parseLong(row.get("total").toString()) : 0;
            long success = row.get("success_count") != null ? Long.parseLong(row.get("success_count").toString()) : 0;
            item.put("count", total);
            item.put("success_rate", total > 0 ? (double) success / total : 0);
            trend.add(item);
        }
        return trend;
    }

    /**
     * 解析 field_confidence JSON 字符串
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseFieldConfidence(String json) {
        if (json == null || json.isEmpty() || "{}".equals(json)) {
            return new HashMap<>();
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("[OCR统计] 解析 field_confidence 失败：{}", json);
            return new HashMap<>();
        }
    }

    private double toDouble(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return 0;
        }
    }
}
