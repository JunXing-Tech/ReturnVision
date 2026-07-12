package tech.jxing.returnvision.common.exception;

/**
 * 【公共模块】LLM分析失败异常
 *
 * 错误码：2003
 */
public class LlmError extends BizException {
    public LlmError(String msg) {
        super(2003, msg);
    }
}
