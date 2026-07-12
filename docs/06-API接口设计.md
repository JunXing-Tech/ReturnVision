> 所属：退货OCR解决方案 | 版本：v2.0 | 日期：2026-07-10

# API 接口设计

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/upload` | POST | 上传图片，返回 OCR 识别结果 |
| `/api/confirm` | POST | 确认识别结果，写入飞书表格 |
| `/api/records` | GET | 查询退货记录列表 |
| `/api/records/batch` | POST | 批量上传多张图片，批量识别 |

**上传识别接口示例**：

```
POST /api/upload
Content-Type: multipart/form-data

# 请求
file: [快递单照片]

# 响应（交叉验证一致）
{
  "code": 0,
  "msg": "success",
  "data": {
    "waybill_no": "SF1234567890",
    "rec_name": "张三",
    "rec_phone": "138****8888",
    "rec_address": "北京市朝阳区...",
    "express_company": "顺丰",
    "cross_validation": "matched",
    "confidence": "high",
    "engines": ["zhipu_ocr", "aliyun_waybill"],
    "llm_analysis": {
      "return_reason": "商品质量-开胶",
      "category": "质量问题",
      "confidence": 0.92,
      "validation": {"has_issue": false, "issues": []}
    }
  }
}

# 响应（交叉验证有差异）
{
  "code": 0,
  "msg": "success",
  "data": {
    "waybill_no": "SF1234567890",
    "rec_name": "张三",
    "cross_validation": "review",
    "diff_fields": ["rec_address"],
    "confidence": "medium",
    "engines": ["zhipu_ocr", "aliyun_waybill"],
    "diff_detail": {
      "rec_address": {
        "zhipu": "北京市朝阳区A路1号",
        "aliyun": "北京市朝阳区A路2号",
        "aliyun_prob": 67
      }
    }
  }
}
```

**确认写入接口示例**：

```
POST /api/confirm
Content-Type: application/json

# 请求
{
  "record_id": 1,
  "waybill_no": "SF1234567890",
  "rec_name": "张三",
  "rec_phone": "138****8888",
  "rec_address": "北京市朝阳区...",
  "express_company": "顺丰",
  "return_reason": "商品质量-开胶",
  "return_category": "质量问题"
}

# 响应
{
  "code": 0,
  "msg": "success",
  "data": {
    "feishu_record_id": "recXXXXXX",
    "status": "synced"
  }
}
```

**记录查询接口示例**：

```
GET /api/records?page=1&page_size=20&status=pending

# 响应
{
  "code": 0,
  "msg": "success",
  "data": {
    "total": 50,
    "list": [
      {
        "id": 1,
        "waybill_no": "SF1234567890",
        "rec_name": "张三",
        "express_company": "顺丰",
        "return_reason": "商品质量-开胶",
        "return_category": "质量问题",
        "status": "pending",
        "created_at": "2026-07-10T14:30:00"
      }
    ]
  }
}
```

---
