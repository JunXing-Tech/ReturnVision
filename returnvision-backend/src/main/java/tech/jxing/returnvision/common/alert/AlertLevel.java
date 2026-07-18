package tech.jxing.returnvision.common.alert;

/**
 * 【公共模块】告警级别枚举
 *
 * 职责：定义告警严重程度，对应飞书卡片不同颜色，便于运维快速识别
 * 层级：common.alert 层
 * 关联：AlertService 发送告警时传入；docs/04 第 4.5.3 节
 */
public enum AlertLevel {

    /** 警告：可恢复的单次失败，如 OCR 双失败、COS 上传失败 */
    WARN("🟡", "yellow"),

    /** 错误：需运维介入，如飞书写入连续失败、系统未预期异常 */
    ERROR("🟠", "orange"),

    /** 严重：服务可能不可用，如应用启动失败 */
    CRITICAL("🔴", "red");

    /** emoji 前缀，用于飞书卡片标题视觉区分 */
    private final String emoji;

    /** 飞书卡片 header template 颜色值 */
    private final String template;

    AlertLevel(String emoji, String template) {
        this.emoji = emoji;
        this.template = template;
    }

    public String getEmoji() {
        return emoji;
    }

    public String getTemplate() {
        return template;
    }
}
