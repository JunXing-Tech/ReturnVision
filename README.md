# 退运智录（ReturnVision）

> 拍照识别快递面单 -> 双引擎交叉验证 -> DeepSeek LLM 分析 -> 人工确认 -> 写入飞书多维表格

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.4-brightgreen.svg)](https://vuejs.org/)
[![Vite](https://img.shields.io/badge/Vite-5.2-purple.svg)](https://vitejs.dev/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)
[![CI](https://github.com/JunXing-Tech/ReturnVision/actions/workflows/ci.yml/badge.svg)](https://github.com/JunXing-Tech/ReturnVision/actions)
[![License](https://img.shields.io/badge/license-Internal-lightgrey.svg)](#许可证)

**线上地址**：<https://returnvision.jxing.tech>

---

## 目录

- [项目简介](#项目简介)
- [功能特性](#功能特性)
- [技术栈](#技术栈)
- [系统架构](#系统架构)
- [项目结构](#项目结构)
- [环境要求](#环境要求)
- [快速开始](#快速开始)
- [配置说明](#配置说明)
- [使用说明](#使用说明)
- [API 接口](#api-接口)
- [开发指南](#开发指南)
- [文档导航](#文档导航)
- [版本历史](#版本历史)
- [许可证](#许可证)

---

## 项目简介

**退运智录** 是一套面向电商退货场景的智能 OCR 识别系统。通过拍摄快递面单照片，系统自动完成面单信息识别、退货原因分析与归类，并写入飞书多维表格，降低人工录入成本、提升退货处理效率与数据准确率。

系统采用「双引擎交叉验证 + DeepSeek 语义校验 + 人工确认」五层保障体系，关键字段一致时识别准确率接近 99.9%。

v3.0 起，系统从「单机识别工具」升级为「多角色协作平台」：新增飞书 OAuth 单点登录、三角色权限体系、操作审计、退货报表、退货字典、数据导出与保留期、异常监控告警等企业级能力，前端迁移至源力（Volcengine）设计系统。

---

## 功能特性

### 核心识别能力

- **双引擎 OCR 交叉验证**：智谱 OCR（引擎 A）与阿里云面单 OCR（引擎 B）并行识别，结果交叉比对
- **LLM 智能分析**：DeepSeek V4 Flash 进行语义校验、退货原因提取与智能分类
- **图片持久化**：腾讯云 COS 存储原图，URL 供 OCR 与飞书引用
- **飞书自动写入**：识别结果一键写入飞书多维表格
- **五层保障体系**：交叉验证 -> 置信度判断 -> 格式校验 -> DeepSeek 语义校验 -> 人工确认
- **成本优化**：单次识别成本约 ¥0.01，相比单引擎方案月成本降低约 80%

### 上传与交互

- **SSE 流式上传**：`/api/upload/sse` 实时推送 OCR/LLM 各阶段进度，前端展示流水线状态
- **批量上传**：支持单次最多 20 张面单图片（每张 ≤ 10MB）的批量识别
- **OCR 失败保护**：双引擎均未识别出有效运单信息时不写库，避免记录页出现"失败数据"
- **重复运单拦截**：确认写入前校验运单号是否已存在，避免重复录入
- **记录管理**：支持单条/批量删除、编辑确认、同步状态检索

### 鉴权与权限（F01）

- **飞书 OAuth 单点登录**：员工使用飞书账号一键登录，首次登录自动绑定/建号
- **账号密码登录**：管理员可本地创建账号（BCrypt 加密），支持改密与重置密码
- **JWT 无状态鉴权**：access token（2h）+ refresh token（7d）双令牌，前端自动刷新
- **三角色权限体系**：
  - **客服（STAFF）**：面单识别、退货记录、退货报表、个人中心
  - **主管（SUPERVISOR）**：客服全部权限 + 工作台、审计日志
  - **管理员（ADMIN）**：全部权限 + 用户管理、退货字典
- **菜单动态渲染**：按角色过滤侧边栏导航，接口级路径权限 + 方法级 `@PreAuthorize` 双重校验

### 数据治理（F02 / F03）

- **Excel 导出**：退货记录导出为带水印的 xlsx（Apache POI），单次 ≤ 1000 行、每人每日 ≤ 5 次
- **数据保留期**：定时任务分批清理过期数据（退货记录 90 天 / 审计日志 180 天 / OCR 日志 30 天），待确认 30 天提醒不删除
- **操作审计日志**：AOP 切面自动审计写操作，敏感字段脱敏，主管/管理员可检索查询

### 分析与监控

- **退货报表（F04）**：多维度聚合（分类/快递/时间/原因）+ ECharts 图表，按角色数据范围隔离
- **OCR 准确率仪表盘（F05）**：字段级置信度回溯，定位识别薄弱环节
- **退货字典管理（F08）**：两级标准字典（退货分类 + 退货原因），LLM prompt 动态注入，越界自动降级
- **异常监控告警（F12）**：关键异常（如飞书 API 连续失败）通过飞书机器人推送卡片告警，带去重窗口

### 前端体验

- **源力设计系统 v3.0**：品牌蓝主色 + 中文优先字体，固定浅色主题（运营场景），无暗色模式
- **侧边栏布局**：app-shell 网格（侧边栏 + 主内容区），分组导航 + 激活态品牌蓝指示条
- **登录页**：飞书 OAuth 入口 + 账号密码入口，错误信息回显
- **工作台**：首页展示识别总量、待确认、已同步、失败数等 KPI 与趋势
- **移动端适配**：响应式设计，窄屏隐藏侧边栏

---

## 技术栈

| 层级 | 选型 |
|------|------|
| 后端 | Java 21、Spring Boot 3.3.0、Spring Security、Spring AOP、MyBatis-Plus 3.5.7、Maven |
| 鉴权 | JJWT 0.12.6（JWT）+ 飞书 OAuth 2.0 + BCrypt |
| 导出 | Apache POI 5.2.5（Excel + 水印） |
| 前端 | Vue 3.4、Vite 5.2、Element Plus 2.7、Axios、ECharts 6.1 |
| 数据库 | MySQL 8.0（宿主机系统级安装，不放入 Docker）；H2（测试，MySQL 兼容模式） |
| 图片存储 | 腾讯云 COS（cos_api 5.6.227） |
| OCR 引擎 A | 智谱 OCR（OkHttp 直连） |
| OCR 引擎 B | 阿里云面单 OCR（ocr_api20210707 3.1.3） |
| LLM | DeepSeek V4 Flash |
| 飞书 | lark-oapi 2.3.6 + Webhook 机器人（OAuth SSO + 告警通知） |
| 容器编排 | Docker Compose（backend 容器内网 + frontend 容器对外） |
| Web 服务器 | Nginx（frontend 容器内置，静态托管 + /api 反向代理 + SSE 配置） |
| HTTPS | Let's Encrypt 自动签发与续期（jonasal/nginx-certbot 5.2.3） |
| CI | GitHub Actions（push / PR 自动跑 `mvn test`） |
| 构建工具 | Maven Wrapper、Vite |

---

## 系统架构

```
                      ┌─────────────────────────────────────────────┐
                      │              前端（Vue 3 工作台）              │
                      │  登录(SSO) / 工作台 / 识别 / 记录 / 报表 /     │
                      │  用户管理 / 字典 / 审计 / OCR统计 / 个人中心   │
                      └───────────────┬─────────────────────────────┘
                                      │ JWT Bearer + /api 反代(Nginx)
                                      ▼
          ┌───────────────────────────────────────────────────────────┐
          │                    Spring Security 过滤链                 │
          │  路径权限: /api/auth/** 公开 / /api/admin/** ADMIN /       │
          │           /api/dashboard/** 主管+管理员 / 其余需登录       │
          │  方法权限: @PreAuthorize 角色校验                          │
          └───────────────┬───────────────────────────────────────────┘
                          ▼
  拍照上传 -> COS存储 -> 双引擎OCR并行 -> 交叉验证 -> DeepSeek分析 -> 人工确认 -> 飞书写入
                |              |              |              |              |              |
                |              |              |              |              |              ├─ return_reason
                |              |              |              |              |              ├─ return_category
                |              |              |              |              |              └─ photo_url
                |              |              |              |              └─ MySQL status=synced
                |              |              |              └─ 语义校验 + 退货原因 + 智能分类(字典注入)
                |              |              └─ 一致 / 差异 / 转人工
                |              └─ 引擎A(智谱) + 引擎B(阿里云)
                └─ 获取URL供双引擎使用

  横切能力：操作审计(AOP) / 数据保留期(定时) / Excel导出 / 异常告警(飞书机器人) / OCR日志埋点
```

**架构模式**：MVC（Controller -> Service -> Model），后端统一 `ResponseResult<T>` 响应格式与全局异常处理。

---

## 项目结构

```
ReturnVision/
├── returnvision-backend/            # 后端服务（Spring Boot）
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd              # Maven Wrapper（无需预装 Maven）
│   ├── .mvn/                        # Maven Wrapper 配置
│   └── src/main/java/tech/jxing/returnvision/
│       ├── ReturnVisionApplication.java     # 启动类
│       ├── controller/                      # 接口层
│       │   ├── RootController.java          #   根路径健康检查
│       │   ├── UploadController.java        #   上传/确认/记录/仪表盘
│       │   ├── AuthController.java          #   登录/刷新/登出/飞书OAuth(F01)
│       │   ├── AdminUserController.java    #   用户管理(F01)
│       │   ├── DictController.java          #   退货字典(F08)
│       │   ├── AuditController.java         #   审计日志查询(F03)
│       │   ├── ExportController.java        #   Excel导出(F02)
│       │   ├── OcrStatsController.java      #   OCR准确率统计(F05)
│       │   └── ReportController.java        #   退货报表(F04)
│       ├── service/                         # 业务逻辑层
│       │   ├── OcrZhipuService / OcrAliyunService   # 双引擎 OCR
│       │   ├── OcrCrossValidatorService             # 交叉验证
│       │   ├── LlmAnalyzerService                   # DeepSeek 分析
│       │   ├── CosClientService                     # 腾讯云 COS
│       │   ├── ValidatorService / WaybillValidator  # 格式/运单前缀校验
│       │   ├── AuthService / AdminUserService       # 鉴权与用户管理(F01)
│       │   ├── DictService                          # 字典管理(F08)
│       │   └── ReportService                        # 报表聚合(F04)
│       ├── feishu/                           # 飞书对接层（多维表格 + OAuth SSO）
│       ├── security/                         # 鉴权模块(F01)
│       │   ├── SecurityConfig.java           #   过滤链 + 路径权限 + 401/403
│       │   ├── JwtUtil / JwtAuthenticationFilter  # JWT 生成解析 + 过滤器
│       │   ├── AuthUser.java                 #   认证用户
│       │   └── FeishuOAuthService.java       #   飞书 OAuth 单点登录
│       ├── audit/                            # 操作审计(F03)
│       │   ├── AuditAspect.java             #   AOP 切面
│       │   ├── AuditLog.java                 #   审计注解
│       │   ├── AuditService.java             #   审计写入
│       │   └── AuditDataMaskUtil.java        #   敏感数据脱敏
│       ├── export/                           # Excel 导出(F02)
│       │   ├── ExportService.java            #   导出逻辑
│       │   ├── ExcelWatermarkUtil.java       #   水印
│       │   └── ExportRateLimiter.java        #   限频(每人每日)
│       ├── ocrstats/                         # OCR 统计(F05)
│       │   ├── OcrStatsService.java          #   准确率聚合
│       │   └── OcrLogWriter.java             #   字段置信度埋点
│       ├── retention/                        # 数据保留期(F02)
│       │   ├── RetentionScheduler.java       #   定时分批清理
│       │   └── RetentionProperties.java      #   保留期配置
│       ├── model/                            # 数据层
│       │   ├── entity/                       #   MyBatis-Plus 实体
│       │   │   ├── ReturnRecord / OcrLog / OperationLog
│       │   │   └── SysUser / SysRole / SysUserRole / SysRefreshToken / SysDict / SysDictItem
│       │   ├── dto/                          #   请求 DTO（用户/字典 CRUD）
│       │   └── mapper/                       #   Mapper 接口
│       ├── config/                           # 配置类（CORS / App / AdminInitializer）
│       └── common/                           # 通用组件
│           ├── ResponseResult.java           #   统一响应
│           ├── GlobalExceptionHandler.java   #   全局异常处理
│           ├── alert/                        #   异常监控告警(F12)
│           │   ├── AlertService.java         #     飞书机器人卡片告警
│           │   ├── AlertLevel.java           #     告警级别
│           │   └── AlertStartupListener.java #     启动监听
│           └── exception/                    #   自定义业务异常
│               ├── BizException.java         #     业务异常基类
│               ├── AuthError / OcrError / LlmError / CosError / FeishuApiError
│               └── DuplicateWaybillError.java#     重复运单
│
├── returnvision-frontend/           # 前端应用（Vue 3 + Vite）
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── App.vue                          # 根组件（登录闸 + 侧边栏布局 + 角色化导航）
│       ├── main.js                          # 入口
│       ├── api.js                           # 接口封装（含 token 自动刷新）
│       ├── icons.js                         # SVG 图标集合
│       ├── components/                      # 业务组件
│       │   ├── LoginPanel.vue               #   登录页（飞书OAuth + 账号密码）
│       │   ├── DashboardPanel.vue           #   工作台（KPI + 趋势）
│       │   ├── RecognitionPanel.vue         #   面单识别（上传 + SSE + 结果）
│       │   ├── RecordsPanel.vue             #   退货记录（列表 + 编辑 + 删除）
│       │   ├── ReportPanel.vue              #   退货报表（ECharts 图表）(F04)
│       │   ├── UserManagePanel.vue          #   用户管理(F01)
│       │   ├── DictPanel.vue                #   退货字典(F08)
│       │   ├── AuditLogPanel.vue            #   审计日志(F03)
│       │   ├── OcrStatsPanel.vue            #   OCR 统计(F05)
│       │   └── ProfilePanel.vue             #   个人中心
│       └── composables/                     # 组合式函数
│           ├── useAuth.js                   #   鉴权状态（token / 用户 / 登录登出）
│           └── useMobile.js                 #   移动端检测
│
├── returnvision-yuanli/             # 源力设计系统原型/设计稿（HTML 预览，非生产代码）
│   ├── pages/                       #   各页面设计稿 HTML
│   ├── partials/project-shell.html  #   页面外壳布局
│   └── colors_and_type.css          #   色彩与字体规范
│
├── deploy/                          # 线上部署配置（Docker Compose + Nginx + 脚本）
│   ├── docker-compose.yml           # 容器编排：backend（内网）+ frontend（HTTPS 自动签发）
│   ├── .env.example                 # 环境变量模板（密钥占位，.env 本身不提交 git）
│   ├── deploy.sh                    # 服务器一键部署脚本
│   ├── returnvision-backend/Dockerfile       # JDK21 JRE 镜像
│   └── nginx/user_conf.d/default.conf       # Nginx 静态托管 + /api 反代 + SSE 配置
│
├── test-checklists/                 # 测试清单工作流（开发前写大纲 -> 开发后补记录）
│   ├── README.md                    #   清单使用规范 v1.1
│   └── YYYY-MM-DD_功能名.md         #   各功能测试清单
│
├── .github/workflows/ci.yml         # GitHub Actions CI（push/PR 跑 mvn test）
├── docs/                            # 设计文档（00-13 + 基础规范）
├── AGENTS.md                        # AI 智能体入口文件
└── README.md                        # 项目说明（本文件）
```

---

## 环境要求

- **JDK**：21 及以上
- **Maven**：无需预装（项目内置 Maven Wrapper，`mvnw` / `mvnw.cmd` 自动下载指定版本）
- **Node.js**：18+（推荐 20 LTS）
- **npm**：9+ 或 pnpm / yarn
- **MySQL**：8.0
- **Docker**（可选，仅生产部署需要）：Docker 24+ 与 Docker Compose v2
- **外部服务账号**：腾讯云 COS、智谱开放平台、阿里云 OCR、DeepSeek、飞书开放平台（含 OAuth 应用）

---

## 快速开始

### 1. 克隆仓库

```bash
# HTTPS（无需配置，直接克隆）
git clone https://github.com/JunXing-Tech/ReturnVision.git

# SSH（已配置 SSH key 时推荐，push 免密）
git clone git@github.com:JunXing-Tech/ReturnVision.git
cd ReturnVision
```

### 2. 配置环境变量

复制示例配置并填写真实密钥（以下变量为必填）：

```bash
# MySQL
export MYSQL_USER=return_ocr
export MYSQL_PASSWORD=your_password

# 智谱 OCR
export ZHIPU_API_KEY=your_zhipu_key

# 阿里云 OCR
export ALIYUN_AK_ID=your_aliyun_ak_id
export ALIYUN_AK_SECRET=your_aliyun_ak_secret

# 腾讯云 COS
export COS_SECRET_ID=your_cos_secret_id
export COS_SECRET_KEY=your_cos_secret_key
export COS_BUCKET=your-bucket-1250000000
# COS_REGION 默认 ap-guangzhou

# DeepSeek
export DEEPSEEK_API_KEY=your_deepseek_key

# 飞书（多维表格 + OAuth SSO + 告警机器人）
export FEISHU_APP_ID=your_app_id
export FEISHU_APP_SECRET=your_app_secret
export FEISHU_APP_TOKEN=your_app_token
export FEISHU_TABLE_ID=your_table_id
export FEISHU_BOT_WEBHOOK=your_bot_webhook
export FEISHU_OAUTH_CLIENT_ID=your_oauth_client_id
export FEISHU_OAUTH_CLIENT_SECRET=your_oauth_client_secret
export FEISHU_OAUTH_REDIRECT_URI=https://returnvision.jxing.tech/login

# JWT 鉴权（F01）
export JWT_SECRET=your_jwt_secret

# 异常监控告警（F12，默认开启）
export ALERT_ENABLED=true
```

### 3. 启动后端

```bash
cd returnvision-backend
# Windows
mvnw.cmd clean spring-boot:run
# macOS / Linux
./mvnw clean spring-boot:run
```

后端默认监听 `http://localhost:8080`，启动时自动执行 `schema.sql` 建表，并初始化管理员账号。

### 4. 启动前端

```bash
cd returnvision-frontend
npm install
npm run dev
```

前端默认监听 `http://localhost:5173`，开发环境通过 Vite 代理转发 API 至后端。首次访问需登录。

### 5. 生产构建

```bash
# 后端
cd returnvision-backend
mvnw.cmd clean package -DskipTests      # Windows
./mvnw clean package -DskipTests        # macOS / Linux
java -jar target/returnvision-backend-1.0.0.jar

# 前端
cd returnvision-frontend
npm run build
# 产物位于 dist/，可由 Nginx 或后端静态资源托管
```

### 6. Docker 部署（生产环境）

项目内置 Docker Compose 部署方案，详见 [`deploy/`](deploy/) 目录：

```bash
cd deploy
cp .env.example .env       # 填写真实密钥
bash deploy.sh             # 一键部署
```

架构：backend 容器（JDK21 JRE，仅内网）+ frontend 容器（Nginx + Certbot，HTTPS 自动签发），MySQL 使用宿主机系统级安装。

---

## 配置说明

主配置文件位于 [`returnvision-backend/src/main/resources/application.yml`](returnvision-backend/src/main/resources/application.yml)，关键配置项如下：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `server.port` | 后端服务端口 | `8080` |
| `server.forward-headers-strategy` | 信任 Nginx 反代头（修复 CORS 403） | `native` |
| `spring.profiles.active` | 激活配置文件 | `local` |
| `spring.servlet.multipart.*` | 单文件 `10MB` / 单请求 `200MB` | - |
| `spring.datasource.*` | MySQL 数据源（HikariCP 连接池） | 环境变量注入 |
| `spring.sql.init.*` | 启动自动执行 `schema.sql`（`continue-on-error`） | `always` |
| `mybatis-plus.*` | 驼峰映射、自增主键 | - |
| `cos.region` | 腾讯云 COS 地域 | `ap-guangzhou` |
| `deepseek.base-url` / `model` | DeepSeek API 地址 / 模型 | `deepseek-v4-flash` |
| `feishu.oauth.*` | 飞书 OAuth 单点登录（client-id/secret/redirect-uri） | 环境变量注入 |
| `jwt.secret` / `access-token-expiration` / `refresh-token-expiration` | JWT 密钥与有效期（2h / 7d） | 环境变量注入 |
| `retention.*` | 数据保留期（退货记录 90d / 审计 180d / OCR 30d / 待确认提醒 30d） | 见 yml |
| `export.max-rows-per-export` / `max-exports-per-day` | 导出管控（≤1000 行 / 每人每日 ≤5 次） | 见 yml |
| `alert.enabled` / `dedup-window-ms` / `feishu-fail-threshold` | 异常告警开关与去重窗口 | `true` / `300000` / `3` |

> **安全提示**：所有 API Key、密码、Token 必须通过环境变量注入，严禁硬编码到配置文件或代码中。

---

## 使用说明

1. 打开前端页面（本地 `http://localhost:5173`，线上 <https://returnvision.jxing.tech>）
2. 在「**登录**」页选择飞书 OAuth 一键登录，或使用账号密码登录
3. 登录后按角色进入默认页：管理员/主管进「工作台」，客服进「面单识别」
4. 在「**工作台**」查看识别总量、待确认、已同步、失败数等 KPI 与趋势
5. 切换到「**面单识别**」，拍照或上传快递面单图片（支持批量 ≤ 20 张），实时查看 SSE 流式进度
6. 系统自动调用 COS 上传 -> 双引擎 OCR -> 交叉验证 -> DeepSeek 分析，并展示识别结果
7. 人工审核并确认退货原因、退货分类等字段（分类/原因来自标准字典）
8. 确认后系统自动写入飞书多维表格，并在「**退货记录**」中检索、编辑、导出
9. 管理员可在「**用户管理**」「**退货字典**」维护账号与分类标准；主管可在「**审计日志**」查看操作记录

> 侧边栏导航按角色动态渲染：客服不显示工作台与系统管理（除个人中心），仅管理员可见用户管理与字典。

---

## API 接口

接口遵循统一响应格式 `ResponseResult<T>`（`code=0` 表示成功，字段为 `code/msg/data`）：

```json
{
  "code": 0,
  "msg": "success",
  "data": { }
}
```

> 鉴权接口（登录/刷新/飞书回调）公开，其余 `/api/**` 需携带 `Authorization: Bearer <token>`；`/api/admin/**` 需 ADMIN 角色，`/api/dashboard/**` 需主管或管理员。

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 基础 | GET | `/` | 健康检查 / 根路径 |
| 上传识别 | POST | `/api/upload` | 上传单张面单图片并触发识别流程 |
| 上传识别 | POST | `/api/upload/sse` | SSE 流式上传：实时推送 OCR/LLM 各阶段进度 |
| 上传识别 | POST | `/api/upload/batch` | 批量上传：一次提交多张图片并行识别 |
| 上传识别 | POST | `/api/records/batch` | 批量上传（旧接口，返回汇总结构） |
| 记录管理 | GET | `/api/records` | 查询退货记录列表（分页 + 状态筛选） |
| 记录管理 | DELETE | `/api/records/{id}` | 删除单条退货记录 |
| 记录管理 | DELETE | `/api/records/batch` | 批量删除退货记录 |
| 记录管理 | POST | `/api/records/export` | 导出 Excel（带水印 + 限频）(F02) |
| 确认写入 | POST | `/api/confirm` | 确认单条退货记录并写入飞书 |
| 确认写入 | POST | `/api/confirm/batch` | 批量确认并写入飞书 |
| 仪表盘 | GET | `/api/dashboard/stats` | 工作台 KPI 统计（总量/待确认/已同步/失败 + 趋势） |
| 仪表盘 | GET | `/api/dashboard/ocr-stats` | OCR 准确率统计（字段级置信度）(F05) |
| 报表 | GET | `/api/reports` | 多维度退货报表聚合（按角色数据范围）(F04) |
| 鉴权 | POST | `/api/auth/login` | 账号密码登录 |
| 鉴权 | POST | `/api/auth/refresh` | 刷新 access token |
| 鉴权 | POST | `/api/auth/logout` | 登出 |
| 鉴权 | GET | `/api/auth/me` | 当前用户信息 |
| 鉴权 | GET/PUT | `/api/auth/profile` | 查看 / 修改个人资料 |
| 鉴权 | POST | `/api/auth/change-password` | 修改密码 |
| 鉴权 | GET | `/api/auth/feishu/url` | 获取飞书 OAuth 授权地址 |
| 鉴权 | POST | `/api/auth/feishu/callback` | 飞书 OAuth 回调换 token (F01) |
| 用户管理 | GET/POST | `/api/admin/users` | 用户列表 / 新建用户 (ADMIN) |
| 用户管理 | PUT/DELETE | `/api/admin/users/{id}` | 编辑 / 删除用户 |
| 用户管理 | POST | `/api/admin/users/{id}/reset-password` | 重置密码 |
| 字典 | GET | `/api/dict/categories` | 字典分类查询 (F08) |
| 字典 | POST/PUT/DELETE | `/api/admin/dict/items[/{id}]` | 字典项 CRUD (ADMIN) |
| 审计 | GET | `/api/audit/logs` | 审计日志检索（主管/管理员）(F03) |

> 实际接口以 [`docs/06-API接口设计.md`](docs/06-API接口设计.md) 为准；响应字段定义见 [`docs/基础框架规范.md`](docs/基础框架规范.md) 第一章。

---

## 开发指南

### 编码规范

- 代码使用英文，注释与日志使用中文
- Controller 方法必须写业务流程（编号步骤），Service 方法必须写实现步骤
- 业务异常抛 `BizException` 子类，由全局异常处理器统一返回，**禁止在 Controller 手动 try-catch**
- 依赖注入默认使用构造器注入（字段用 `final`，无需 `@Autowired`）
- 日志使用 `@Slf4j`，格式为 `[模块名] 消息`
- 统一响应类型 `ResponseResult<T>`
- 写操作接口加 `@AuditLog` 注解触发操作审计（F03）

详见 [`docs/基础框架规范.md`](docs/基础框架规范.md) 与 [`AGENTS.md`](AGENTS.md)。

### 测试

项目遵循「测试清单工作流」（见 [`test-checklists/README.md`](test-checklists/README.md) v1.1）：开发新模块/优化功能前先写测试清单，自审三阶段（角色切换通读 -> 固定检查表 -> AI 反向核对）。

- **单元/集成测试**：覆盖交叉验证、格式校验、运单前缀校验、字典、报表、LLM 等核心逻辑（H2 MySQL 兼容模式，不调真实外部服务）
- **CI 守门**：GitHub Actions（[`.github/workflows/ci.yml`](.github/workflows/ci.yml)）在 push 到 main 或 PR 时自动跑 `mvn test`，测试不绿不能进 main

```bash
cd returnvision-backend
mvnw.cmd test          # Windows
./mvnw test            # macOS / Linux
```

### 常用命令

```bash
# 后端编译（Windows 用 mvnw.cmd，macOS/Linux 用 ./mvnw）
cd returnvision-backend && mvnw.cmd clean compile

# 后端打包（跳过测试）
mvnw.cmd clean package -DskipTests

# 后端测试
mvnw.cmd test

# 前端开发
cd returnvision-frontend && npm run dev

# 前端构建
npm run build

# 前端预览构建产物
npm run preview

# 生产部署（Docker）
cd deploy && bash deploy.sh
```

### 提交规范

提交信息格式：

- 初始开发阶段（对齐 [docs/00-开发执行指南.md](docs/00-开发执行指南.md) 检查点）：`步骤N：简要描述`
- 功能迭代（对齐 [docs/11-开发流程规范.md](docs/11-开发流程规范.md)）：`F0X：简要描述` 或 `模块/功能：简要描述`
- 修复：`hotfix/修复：简要描述`

提交后推送到 `origin/main`。回滚统一走 GitHub 版本控制（`git revert` 或回退到上一个 tag）。

---

## 文档导航

| 文件 | 内容 | 面向角色 |
|------|------|----------|
| [AGENTS.md](AGENTS.md) | AI 智能体入口文件、执行规则、自审机制、禁止事项 | AI |
| [docs/00-开发执行指南.md](docs/00-开发执行指南.md) | 分步执行计划、检查点、文档串联 | AI / 开发者 |
| [docs/基础框架规范.md](docs/基础框架规范.md) | 编码规范、注释规范、配置模板 | AI / 开发者 |
| [docs/01-项目概述.md](docs/01-项目概述.md) | 业务背景、建设目标、核心流程 | 所有人 |
| [docs/02-技术架构设计.md](docs/02-技术架构设计.md) | 技术栈选型、架构模式、系统架构图 | 技术决策者 |
| [docs/03-技术可行性验证.md](docs/03-技术可行性验证.md) | 智谱 OCR、阿里云面单 OCR、腾讯云运单 OCR、LLM 多模态、飞书 API 验证 | 技术决策者 |
| [docs/04-详细方案设计.md](docs/04-详细方案设计.md) | OCR 双引擎、信息提取、飞书写入、鉴权、前端交互 | 开发人员 |
| [docs/05-数据库设计.md](docs/05-数据库设计.md) | 表结构、状态流转、ER 图、ORM 定义 | 开发人员 |
| [docs/06-API接口设计.md](docs/06-API接口设计.md) | 接口总览、请求/响应示例 | 前后端对接 |
| [docs/07-项目结构与代码规范.md](docs/07-项目结构与代码规范.md) | 目录结构、模块拆分、分阶段交付 | 开发人员 |
| [docs/08-实施计划与风险.md](docs/08-实施计划与风险.md) | 实施步骤、风险应对、环境依赖 | 项目管理 |
| [docs/09-技术决策附录.md](docs/09-技术决策附录.md) | 关键技术选型决策汇总 | 技术决策者 |
| [docs/10-产品演进与功能可行性方案.md](docs/10-产品演进与功能可行性方案.md) | 产品视角缺口诊断、候选功能、4 期演进路线 | 产品 / 项目管理 |
| [docs/11-开发流程规范.md](docs/11-开发流程规范.md) | 新增/优化功能的影响面评估清单与端到端流程 | AI / 开发者 |
| [docs/12-前端设计规范.md](docs/12-前端设计规范.md) | 前端技术栈、设计 token、页面脚手架、禁止事项 | 前端开发 |
| [docs/13-微信小程序方案设计.md](docs/13-微信小程序方案设计.md) | 小程序端技术选型、后端增量、用户体系、部署发布 | 前端 / 架构 |
| [test-checklists/README.md](test-checklists/README.md) | 测试清单使用规范 v1.1 | AI / 开发者 |

### 阅读建议

- **AI 执行开发**：AGENTS.md -> 00（按步骤执行，每个检查点暂停）
- **AI 接到新需求/优化项**：AGENTS.md「新增/优化功能的开发流程」节 -> docs/11 -> docs/12（前端）
- **快速了解项目**：01 -> 02 -> 09
- **准备开发**：基础框架规范 -> 04 -> 05 -> 06 -> 07
- **评估可行性**：02 -> 03 -> 08
- **规划后续迭代**：docs/10（产品演进）-> docs/11（落地流程）-> docs/13（小程序端）

---

## 版本历史

### v3.0（2026-07-22）

- **前端重构：迁移源力设计系统**：改用源力（Volcengine）设计系统 v3.0，品牌蓝主色 + 中文优先字体，**移除暗色模式**（运营场景固定浅色主题），侧边栏分组布局
- **登录页重做**：新增 `LoginPanel` + `useAuth` composable，飞书 OAuth 入口 + 账号密码入口
- **F01 鉴权体系**：飞书 OAuth 单点登录 + JWT 双令牌 + BCrypt + 三角色权限（客服/主管/管理员）+ 菜单动态渲染 + 路径/方法双重权限校验
- **F02 数据导出与保留期**：Excel 导出（POI + 水印 + 限频）+ 定时分批清理过期数据
- **F03 操作审计日志**：AOP 切面自动审计 + 敏感数据脱敏 + 审计日志检索
- **F04 退货报表**：多维度聚合 + ECharts 图表 + 数据范围权限
- **F05 OCR 准确率仪表盘**：字段级置信度回溯 + OCR 日志埋点
- **F08 退货字典管理**：两级标准字典 + LLM prompt 动态注入 + 越界降级
- **F12 异常监控告警**：飞书机器人卡片告警 + 去重窗口
- **闭环测试体系**：GitHub Actions CI + test-checklists 测试清单工作流 + H2 测试库，核心逻辑 125+ 测试全绿
- **新增依赖**：Spring Security、Spring AOP、JJWT 0.12.6、Apache POI 5.2.5、ECharts 6.1

### v2.1（2026-07-17）

- **前端双主题重设计**：改用 Vercel 单色系设计，支持浅色/暗色一键切换（注：v3.0 已迁移至源力设计系统并移除暗色模式）
- **新增 Maven Wrapper**：`mvnw` / `mvnw.cmd` / `.mvn/`，无需预装 Maven 即可构建
- **OCR 失败保护**：双引擎均未识别出有效运单信息时不写库，避免记录页出现"失败数据"
- **Docker 部署上线**：双容器架构（backend 内网 + frontend 对外），Nginx 反代 + Let's Encrypt HTTPS 自动签发
- **CORS 反代修复**：`server.forward-headers-strategy: native` + 线上域名兜底，解决 Nginx 反代跨域 403
- **新增 3 份规范文档**：docs/10 产品演进方案、docs/11 开发流程规范、docs/12 前端设计规范
- **AGENTS.md 升级**：新增「新增/优化功能的开发流程」章节与必读文档指引

### v2.0（2026-07-10）

- **OCR 引擎升级**：从单引擎（阿里云）改为双引擎交叉验证（智谱 OCR 引擎 A + 阿里云引擎 B）
- **修正 GLM-5.2 错误**：GLM-5.2 为纯文本模型不支持图片，已更正为智谱 OCR 解析工具
- **新增 DeepSeek LLM 分析**：语义校验 + 退货原因提取 + 智能分类，约 ¥0.0004/次
- **新增腾讯云 COS**：图片持久化存储，URL 供双引擎 OCR 和飞书引用
- **数据库改为 MySQL**：生产级可靠性，替代 SQLite
- **新增飞书机器人通知**：写入飞书后自动发卡片消息到群
- **新增交叉验证引擎**：双引擎并行识别 + 字段比对，一致时准确率接近 99.9%
- **后端语言改为 Java**：Python/FastAPI 改为 Java 21/Spring Boot 3.x + MyBatis-Plus
- **新增五层保障体系**：交叉验证 -> 置信度判断 -> 格式校验 -> DeepSeek 语义校验 -> 人工确认
- **成本优化**：智谱 ¥0.01/次（原阿里云 ¥0.0825/次），月成本降低约 80%

---

## 许可证

本项目为内部使用项目，版权归 © 2026 JunXing Tech 所有，不对外开源授权。如需使用或二次开发，请联系项目维护方。
