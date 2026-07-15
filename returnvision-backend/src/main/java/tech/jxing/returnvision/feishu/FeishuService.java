package tech.jxing.returnvision.feishu;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.jxing.returnvision.common.exception.FeishuApiError;

import java.util.HashMap;
import java.util.Map;

/**
 * 【飞书对接层】飞书多维表格写入服务
 *
 * 职责：将退货记录写入飞书多维表格，返回飞书记录ID
 * 层级：Feishu 层
 * 调用方：UploadController（步骤10）
 *
 * 流程：获取tenant_access_token -> 调用Bitable API新增记录 -> 返回record_id
 */
@Service
@Slf4j
public class FeishuService {

    private final String appId;
    private final String appSecret;
    private final String appToken;
    private final String tableId;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final String TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
    private static final String BITABLE_RECORD_URL = "https://open.feishu.cn/open-apis/bitable/v1/apps/%s/tables/%s/records";
    private static final MediaType JSON = MediaType.parse("application/json");

    /** 缓存的tenant_access_token */
    private String cachedToken;
    /** token过期时间（毫秒时间戳） */
    private long tokenExpireAt;

    /**
     * 构造器注入
     */
    public FeishuService(
            @Value("${feishu.app-id}") String appId,
            @Value("${feishu.app-secret}") String appSecret,
            @Value("${feishu.app-token}") String appToken,
            @Value("${feishu.table-id}") String tableId,
            OkHttpClient httpClient,
            ObjectMapper objectMapper) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.appToken = appToken;
        this.tableId = tableId;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 将退货记录写入飞书多维表格
     *
     * 实现步骤：
     *   1. 获取tenant_access_token
     *   2. 组装表格字段数据（列名 -> 值）
     *   3. 调用Bitable API新增记录
     *   4. 返回飞书记录ID
     *
     * @param recordData 退货记录数据（waybill_no, rec_name等字段）
     * @param imageUrl   COS图片URL
     * @return 飞书记录ID（record_id）
     */
    @SuppressWarnings("unchecked")
    public String writeRecord(Map<String, Object> recordData, String imageUrl) {
        log.info("[飞书] 开始写入记录，waybill_no={}", recordData.get("waybill_no"));

        // 凭证未配置时优雅降级
        if (appId == null || appId.isEmpty() || appSecret == null || appSecret.isEmpty()) {
            log.warn("[飞书] 凭证未配置，跳过飞书写入");
            return null;
        }

        try {
            // 步骤1：获取tenant_access_token
            String token = getTenantAccessToken();

            // 步骤2：组装表格字段数据（列名使用中文，与飞书表格列名对应）
            Map<String, Object> fields = new HashMap<>();
            fields.put("运单号", recordData.getOrDefault("waybill_no", ""));
            fields.put("收件人姓名", recordData.getOrDefault("rec_name", ""));
            fields.put("收件人电话", recordData.getOrDefault("rec_phone", ""));
            fields.put("收件人地址", recordData.getOrDefault("rec_address", ""));
            fields.put("寄件人姓名", recordData.getOrDefault("sender_name", ""));
            fields.put("寄件人电话", recordData.getOrDefault("sender_phone", ""));
            fields.put("快递公司", recordData.getOrDefault("express_company", ""));
            fields.put("退货原因", recordData.getOrDefault("return_reason", ""));
            fields.put("退货分类", recordData.getOrDefault("return_category", ""));
            fields.put("图片链接", Map.of("link", imageUrl, "text", "查看图片"));
            fields.put("退货日期", System.currentTimeMillis());

            // 步骤3：调用Bitable API新增记录
            Map<String, Object> body = new HashMap<>();
            body.put("fields", fields);

            String url = String.format(BITABLE_RECORD_URL, appToken, tableId);
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

                // 步骤4：解析返回的record_id
                double code = ((Number) result.getOrDefault("code", -1)).doubleValue();
                if (code != 0) {
                    log.error("[飞书] 写入失败，code={}, msg={}", code, result.get("msg"));
                    throw new FeishuApiError("飞书写入失败：" + result.get("msg"));
                }

                Map<String, Object> data = (Map<String, Object>) result.get("data");
                Map<String, Object> record = (Map<String, Object>) data.get("record");
                String recordId = (String) record.get("record_id");

                log.info("[飞书] 写入成功，record_id={}", recordId);
                return recordId;
            }
        } catch (FeishuApiError e) {
            throw e;
        } catch (Exception e) {
            log.error("[飞书] 写入异常", e);
            throw new FeishuApiError("飞书写入异常：" + e.getMessage());
        }
    }

    /**
     * 获取tenant_access_token（带缓存）
     *
     * 实现步骤：
     *   1. 检查缓存的token是否有效
     *   2. 若无效，调用飞书API获取新token
     *   3. 缓存token并设置过期时间
     */
    @SuppressWarnings("unchecked")
    private String getTenantAccessToken() throws Exception {
        // 步骤1：检查缓存的token是否有效（提前5分钟过期）
        long now = System.currentTimeMillis();
        if (cachedToken != null && now < tokenExpireAt - 300_000) {
            return cachedToken;
        }

        // 步骤2：调用飞书API获取新token
        log.info("[飞书] 获取tenant_access_token");
        Map<String, Object> body = new HashMap<>();
        body.put("app_id", appId);
        body.put("app_secret", appSecret);

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

            // 步骤3：缓存token
            cachedToken = (String) result.get("tenant_access_token");
            int expire = ((Number) result.getOrDefault("expire", 7200)).intValue();
            tokenExpireAt = now + expire * 1000L;

            log.info("[飞书] 获取token成功，有效期={}秒", expire);
            return cachedToken;
        }
    }
}
