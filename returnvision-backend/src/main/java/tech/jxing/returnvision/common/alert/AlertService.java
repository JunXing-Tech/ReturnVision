package tech.jxing.returnvision.common.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 【公共模块】异常监控告警服务
 *
 * 职责：将线上"沉默错误"通过飞书机器人 Webhook 推送到运维群，支持级别分级、去重、异步发送
 * 层级：common.alert 层
 * 调用方：UploadController（OCR 双失败 / 飞书写入失败）、CosClientService（COS 上传失败）、
 *         ReturnVisionApplication（应用启动失败）、GlobalExceptionHandler（系统异常）
 * 关联：docs/04 第 4.5 节、docs/10 第 7.4 节 F12
 *
 * 设计要点：
 *   1. 复用 feishu.bot-webhook 配置，不引入新环境变量
 *   2. 同类告警 dedup-window-ms 内只发一次（默认 5 分钟）
 *   3. 飞书写入失败用独立计数器，达到 feishu-fail-threshold 触发告警
 *   4. 异步发送（单线程 Executor），不阻塞业务流程
 *   5. webhook 未配置或 alert.enabled=false 时静默跳过
 *   6. 告警消息严禁包含手机号/姓名/地址等个人信息（PIPL 合规）
 */
@Service
@Slf4j
public class AlertService {

    private final String webhookUrl;
    private final boolean enabled;
    private final long dedupWindowMs;
    private final int feishuFailThreshold;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    /** 去重缓存：alertType -> 上次发送时间戳 */
    private final ConcurrentHashMap<String, Long> lastSentMap = new ConcurrentHashMap<>();

    /** 飞书写入连续失败计数器 */
    private final AtomicInteger feishuFailCount = new AtomicInteger(0);

    /** 告警异步发送线程池（守护线程，不阻塞 JVM 关闭） */
    private final ExecutorService alertExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "alert-sender");
        t.setDaemon(true);
        return t;
    });

    private static final MediaType JSON = MediaType.parse("application/json");

    /** 北京时区，用于告警时间戳格式化 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 构造器注入
     *
     * @param webhookUrl          飞书机器人 Webhook 地址（feishu.bot-webhook）
     * @param enabled             告警开关（alert.enabled，本地开发可关闭）
     * @param dedupWindowMs       去重窗口毫秒（alert.dedup-window-ms，默认 5 分钟）
     * @param feishuFailThreshold 飞书连续失败阈值（alert.feishu-fail-threshold，默认 3）
     * @param httpClient          OkHttp 客户端（AppConfig 提供 Bean）
     * @param objectMapper        Jackson JSON 序列化器
     */
    public AlertService(
            @Value("${feishu.bot-webhook:}") String webhookUrl,
            @Value("${alert.enabled:true}") boolean enabled,
            @Value("${alert.dedup-window-ms:300000}") long dedupWindowMs,
            @Value("${alert.feishu-fail-threshold:3}") int feishuFailThreshold,
            OkHttpClient httpClient,
            ObjectMapper objectMapper) {
        this.webhookUrl = webhookUrl;
        this.enabled = enabled;
        this.dedupWindowMs = dedupWindowMs;
        this.feishuFailThreshold = feishuFailThreshold;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 容器销毁时关闭告警线程池
     */
    @PreDestroy
    public void shutdown() {
        alertExecutor.shutdown();
        try {
            if (!alertExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                alertExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            alertExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 发送告警（自动去重 + 异步发送）
     *
     * 实现步骤：
     *   1. 检查 alert.enabled 开关
     *   2. 检查 webhook 是否配置
     *   3. 去重判定：同类告警在 dedup-window-ms 内只发一次
     *   4. 更新去重缓存 + 清理过期 key
     *   5. 异步发送到飞书机器人
     *
     * @param level     告警级别（WARN/ERROR/CRITICAL）
     * @param alertType 告警类型 key，用于去重（如 "ocr_dual_fail"）
     * @param message   告警消息（不含敏感信息）
     * @param context   上下文键值对（如 cosUrl / record_id / 异常类名）
     */
    public void notify(AlertLevel level, String alertType, String message, Map<String, Object> context) {
        // 步骤1：检查开关
        if (!enabled) {
            return;
        }

        // 步骤2：检查 webhook 配置
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("[告警] webhook 未配置，跳过告警：type={}, msg={}", alertType, message);
            return;
        }

        // 步骤3：去重判定
        long now = System.currentTimeMillis();
        Long lastSent = lastSentMap.get(alertType);
        if (lastSent != null && now - lastSent < dedupWindowMs) {
            log.debug("[告警] 同类告警在去重窗口内，跳过：type={}, 距上次={}ms", alertType, now - lastSent);
            return;
        }

        // 步骤4：更新去重缓存 + 清理过期 key
        lastSentMap.put(alertType, now);
        cleanupExpiredKeys(now);

        // 步骤5：异步发送（拷贝上下文避免并发修改）
        Map<String, Object> ctxCopy = context != null ? new HashMap<>(context) : new HashMap<>();
        alertExecutor.execute(() -> sendToFeishu(level, alertType, message, ctxCopy, now));
    }

    /**
     * 飞书写入失败计数（达到阈值时触发告警）
     *
     * 实现步骤：
     *   1. 计数器自增
     *   2. 未达阈值：仅记录日志
     *   3. 达到阈值：发送 ERROR 告警并重置计数器
     *
     * @param context 描述上下文（如 "record_id=1024"）
     */
    public void recordFeishuFailure(String context) {
        if (!enabled) {
            return;
        }
        int count = feishuFailCount.incrementAndGet();
        log.warn("[告警] 飞书写入失败计数：{}/{}", count, feishuFailThreshold);
        if (count >= feishuFailThreshold) {
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("context", context);
            ctx.put("threshold", feishuFailThreshold);
            notify(AlertLevel.ERROR, "feishu_write_fail",
                    "飞书写入连续失败 " + count + " 次，请检查飞书服务状态", ctx);
            feishuFailCount.set(0);
        }
    }

    /**
     * 飞书写入成功时重置计数器
     */
    public void resetFeishuFailureCount() {
        if (feishuFailCount.get() != 0) {
            feishuFailCount.set(0);
        }
    }

    /**
     * 异步发送到飞书机器人
     *
     * 实现步骤：
     *   1. 构建交互卡片 JSON
     *   2. 调用 Webhook POST
     *   3. 解析响应，失败仅记日志不抛异常
     */
    private void sendToFeishu(AlertLevel level, String alertType, String message,
                              Map<String, Object> context, long timestamp) {
        try {
            // 步骤1：构建卡片 JSON
            String json = buildCardJson(level, alertType, message, context, timestamp);

            // 步骤2：调用 Webhook POST
            RequestBody body = RequestBody.create(json, JSON);
            Request request = new Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                // 步骤3：解析响应
                if (!response.isSuccessful()) {
                    log.error("[告警] 飞书机器人响应失败：code={}, body={}", response.code(), respBody);
                } else {
                    log.info("[告警] 已发送：level={}, type={}", level, alertType);
                }
            }
        } catch (Exception e) {
            // 告警失败不能影响主流程，仅记日志
            log.error("[告警] 发送失败：type={}", alertType, e);
        }
    }

    /**
     * 构建飞书交互卡片 JSON
     *
     * 实现步骤：
     *   1. 组装 header（标题 + 级别 emoji + 颜色 template）
     *   2. 组装 elements（类型/时间/描述/上下文）
     *   3. 序列化为 JSON
     */
    private String buildCardJson(AlertLevel level, String alertType, String message,
                                 Map<String, Object> context, long timestamp) throws Exception {
        Map<String, Object> card = new HashMap<>();
        card.put("msg_type", "interactive");

        Map<String, Object> cardBody = new HashMap<>();

        // 步骤1：header
        Map<String, Object> header = new HashMap<>();
        header.put("title", Map.of("tag", "plain_text",
                "content", level.getEmoji() + " [" + level + "] " + message));
        header.put("template", level.getTemplate());
        cardBody.put("header", header);

        // 步骤2：elements
        List<Map<String, Object>> elements = new ArrayList<>();
        elements.add(createDiv("**类型**：" + alertType));
        elements.add(createDiv("**时间**：" + formatTime(timestamp)));
        elements.add(createDiv("**描述**：" + message));

        if (context != null && !context.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            context.forEach((k, v) -> sb.append("- ").append(k).append("：").append(v).append("\n"));
            elements.add(createDiv("**上下文**：\n" + sb));
        }
        cardBody.put("elements", elements);

        card.put("card", cardBody);

        // 步骤3：序列化
        return objectMapper.writeValueAsString(card);
    }

    /**
     * 创建飞书卡片 div 元素（lark_md 富文本）
     */
    private Map<String, Object> createDiv(String content) {
        return Map.of("tag", "div", "text", Map.of("tag", "lark_md", "content", content));
    }

    /**
     * 格式化时间戳为北京时区字符串
     */
    private String formatTime(long timestamp) {
        return TIME_FMT.format(Instant.ofEpochMilli(timestamp).atZone(ZONE));
    }

    /**
     * 清理超过 2 倍去重窗口的过期 key，避免内存泄漏
     */
    private void cleanupExpiredKeys(long now) {
        long expireThreshold = now - 2 * dedupWindowMs;
        lastSentMap.entrySet().removeIf(e -> e.getValue() < expireThreshold);
    }
}
