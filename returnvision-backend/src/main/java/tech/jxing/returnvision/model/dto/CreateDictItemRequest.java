package tech.jxing.returnvision.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 【数据传输对象】创建字典项请求
 * <p>
 * 校验规则（在 DictService 手动校验，与现有 CreateUserRequest 风格一致）：
 *   - dictId 必填
 *   - itemCode 必填，长度 ≤ 50
 *   - itemLabel 必填，长度 ≤ 50
 *   - parentId 可空（一级项）
 *   - isLeaf 可空，默认 true
 *   - sortOrder 可空，默认 0
 * </p>
 *
 * 注意：前端传下划线命名，用 @JsonProperty 映射到驼峰字段名
 */
@Data
public class CreateDictItemRequest {

    @JsonProperty("dict_id")
    private Long dictId;

    @JsonProperty("parent_id")
    private Long parentId;

    @JsonProperty("item_code")
    private String itemCode;

    @JsonProperty("item_label")
    private String itemLabel;

    @JsonProperty("is_leaf")
    private Boolean isLeaf;

    @JsonProperty("sort_order")
    private Integer sortOrder;
}