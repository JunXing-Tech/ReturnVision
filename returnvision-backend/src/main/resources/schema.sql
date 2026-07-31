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
    wx_openid       VARCHAR(64),                         -- 微信小程序 openid（自助绑定用，可为空）
    status          VARCHAR(20) DEFAULT 'active',        -- active/disabled
    last_login_at   DATETIME,                            -- 最后登录时间
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_sys_user_feishu ON sys_user(feishu_user_id);

-- 小程序扩展：微信 openid 自助绑定（已存在的表用 ALTER 升级，continue-on-error 忽略重复）
ALTER TABLE sys_user ADD COLUMN wx_openid VARCHAR(64) COMMENT '微信小程序 openid（自助绑定用，可为空）';
CREATE UNIQUE INDEX uk_sys_user_wx_openid ON sys_user(wx_openid);

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
    UNIQUE KEY uk_dict_item (dict_id, item_code)            -- 同字典内 code 唯一（不含 parent_id，避免 NULL 陷阱）
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 已部署环境升级：删旧约束（含 parent_id）加新约束（不含 parent_id）
-- continue-on-error 忽略"约束不存在"错误（新环境首次建表无旧约束）
ALTER TABLE sys_dict_item DROP INDEX uk_dict_item;
ALTER TABLE sys_dict_item ADD UNIQUE KEY uk_dict_item (dict_id, item_code);

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

-- ============================================================
-- 多租户改造（v2.3 新增，详见 docs/14 + docs/05 第 4.5.12 节）
-- 设计要点：
--   1. feishu_config 隔离每家公司的飞书应用配置，app_secret AES/GCM 加密存储
--   2. register_code 注册码表，公司管理员生成，普通用户用码注册
--   3. sys_user / return_records 加 feishu_config_id 软关联（NULL=平台级）
--   4. 历史数据迁移：return_records.feishu_config_id 默认设为预置主公司行的 id
--   5. continue-on-error 忽略重复列/重复键错误，兼容新环境建表与已部署环境升级
-- ============================================================

-- 飞书多租户配置表
CREATE TABLE IF NOT EXISTS feishu_config (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    org_name        VARCHAR(100) NOT NULL COMMENT '公司/组织名称',
    app_id          VARCHAR(50)  NOT NULL COMMENT '飞书应用 ID',
    app_secret      VARCHAR(255) NOT NULL COMMENT '飞书应用密钥（AES/GCM 加密，格式 iv:ciphertext:tag）',
    aes_key_version INT DEFAULT 1 COMMENT '加密所用 AES 密钥版本（支持平滑轮换）',
    app_token       VARCHAR(100) NOT NULL COMMENT '多维表格 app_token（系统自动创建）',
    table_id        VARCHAR(50)  NOT NULL COMMENT '多维表格 table_id（系统自动创建）',
    bot_webhook     VARCHAR(255) COMMENT '飞书机器人 webhook（可选）',
    status          VARCHAR(20)  DEFAULT 'active' COMMENT 'active/disabled',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户注册码表
CREATE TABLE IF NOT EXISTS register_code (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    code              VARCHAR(16) NOT NULL UNIQUE COMMENT '注册码（8位字母数字，避免 0/O/1/I）',
    feishu_config_id  BIGINT NOT NULL COMMENT '绑定的飞书配置',
    max_uses          INT DEFAULT 1 COMMENT '最大使用次数（1=一次性，N=多人共用）',
    used_count        INT DEFAULT 0 COMMENT '已使用次数',
    expires_at        DATETIME COMMENT '过期时间（NULL=永不过期）',
    status            VARCHAR(20) DEFAULT 'active' COMMENT 'active/revoked',
    created_by        BIGINT COMMENT '创建人（公司 ADMIN）',
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (feishu_config_id) REFERENCES feishu_config(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- sys_user 加 feishu_config_id（已存在的表用 ALTER 升级，continue-on-error 忽略重复列错误）
-- NULL = 平台级（平台 ADMIN，用 .env 默认配置）；非空 = 绑某公司
ALTER TABLE sys_user ADD COLUMN feishu_config_id BIGINT COMMENT '所属飞书配置（NULL=平台级/未绑公司）';
CREATE INDEX idx_sys_user_feishu_config ON sys_user(feishu_config_id);

-- return_records 加 feishu_config_id（记录创建时写入，confirm 按此查凭证，不是当前用户的）
-- NULL = 平台级记录；非空 = 某公司记录
ALTER TABLE return_records ADD COLUMN feishu_config_id BIGINT COMMENT '所属飞书配置（NULL=平台级，按公司隔离用）';
CREATE INDEX idx_return_records_feishu_config ON return_records(feishu_config_id);

-- 历史数据迁移（改进点 #7）：
-- 历史记录 feishu_config_id 保持 NULL（平台级语义），不迁移到任何主公司行。
-- 理由：主公司行的 app_secret 在 SQL 层无法 AES 加密填充（AES 密钥在应用层），
--   若强行迁移会导致 confirm 查表拿到空串 app_secret -> 降级返回 null -> status=failed，
--   破坏单租户回归（历史记录本应能用 .env 配置正常写飞书）。
-- 正确行为：历史记录 feishu_config_id=NULL -> confirm 走 getDefaultConfig()（.env 默认配置）-> 与现状等价。
-- 平台 ADMIN（feishu_config_id=null）查询时租户插件不加条件，能看到全部历史记录。
-- 主公司若要独立管理，由公司管理员走正常注册流程创建 feishu_config，不在此预置。
-- （无需 UPDATE 语句，ALTER 后历史记录 feishu_config_id 默认即为 NULL）
