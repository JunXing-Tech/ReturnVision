package tech.jxing.returnvision.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【数据模型】用户注册码表
 * =============================================================
 * 业务职责：公司管理员生成注册码，普通员工用码注册绑公司
 * 所属流程：多租户改造（docs/14 §3.6.2 方式 A 批量注册）
 * 关联：feishu_config（feishu_config_id，外键约束）
 * =============================================================
 */
@Data
@TableName("register_code")
public class RegisterCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("code")
    private String code;                    // 注册码（8位字母数字，避免 0/O/1/I）

    @TableField("feishu_config_id")
    private Long feishuConfigId;            // 绑定的飞书配置

    @TableField("max_uses")
    private Integer maxUses;                // 最大使用次数（1=一次性，N=多人共用）

    @TableField("used_count")
    private Integer usedCount;              // 已使用次数

    @TableField("expires_at")
    private LocalDateTime expiresAt;        // 过期时间（NULL=永不过期）

    @TableField("status")
    private String status;                  // active/revoked

    @TableField("created_by")
    private Long createdBy;                 // 创建人（公司 ADMIN）

    @TableField("created_at")
    private LocalDateTime createdAt;        // 创建时间
}
