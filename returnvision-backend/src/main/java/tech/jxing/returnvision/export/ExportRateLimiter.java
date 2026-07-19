package tech.jxing.returnvision.export;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 【导出管控模块】导出限流器（F02）
 *
 * 职责：限制单次导出条数 + 每人每日导出次数
 * 层级：export 层
 * 关联：docs/04 第 4.9.6 节
 *
 * 限流规则：
 *   - 单次最多 1000 条（由 ExportService 查询前校验）
 *   - 每人每日最多 5 次（本类负责计数）
 *
 * 实现：
 *   - ConcurrentHashMap 存 "userId+date -> 次数"
 *   - 每天日期变化后自动开始新计数（旧 key 自然失效）
 *   - 每天 0 点后台清理昨天的 key（避免内存泄漏）
 */
@Component
@Slf4j
public class ExportRateLimiter {

    private final ConcurrentHashMap<String, AtomicInteger> dailyCountMap = new ConcurrentHashMap<>();

    private final int maxExportsPerDay;

    public ExportRateLimiter(org.springframework.core.env.Environment env) {
        this.maxExportsPerDay = env.getProperty("export.max-exports-per-day", Integer.class, 5);
    }

    /**
     * 检查并增加今日导出次数
     *
     * 实现步骤：
     *   1. 构造 key（userId + 今日日期）
     *   2. 原子递增
     *   3. 超限返回 false
     *
     * @param userId 用户ID
     * @return true=允许导出，false=超限
     */
    public boolean tryAcquire(Long userId) {
        String key = buildKey(userId);
        AtomicInteger count = dailyCountMap.computeIfAbsent(key, k -> new AtomicInteger(0));
        int current = count.incrementAndGet();

        if (current > maxExportsPerDay) {
            log.warn("[导出限流] 用户 {} 今日导出次数超限：{}/{}", userId, current, maxExportsPerDay);
            return false;
        }

        log.info("[导出限流] 用户 {} 今日导出次数：{}/{}", userId, current, maxExportsPerDay);
        return true;
    }

    /**
     * 清理昨天的计数（每天凌晨调用）
     */
    public void cleanupYesterday() {
        String yesterday = LocalDate.now().minusDays(1).toString();
        int beforeSize = dailyCountMap.size();
        dailyCountMap.keySet().removeIf(k -> k.endsWith(yesterday));
        int afterSize = dailyCountMap.size();
        log.info("[导出限流] 清理昨日计数：{} -> {}", beforeSize, afterSize);
    }

    private String buildKey(Long userId) {
        return userId + "_" + LocalDate.now();
    }
}
