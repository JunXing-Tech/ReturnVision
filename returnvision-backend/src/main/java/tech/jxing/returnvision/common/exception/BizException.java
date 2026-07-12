package tech.jxing.returnvision.common.exception;

/**
 * 【公共模块】业务异常基类
 *
 * 职责：所有业务异常的父类，携带错误码，由全局异常处理器统一捕获
 */
public class BizException extends RuntimeException {
    private final int code;

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
