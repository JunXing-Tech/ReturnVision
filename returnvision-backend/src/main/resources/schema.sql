-- 退货记录表（核心表）
CREATE TABLE return_records (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    waybill_no      VARCHAR(30),                    -- 运单号
    rec_name        VARCHAR(50),                    -- 收件人姓名
    rec_phone       VARCHAR(20),                    -- 收件人电话
    rec_address     TEXT,                           -- 收件人地址
    sender_name     VARCHAR(50),                    -- 寄件人姓名
    sender_phone    VARCHAR(20),                    -- 寄件人电话
    express_company VARCHAR(30),                    -- 快递公司
    return_date     DATE,                           -- 退货日期
    status          VARCHAR(20) DEFAULT 'pending',  -- 状态：pending/confirmed/synced/failed
    ocr_engine      VARCHAR(20),                    -- 识别引擎：zhipu/aliyun/cross_validated/manual
    ocr_confidence  DECIMAL(3,2),                   -- OCR置信度（0.00-1.00）
    image_url       VARCHAR(500),                   -- 腾讯云COS图片URL
    return_reason   VARCHAR(100),                   -- 退货原因（DeepSeek分析）
    return_category VARCHAR(50),                    -- 退货分类：质量问题/物流问题/尺寸不符/...
    llm_confidence  DECIMAL(3,2),                   -- LLM分析置信度（0.00-1.00）
    feishu_record_id VARCHAR(50),                   -- 飞书记录ID（写入后回填）
    remark          TEXT,                           -- 备注
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,  -- 识别时间
    confirmed_at    DATETIME,                       -- 确认时间
    synced_at       DATETIME                        -- 同步飞书时间
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_status ON return_records(status);       -- 按状态筛选（待确认列表）
CREATE INDEX idx_date ON return_records(return_date);    -- 按日期查询

-- OCR识别日志表（识别监控）
CREATE TABLE ocr_log (
    id            INT PRIMARY KEY AUTO_INCREMENT,
    record_id     INT,                            -- 关联退货记录ID
    engine        VARCHAR(20) NOT NULL,           -- 引擎：zhipu_ocr/aliyun_waybill
    duration_ms   INT,                            -- 识别耗时（毫秒）
    success       TINYINT(1) DEFAULT 1,           -- 是否成功
    confidence    DECIMAL(3,2),                   -- 置信度
    error_msg     TEXT,                           -- 错误信息
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
