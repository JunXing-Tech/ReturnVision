package tech.jxing.returnvision.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jxing.returnvision.common.exception.BizException;
import tech.jxing.returnvision.common.util.AesCryptoUtil;
import tech.jxing.returnvision.controller.dto.AdminRegisterRequest;
import tech.jxing.returnvision.controller.dto.StaffRegisterRequest;
import tech.jxing.returnvision.feishu.FeishuService;
import tech.jxing.returnvision.model.entity.FeishuConfig;
import tech.jxing.returnvision.model.entity.RegisterCode;
import tech.jxing.returnvision.model.entity.SysRole;
import tech.jxing.returnvision.model.entity.SysUser;
import tech.jxing.returnvision.model.entity.SysUserRole;
import tech.jxing.returnvision.model.mapper.FeishuConfigMapper;
import tech.jxing.returnvision.model.mapper.RegisterCodeMapper;
import tech.jxing.returnvision.model.mapper.SysRoleMapper;
import tech.jxing.returnvision.model.mapper.SysUserMapper;
import tech.jxing.returnvision.model.mapper.SysUserRoleMapper;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 【业务逻辑层】注册服务
 *
 * 职责：公司管理员注册（飞书验证+建表）+ 普通用户注册（注册码）
 * 层级：Service 层
 * 调用方：AuthController（/api/auth/register/admin、/api/auth/register/staff）
 * 关联：docs/14 §3.6.1 / §3.6.2、docs/06 §8.2 / §8.3
 *
 * 管理员注册流程（docs/14 §3.6.1）：
 *   1. 用户名/密码强度校验
 *   2. 用 app_id/app_secret 调飞书换 token（验证凭证，失败抛 2006）
 *   3. 用 token 创建多维表格 + 14 个标准字段（失败抛 2007）
 *   4. AES 加密 app_secret，创建 feishu_config
 *   5. 创建 sys_user(ADMIN, feishu_config_id) + 角色关联
 *
 * 普通用户注册流程（docs/14 §3.6.2）：
 *   1. 用户名/密码强度校验
 *   2. 注册码校验（未过期/未用尽/未失效，失败抛 2008）
 *   3. 创建 sys_user(STAFF, feishu_config_id)
 *   4. 注册码 used_count++
 */
@Service
@Slf4j
public class RegisterService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final FeishuConfigMapper feishuConfigMapper;
    private final RegisterCodeMapper registerCodeMapper;
    private final PasswordEncoder passwordEncoder;
    private final AesCryptoUtil aesCryptoUtil;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    private static final String TOKEN_URL = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
    private static final String BITABLE_APP_URL = "https://open.feishu.cn/open-apis/bitable/v1/apps";
    private static final String BITABLE_TABLE_URL = "https://open.feishu.cn/open-apis/bitable/v1/apps/%s/tables";
    private static final MediaType JSON = MediaType.parse("application/json");

    public RegisterService(SysUserMapper userMapper,
                           SysRoleMapper roleMapper,
                           SysUserRoleMapper userRoleMapper,
                           FeishuConfigMapper feishuConfigMapper,
                           RegisterCodeMapper registerCodeMapper,
                           PasswordEncoder passwordEncoder,
                           AesCryptoUtil aesCryptoUtil,
                           OkHttpClient httpClient,
                           ObjectMapper objectMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.feishuConfigMapper = feishuConfigMapper;
        this.registerCodeMapper = registerCodeMapper;
        this.passwordEncoder = passwordEncoder;
        this.aesCryptoUtil = aesCryptoUtil;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 公司管理员注册
     *
     * @param request 注册请求
     * @return Map 含 user_id 和 org_name
     */
    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> registerAdmin(AdminRegisterRequest request) {
        // 步骤1：用户名重复 + 密码强度校验
        validateUsername(request.getUsername());
        validatePassword(request.getPassword());

        // 步骤2：飞书凭证验证（换 token）
        String token = verifyFeishuCredentials(request.getAppId(), request.getAppSecret());

        // 步骤3：创建多维表格 + 标准字段
        String[] tableInfo = createFeishuBitable(token, request.getOrgName());
        String appToken = tableInfo[0];
        String tableId = tableInfo[1];

        // 步骤4：AES 加密 app_secret，创建 feishu_config
        int keyVersion = aesCryptoUtil.getDefaultKeyVersion();
        String encryptedSecret = aesCryptoUtil.encrypt(request.getAppSecret(), keyVersion);

        FeishuConfig config = new FeishuConfig();
        config.setOrgName(request.getOrgName());
        config.setAppId(request.getAppId());
        config.setAppSecret(encryptedSecret);
        config.setAesKeyVersion(keyVersion);
        config.setAppToken(appToken);
        config.setTableId(tableId);
        config.setStatus("active");
        feishuConfigMapper.insert(config);
        log.info("[注册] 飞书配置创建成功，orgName={}, configId={}", request.getOrgName(), config.getId());

        // 步骤5：创建 sys_user(ADMIN) + 角色关联
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getOrgName() + " 管理员");
        user.setFeishuConfigId(config.getId());
        user.setStatus("active");
        userMapper.insert(user);
        assignRole(user.getId(), "ADMIN");

        log.info("[注册] 管理员注册成功，userId={}, username={}, orgName={}",
                user.getId(), user.getUsername(), request.getOrgName());

        Map<String, Object> result = new HashMap<>();
        result.put("user_id", user.getId());
        result.put("org_name", request.getOrgName());
        return result;
    }

    /**
     * 普通用户注册
     *
     * @param request 注册请求
     * @return Map 含 user_id
     */
    @Transactional
    public Map<String, Object> registerStaff(StaffRegisterRequest request) {
        // 步骤1：用户名重复 + 密码强度校验
        validateUsername(request.getUsername());
        validatePassword(request.getPassword());

        // 步骤2：注册码校验（未过期/未用尽/未失效）
        RegisterCode code = validateRegisterCode(request.getRegisterCode());

        // 步骤3：创建 sys_user(STAFF, feishu_config_id)
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setDisplayName(request.getUsername());
        user.setFeishuConfigId(code.getFeishuConfigId());
        user.setStatus("active");
        userMapper.insert(user);
        assignRole(user.getId(), "STAFF");

        // 步骤4：注册码 used_count++
        code.setUsedCount(code.getUsedCount() + 1);
        registerCodeMapper.updateById(code);

        log.info("[注册] 员工注册成功，userId={}, username={}, configId={}",
                user.getId(), user.getUsername(), code.getFeishuConfigId());

        Map<String, Object> result = new HashMap<>();
        result.put("user_id", user.getId());
        return result;
    }

    // ==================== 内部方法 ====================

    private void validateUsername(String username) {
        Long count = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (count != null && count > 0) {
            throw new BizException(1002, "用户名已存在");
        }
    }

    private void validatePassword(String password) {
        if (password.length() < 8) {
            throw new BizException(1003, "密码至少 8 位");
        }
        boolean hasLetter = password.matches(".*[a-zA-Z].*");
        boolean hasDigit = password.matches(".*\\d.*");
        if (!hasLetter || !hasDigit) {
            throw new BizException(1003, "密码需包含字母和数字");
        }
    }

    private RegisterCode validateRegisterCode(String codeStr) {
        RegisterCode code = registerCodeMapper.selectOne(new LambdaQueryWrapper<RegisterCode>()
                .eq(RegisterCode::getCode, codeStr));
        if (code == null || !"active".equals(code.getStatus())) {
            throw new BizException(2008, "注册码无效或已撤销");
        }
        if (code.getExpiresAt() != null && code.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(2008, "注册码已过期");
        }
        if (code.getUsedCount() != null && code.getMaxUses() != null
                && code.getUsedCount() >= code.getMaxUses()) {
            throw new BizException(2008, "注册码已用尽");
        }
        return code;
    }

    private void assignRole(Long userId, String roleCode) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode));
        if (role != null) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(role.getId());
            userRoleMapper.insert(userRole);
        }
    }

    /**
     * 调飞书 API 验证凭证（换 tenant_access_token）
     *
     * @return tenant_access_token（验证成功）
     * @throws BizException 2006 凭证无效
     */
    @SuppressWarnings("unchecked")
    private String verifyFeishuCredentials(String appId, String appSecret) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("app_id", appId);
            body.put("app_secret", appSecret);

            RequestBody requestBody = RequestBody.create(objectMapper.writeValueAsString(body), JSON);
            Request request = new Request.Builder()
                    .url(TOKEN_URL)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                Map<String, Object> result = objectMapper.readValue(response.body().string(), Map.class);
                double code = ((Number) result.getOrDefault("code", -1)).doubleValue();
                if (code != 0) {
                    log.warn("[注册] 飞书凭证验证失败，appId={}, msg={}", appId, result.get("msg"));
                    throw new BizException(2006, "飞书应用验证失败：" + result.get("msg"));
                }
                log.info("[注册] 飞书凭证验证成功，appId={}", appId);
                return (String) result.get("tenant_access_token");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[注册] 飞书凭证验证异常，appId={}", appId, e);
            throw new BizException(2006, "飞书应用验证异常：" + e.getMessage());
        }
    }

    /**
     * 用 token 创建多维表格 + 标准字段
     *
     * @return [appToken, tableId]
     * @throws BizException 2007 建表失败
     */
    @SuppressWarnings("unchecked")
    private String[] createFeishuBitable(String token, String orgName) {
        try {
            // 步骤1：创建多维表格 app
            Map<String, Object> appBody = new HashMap<>();
            appBody.put("name", orgName + "退货记录");

            RequestBody requestBody = RequestBody.create(objectMapper.writeValueAsString(appBody), JSON);
            Request request = new Request.Builder()
                    .url(BITABLE_APP_URL)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            String appToken;
            try (Response response = httpClient.newCall(request).execute()) {
                Map<String, Object> result = objectMapper.readValue(response.body().string(), Map.class);
                double code = ((Number) result.getOrDefault("code", -1)).doubleValue();
                if (code != 0) {
                    throw new BizException(2007, "创建多维表格失败：" + result.get("msg"));
                }
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                Map<String, Object> app = (Map<String, Object>) data.get("app");
                appToken = (String) app.get("app_token");
            }

            // 步骤2：创建数据表 + 14 个标准字段
            Map<String, Object> tableBody = new HashMap<>();
            tableBody.put("table", Map.of(
                    "name", "退货记录",
                    "default_view_name", "全部",
                    "fields", FeishuService.buildStandardFields()
            ));

            String tableUrl = String.format(BITABLE_TABLE_URL, appToken);
            RequestBody tableRequestBody = RequestBody.create(objectMapper.writeValueAsString(tableBody), JSON);
            Request tableRequest = new Request.Builder()
                    .url(tableUrl)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .post(tableRequestBody)
                    .build();

            String tableId;
            try (Response response = httpClient.newCall(tableRequest).execute()) {
                Map<String, Object> result = objectMapper.readValue(response.body().string(), Map.class);
                double code = ((Number) result.getOrDefault("code", -1)).doubleValue();
                if (code != 0) {
                    throw new BizException(2007, "创建数据表失败：" + result.get("msg"));
                }
                Map<String, Object> data = (Map<String, Object>) result.get("data");
                Map<String, Object> table = (Map<String, Object>) data.get("table");
                tableId = (String) table.get("table_id");
            }

            log.info("[注册] 飞书多维表格创建成功，appToken={}, tableId={}", appToken, tableId);
            return new String[]{appToken, tableId};
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[注册] 飞书建表异常", e);
            throw new BizException(2007, "飞书多维表格创建异常：" + e.getMessage());
        }
    }
}
