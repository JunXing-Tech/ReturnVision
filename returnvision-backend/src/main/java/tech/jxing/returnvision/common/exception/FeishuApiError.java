package tech.jxing.returnvision.common.exception;

/**
 * 【公共模块】飞书API调用失败异常
 *
 * 错误码：2009（v2.3 起从 2001 改为 2009，与 OCR 失败的 2001 分开，改进点 #14）
 *
 * 兼容说明：保留 2001 的旧构造方法，供尚未迁移的调用方使用；新调用方用 2009 默认构造。
 */
public class FeishuApiError extends BizException {

    /** 飞书写入失败（默认，v2.3 起与 OCR 的 2001 分开） */
    public FeishuApiError(String msg) {
        super(2009, msg);
    }

    /** 指定错误码的构造方法（兼容历史调用或特殊场景） */
    public FeishuApiError(int code, String msg) {
        super(code, msg);
    }
}
