package tech.jxing.returnvision.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.jxing.returnvision.common.ResponseResult;
import tech.jxing.returnvision.ocrstats.OcrStatsService;

import java.util.Map;

/**
 * 【接口层】OCR 统计控制器（F05）
 *
 * 职责：提供 OCR 准确率仪表盘数据接口
 * 层级：Controller 层
 * 关联：docs/06 数据导出接口第 5.2 节
 *
 * 权限：仅 SUPERVISOR/ADMIN 可访问（复用 /api/dashboard/** 路径权限）
 */
@Slf4j
@RestController
@RequestMapping("/api/dashboard")
public class OcrStatsController {

    private final OcrStatsService ocrStatsService;

    public OcrStatsController(OcrStatsService ocrStatsService) {
        this.ocrStatsService = ocrStatsService;
    }

    /**
     * 获取 OCR 准确率统计
     *
     * 业务流程：
     *   1. 解析 days 参数（默认 7）
     *   2. 调 OcrStatsService 聚合数据
     *   3. 返回 summary + field_accuracy + trend
     *
     * @param days 统计天数，默认 7
     */
    @GetMapping("/ocr-stats")
    public ResponseResult<Map<String, Object>> getOcrStats(
            @RequestParam(value = "days", defaultValue = "7") int days) {
        // 步骤1：参数校验（days 1~90）
        if (days < 1 || days > 90) {
            days = 7;
        }

        // 步骤2：查询统计
        Map<String, Object> stats = ocrStatsService.getOcrStats(days);

        // 步骤3：返回
        log.info("[OCR统计] 查询完成，days={}", days);
        return ResponseResult.success(stats);
    }
}
