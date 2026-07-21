package tech.jxing.returnvision.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 【数据传输对象】修改字典项请求
 * <p>
 * 只允许改 label/sortOrder/is_leaf，不改 item_code（保护历史数据反查）
 * 全部字段可空，按需更新
 * </p>
 *
 * 注意：前端传下划线命名，用 @JsonProperty 映射到驼峰字段名
 */
@Data
public class UpdateDictItemRequest {

    @JsonProperty("item_label")
    private String itemLabel;

    @JsonProperty("sort_order")
    private Integer sortOrder;

    @JsonProperty("is_leaf")
    private Boolean isLeaf;
}