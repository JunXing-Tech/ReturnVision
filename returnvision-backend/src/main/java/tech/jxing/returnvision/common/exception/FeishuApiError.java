package tech.jxing.returnvision.common.exception;

/**
 * 【公共模块】飞书API调用失败异常
 *
 * 错误码：2001
 */
public class FeishuApiError extends BizException {
    public FeishuApiError(String msg) {
        super(2001, msg);
    }
}
