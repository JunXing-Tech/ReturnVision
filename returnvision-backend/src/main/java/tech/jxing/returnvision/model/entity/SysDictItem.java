package tech.jxing.returnvision.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【数据模型】字典项表
 * =============================================================
 * 业务职责：维护字典具体条目，支持两级（parent_id 链）
 * 所属流程：F08 退货分类标准字典管理
 * 关联：docs/05 第 4.5.11 节
 *
 * 设计要点：
 *   1. is_leaf=1 表示叶子项，LLM 只从叶子项选
 *   2. 一级项 parent_id=NULL，若 is_leaf=1 表示"既是目录也是叶子"（如"其他"无子项）
 *   3. status=disabled 为软删，保护历史 return_records.return_category
 * =============================================================
 */
@Data
@TableName("sys_dict_item")
public class SysDictItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("dict_id")
    private Long dictId;                    // 关联 sys_dict.id

    @TableField("parent_id")
    private Long parentId;                  // 父项ID，一级项为 NULL

    @TableField("item_code")
    private String itemCode;                // 项编码，如 'QUALITY'

    @TableField("item_label")
    private String itemLabel;               // 项名称，如 '质量问题'

    @TableField("is_leaf")
    private Boolean isLeaf;                 // 1=叶子（LLM 可选），0=仅目录

    @TableField("sort_order")
    private Integer sortOrder;              // 排序

    @TableField("status")
    private String status;                  // active/disabled

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}