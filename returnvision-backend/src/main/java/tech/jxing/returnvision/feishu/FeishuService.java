package tech.jxing.returnvision.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import tech.jxing.returnvision.common.alert.AlertService;
import tech.jxing.returnvision.common.exception.FeishuApiError;
import tech.jxing.returnvision.model.entity.FeishuConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 【飞书对接层】飞书多维表格写入服务
 *
 * 职责：将退货记录写入飞书多维表格，返回飞书记录ID
 * 层级：Feishu 层
 * 调用方：UploadController（步骤10）
 * 关联：docs/14 §3.2 / §3.2.1
 *
 * 多租户改造（v2.3）：
 *   - 凭证从 @Value 单例改为方法参数 FeishuConfig
 *   - token 缓存按 configId 隔离（ConcurrentHashMap<Long, CompletableFuture<TokenCache>>），防串号
 *   - 并发 miss 用 CompletableFuture 合并，避免重复请求飞书 token 接口
 *   - 保留旧 writeRecord(data, image) 签名委托新方法（用默认配置），兼容现有调用
 *
 * 流程：获取tenant_access_token -> 调用Bitable API新增记录 -> 返回record_id
 */
@Service
@Slf4j
public class FeishuService {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final AlertService alertService;
    private final FeishuConfigService feishuConfigService;

    private static final String TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
    private static final String BITABLE_RECORD_URL = "https://open.feishu.cn/open-apis/bitable/v1/apps/%s/tables/%s/records";
    private static final MediaType JSON = MediaType.parse("application/json");

    /** 平台级默认配置的 token 缓存 key（configId 为 null 时用此 key，避免 NPE） */
    private static final Long DEFAULT_CONFIG_CACHE_KEY = 0L;

    /** token 缓存：configId -> CompletableFuture<TokenCache>，按 configId 隔离防串号 */
    private final ConcurrentHashMap<Long, CompletableFuture<TokenCache>> tokenCache = new ConcurrentHashMap<>();

    /**
     * 构造器注入
     */
    public FeishuService(OkHttpClient httpClient,
                         ObjectMapper objectMapper,
                         AlertService alertService,
                         FeishuConfigService feishuConfigService) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.alertService = alertService;
        this.feishuConfigService = feishuConfigService;
    }

    /**
     * 将退货记录写入飞书多维表格（旧签名，兼容现有调用）
     *
     * 兼容说明：用平台级默认配置（.env）写入，等价于改造前的全局单例行为。
     * 多租户场景应调用 writeRecord(data, imageUrl, config) 显式传配置。
     *
     * @param recordData 退货记录数据（waybill_no, rec_name等字段）
     * @param imageUrl   COS图片URL
     * @return 飞书记录ID（record_id）
     */
    public String writeRecord(Map<String, Object> recordData, String imageUrl) {
        return writeRecord(recordData, imageUrl, feishuConfigService.getDefaultConfig());
    }

    /**
     * 将退货记录写入飞书多维表格（多租户签名）
     *
     * 实现步骤：
     *   1. 凭证兜底：config 为 null 或 appId 为空时，尝试默认配置；仍为空则跳过返回 null
     *   2. 获取tenant_access_token（按 configId 缓存隔离）
     *   3. 组装表格字段数据（列名 -> 值）
     *   4. 调用Bitable API新增记录
     *   5. 返回飞书记录ID
     *
     * @param recordData 退货记录数据（waybill_no, rec_name等字段）
     * @param imageUrl   COS图片URL
     * @param config     飞书配置（null 走 .env 默认兜底）
     * @return 飞书记录ID（record_id）
     */
    @SuppressWarnings("unchecked")
    public String writeRecord(Map<String, Object> recordData, String imageUrl, FeishuConfig config) {
        // 步骤1：凭证兜底
        if (config == null || config.getAppId() == null || config.getAppId().isEmpty()) {
            config = feishuConfigService.getDefaultConfig();
        }
        if (config == null || config.getAppId() == null || config.getAppId().isEmpty()) {
            log.warn("[飞书] 凭证未配置，跳过飞书写入，waybill_no={}", recordData.get("waybill_no"));
            return null;
        }

        log.info("[飞书] 开始写入记录，waybill_no={}, orgName={}",
                recordData.get("waybill_no"), config.getOrgName());

        String waybillNo = recordData.getOrDefault("waybill_no", "").toString();

        try {
            // 步骤2：获取tenant_access_token（按 configId 隔离缓存）
            String token = getTenantAccessToken(config);

            // 步骤3：组装表格字段数据（列名使用中文，与飞书表格列名对应）
            Map<String, Object> fields = new HashMap<>();
            fields.put("运单号", recordData.getOrDefault("waybill_no", ""));
            fields.put("收件人姓名", recordData.getOrDefault("rec_name", ""));
            fields.put("收件人电话", recordData.getOrDefault("rec_phone", ""));
            fields.put("收件人地址", recordData.getOrDefault("rec_address", ""));
            fields.put("寄件人姓名", recordData.getOrDefault("sender_name", ""));
            fields.put("寄件人电话", recordData.getOrDefault("sender_phone", ""));
            fields.put("寄件人地址", recordData.getOrDefault("sender_address", ""));
            fields.put("快递公司", recordData.getOrDefault("express_company", ""));
            fields.put("托寄物", recordData.getOrDefault("goods", ""));
            fields.put("退货原因", recordData.getOrDefault("return_reason", ""));
            fields.put("退货分类", recordData.getOrDefault("return_category", ""));
            fields.put("图片链接", Map.of("link", imageUrl, "text", "查看图片"));
            fields.put("退货日期", System.currentTimeMillis());

            // 步骤4：调用Bitable API新增记录
            Map<String, Object> body = new HashMap<>();
            body.put("fields", fields);

            String url = String.format(BITABLE_RECORD_URL, config.getAppToken(), config.getTableId());
            RequestBody requestBody = RequestBody.create(
                    objectMapper.writeValueAsString(body), JSON);
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);

                // 步骤5：解析返回的record_id
                double code = ((Number) result.getOrDefault("code", -1)).doubleValue();
                if (code != 0) {
                    log.error("[飞书] 写入失败，code={}, msg={}", code, result.get("msg"));
                    // F12 埋点：飞书 API 返回错误，记入连续失败计数
                    alertService.recordFeishuFailure("waybill_no=" + waybillNo);
                    throw new FeishuApiError("飞书写入失败：" + result.get("msg"));
                }

                Map<String, Object> data = (Map<String, Object>) result.get("data");
                Map<String, Object> record = (Map<String, Object>) data.get("record");
                String recordId = (String) record.get("record_id");

                // F12 埋点：写入成功，重置连续失败计数
                alertService.resetFeishuFailureCount();
                log.info("[飞书] 写入成功，record_id={}", recordId);
                return recordId;
            }
        } catch (FeishuApiError e) {
            throw e;
        } catch (Exception e) {
            log.error("[飞书] 写入异常", e);
            // F12 埋点：其他异常（网络/序列化等），记入连续失败计数
            alertService.recordFeishuFailure("waybill_no=" + waybillNo + ", error=" + e.getClass().getSimpleName());
            throw new FeishuApiError("飞书写入异常：" + e.getMessage());
        }
    }

    /**
     * 获取tenant_access_token（按 configId 隔离缓存 + 并发合并 + 失败自清理）
     *
     * 实现步骤：
     *   1. 取缓存 key（configId 为 null 用 DEFAULT_CONFIG_CACHE_KEY）
     *   2. computeIfAbsent 取/创建 CompletableFuture，多线程 miss 合并为同一 Future
     *   3. 失败自清理：Future 异常完成时移除自身，避免后续请求持续命中失败 Future（否则飞书恢复后仍无法恢复）
     *   4. 过期则移除并递归获取一次（新 token 有效期 7200s 不会立即过期，递归安全）
     *
     * @param config 飞书配置
     * @return tenant_access_token
     */
    private String getTenantAccessToken(FeishuConfig config) throws Exception {
        Long cacheKey = config.getId() != null ? config.getId() : DEFAULT_CONFIG_CACHE_KEY;

        CompletableFuture<TokenCache> future = tokenCache.computeIfAbsent(cacheKey,
                k -> {
                    CompletableFuture<TokenCache> f = CompletableFuture.supplyAsync(() -> fetchNewToken(config));
                    // 失败自清理：异常完成时移除自身，防止失败 Future 滞留缓存导致后续请求无法恢复
                    f.whenComplete((result, ex) -> {
                        if (ex != null) {
                            tokenCache.remove(cacheKey, f);
                            log.warn("[飞书] token 获取失败，已清理缓存条目，configId={}", config.getId());
                        }
                    });
                    return f;
                });
        TokenCache cache = future.get();

        // 过期则移除重新获取（提前 5 分钟过期）
        long now = System.currentTimeMillis();
        if (cache == null || now >= cache.expireAt - 300_000) {
            tokenCache.remove(cacheKey, future);
            return getTenantAccessToken(config);  // 递归一次
        }
        return cache.token;
    }

    /**
     * 调用飞书 API 获取新 token（同步，包在 CompletableFuture 中异步执行）
     *
     * 实现步骤：
     *   1. 组装请求体（app_id + app_secret）
     *   2. 调飞书 token 接口
     *   3. 缓存 token + 过期时间
     */
    @SuppressWarnings("unchecked")
    private TokenCache fetchNewToken(FeishuConfig config) {
        try {
            log.info("[飞书] 获取tenant_access_token，orgName={}", config.getOrgName());
            Map<String, Object> body = new HashMap<>();
            body.put("app_id", config.getAppId());
            body.put("app_secret", config.getAppSecret());

            RequestBody requestBody = RequestBody.create(
                    objectMapper.writeValueAsString(body), JSON);
            Request request = new Request.Builder()
                    .url(TOKEN_URL)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body().string();
                Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);

                double code = ((Number) result.getOrDefault("code", -1)).doubleValue();
                if (code != 0) {
                    throw new FeishuApiError("获取飞书token失败：" + result.get("msg"));
                }

                String token = (String) result.get("tenant_access_token");
                int expire = ((Number) result.getOrDefault("expire", 7200)).intValue();
                long expireAt = System.currentTimeMillis() + expire * 1000L;

                log.info("[飞书] 获取token成功，orgName={}, 有效期={}秒", config.getOrgName(), expire);
                return new TokenCache(token, expireAt);
            }
        } catch (Exception e) {
            log.error("[飞书] 获取token异常，orgName={}", config.getOrgName(), e);
            throw new RuntimeException("获取飞书token失败：" + e.getMessage(), e);
        }
    }

    /**
     * 配置变更时清除 token 缓存（改进点 #5）
     *
     * 场景：feishu_config 更新（改 app_secret/app_id）后，旧 token 可能已失效，需重新获取
     *
     * @param configId 飞书配置ID
     */
    public void invalidateTokenCache(Long configId) {
        Long cacheKey = configId != null ? configId : DEFAULT_CONFIG_CACHE_KEY;
        CompletableFuture<TokenCache> removed = tokenCache.remove(cacheKey);
        if (removed != null) {
            log.info("[飞书] 已清除 token 缓存，configId={}", configId);
        }
    }

    /** token 缓存条目 */
    private record TokenCache(String token, long expireAt) {}
}
