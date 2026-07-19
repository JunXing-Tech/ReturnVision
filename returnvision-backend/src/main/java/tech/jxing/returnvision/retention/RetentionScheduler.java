package tech.jxing.returnvision.retention;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import tech.jxing.returnvision.model.entity.OcrLog;
import tech.jxing.returnvision.model.entity.OperationLog;
import tech.jxing.returnvision.model.entity.ReturnRecord;
import tech.jxing.returnvision.model.mapper.OcrLogMapper;
import tech.jxing.returnvision.model.mapper.OperationLogMapper;
import tech.jxing.returnvision.model.mapper.ReturnRecordMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 【保留期模块】定时清理任务（F02）
 *
 * 职责：每天凌晨 2 点清理超期数据，物理删除
 * 层级：retention 层
 * 关联：docs/04 第 4.9.3 节
 *
 * 清理策略：
 *   - 退货记录（synced）保留 90 天
 *   - 审计日志保留 180 天
 *   - OCR 日志保留 30 天
 *   - 分批删除（每批 500 条 + sleep 100ms），避免锁表
 *   - 异常时记错误日志，不影响下次执行
 */
@Component
@Slf4j
public class RetentionScheduler {

    private static final String STATUS_SYNCED = "synced";

    private final ReturnRecordMapper returnRecordMapper;
    private final OperationLogMapper operationLogMapper;
    private final OcrLogMapper ocrLogMapper;
    private final RetentionProperties properties;

    public RetentionScheduler(ReturnRecordMapper returnRecordMapper,
                              OperationLogMapper operationLogMapper,
                              OcrLogMapper ocrLogMapper,
                              RetentionProperties properties) {
        this.returnRecordMapper = returnRecordMapper;
        this.operationLogMapper = operationLogMapper;
        this.ocrLogMapper = ocrLogMapper;
        this.properties = properties;
    }

    /**
     * 定时清理任务：每天凌晨 2 点执行
     * cron: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledCleanup() {
        log.info("[保留期清理] 开始执行定时清理任务");
        long startTime = System.currentTimeMillis();

        try {
            // 步骤1：清理已同步退货记录
            int returnDeleted = cleanupReturnRecords();

            // 步骤2：清理审计日志
            int auditDeleted = cleanupAuditLogs();

            // 步骤3：清理 OCR 日志
            int ocrDeleted = cleanupOcrLogs();

            long cost = System.currentTimeMillis() - startTime;
            log.info("[保留期清理] 任务完成：退货记录删除 {} 条，审计日志删除 {} 条，OCR 日志删除 {} 条，耗时 {}ms",
                    returnDeleted, auditDeleted, ocrDeleted, cost);
        } catch (Exception e) {
            log.error("[保留期清理] 任务执行失败", e);
        }
    }

    /**
     * 清理已同步退货记录
     * 实现步骤：分批删除 status=synced 且 created_at < 阈值 的记录
     */
    private int cleanupReturnRecords() {
        int days = properties.getReturnRecord().getDays();
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        int batchSize = properties.getBatchSize();
        int totalDeleted = 0;

        while (true) {
            // 步骤1：查一批最老的记录 ID
            List<ReturnRecord> batch = returnRecordMapper.selectList(
                    new LambdaQueryWrapper<ReturnRecord>()
                            .eq(ReturnRecord::getStatus, STATUS_SYNCED)
                            .lt(ReturnRecord::getCreatedAt, threshold)
                            .orderByAsc(ReturnRecord::getCreatedAt)
                            .last("LIMIT " + batchSize)
            );

            if (batch.isEmpty()) {
                break;
            }

            // 步骤2：删除这批记录
            List<Long> ids = batch.stream().map(ReturnRecord::getId).toList();
            returnRecordMapper.deleteBatchIds(ids);
            totalDeleted += ids.size();

            // 步骤3：sleep 降低 DB 压力
            sleep(properties.getBatchSleepMs());

            // 步骤4：最后一批则退出
            if (batch.size() < batchSize) {
                break;
            }
        }

        log.info("[保留期清理] 退货记录（保留 {} 天）删除 {} 条", days, totalDeleted);
        return totalDeleted;
    }

    /**
     * 清理审计日志
     */
    private int cleanupAuditLogs() {
        int days = properties.getAuditLog().getDays();
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        int batchSize = properties.getBatchSize();
        int totalDeleted = 0;

        while (true) {
            List<OperationLog> batch = operationLogMapper.selectList(
                    new LambdaQueryWrapper<OperationLog>()
                            .lt(OperationLog::getCreatedAt, threshold)
                            .orderByAsc(OperationLog::getCreatedAt)
                            .last("LIMIT " + batchSize)
            );

            if (batch.isEmpty()) {
                break;
            }

            List<Long> ids = batch.stream().map(OperationLog::getId).toList();
            operationLogMapper.deleteBatchIds(ids);
            totalDeleted += ids.size();

            sleep(properties.getBatchSleepMs());

            if (batch.size() < batchSize) {
                break;
            }
        }

        log.info("[保留期清理] 审计日志（保留 {} 天）删除 {} 条", days, totalDeleted);
        return totalDeleted;
    }

    /**
     * 清理 OCR 日志
     */
    private int cleanupOcrLogs() {
        int days = properties.getOcrLog().getDays();
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        int batchSize = properties.getBatchSize();
        int totalDeleted = 0;

        while (true) {
            List<OcrLog> batch = ocrLogMapper.selectList(
                    new LambdaQueryWrapper<OcrLog>()
                            .lt(OcrLog::getCreatedAt, threshold)
                            .orderByAsc(OcrLog::getCreatedAt)
                            .last("LIMIT " + batchSize)
            );

            if (batch.isEmpty()) {
                break;
            }

            List<Long> ids = batch.stream().map(OcrLog::getId).toList();
            ocrLogMapper.deleteBatchIds(ids);
            totalDeleted += ids.size();

            sleep(properties.getBatchSleepMs());

            if (batch.size() < batchSize) {
                break;
            }
        }

        log.info("[保留期清理] OCR 日志（保留 {} 天）删除 {} 条", days, totalDeleted);
        return totalDeleted;
    }

    /**
     * 计算待确认超期数量（供 Dashboard 超期预警用）
     * 待确认且 created_at < now - 24h
     */
    public long countOverduePending() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        return returnRecordMapper.selectCount(
                new LambdaQueryWrapper<ReturnRecord>()
                        .eq(ReturnRecord::getStatus, "pending")
                        .lt(ReturnRecord::getCreatedAt, threshold)
        );
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
