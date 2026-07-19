package tech.jxing.returnvision.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【数据模型】操作审计日志表
 * =============================================================
 * 业务职责：记录所有敏感操作（谁在什么时候做了什么）
 * 所属流程：F03 操作审计
 * 设计要点：只增不删（合规要求）+ 冗余 username 避免 join + 索引优化查询
 * =============================================================
 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;                     // 操作者user_id（NULL表示未登录操作）

    @TableField("username")
    private String username;                 // 操作者用户名（冗余，避免 join）

    @TableField("action")
    private String action;                   // 操作类型：LOGIN/CONFIRM/DELETE_RECORD等

    @TableField("target_type")
    private String targetType;               // 操作对象类型：return_record/user/auth等

    @TableField("target_id")
    private String targetId;                 // 操作对象ID

    @TableField("description")
    private String description;              // 操作描述

    @TableField("success")
    private Boolean success;                 // 是否成功（失败操作也要审计）

    @TableField("ip")
    private String ip;                       // 操作者IP

    @TableField("user_agent")
    private String userAgent;                // User-Agent

    @TableField("request_data")
    private String requestData;              // 请求参数（JSON，脱敏后）

    @TableField("created_at")
    private LocalDateTime createdAt;         // 操作时间
}
