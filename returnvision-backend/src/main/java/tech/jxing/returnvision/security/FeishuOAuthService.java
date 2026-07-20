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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 【鉴权模块】飞书 OAuth 服务
 *
 * 职责：生成授权 URL + 用授权码换 access_token + 获取用户信息
 * 层级：security 层
 * 调用方：AuthService（飞书登录流程）
 * 关联：docs/04 第 4.7.7 节 OAuth 首次登录策略
 *
 * 流程：
 *   1. 前端调 /api/auth/feishu/url 获取授权 URL
 *   2. 用户跳转飞书授权，飞书回调前端 URL（带 code）
 *   3. 前端调 /api/auth/feishu/callback 传 code
 *   4. 后端用 code 换 user_access_token，再获取 user_info
 *   5. 用 feishu_user_id 查 sys_user，已绑定则登录，未绑定返回 1004
 */
@Service
@Slf4j
public class FeishuOAuthService {

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final String AUTH_URL = "https://open.feishu.cn/open-apis/authen/v1/index";
    private static final String TOKEN_URL = "https://open.feishu.cn/open-apis/authen/v1/oidc/access_token";
    private static final String USER_INFO_URL = "https://open.feishu.cn/open-apis/authen/v1/user_info";
    private static final MediaType JSON = MediaType.parse("application/json");

    public FeishuOAuthService(
            @Value("${feishu.oauth.client-id:}") String clientId,
            @Value("${feishu.oauth.client-secret:}") String clientSecret,
            @Value("${feishu.oauth.redirect-uri:}") String redirectUri,
            OkHttpClient httpClient,
            ObjectMapper objectMapper) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成飞书 OAuth 授权 URL
     *
     * @param state 防 CSRF 的随机串（前端生成，回调时原样返回校验）
     * @return 授权 URL
     */
    public String generateAuthUrl(String state) {
        return AUTH_URL + "?app_id=" + clientId
                + "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8)
                + "&state=" + state;
    }

    /**
     * 用授权码换取飞书 user_access_token，再获取用户信息
     *
     * 实现步骤：
     *   1. 用 code 换 user_access_token
     *   2. 用 token 调 user_info 接口
     *   3. 返回 feishu_user_id 和 name
     *
     * @param code 飞书回调的授权码
     * @return Map 含 feishu_user_id / name，失败返回 null
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getUserInfoByCode(String code) throws Exception {
        log.info("[飞书OAuth] 开始用 code 换 user_access_token，code 长度={}", code.length());
        // 步骤1：换 user_access_token
        Map<String, Object> body = new HashMap<>();
        body.put("grant_type", "authorization_code");
        body.put("code", code);

        String tokenResp = postJson(TOKEN_URL, body);
        log.info("[飞书OAuth] 换 token 返回：{}", tokenResp);
        Map<String, Object> tokenResult = objectMapper.readValue(tokenResp, Map.class);
        Number code0 = (Number) tokenResult.getOrDefault("code", -1);
        if (code0.intValue() != 0) {
            log.error("[飞书OAuth] 换 token 失败：code={}, msg={}", code0, tokenResult.get("msg"));
            return null;
        }

        Map<String, Object> tokenData = (Map<String, Object>) tokenResult.get("data");
        String userAccessToken = (String) tokenData.get("access_token");

        // 步骤2：调 user_info 接口
        Request request = new Request.Builder()
                .url(USER_INFO_URL)
                .addHeader("Authorization", "Bearer " + userAccessToken)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            Map<String, Object> result = objectMapper.readValue(respBody, Map.class);
            Number code1 = (Number) result.getOrDefault("code", -1);
            if (code1.intValue() != 0) {
                log.error("[飞书OAuth] 获取用户信息失败：{}", result.get("msg"));
                return null;
            }

            // 步骤3：返回 feishu_user_id 和 name
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            Map<String, String> userInfo = new HashMap<>();
            userInfo.put("feishu_user_id", String.valueOf(data.get("user_id")));
            userInfo.put("name", String.valueOf(data.getOrDefault("name", "")));
            log.info("[飞书OAuth] 成功获取用户信息：feishu_user_id={}, name={}",
                    userInfo.get("feishu_user_id"), userInfo.get("name"));
            return userInfo;
        }
    }

    /**
     * POST JSON 请求
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
     * 检查 OAuth 是否已配置
     */
    public boolean isConfigured() {
        return clientId != null && !clientId.isEmpty()
                && clientSecret != null && !clientSecret.isEmpty();
    }
}
