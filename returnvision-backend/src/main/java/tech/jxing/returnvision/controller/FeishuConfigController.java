package tech.jxing.returnvision.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tech.jxing.returnvision.audit.AuditLog;
import tech.jxing.returnvision.common.ResponseResult;
import tech.jxing.returnvision.common.exception.BizException;
import tech.jxing.returnvision.common.util.AesCryptoUtil;
import tech.jxing.returnvision.feishu.FeishuService;
import tech.jxing.returnvision.model.entity.FeishuConfig;
import tech.jxing.returnvision.model.mapper.FeishuConfigMapper;
import tech.jxing.returnvision.security.TenantContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 【接口层】飞书多租户配置控制器（管理员后台）
 *
 * 职责：提供飞书配置查询/修改/禁用 3 个接口
 * 层级：Controller 层
 * 关联：docs/06 §8.4.1
 *
 * 权限：所有接口 @PreAuthorize("hasRole('ADMIN')")，仅管理员可访问
 *       修改/禁用接口仅平台 ADMIN 可操作（feishuConfigId==null）
 *
 * 数据范围：
 *   - 平台 ADMIN（feishuConfigId=null）：看所有公司配置
 *   - 公司 ADMIN（feishuConfigId=非空）：只看自己公司配置（feishu_config 表不隔离，手动按 id 过滤）
 *
 * 接口列表：
 *   GET    /api/admin/feishu-config        - 飞书配置列表（公司ADMIN只看自己，平台ADMIN看全部）
 *   PUT    /api/admin/feishu-config/{id}   - 修改飞书配置（仅平台ADMIN，更新后清token缓存）
 *   DELETE /api/admin/feishu-config/{id}   - 禁用飞书配置（仅平台ADMIN，status=disabled）
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/feishu-config")
@PreAuthorize("hasRole('ADMIN')")
public class FeishuConfigController {

    private static final String APP_SECRET_MASK = "******";
    private static final String STATUS_DISABLED = "disabled";

    private final FeishuConfigMapper feishuConfigMapper;
    private final FeishuService feishuService;
    private final AesCryptoUtil aesCryptoUtil;

    public FeishuConfigController(FeishuConfigMapper feishuConfigMapper,
                                  FeishuService feishuService,
                                  AesCryptoUtil aesCryptoUtil) {
        this.feishuConfigMapper = feishuConfigMapper;
        this.feishuService = feishuService;
        this.aesCryptoUtil = aesCryptoUtil;
    }

    /**
     * 飞书配置列表
     *
     * 业务流程：
     *   1. 取当前用户 feishuConfigId
     *   2. 平台ADMIN（null）查全部；公司ADMIN 只查自己公司配置（按 id 过滤）
     *   3. app_secret 脱敏为 "******"，不回显明文
     *   4. 返回列表
     */
    @GetMapping
    public ResponseResult<List<Map<String, Object>>> listConfigs() {
        // 步骤1：取当前用户 feishuConfigId
        Long currentConfigId = TenantContext.currentFeishuConfigId();

        // 步骤2：按数据范围查询（公司ADMIN 按 id 过滤，平台ADMIN 无条件等价 selectList(null)）
        LambdaQueryWrapper<FeishuConfig> wrapper = new LambdaQueryWrapper<>();
        if (currentConfigId != null) {
            wrapper.eq(FeishuConfig::getId, currentConfigId);
        }
        List<FeishuConfig> configs = feishuConfigMapper.selectList(wrapper);

        // 步骤3-4：脱敏并返回
        List<Map<String, Object>> list = new ArrayList<>();
        for (FeishuConfig config : configs) {
            list.add(buildConfigMap(config));
        }
        return ResponseResult.success(list);
    }

    /**
     * 修改飞书配置
     *
     * 业务流程：
     *   1. 校验仅平台ADMIN 可操作
     *   2. 查原配置，不存在抛异常
     *   3. 按非空字段更新（orgName/appId/appSecret/botWebhook/status）
     *   4. 若传新 app_secret 明文（非占位），AES 加密后存储并刷新 aesKeyVersion
     *   5. updateById 持久化
     *   6. 清 token 缓存（凭证变更后旧 token 可能失效）
     *   7. 返回成功
     *
     * @param id      飞书配置ID
     * @param request 待更新字段（仅非空字段生效）
     */
    @PutMapping("/{id}")
    @AuditLog(action = "UPDATE_FEISHU_CONFIG", targetType = "feishu_config", description = "修改飞书配置", recordParams = false)
    public ResponseResult<Map<String, Object>> updateConfig(@PathVariable Long id,
                                                             @RequestBody FeishuConfig request) {
        // 步骤1：校验仅平台ADMIN
        if (!TenantContext.isPlatform()) {
            throw new BizException(403, "仅平台管理员可操作");
        }

        // 步骤2：查原配置
        FeishuConfig config = feishuConfigMapper.selectById(id);
        if (config == null) {
            throw new BizException(1007, "飞书配置不存在");
        }

        // 步骤3：按非空字段更新
        if (request.getOrgName() != null) {
            config.setOrgName(request.getOrgName());
        }
        if (request.getAppId() != null) {
            config.setAppId(request.getAppId());
        }
        if (request.getBotWebhook() != null) {
            config.setBotWebhook(request.getBotWebhook());
        }
        if (request.getStatus() != null) {
            config.setStatus(request.getStatus());
        }

        // 步骤4：app_secret 非空且非占位时加密存储（凭证轮换场景）
        if (request.getAppSecret() != null && !request.getAppSecret().isEmpty()
                && !APP_SECRET_MASK.equals(request.getAppSecret())) {
            int keyVersion = aesCryptoUtil.getDefaultKeyVersion();
            config.setAppSecret(aesCryptoUtil.encrypt(request.getAppSecret(), keyVersion));
            config.setAesKeyVersion(keyVersion);
        }

        // 步骤5：持久化
        feishuConfigMapper.updateById(config);

        // 步骤6：清 token 缓存
        feishuService.invalidateTokenCache(id);

        log.info("[飞书配置] 修改成功，id={}", id);

        // 步骤7：返回
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ResponseResult.success(result);
    }

    /**
     * 禁用飞书配置
     *
     * 业务流程：
     *   1. 校验仅平台ADMIN 可操作
     *   2. 查原配置，不存在抛异常
     *   3. status 置 disabled
     *   4. updateById 持久化
     *   5. 清 token 缓存（禁用后旧 token 不应再用）
     *   6. 返回成功
     *
     * @param id 飞书配置ID
     */
    @DeleteMapping("/{id}")
    @AuditLog(action = "DISABLE_FEISHU_CONFIG", targetType = "feishu_config", description = "禁用飞书配置")
    public ResponseResult<Map<String, Object>> disableConfig(@PathVariable Long id) {
        // 步骤1：校验仅平台ADMIN
        if (!TenantContext.isPlatform()) {
            throw new BizException(403, "仅平台管理员可操作");
        }

        // 步骤2：查原配置
        FeishuConfig config = feishuConfigMapper.selectById(id);
        if (config == null) {
            throw new BizException(1007, "飞书配置不存在");
        }

        // 步骤3-4：置 disabled 并持久化
        config.setStatus(STATUS_DISABLED);
        feishuConfigMapper.updateById(config);

        // 步骤5：清 token 缓存
        feishuService.invalidateTokenCache(id);

        log.info("[飞书配置] 禁用成功，id={}", id);

        // 步骤6：返回
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        return ResponseResult.success(result);
    }

    // ==================== 内部方法 ====================

    /**
     * 组装飞书配置 Map（app_secret 脱敏为 "******"，不回显明文）
     */
    private Map<String, Object> buildConfigMap(FeishuConfig config) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", config.getId());
        map.put("org_name", config.getOrgName());
        map.put("app_id", config.getAppId());
        map.put("app_secret", APP_SECRET_MASK);
        map.put("aes_key_version", config.getAesKeyVersion());
        map.put("app_token", config.getAppToken());
        map.put("table_id", config.getTableId());
        map.put("bot_webhook", config.getBotWebhook());
        map.put("status", config.getStatus());
        map.put("created_at", config.getCreatedAt());
        map.put("updated_at", config.getUpdatedAt());
        return map;
    }
}
