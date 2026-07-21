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
-- F03 操作审计：记录创建者/修改者 user_id（客服记录范围细化用）
ALTER TABLE return_records ADD COLUMN created_by INT COMMENT '创建者user_id';
ALTER TABLE return_records ADD COLUMN updated_by INT COMMENT '最后修改者user_id';
CREATE INDEX IF NOT EXISTS idx_return_records_created_by ON return_records(created_by);

-- OCR识别日志表（识别监控）
CREATE TABLE IF NOT EXISTS ocr_log (
    id            INT PRIMARY KEY AUTO_INCREMENT,
    record_id     INT,                            -- 关联退货记录ID
    engine        VARCHAR(20) NOT NULL,           -- 引擎：zhipu_ocr/aliyun_waybill
    duration_ms   INT,                            -- 识别耗时（毫秒）
    success       TINYINT(1) DEFAULT 1,           -- 是否成功
    confidence    DECIMAL(3,2),                   -- 表级置信度（0.00-1.00）
    field_confidence JSON,                        -- F05 字段级置信度：{"waybill_no":0.95,...}
    error_msg     TEXT,                           -- 错误信息
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- F05 增量字段（已存在的 ocr_log 表添加新列，continue-on-error 忽略重复列错误）
ALTER TABLE ocr_log ADD COLUMN field_confidence JSON COMMENT 'F05 字段级置信度';

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

-- ============================================================
-- F03 操作审计日志表（v2.1 新增）
-- 详见 docs/05 第 4.5.9 节
-- ============================================================
CREATE TABLE IF NOT EXISTS operation_log (
    id            INT PRIMARY KEY AUTO_INCREMENT,
    user_id       INT,                              -- 操作者user_id（NULL表示未登录操作，如登录失败）
    username      VARCHAR(50),                      -- 操作者用户名（冗余，避免 join）
    action        VARCHAR(50) NOT NULL,             -- 操作类型：CREATE/UPDATE/DELETE/LOGIN/LOGOUT/CONFIRM等
    target_type   VARCHAR(50),                      -- 操作对象类型：return_record/user/auth等
    target_id     VARCHAR(50),                      -- 操作对象ID
    description   VARCHAR(500),                     -- 操作描述
    success       TINYINT(1) DEFAULT 1,             -- 是否成功（0=失败，1=成功，失败操作也要审计）
    ip            VARCHAR(50),                      -- 操作者IP
    user_agent    VARCHAR(200),                     -- User-Agent
    request_data  TEXT,                             -- 请求参数（JSON，脱敏后）
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_operation_log_user ON operation_log(user_id);
CREATE INDEX idx_operation_log_action ON operation_log(action);
CREATE INDEX idx_operation_log_created ON operation_log(created_at);

-- ============================================================
-- F08 退货分类标准字典表（v2.2 新增）
-- 详见 docs/05 第 4.5.11 节
-- 设计要点：
--   1. 两级字典：sys_dict（字典目录） + sys_dict_item（字典项）
--   2. sys_dict_item.is_leaf 标识叶子项（LLM 只从叶子项选）
--   3. status=disabled 为软删（停用），不物理删除，保护历史 return_records.return_category
--   4. uk_dict_item 保证同层级内 item_code 唯一
-- ============================================================

-- 字典主表（分类目录）
CREATE TABLE IF NOT EXISTS sys_dict (
    id           INT PRIMARY KEY AUTO_INCREMENT,
    dict_code    VARCHAR(30) NOT NULL UNIQUE,               -- 字典编码，如 'return_category'
    dict_name    VARCHAR(50) NOT NULL,                      -- 字典名称，如 '退货分类'
    status       VARCHAR(20) DEFAULT 'active',              -- active/disabled
    remark       VARCHAR(200),                              -- 备注
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 字典项表（具体条目，支持两级）
CREATE TABLE IF NOT EXISTS sys_dict_item (
    id           INT PRIMARY KEY AUTO_INCREMENT,
    dict_id      INT NOT NULL,                              -- 关联 sys_dict.id
    parent_id    INT DEFAULT NULL,                          -- 父项ID，一级项为 NULL
    item_code    VARCHAR(50) NOT NULL,                      -- 项编码，如 'QUALITY'
    item_label   VARCHAR(50) NOT NULL,                      -- 项名称，如 '质量问题'
    is_leaf      TINYINT(1) DEFAULT 1,                      -- 1=叶子（LLM 可选），0=仅目录
    sort_order   INT DEFAULT 0,                             -- 排序
    status       VARCHAR(20) DEFAULT 'active',              -- active/disabled
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (dict_id) REFERENCES sys_dict(id) ON DELETE CASCADE,
    UNIQUE KEY uk_dict_item (dict_id, parent_id, item_code) -- 同层级内 code 唯一
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_sys_dict_item_dict ON sys_dict_item(dict_id);
CREATE INDEX idx_sys_dict_item_parent ON sys_dict_item(parent_id);

-- 预置 return_category 字典（两级，5 个一级项）
INSERT INTO sys_dict (dict_code, dict_name, remark) VALUES
    ('return_category', '退货分类', 'F08 退货分类标准字典，LLM 分析时从此字典选分类')
ON DUPLICATE KEY UPDATE dict_name = VALUES(dict_name), remark = VALUES(remark);

-- 一级项（is_leaf=1 表示既是目录也是叶子，LLM 可直接选）
-- item_code 用大写英文，LLM 返回 code 后端反查 label
INSERT INTO sys_dict_item (dict_id, parent_id, item_code, item_label, is_leaf, sort_order) VALUES
    ((SELECT id FROM (SELECT id FROM sys_dict WHERE dict_code='return_category') t), NULL, 'QUALITY',   '质量问题', 1, 1),
    ((SELECT id FROM (SELECT id FROM sys_dict WHERE dict_code='return_category') t), NULL, 'LOGISTICS','物流问题', 1, 2),
    ((SELECT id FROM (SELECT id FROM sys_dict WHERE dict_code='return_category') t), NULL, 'SIZE',     '尺寸不符', 1, 3),
    ((SELECT id FROM (SELECT id FROM sys_dict WHERE dict_code='return_category') t), NULL, 'PRICE',    '价格问题', 1, 4),
    ((SELECT id FROM (SELECT id FROM sys_dict WHERE dict_code='return_category') t), NULL, 'OTHER',    '其他',     1, 99)
ON DUPLICATE KEY UPDATE item_label = VALUES(item_label), is_leaf = VALUES(is_leaf), sort_order = VALUES(sort_order);
