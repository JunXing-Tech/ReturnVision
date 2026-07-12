package tech.jxing.returnvision.common.exception;

/**
 * 【公共模块】COS上传失败异常
 *
 * 错误码：2004
 */
public class CosError extends BizException {
    public CosError(String msg) {
        super(2004, msg);
    }
}
