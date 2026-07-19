package tech.jxing.returnvision.retention;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 【配置类】数据保留期配置（F02）
 *
 * 职责：从 application.yml 读取 retention.* 配置
 * 关联：docs/05 第 4.5.10 节
 *
 * 配置示例：
 *   retention:
 *     return-record:
 *       days: 90
 *     pending-remind:
 *       days: 30
 *     audit-log:
 *       days: 180
 *     ocr-log:
 *       days: 30
 *     batch-size: 500
 *     batch-sleep-ms: 100
 */
@Data
@Component
@ConfigurationProperties(prefix = "retention")
public class RetentionProperties {

    /** 已同步退货记录保留天数 */
    private DaysConfig returnRecord = new DaysConfig();

    /** 待确认退货提醒天数（不删除） */
    private DaysConfig pendingRemind = new DaysConfig();

    /** 审计日志保留天数 */
    private DaysConfig auditLog = new DaysConfig();

    /** OCR 日志保留天数 */
    private DaysConfig ocrLog = new DaysConfig();

    /** 分批删除每批条数 */
    private int batchSize = 500;

    /** 每批之间 sleep 毫秒数 */
    private int batchSleepMs = 100;

    @Data
    public static class DaysConfig {
        private int days = 90;
    }
}
