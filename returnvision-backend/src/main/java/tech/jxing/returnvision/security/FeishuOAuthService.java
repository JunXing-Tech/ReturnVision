package tech.jxing.returnvision.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.jxing.returnvision.feishu.FeishuConfigService;
import tech.jxing.returnvision.model.entity.FeishuConfig;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 【鉴权模块】飞书 OAuth 服务
 *
 * 职责：生成授权 URL + 用授权码换 user_access_token + 获取用户信息
 * 层级：security 层
 * 调用方：AuthService（飞书登录流程）
 * 关联：docs/04 第 4.7.7 节 OAuth 首次登录策略
 *
 * 多租户改造（v2.3）：
 *   - generateAuthUrl / getUserInfoByCode 支持传 configId，用对应公司的飞书应用凭证
 *   - configId=null 走平台级 .env 配置（向后兼容 + 平台用户）
 *   - 隔离保证：用哪个 app_id 生成的 URL，code 就只能用该 app_id 的 secret 换 token
 *
 * 流程（v1 OAuth 完整链路，2026-07-20 修复 20014 错误）：
 *   1. 前端调 /api/auth/feishu/url?config_id=xxx 获取授权 URL（config_id 决定用哪家公司的飞书应用）
 *   2. 用户跳转飞书授权，飞书回调前端 URL（带 code）
 *   3. 前端调 /api/auth/feishu/callback 传 code + state（state 含 configId）
 *   4. 后端从 state 解析 configId，用该公司的 app_id + app_secret 换 app_access_token
 *   5. 后端用 app_access_token 作为 Bearer，带 code 调 /authen/v1/access_token 换 user_access_token
 *   6. 后端用 user_access_token 作为 Bearer 调 /authen/v1/user_info 拿用户信息
 *   7. 用 feishu_user_id 查 sys_user，已绑定则登录，未绑定返回 1004
 */
@Service
@Slf4j
public class FeishuOAuthService {

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final FeishuConfigService feishuConfigService;

    private static final String AUTH_URL = "https://open.feishu.cn/open-apis/authen/v1/index";
    /** F01 修复：v1 oidc 接口需要 app_access_token，先调 v3 拿 app_access_token */
    private static final String APP_TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/app_access_token/internal";
    private static final String TOKEN_URL = "https://open.feishu.cn/open-apis/authen/v1/access_token";
    private static final String USER_INFO_URL = "https://open.feishu.cn/open-apis/authen/v1/user_info";
    private static final MediaType JSON = MediaType.parse("application/json");

    public FeishuOAuthService(
            @Value("${feishu.oauth.client-id:}") String clientId,
            @Value("${feishu.oauth.client-secret:}") String clientSecret,
            @Value("${feishu.oauth.redirect-uri:}") String redirectUri,
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            FeishuConfigService feishuConfigService) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.feishuConfigService = feishuConfigService;
    }

    /**
     * 生成飞书 OAuth 授权 URL（平台级，向后兼容）
     *
     * @param state 防 CSRF 的随机串（前端生成，回调时原样返回校验）
     * @return 授权 URL
     */
    public String generateAuthUrl(String state) {
        return generateAuthUrl(state, null);
    }

    /**
     * 生成飞书 OAuth 授权 URL（多租户，按 configId 用对应公司飞书应用）
     *
     * 实现步骤：
     *   1. configId 为 null -> 用平台级 .env 的 clientId
     *   2. configId 非空 -> 用 FeishuConfigService 取该公司的 appId
     *   3. redirect_uri 统一用平台级（所有公司飞书应用后台配同一回调地址）
     *
     * @param state    防 CSRF 随机串
     * @param configId 飞书配置ID（null=平台级）
     * @return 授权 URL
     */
    public String generateAuthUrl(String state, Long configId) {
        String appId = resolveAppId(configId);
        return AUTH_URL + "?app_id=" + appId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&state=" + state;
    }

    /**
     * 用 code 换取用户信息（平台级，向后兼容）
     */
    public Map<String, String> getUserInfoByCode(String code) throws Exception {
        return getUserInfoByCode(code, null);
    }

    /**
     * 用 code 换取用户信息（多租户，按 configId 用对应公司凭证）
     *
     * 实现步骤：
     *   1. 按 configId 解析公司凭证（FeishuConfig，含明文 app_secret）
     *   2. 用该公司 app_id + app_secret 换 app_access_token
     *   3. 用 app_access_token + code 换 user_access_token
     *   4. 用 user_access_token 调 user_info 拿用户信息
     *
     * @param code     飞书回调的授权码
     * @param configId 飞书配置ID（null=平台级）
     * @return {feishu_user_id, name}，失败返回 null
     */
    public Map<String, String> getUserInfoByCode(String code, Long configId) throws Exception {
        log.info("[飞书OAuth] 开始换 token 流程，code 长度={}, configId={}", code.length(), configId);

        // 步骤1：解析公司凭证
        FeishuConfig config = feishuConfigService.resolveConfig(configId);
        if (config == null) {
            log.error("[飞书OAuth] 凭证未配置，configId={}", configId);
            return null;
        }

        // 步骤2：用 app_id + app_secret 换 app_access_token
        String appAccessToken = getAppAccessToken(config);
        if (appAccessToken == null) {
            log.error("[飞书OAuth] 获取 app_access_token 失败，configId={}", configId);
            return null;
        }
        log.info("[飞书OAuth] 拿到 app_access_token，长度={}", appAccessToken.length());

        // 步骤3：用 app_access_token + code 换 user_access_token
        Map<String, Object> body = new HashMap<>();
        body.put("grant_type", "authorization_code");
        body.put("code", code);

        String tokenResp = postJsonWithAuth(TOKEN_URL, body, appAccessToken);
        log.info("[飞书OAuth] 换 user_access_token 返回：{}", tokenResp);
        Map<String, Object> tokenResult = objectMapper.readValue(tokenResp, Map.class);
        Number code0 = (Number) tokenResult.getOrDefault("code", -1);
        if (code0.intValue() != 0) {
            log.error("[飞书OAuth] 换 user_access_token 失败：code={}, msg={}", code0, tokenResult.get("msg"));
            return null;
        }

        Map<String, Object> data = (Map<String, Object>) tokenResult.get("data");
        String userAccessToken = (String) data.get("access_token");
        if (userAccessToken == null || userAccessToken.isEmpty()) {
            log.error("[飞书OAuth] user_access_token 为空：{}", tokenResp);
            return null;
        }

        // 步骤4：用 user_access_token 调 user_info 拿用户信息
        Map<String, String> userInfo = getUserInfo(userAccessToken);
        if (userInfo != null) {
            log.info("[飞书OAuth] 成功获取用户信息：feishu_user_id={}, name={}, configId={}",
                    userInfo.get("feishu_user_id"), userInfo.get("name"), configId);
        }
        return userInfo;
    }

    /**
     * 按 configId 解析 app_id（null 走平台级 .env）
     */
    private String resolveAppId(Long configId) {
        if (configId == null) {
            return clientId;
        }
        FeishuConfig config = feishuConfigService.resolveConfig(configId);
        if (config == null || config.getAppId() == null) {
            log.warn("[飞书OAuth] configId={} 凭证未配置，回退平台级", configId);
            return clientId;
        }
        return config.getAppId();
    }

    /**
     * 获取 app_access_token（用指定公司凭证）
     */
    private String getAppAccessToken(FeishuConfig config) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("app_id", config.getAppId());
        body.put("app_secret", config.getAppSecret());

        String resp = postJson(APP_TOKEN_URL, body);
        log.info("[飞书OAuth] app_access_token 接口返回：{}", resp);
        Map<String, Object> result = objectMapper.readValue(resp, Map.class);
        Number code = (Number) result.getOrDefault("code", -1);
        if (code.intValue() != 0) {
            log.error("[飞书OAuth] 获取 app_access_token 失败：code={}, msg={}", code, result.get("msg"));
            return null;
        }
        return (String) result.get("app_access_token");
    }

    /**
     * 用 user_access_token 调 user_info 接口
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> getUserInfo(String userAccessToken) throws Exception {
        Request request = new Request.Builder()
                .url(USER_INFO_URL)
                .addHeader("Authorization", "Bearer " + userAccessToken)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            log.info("[飞书OAuth] user_info 接口返回：{}", respBody);
            Map<String, Object> result = objectMapper.readValue(respBody, Map.class);
            Number code = (Number) result.getOrDefault("code", -1);
            if (code.intValue() != 0) {
                log.error("[飞书OAuth] 获取 user_info 失败：code={}, msg={}", code, result.get("msg"));
                return null;
            }

            Map<String, Object> data = (Map<String, Object>) result.get("data");
            Map<String, String> userInfo = new HashMap<>();
            userInfo.put("feishu_user_id", String.valueOf(data.get("user_id")));
            userInfo.put("name", String.valueOf(data.getOrDefault("name", "")));
            return userInfo;
        }
    }

    /**
     * 配置是否就绪（client_id 和 client_secret 都配置了）
     */
    public boolean isConfigured() {
        return clientId != null && !clientId.isEmpty()
                && clientSecret != null && !clientSecret.isEmpty();
    }

    /**
     * POST JSON 请求（无鉴权头）
     */
    private String postJson(String url, Map<String, Object> body) throws Exception {
        RequestBody requestBody = RequestBody.create(objectMapper.writeValueAsString(body), JSON);
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.body() != null ? response.body().string() : "";
        }
    }

    /**
     * POST JSON 请求（带 Bearer 鉴权头）
     */
    private String postJsonWithAuth(String url, Map<String, Object> body, String accessToken) throws Exception {
        RequestBody requestBody = RequestBody.create(objectMapper.writeValueAsString(body), JSON);
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .post(requestBody)
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.body() != null ? response.body().string() : "";
        }
    }
}