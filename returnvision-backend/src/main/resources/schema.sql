-- 退货记录表（核心表）
CREATE TABLE IF NOT EXISTS return_records (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    waybill_no      VARCHAR(30),                    -- 运单号
    rec_name        VARCHAR(50),                    -- 收件人姓名
    rec_phone       VARCHAR(20),                    -- 收件人电话
    rec_address     TEXT,                           -- 收件人地址
    sender_name     VARCHAR(50),                    -- 寄件人姓名
    sender_phone    VARCHAR(20),                    -- 寄件人电话
    sender_address  TEXT,                           -- 寄件人地址
    express_company VARCHAR(30),                    -- 快递公司
    goods           VARCHAR(100),                   -- 托寄物
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

-- 增量字段（已存在表时添加新列，continue-on-error忽略重复列错误）
ALTER TABLE return_records ADD COLUMN sender_address TEXT COMMENT '寄件人地址';
ALTER TABLE return_records ADD COLUMN goods VARCHAR(100) COMMENT '托寄物';

-- OCR识别日志表（识别监控）
CREATE TABLE IF NOT EXISTS ocr_log (
    id            INT PRIMARY KEY AUTO_INCREMENT,
    record_id     INT,                            -- 关联退货记录ID
    engine        VARCHAR(20) NOT NULL,           -- 引擎：zhipu_ocr/aliyun_waybill
    duration_ms   INT,                            -- 识别耗时（毫秒）
    success       TINYINT(1) DEFAULT 1,           -- 是否成功
    confidence    DECIMAL(3,2),                   -- 置信度
    error_msg     TEXT,                           -- 错误信息
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- F01 鉴权表（v2.1 新增）
-- 详见 docs/05 第 4.5.8 节
-- ============================================================

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id              INT PRIMARY KEY AUTO_INCREMENT,
    username        VARCHAR(50) NOT NULL UNIQUE,         -- 登录用户名
    password_hash   VARCHAR(100),                        -- BCrypt 哈希（飞书 OAuth 用户可为空）
    display_name    VARCHAR(50),                         -- 显示名称
    feishu_user_id  VARCHAR(50),                         -- 飞书 user_id（OAuth 绑定用，可为空）
    status          VARCHAR(20) DEFAULT 'active',        -- active/disabled
    last_login_at   DATETIME,                            -- 最后登录时间
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_sys_user_feishu ON sys_user(feishu_user_id);

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id           INT PRIMARY KEY AUTO_INCREMENT,
    role_code    VARCHAR(30) NOT NULL UNIQUE,            -- STAFF/SUPERVISOR/ADMIN
    role_name    VARCHAR(30) NOT NULL,                   -- 客服/主管/管理员
    description  VARCHAR(100),
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id  INT NOT NULL,
    role_id  INT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- refresh token 表（支持主动失效）
CREATE TABLE IF NOT EXISTS sys_refresh_token (
    id            INT PRIMARY KEY AUTO_INCREMENT,
    user_id       INT NOT NULL,                          -- 关联用户
    token_hash    VARCHAR(100) NOT NULL,                 -- refresh token 的 SHA-256 哈希（不存明文）
    expires_at    DATETIME NOT NULL,                     -- 过期时间
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_refresh_token_user ON sys_refresh_token(user_id);
CREATE INDEX idx_refresh_token_hash ON sys_refresh_token(token_hash);

-- 预置三角色数据（admin 账号由 ApplicationRunner 初始化，避免 BCrypt 哈希硬编码）
INSERT INTO sys_role (role_code, role_name, description) VALUES
    ('STAFF', '客服', '录入退货记录，查看自己处理的记录')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

INSERT INTO sys_role (role_code, role_name, description) VALUES
    ('SUPERVISOR', '主管', '审核记录+查看统计+导出数据')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

INSERT INTO sys_role (role_code, role_name, description) VALUES
    ('ADMIN', '管理员', '用户管理+全权限')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);
