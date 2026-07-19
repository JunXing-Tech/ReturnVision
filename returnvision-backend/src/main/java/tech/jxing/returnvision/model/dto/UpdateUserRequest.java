package tech.jxing.returnvision.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 【数据模型】编辑用户请求 DTO
 * =============================================================
 * 业务职责：管理员编辑用户时的请求参数封装（所有字段可选，只传需要改的）
 * 所属流程：F01.1 用户管理 CRUD
 *
 * 注意：前端传下划线命名（display_name/role_codes/feishu_user_id），
 *       用 @JsonProperty 映射到驼峰字段名
 * =============================================================
 */
@Data
public class UpdateUserRequest {

    /** 显示名称（选填） */
    @JsonProperty("display_name")
    private String displayName;

    /** 角色 code 列表（选填，传则覆盖原角色） */
    @JsonProperty("role_codes")
    private List<String> roleCodes;

    /** 状态（选填，active/disabled） */
    private String status;

    /** 飞书 user_id（选填，传则覆盖） */
    @JsonProperty("feishu_user_id")
    private String feishuUserId;
}

