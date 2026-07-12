package tech.jxing.returnvision.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 【数据模型】退货记录表
 * =============================================================
 * 业务职责：管理OCR识别结果、确认状态、飞书同步状态
 * 所属流程：OCR识别 -> [本模块] -> 确认 -> 飞书写入
 * =============================================================
 */
@Data
@TableName("return_records")
public class ReturnRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("waybill_no")
    private String waybillNo;              // 运单号

    @TableField("rec_name")
    private String recName;                // 收件人姓名

    @TableField("rec_phone")
    private String recPhone;               // 收件人电话

    @TableField("rec_address")
    private String recAddress;             // 收件人地址

    @TableField("sender_name")
    private String senderName;             // 寄件人姓名

    @TableField("sender_phone")
    private String senderPhone;            // 寄件人电话

    @TableField("express_company")
    private String expressCompany;         // 快递公司

    @TableField("return_date")
    private LocalDate returnDate;          // 退货日期

    @TableField("status")
    private String status;                 // 状态：pending/confirmed/synced/failed

    @TableField("ocr_engine")
    private String ocrEngine;              // 识别引擎

    @TableField("ocr_confidence")
    private BigDecimal ocrConfidence;      // OCR置信度（0.00-1.00）

    @TableField("image_url")
    private String imageUrl;               // 腾讯云COS图片URL

    @TableField("return_reason")
    private String returnReason;           // 退货原因（DeepSeek分析）

    @TableField("return_category")
    private String returnCategory;         // 退货分类

    @TableField("llm_confidence")
    private BigDecimal llmConfidence;      // LLM分析置信度（0.00-1.00）

    @TableField("feishu_record_id")
    private String feishuRecordId;         // 飞书记录ID（写入后回填）

    @TableField("remark")
    private String remark;                 // 备注

    @TableField("created_at")
    private LocalDateTime createdAt;       // 识别时间

    @TableField("confirmed_at")
    private LocalDateTime confirmedAt;     // 确认时间

    @TableField("synced_at")
    private LocalDateTime syncedAt;        // 同步飞书时间
}
