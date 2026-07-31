package tech.jxing.returnvision.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【数据模型】飞书多租户配置表
 * =============================================================
 * 业务职责：隔离每家公司的飞书应用配置，app_secret AES/GCM 加密存储
 * 所属流程：多租户改造（docs/14）
 * 关联：sys_user（feishu_config_id）、return_records（feishu_config_id）、register_code（feishu_config_id）
 * =============================================================
 */
@Data
@TableName("feishu_config")
public class FeishuConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("org_name")
    private String orgName;                 // 公司/组织名称

    @TableField("app_id")
    private String appId;                   // 飞书应用 ID

    /** AES/GCM 加密存储，格式 iv:ciphertext:tag；接口返回时固定为 "******" 占位，不回显明文 */
    @TableField("app_secret")
    private String appSecret;               // 飞书应用密钥（AES/GCM 加密）

    /** 加密所用 AES 密钥版本（配合环境变量 AES_SECRET_KEY_V1/V2/... 实现平滑轮换，见 docs/14 §3.7.2） */
    @TableField("aes_key_version")
    private Integer aesKeyVersion;          // AES 密钥版本

    @TableField("app_token")
    private String appToken;                // 多维表格 app_token（系统自动创建）

    @TableField("table_id")
    private String tableId;                 // 多维表格 table_id（系统自动创建）

    @TableField("bot_webhook")
    private String botWebhook;              // 飞书机器人 webhook（可选）

    @TableField("status")
    private String status;                  // active/disabled

    @TableField("created_at")
    private LocalDateTime createdAt;        // 创建时间

    @TableField("updated_at")
    private LocalDateTime updatedAt;        // 修改时间
}
