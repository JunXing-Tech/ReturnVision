package tech.jxing.returnvision.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【数据模型】refresh token 表
 * =============================================================
 * 业务职责：存储 refresh token 的 SHA-256 哈希，支持主动失效（登出/禁用账号）
 * 所属流程：F01 鉴权，refresh access token 时查库校验
 * 设计要点：不存明文，防 DB 泄露后 token 被盗用
 * =============================================================
 */
@Data
@TableName("sys_refresh_token")
public class SysRefreshToken {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;                      // 关联用户

    @TableField("token_hash")
    private String tokenHash;                 // refresh token 的 SHA-256 哈希

    @TableField("expires_at")
    private LocalDateTime expiresAt;          // 过期时间

    @TableField("created_at")
    private LocalDateTime createdAt;          // 创建时间
}
