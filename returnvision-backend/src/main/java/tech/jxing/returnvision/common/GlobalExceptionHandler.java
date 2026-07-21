package tech.jxing.returnvision.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tech.jxing.returnvision.common.alert.AlertLevel;
import tech.jxing.returnvision.common.alert.AlertService;
import tech.jxing.returnvision.common.exception.BizException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

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

    /**
     * 权限拒绝异常：返回403，不触发告警
     *
     * 说明：AccessDeniedException 及其子类（如 Spring Security 6 方法级注解抛出的
     * AuthorizationDeniedException）属于正常业务行为，不应作为系统异常告警推送飞书。
     * 接口由 SecurityConfig 的 accessDeniedHandler 处理不到时（如 @PreAuthorize 在
     * Controller 方法阶段抛出），由此处理器兜底。
     *
     * 日志字段说明（用于线上排查权限拒绝根因）：
     *   - path：请求方法 + URI，定位是哪个接口被拒
     *   - user：当前认证用户名（未认证时为 anonymous）
     *   - authorities：当前用户拥有的权限列表（如 ROLE_ADMIN），空列表即根因
     *   - exception：异常类全名（区分 AuthorizationDeniedException / AccessDeniedException）
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseResult<?> handleAccessDenied(AccessDeniedException e, HttpServletRequest request) {
        // 步骤1：从 SecurityContext 获取当前用户信息（路径级拒绝时可能已认证但角色不足）
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "anonymous";
        String authorities = auth != null
                ? auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.joining(","))
                : "";

        // 步骤2：组装请求路径（method + URI）
        String path = request != null
                ? request.getMethod() + " " + request.getRequestURI()
                : "unknown";

        // 步骤3：记录详细日志（关键：authorities 为空即可定位"JWT roles 为空"根因）
        log.warn("[权限拒绝] path={}, user={}, authorities=[{}], exception={}, msg={}",
                path, username, authorities, e.getClass().getName(), e.getMessage());

        return ResponseResult.error(403, "权限不足");
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
