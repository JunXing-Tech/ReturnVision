package tech.jxing.returnvision.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 【数据模型】字典主表
 * =============================================================
 * 业务职责：维护字典目录（如 return_category 退货分类）
 * 所属流程：F08 退货分类标准字典管理
 * 关联：docs/05 第 4.5.11 节
 * =============================================================
 */
@Data
@TableName("sys_dict")
public class SysDict {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("dict_code")
    private String dictCode;                // 字典编码，如 'return_category'

    @TableField("dict_name")
    private String dictName;                // 字典名称，如 '退货分类'

    @TableField("status")
    private String status;                  // active/disabled

    @TableField("remark")
    private String remark;                  // 备注

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}