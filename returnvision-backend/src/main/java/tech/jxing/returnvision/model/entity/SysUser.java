package tech.jxing.returnvision.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【数据模型】用户表
 * =============================================================
 * 业务职责：管理登录账号、密码哈希、飞书绑定、状态
 * 所属流程：F01 鉴权（登录/登出/刷新token/用户管理）
 * 关联：sys_role（多对多）、sys_refresh_token（一对多）
 * =============================================================
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("username")
    private String username;                  // 登录用户名

    @TableField("password_hash")
    private String passwordHash;              // BCrypt 哈希（飞书 OAuth 用户可为空）

    @TableField("display_name")
    private String displayName;               // 显示名称

    @TableField("feishu_user_id")
    private String feishuUserId;              // 飞书 user_id（OAuth 绑定用，可为空）

    @TableField("status")
    private String status;                    // active/disabled

    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;        // 最后登录时间

    @TableField("created_at")
    private LocalDateTime createdAt;          // 创建时间

    @TableField("updated_at")
    private LocalDateTime updatedAt;          // 修改时间
}
