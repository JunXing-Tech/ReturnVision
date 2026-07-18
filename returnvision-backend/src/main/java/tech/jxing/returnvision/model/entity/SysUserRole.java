package tech.jxing.returnvision.model.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 【数据模型】用户-角色关联表
 * =============================================================
 * 业务职责：用户与角色的多对多关联（一个用户可多角色，预留扩展）
 * 所属流程：F01 鉴权，登录时查用户角色
 * =============================================================
 */
@Data
@TableName("sys_user_role")
public class SysUserRole {

    @TableField("user_id")
    private Long userId;                      // 用户ID

    @TableField("role_id")
    private Long roleId;                      // 角色ID
}
