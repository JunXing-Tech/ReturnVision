package tech.jxing.returnvision.common.exception;

/**
 * 【公共模块】OCR识别失败异常
 *
 * 错误码：2002
 */
public class OcrError extends BizException {
    public OcrError(String msg) {
        super(2002, msg);
    }
}
