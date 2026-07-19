package tech.jxing.returnvision.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 【数据模型】创建用户请求 DTO
 * =============================================================
 * 业务职责：管理员创建用户时的请求参数封装
 * 所属流程：F01.1 用户管理 CRUD
 *
 * 注意：前端传下划线命名（display_name/role_codes/feishu_user_id），
 *       用 @JsonProperty 映射到驼峰字段名
 * =============================================================
 */
@Data
public class CreateUserRequest {

    /** 登录用户名（必填，唯一） */
    private String username;

    /** 明文密码（必填，后端 BCrypt 哈希后存库） */
    private String password;

    /** 显示名称（选填） */
    @JsonProperty("display_name")
    private String displayName;

    /** 角色 code 列表（必填，如 ["STAFF"]） */
    @JsonProperty("role_codes")
    private List<String> roleCodes;

    /** 飞书 user_id（选填，绑定后可用 OAuth 登录） */
    @JsonProperty("feishu_user_id")
    private String feishuUserId;
}

