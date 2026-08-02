package tech.jxing.returnvision.common.ratelimit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 【公共模块】注册接口限流器
 *
 * 职责：防暴力注册，同一 IP 每小时限制 N 次
 * 模式：复用 ExportRateLimiter 的 ConcurrentHashMap+AtomicInteger 模式
 * 关联：docs/14 §3.7
 *
 * 设计要点：
 *   1. key 为 "ip+小时"（每小时重置，旧 key 自动失效被 GC）
 *   2. 超限抛 IllegalStateException，由调用方转 BizException
 */
@Component
@Slf4j
public class RegisterRateLimiter {

    private static final int MAX_PER_HOUR = 5;
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    /**
     * 检查并计数
     *
     * @param ip 客户端 IP
     * @return true 允许注册，false 超限
     */
    public boolean tryAcquire(String ip) {
        String key = ip + ":" + LocalDateTime.now().format(HOUR_FMT);
        AtomicInteger counter = counters.computeIfAbsent(key, k -> new AtomicInteger(0));
        int current = counter.incrementAndGet();
        if (current > MAX_PER_HOUR) {
            log.warn("[限流] 注册接口超限，ip={}, count={}", ip, current);
            return false;
        }
        return true;
    }
}
