# 退运智录（ReturnVision）

> 拍照识别快递面单 → 双引擎交叉验证 → DeepSeek LLM 分析 → 人工确认 → 写入飞书多维表格

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.4-brightgreen.svg)](https://vuejs.org/)
[![Vite](https://img.shields.io/badge/Vite-5.2-purple.svg)](https://vitejs.dev/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0-blue.svg)](https://www.mysql.com/)
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

---

## 功能特性

### 核心识别能力

- **双引擎 OCR 交叉验证**：智谱 OCR（引擎 A）与阿里云面单 OCR（引擎 B）并行识别，结果交叉比对
- **LLM 智能分析**：DeepSeek V4 Flash 进行语义校验、退货原因提取与智能分类
- **图片持久化**：腾讯云 COS 存储原图，URL 供 OCR 与飞书引用
- **飞书自动写入**：识别结果一键写入飞书多维表格，并通过机器人推送卡片消息
- **五层保障体系**：交叉验证 -> 置信度判断 -> 格式校验 -> DeepSeek 语义校验 -> 人工确认
- **成本优化**：单次识别成本约 ¥0.01，相比单引擎方案月成本降低约 80%

### 上传与交互

- **SSE 流式上传**：`/api/upload/sse` 实时推送 OCR/LLM 各阶段进度，前端展示流水线状态
- **批量上传**：支持单次最多 20 张面单图片（每张 ≤ 10MB）的批量识别
- **OCR 失败保护**：双引擎均未识别出有效运单信息时不写库，避免记录页出现"失败数据"
- **记录管理**：支持单条/批量删除、编辑确认、同步状态检索

### 前端体验

- **双主题切换**：Vercel 风格单色系设计，支持浅色/暗色一键切换，自动跟随系统偏好并持久化
- **仪表盘**：首页展示识别总量、待确认、已同步、失败数等统计数据，支持自动刷新
- **移动端适配**：响应式设计，适配手机拍照上传场景，移动端底部 Tab 栏

---

## 技术栈

| 层级 | 选型 |
|------|------|
| 后端 | Java 21、Spring Boot 3.3.0、MyBatis-Plus 3.5.7、Maven |
| 前端 | Vue 3.4、Vite 5.2、Element Plus 2.7、Axios |
| 数据库 | MySQL 8.0（宿主机系统级安装，不放入 Docker） |
| 图片存储 | 腾讯云 COS（cos_api 5.6.227） |
| OCR 引擎 A | 智谱 OCR（OkHttp 直连） |
| OCR 引擎 B | 阿里云面单 OCR（ocr_api20210707 3.1.3） |
| LLM | DeepSeek V4 Flash |
| 飞书 | lark-oapi 2.3.6 + Webhook 机器人 |
| 容器编排 | Docker Compose（backend 容器内网 + frontend 容器对外） |
| Web 服务器 | Nginx（frontend 容器内置，静态托管 + /api 反向代理 + SSE 配置） |
| HTTPS | Let's Encrypt 自动签发与续期（jonasal/nginx-certbot 5.2.3） |
| 构建工具 | Maven Wrapper、Vite |

---

## 系统架构

```
拍照上传 -> COS存储 -> 双引擎OCR并行 -> 交叉验证 -> DeepSeek分析 -> 人工确认 -> 飞书写入 + 机器人通知
              |              |              |              |              |              |
              |              |              |              |              |              ├─ return_reason
              |              |              |              |              |              ├─ return_category
              |              |              |              |              |              └─ photo_url
              |              |              |              |              └─ MySQL status=synced
              |              |              |              └─ 语义校验 + 退货原因 + 智能分类
              |              |              └─ 一致 / 差异 / 转人工
              |              └─ 引擎A(智谱) + 引擎B(阿里云)
              └─ 获取URL供双引擎使用
```

**架构模式**：MVC（Controller → Service → Model），后端统一 `ResponseResult<T>` 响应格式与全局异常处理。

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
│       ├── service/                         # 业务逻辑层（OCR / LLM / COS / 验证）
│       ├── feishu/                           # 飞书对接层
│       ├── model/                           # 数据层
│       │   ├── entity/                       # MyBatis-Plus 实体
│       │   └── mapper/                      # Mapper 接口
│       ├── config/                          # 配置类（CORS / App）
│       └── common/                          # 通用组件
│           ├── ResponseResult.java          # 统一响应
│           ├── GlobalExceptionHandler.java  # 全局异常处理
│           └── exception/                    # 自定义业务异常
│
├── returnvision-frontend/           # 前端应用（Vue 3 + Vite）
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── App.vue                          # 根组件（主题切换 + Tab 导航）
│       ├── main.js                          # 入口（防闪屏主题初始化）
│       ├── api.js                           # 接口封装
│       ├── icons.js                         # SVG 图标集合
│       ├── components/                      # 业务组件
│       │   ├── DashboardPanel.vue           # 仪表盘（统计 + 自动刷新）
│       │   ├── RecognitionPanel.vue         # 识别面板（上传 + SSE 进度 + 结果）
│       │   └── RecordsPanel.vue             # 记录面板（列表 + 编辑 + 删除）
│       └── composables/                     # 组合式函数
│           ├── useMobile.js                 # 移动端检测
│           └── useTheme.js                  # 主题切换（系统偏好 + localStorage）
│
├── deploy/                          # 线上部署配置（Docker Compose + Nginx + 脚本）
│   ├── docker-compose.yml           # 容器编排：backend（内网）+ frontend（HTTPS 自动签发）
│   ├── .env.example                 # 环境变量模板（密钥占位，.env 本身不提交 git）
│   ├── deploy.sh                    # 服务器一键部署脚本
│   ├── returnvision-backend/Dockerfile       # JDK21 JRE 镜像
│   └── nginx/user_conf.d/default.conf       # Nginx 静态托管 + /api 反代 + SSE 配置
│
├── docs/                            # 设计文档（00-12 + 基础规范）
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
- **外部服务账号**：腾讯云 COS、智谱开放平台、阿里云 OCR、DeepSeek、飞书开放平台

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

# 飞书
export FEISHU_APP_ID=your_app_id
export FEISHU_APP_SECRET=your_app_secret
export FEISHU_APP_TOKEN=your_app_token
export FEISHU_TABLE_ID=your_table_id
export FEISHU_BOT_WEBHOOK=your_bot_webhook
```

### 3. 启动后端

```bash
cd returnvision-backend
# Windows
mvnw.cmd clean spring-boot:run
# macOS / Linux
./mvnw clean spring-boot:run
```

后端默认监听 `http://localhost:8080`，启动时自动执行 `schema.sql` 建表。

### 4. 启动前端

```bash
cd returnvision-frontend
npm install
npm run dev
```

前端默认监听 `http://localhost:5173`，开发环境通过 Vite 代理转发 API 至后端。

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
| `spring.profiles.active` | 激活配置文件 | `local` |
| `spring.servlet.multipart.max-file-size` | 单文件上限 | `10MB` |
| `spring.servlet.multipart.max-request-size` | 单请求上限 | `200MB` |
| `spring.datasource.*` | MySQL 数据源连接 | 通过环境变量注入 |
| `mybatis-plus.*` | MyBatis-Plus 配置（驼峰映射、自增主键） | - |
| `cos.region` | 腾讯云 COS 地域 | `ap-guangzhou` |
| `deepseek.base-url` | DeepSeek API 地址 | `https://api.deepseek.com` |
| `deepseek.model` | 调用模型 | `deepseek-v4-flash` |

> **安全提示**：所有 API Key、密码、Token 必须通过环境变量注入，严禁硬编码到配置文件或代码中。

---

## 使用说明

1. 打开前端页面（本地 `http://localhost:5173`，线上 <https://returnvision.jxing.tech>）
2. 在「**仪表盘**」查看识别总量、待确认、已同步、失败数等统计
3. 切换到「**识别**」面板，拍照或上传快递面单图片（支持批量 ≤ 20 张），实时查看 SSE 流式进度
4. 系统自动调用 COS 上传 -> 双引擎 OCR -> 交叉验证 -> DeepSeek 分析，并展示识别结果
5. 人工审核并确认退货原因、退货分类等字段
6. 确认后系统自动写入飞书多维表格，并通过飞书机器人推送卡片消息到群
7. 在「**记录**」面板中检索历史记录、编辑字段、单条/批量删除

> 顶栏右侧可一键切换浅色/暗色主题，首次访问自动跟随系统偏好。

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

主要接口：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/` | 健康检查 / 根路径 |
| POST | `/api/upload` | 上传单张面单图片并触发识别流程（multipart/form-data） |
| POST | `/api/upload/sse` | SSE 流式上传：实时推送 OCR/LLM 各阶段进度 |
| POST | `/api/upload/batch` | 批量上传：一次提交多张图片并行识别 |
| POST | `/api/records/batch` | 批量上传（旧接口，返回汇总结构） |
| GET  | `/api/records` | 查询退货记录列表（支持状态筛选） |
| DELETE | `/api/records/{id}` | 删除单条退货记录 |
| DELETE | `/api/records/batch` | 批量删除退货记录 |
| POST | `/api/confirm` | 确认单条退货记录并写入飞书 |
| POST | `/api/confirm/batch` | 批量确认并写入飞书 |
| GET  | `/api/dashboard/stats` | 仪表盘统计数据（总量、待确认、已同步、失败数） |

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

详见 [`docs/基础框架规范.md`](docs/基础框架规范.md) 与 [`AGENTS.md`](AGENTS.md)。

### 常用命令

```bash
# 后端编译（Windows 用 mvnw.cmd，macOS/Linux 用 ./mvnw）
cd returnvision-backend && mvnw.cmd clean compile

# 后端打包（跳过测试）
mvnw.cmd clean package -DskipTests

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
- 后续新增/优化功能（对齐 [docs/11-开发流程规范.md](docs/11-开发流程规范.md)）：`模块/功能：简要描述`

提交后推送到 `origin/main`。回滚统一走 GitHub 版本控制（`git revert` 或回退到上一个 tag）。

---

## 文档导航

| 文件 | 内容 | 面向角色 |
|------|------|----------|
| [AGENTS.md](AGENTS.md) | AI 智能体入口文件、执行规则、禁止事项 | AI |
| [docs/00-开发执行指南.md](docs/00-开发执行指南.md) | 分步执行计划、检查点、文档串联 | AI / 开发者 |
| [docs/基础框架规范.md](docs/基础框架规范.md) | 编码规范、注释规范、配置模板 | AI / 开发者 |
| [docs/Agent.md](docs/Agent.md) | 任务决策记录、历史变更 | 项目管理 |
| [docs/01-项目概述.md](docs/01-项目概述.md) | 业务背景、建设目标、核心流程 | 所有人 |
| [docs/02-技术架构设计.md](docs/02-技术架构设计.md) | 技术栈选型、架构模式、系统架构图 | 技术决策者 |
| [docs/03-技术可行性验证.md](docs/03-技术可行性验证.md) | 智谱 OCR、阿里云面单 OCR、腾讯云运单 OCR、LLM 多模态、飞书 API 验证 | 技术决策者 |
| [docs/04-详细方案设计.md](docs/04-详细方案设计.md) | OCR 双引擎、信息提取、飞书写入、前端交互 | 开发人员 |
| [docs/05-数据库设计.md](docs/05-数据库设计.md) | 表结构、状态流转、ER 图、ORM 定义 | 开发人员 |
| [docs/06-API接口设计.md](docs/06-API接口设计.md) | 接口总览、请求/响应示例 | 前后端对接 |
| [docs/07-项目结构与代码规范.md](docs/07-项目结构与代码规范.md) | 目录结构、模块拆分、分阶段交付 | 开发人员 |
| [docs/08-实施计划与风险.md](docs/08-实施计划与风险.md) | 实施步骤、风险应对、环境依赖 | 项目管理 |
| [docs/09-技术决策附录.md](docs/09-技术决策附录.md) | 关键技术选型决策汇总 | 技术决策者 |
| [docs/10-产品演进与功能可行性方案.md](docs/10-产品演进与功能可行性方案.md) | 产品视角缺口诊断、候选功能、4 期演进路线 | 产品 / 项目管理 |
| [docs/11-开发流程规范.md](docs/11-开发流程规范.md) | 新增/优化功能的影响面评估清单与端到端流程 | AI / 开发者 |
| [docs/12-前端设计规范.md](docs/12-前端设计规范.md) | 前端技术栈、设计 token、页面脚手架、禁止事项 | 前端开发 |

### 阅读建议

- **AI 执行开发**：AGENTS.md → 00（按步骤执行，每个检查点暂停）
- **AI 接到新需求/优化项**：AGENTS.md「新增/优化功能的开发流程」节 → docs/11 → docs/12（前端）
- **快速了解项目**：01 → 02 → 09
- **准备开发**：基础框架规范 → 04 → 05 → 06 → 07
- **评估可行性**：02 → 03 → 08
- **规划后续迭代**：docs/10（产品演进）→ docs/11（落地流程）

---

## 版本历史

### v2.1（2026-07-17）

- **前端双主题重设计**：改用 Vercel 单色系设计，支持浅色/暗色一键切换，自动跟随系统偏好并 localStorage 持久化
- **新增主题切换 composable**：`useTheme.js`，`App.vue` 顶栏主题按钮，`main.js` 防闪屏初始化
- **新增 Maven Wrapper**：`mvnw` / `mvnw.cmd` / `.mvn/`，无需预装 Maven 即可构建
- **OCR 失败保护**：双引擎均未识别出有效运单信息时不写库，避免记录页出现"失败数据"
- **Docker 部署上线**：双容器架构（backend 内网 + frontend 对外），Nginx 反代 + Let's Encrypt HTTPS 自动签发
- **CORS 反代修复**：`server.forward-headers-strategy: native` + 线上域名兜底，解决 Nginx 反代跨域 403
- **新增 3 份规范文档**：docs/10 产品演进方案、docs/11 开发流程规范、docs/12 前端设计规范 v2.1
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