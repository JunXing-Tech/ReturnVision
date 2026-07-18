package tech.jxing.returnvision.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tech.jxing.returnvision.common.alert.AlertLevel;
import tech.jxing.returnvision.common.alert.AlertService;
import tech.jxing.returnvision.common.exception.BizException;

import java.util.HashMap;
import java.util.Map;

/**
 * 【公共模块】全局异常处理器
 *
 * 职责：统一捕获异常，转换为 ResponseResult 返回前端，Controller 无需手动 try-catch
 * 关联：F12 异常监控告警 -- 系统异常分支接入 AlertService
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final AlertService alertService;

    public GlobalExceptionHandler(AlertService alertService) {
        this.alertService = alertService;
    }

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

    /** 系统异常：返回9001，并触发 F12 告警 */
    @ExceptionHandler(Exception.class)
    public ResponseResult<?> handleException(Exception e) {
        log.error("[系统异常]", e);
        // F12 埋点：未预期的系统异常，按 ERROR 级别告警
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("exception", e.getClass().getName());
        String msg = e.getMessage();
        if (msg != null) {
            ctx.put("message", msg.length() > 200 ? msg.substring(0, 200) + "..." : msg);
        }
        alertService.notify(AlertLevel.ERROR, "system_error",
                "系统内部异常：" + e.getClass().getSimpleName(), ctx);
        return ResponseResult.error(9001, "系统内部错误");
    }
}
