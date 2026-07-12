package tech.jxing.returnvision.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tech.jxing.returnvision.common.exception.BizException;

/**
 * 【公共模块】全局异常处理器
 *
 * 职责：统一捕获异常，转换为 ResponseResult 返回前端，Controller 无需手动 try-catch
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：返回具体错误码 */
    @ExceptionHandler(BizException.class)
    public ResponseResult<?> handleBizException(BizException e) {
        log.warn("[业务异常] code={}, msg={}", e.getCode(), e.getMessage());
        return ResponseResult.error(e.getCode(), e.getMessage());
    }

    /** 参数校验异常：返回1001 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseResult<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return ResponseResult.error(1001, msg);
    }

    /** 系统异常：返回9001 */
    @ExceptionHandler(Exception.class)
    public ResponseResult<?> handleException(Exception e) {
        log.error("[系统异常]", e);
        return ResponseResult.error(9001, "系统内部错误");
    }
}
