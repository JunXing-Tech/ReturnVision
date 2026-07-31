package tech.jxing.returnvision.feishu;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tech.jxing.returnvision.common.exception.BizException;
import tech.jxing.returnvision.common.util.AesCryptoUtil;
import tech.jxing.returnvision.model.entity.FeishuConfig;
import tech.jxing.returnvision.model.mapper.FeishuConfigMapper;

/**
 * 【飞书对接层】飞书多租户配置服务
 *
 * 职责：按 configId 查库并解密 app_secret，提供平台级默认配置兜底
 * 层级：Feishu 层
 * 调用方：FeishuService（writeRecord 按 configId 取凭证）、UploadController（confirmSingle）
 * 关联：docs/14 §3.2 / §3.5 / §3.7.3
 *
 * 设计要点：
 *   1. getByIdDecrypted 查库后用 AesCryptoUtil 按 aes_key_version 解密 app_secret
 *   2. disabled 的配置抛 2005（公司禁用后用户"能登录但录不进/确认不了"）
 *   3. getDefaultConfig 从 .env 构造 FeishuConfig，用于平台级记录（feishu_config_id=NULL）兜底
 *   4. 不缓存解密后的明文 app_secret（每次查库解密，避免明文长期驻留内存）
 */
@Service
@Slf4j
public class FeishuConfigService {

    private final FeishuConfigMapper feishuConfigMapper;
    private final AesCryptoUtil aesCryptoUtil;

    /** .env 默认配置（平台级兜底，对应 feishu_config_id=NULL 的记录） */
    private final String defaultAppId;
    private final String defaultAppSecret;
    private final String defaultAppToken;
    private final String defaultTableId;
    private final String defaultBotWebhook;

    public FeishuConfigService(FeishuConfigMapper feishuConfigMapper,
                               AesCryptoUtil aesCryptoUtil,
                               @Value("${feishu.app-id:}") String defaultAppId,
                               @Value("${feishu.app-secret:}") String defaultAppSecret,
                               @Value("${feishu.app-token:}") String defaultAppToken,
                               @Value("${feishu.table-id:}") String defaultTableId,
                               @Value("${feishu.bot-webhook:}") String defaultBotWebhook) {
        this.feishuConfigMapper = feishuConfigMapper;
        this.aesCryptoUtil = aesCryptoUtil;
        this.defaultAppId = defaultAppId;
        this.defaultAppSecret = defaultAppSecret;
        this.defaultAppToken = defaultAppToken;
        this.defaultTableId = defaultTableId;
        this.defaultBotWebhook = defaultBotWebhook;
    }

    /**
     * 按 configId 查库并解密 app_secret
     *
     * 实现步骤：
     *   1. 按 id 查 feishu_config，不存在抛 BizException
     *   2. status=disabled 抛 2005（公司禁用）
     *   3. 按 aes_key_version 解密 app_secret
     *   4. 返回解密后的 FeishuConfig（app_secret 为明文，调用方用完即弃）
     *
     * @param configId 飞书配置ID（非空）
     * @return 解密后的 FeishuConfig（app_secret 为明文）
     */
    public FeishuConfig getByIdDecrypted(Long configId) {
        // 步骤1：查库
        FeishuConfig config = feishuConfigMapper.selectById(configId);
        if (config == null) {
            log.warn("[飞书配置] 配置不存在，configId={}", configId);
            throw new BizException(2005, "飞书配置不存在或已禁用");
        }

        // 步骤2：disabled 检查（docs/14 §3.7.3：关键路径查 status，不主动踢人）
        if ("disabled".equalsIgnoreCase(config.getStatus())) {
            log.warn("[飞书配置] 配置已禁用，configId={}, orgName={}", configId, config.getOrgName());
            throw new BizException(2005, "飞书配置已禁用");
        }

        // 步骤3：按版本解密 app_secret
        Integer version = config.getAesKeyVersion() != null ? config.getAesKeyVersion() : 1;
        String plainSecret = aesCryptoUtil.decrypt(config.getAppSecret(), version);
        config.setAppSecret(plainSecret);

        // 步骤4：返回
        log.debug("[飞书配置] 配置加载成功，configId={}, orgName={}", configId, config.getOrgName());
        return config;
    }

    /**
     * 获取平台级默认配置（.env 兜底）
     *
     * 实现步骤：
     *   1. 从 .env 注入的字段构造 FeishuConfig
     *   2. id 设为 null（标识平台级，token 缓存用 null key 或单独处理）
     *   3. 用于 feishu_config_id=NULL 的记录（历史记录 + 平台 ADMIN）
     *
     * @return 默认 FeishuConfig（app_secret 为 .env 明文），若 .env 未配置返回 null
     */
    public FeishuConfig getDefaultConfig() {
        if (defaultAppId == null || defaultAppId.isEmpty()
                || defaultAppSecret == null || defaultAppSecret.isEmpty()) {
            log.warn("[飞书配置] .env 默认配置未配置，平台级兜底不可用");
            return null;
        }
        FeishuConfig config = new FeishuConfig();
        config.setId(null);  // null 标识平台级（token 缓存用固定 key）
        config.setOrgName("主公司（默认）");
        config.setAppId(defaultAppId);
        config.setAppSecret(defaultAppSecret);
        config.setAppToken(defaultAppToken);
        config.setTableId(defaultTableId);
        config.setBotWebhook(defaultBotWebhook);
        config.setStatus("active");
        return config;
    }

    /**
     * 按 configId 解析有效配置（null 走默认，非空查库）
     *
     * 实现步骤：
     *   1. configId 为 null -> 返回 getDefaultConfig()（.env 兜底）
     *   2. configId 非空 -> 返回 getByIdDecrypted(configId)
     *
     * @param configId 飞书配置ID（null=平台级）
     * @return FeishuConfig（可能为 null，当 .env 也未配置时）
     */
    public FeishuConfig resolveConfig(Long configId) {
        if (configId == null) {
            return getDefaultConfig();
        }
        return getByIdDecrypted(configId);
    }
}
