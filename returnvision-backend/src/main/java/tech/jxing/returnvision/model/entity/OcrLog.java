package tech.jxing.returnvision.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 【数据模型】OCR识别日志表
 * =============================================================
 * 业务职责：记录每次OCR引擎调用的耗时、成功率、置信度，用于监控识别质量
 * 所属流程：OCR引擎调用时写入，与 return_records 为一对多关系
 * =============================================================
 */
@Data
@TableName("ocr_log")
public class OcrLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("record_id")
    private Long recordId;                 // 关联退货记录ID

    @TableField("engine")
    private String engine;                 // 引擎：zhipu_ocr/aliyun_waybill

    @TableField("duration_ms")
    private Integer durationMs;            // 识别耗时（毫秒）

    @TableField("success")
    private Boolean success;               // 是否成功

    @TableField("confidence")
    private BigDecimal confidence;         // 置信度

    @TableField("error_msg")
    private String errorMsg;               // 错误信息

    @TableField("created_at")
    private LocalDateTime createdAt;       // 创建时间
}
