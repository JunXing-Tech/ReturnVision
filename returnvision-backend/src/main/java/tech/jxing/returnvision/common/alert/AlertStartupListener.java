package tech.jxing.returnvision.common.alert;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationFailedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 【公共模块】应用启动失败告警监听器
 *
 * 职责：监听 Spring Boot 启动失败事件，发送 CRITICAL 级别告警
 * 层级：common.alert 层
 * 关联：docs/04 第 4.5.6 节埋点位置 4
 *
 * 设计说明：
 *   启动失败时 Spring 容器可能未完全初始化，因此用 ApplicationContext 在事件触发时
 *   主动获取 AlertService Bean，并通过 try-catch 兜底，避免告警失败影响日志输出。
 *   用 @EventListener 注册而不是在启动类上加监听，是因为启动类本身不是 Bean。
 */
@Component
@Slf4j
public class AlertStartupListener {

    private final ApplicationContext applicationContext;

    public AlertStartupListener(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 应用启动失败时触发 CRITICAL 告警
     *
     * 实现步骤：
     *   1. 从容器获取 AlertService（可能未就绪）
     *   2. 提取异常信息
     *   3. 发送 CRITICAL 告警
     */
    @EventListener(ApplicationFailedEvent.class)
    public void onStartupFailure(ApplicationFailedEvent event) {
        try {
            // 步骤1：从容器获取 AlertService（启动失败时可能未就绪）
            AlertService alertService = applicationContext.getBean(AlertService.class);

            // 步骤2：提取异常信息
            Throwable ex = event.getException();
            String exClass = ex != null ? ex.getClass().getSimpleName() : "Unknown";
            String exMsg = ex != null && ex.getMessage() != null ? ex.getMessage() : "no message";

            // 步骤3：发送 CRITICAL 告警
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("exception", exClass);
            ctx.put("message", exMsg.length() > 200 ? exMsg.substring(0, 200) + "..." : exMsg);
            alertService.notify(AlertLevel.CRITICAL, "app_start_fail",
                    "应用启动失败：" + exClass, ctx);
            log.error("[告警] 启动失败告警已触发：{}", exClass);
        } catch (Exception e) {
            // 告警失败不能掩盖原始启动失败，仅记日志
            log.error("[告警] 启动失败告警发送异常", e);
        }
    }
}
