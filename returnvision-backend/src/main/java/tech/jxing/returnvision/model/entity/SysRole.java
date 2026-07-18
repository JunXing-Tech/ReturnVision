package tech.jxing.returnvision.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【数据模型】角色表
 * =============================================================
 * 业务职责：定义 RBAC 三角色 STAFF/SUPERVISOR/ADMIN
 * 所属流程：F01 鉴权，权限校验时用 role_code
 * =============================================================
 */
@Data
@TableName("sys_role")
public class SysRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("role_code")
    private String roleCode;                  // STAFF/SUPERVISOR/ADMIN

    @TableField("role_name")
    private String roleName;                  // 客服/主管/管理员

    @TableField("description")
    private String description;               // 描述

    @TableField("created_at")
    private LocalDateTime createdAt;          // 创建时间
}
