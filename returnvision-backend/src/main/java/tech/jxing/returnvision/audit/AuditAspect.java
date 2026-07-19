package tech.jxing.returnvision.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tech.jxing.returnvision.model.entity.OperationLog;
import tech.jxing.returnvision.model.mapper.OperationLogMapper;
import tech.jxing.returnvision.security.AuthUser;

/**
 * 【审计模块】AOP 切面
 *
 * 职责：拦截 @AuditLog 注解的方法，自动记录审计日志
 * 层级：audit 层（AOP 切面）
 * 关联：docs/04 第 4.8.4 节
 *
 * 流程：
 *   1. 前置：获取用户信息、IP、User-Agent、请求参数（脱敏）
 *   2. 执行原方法
 *   3. 后置：记录审计日志（成功）
 *   4. 异常：记录审计日志（失败）后重新抛出
 */
@Aspect
@Component
@Slf4j
public class AuditAspect {

    private final OperationLogMapper operationLogMapper;
    private final AuditDataMaskUtil dataMaskUtil;

    public AuditAspect(OperationLogMapper operationLogMapper, AuditDataMaskUtil dataMaskUtil) {
        this.operationLogMapper = operationLogMapper;
        this.dataMaskUtil = dataMaskUtil;
    }

    /**
     * 环绕通知：拦截 @AuditLog 注解的方法
     */
    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        // 步骤1：前置获取上下文信息
        AuthUser currentUser = getCurrentUser();
        HttpServletRequest request = getCurrentRequest();
        String requestData = auditLog.recordParams() ? dataMaskUtil.mask(joinPoint.getArgs()) : null;

        // 步骤2：执行原方法
        Object result;
        boolean success = true;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            // 步骤4：异常时记录失败审计
            success = false;
            throw e;
        } finally {
            // 步骤3/4：记录审计日志（成功/失败都记录）
            try {
                recordAuditLog(auditLog, currentUser, request, requestData, success);
            } catch (Exception ex) {
                // 审计记录失败不影响主流程
                log.error("[审计] 记录审计日志失败", ex);
            }
        }
    }

    /**
     * 记录审计日志
     */
    private void recordAuditLog(AuditLog auditLog, AuthUser currentUser,
                                 HttpServletRequest request, String requestData, boolean success) {
        OperationLog log = new OperationLog();
        log.setAction(auditLog.action());
        log.setTargetType(auditLog.targetType());
        log.setDescription(auditLog.description());
        log.setSuccess(success);
        log.setRequestData(requestData);

        // 用户信息（未登录时为 NULL，如登录失败）
        if (currentUser != null) {
            log.setUserId(currentUser.getUserId());
            log.setUsername(currentUser.getUsername());
        }

        // 请求信息
        if (request != null) {
            log.setIp(getClientIp(request));
            String ua = request.getHeader("User-Agent");
            if (ua != null && ua.length() > 200) {
                ua = ua.substring(0, 200);
            }
            log.setUserAgent(ua);
        }

        operationLogMapper.insert(log);
    }

    /**
     * 从 SecurityContext 获取当前用户（未登录时返回 null）
     */
    private AuthUser getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser) {
            return (AuthUser) auth.getPrincipal();
        }
        return null;
    }

    /**
     * 获取当前 HttpServletRequest（非 HTTP 上下文时返回 null）
     */
    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    /**
     * 获取客户端真实 IP（处理代理转发）
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}
