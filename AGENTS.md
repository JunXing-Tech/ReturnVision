> 版本：v2.0 | 日期：2026-07-10

# AGENTS.md - 退货OCR项目 AI 智能体指南

> 本文件是 AI 智能体开始工作时的入口文件。请先完整阅读本文件，再按指引阅读其他文档。

---

## 项目概述

**项目名称**：退运智录（ReturnVision）
**上线地址**：https://returnvision.jxing.tech
**一句话描述**：拍照识别快递面单 -> 双引擎交叉验证 -> DeepSeek LLM分析 -> 人工确认 -> 写入飞书多维表格。

**项目目录结构**：
```
ReturnVision/                        # 项目根目录
├── returnvision-backend/            # 后端（Java / Spring Boot）
│   ├── pom.xml
│   └── src/main/java/tech/jxing/returnvision/
├── returnvision-frontend/           # 前端（Vue 3 + Vite）
│   ├── package.json
│   └── src/
├── docs/                            # 设计文档目录（所有方案文档）
│   ├── 00-开发执行指南.md
│   ├── 基础框架规范.md
│   ├── 01~09 方案文档
│   └── Agent.md
├── AGENTS.md                        # AI 智能体入口文件（根目录）
└── README.md                        # 项目说明（根目录）
```

**技术栈**：
- 后端：Java 21 + Spring Boot 3.x + MyBatis-Plus + Maven
- 前端：Vue 3 + Vite
- 数据库：MySQL 8.0
- 图片存储：腾讯云 COS
- OCR引擎：智谱OCR（引擎A）+ 阿里云面单OCR（引擎B）
- LLM：DeepSeek V4 Flash
- 飞书：lark-oapi Java SDK + Webhook 机器人

---

## 文件阅读顺序

开始编码前，**必须按以下顺序阅读**：

| 顺序 | 文件 | 作用 |
|------|------|------|
| 1 | **docs/00-开发执行指南.md** | 12步执行计划 + 13个检查点（做什么） |
| 2 | **docs/基础框架规范.md** | 编码规范 + 注释规范 + 配置模板（怎么写） |
| 3 | **docs/04-详细方案设计.md** | 所有业务代码示例（Java） |
| 4 | **docs/05-数据库设计.md** | MySQL DDL + MyBatis-Plus Entity |
| 5 | **docs/06-API接口设计.md** | API 请求/响应格式 |
| 6 | **docs/07-项目结构与代码规范.md** | 目录结构 + 模块拆分 |

其他文档按需阅读：
- docs/01-项目概述.md（业务背景）
- docs/02-技术架构设计.md（架构总览）
- docs/03-技术可行性验证.md（技术选型验证）
- docs/08-实施计划与风险.md（实施计划）
- docs/09-技术决策附录.md（决策记录）

---

## 执行规则

### 检查点规则

**每个步骤完成后必须暂停**，等待用户确认后才能继续下一步：

```
步骤1完成 -> ⏸️ 暂停 -> 用户检查 -> 用户说"继续" -> 步骤2
```

**特别强调**：步骤6（交叉验证引擎）是核心业务逻辑，完成后**必须暂停**，用户需仔细检查比对策略和仲裁规则。

### Git 提交规则

**每个步骤完成并通过用户检查后，提交代码到 GitHub 远程仓库。**

- 仓库地址：`https://github.com/JunXing-Tech/ReturnVision.git`
- 提交时机：检查点通过后，用户说"继续"之前
- 提交信息格式：`步骤N：简要描述`
- 提交后推送到 origin/main

```
步骤完成 -> ⏸️ 暂停 -> 用户检查 -> 用户说"继续" -> git commit + push -> 下一步骤
```

### 设计反思规则（重要）

**AI 不应机械执行文档方案。** 在每个步骤开始前，先思考：

1. **当前方案是否最优**：文档设计方案是否最符合业务需求？有没有更好的实现方式？
2. **业务变化检测**：随着开发推进，业务需求可能发生变化，原有设计可能不再适用
3. **主动建议**：如果发现更好的方案，**先暂停并提出建议**，说明：
   - 当前方案的问题或不足
   - 建议的改进方案
   - 改进后的收益
4. **同步更新文档**：用户同意改进后，**同步更新相关方案文档**（02-09 + 基础框架规范），再实施代码

```
读文档 -> 思考方案是否合理 -> 合理则执行 / 有更好方案则暂停建议 -> 用户确认 -> 更新文档 -> 实施
```

**原则**：文档服务于业务，不是业务服务于文档。文档可随时迭代。

### 编码约定

1. **语言**：代码用英文，注释用中文，日志用中文
2. **架构**：MVC（Controller -> Service -> Model）
3. **注释**：遵循 基础框架规范.md 第十章 注释规范
   - Controller 方法必须写业务流程（编号步骤）
   - Service 方法必须写实现步骤（编号步骤）
   - 方法体内每个步骤前加 `// 步骤N：描述`
4. **响应格式**：统一用 `ResponseResult<T>`（见基础框架规范.md 第一章）
5. **异常处理**：业务异常抛 `BizException` 子类，不手动 try-catch（见第二章）
6. **配置**：敏感信息放环境变量，业务参数放 application.yml（见第五章）
7. **日志**：用 `@Slf4j`，格式 `[模块名] 消息`（见第三章）

### 包名约定

```
tech.jxing.returnvision
├── controller          # 接口层
├── service             # 业务逻辑层
├── feishu              # 飞书对接层
├── model
│   ├── entity          # MyBatis-Plus 实体类
│   ├── dto             # 数据传输对象
│   └── mapper          # Mapper 接口
├── config              # 配置类
└── common
    ├── ResponseResult.java
    ├── GlobalExceptionHandler.java
    └── exception/      # 自定义异常
```

---

## 禁止事项

| 禁止 | 原因 |
|------|------|
| ❌ 使用 Python | 项目已迁移到 Java |
| ❌ 修改 DDL SQL | 数据库设计已确定，如需改先讨论 |
| ❌ 跳过检查点 | 每步必须暂停等用户确认 |
| ❌ 使用 SQLite | 已改为 MySQL 8.0 |
| ❌ 使用 SQLAlchemy | 已改为 MyBatis-Plus |
| ❌ 在 Controller 直接 try-catch | 用全局异常处理器 |
| ❌ 硬编码 API Key | 必须放环境变量 |
| ❌ 跳过注释 | Controller 和 Service 方法注释必填 |

---

## 业务流程速览

```
拍照上传 -> COS存储 -> 双引擎OCR并行 -> 交叉验证 -> DeepSeek分析 -> 人工确认 -> 飞书写入+机器人通知
              |              |              |              |              |              |
              |              |              |              |              |              ├─ return_reason
              |              |              |              |              |              ├─ return_category
              |              |              |              |              |              └─ photo_url
              |              |              |              |              └─ MySQL status=synced
              |              |              |              └─ 语义校验 + 退货原因 + 智能分类
              |              |              └─ 一致/差异/转人工
              |              └─ 引擎A(智谱) + 引擎B(阿里云)
              └─ 获取URL供双引擎使用
```

---

## 联系文档

| 有问题查这里 |
|-------------|
| 业务背景 -> docs/01-项目概述.md |
| 架构设计 -> docs/02-技术架构设计.md |
| 技术选型依据 -> docs/03-技术可行性验证.md |
| 代码怎么写 -> docs/04-详细方案设计.md + docs/基础框架规范.md |
| 表结构 -> docs/05-数据库设计.md |
| API格式 -> docs/06-API接口设计.md |
| 目录结构 -> docs/07-项目结构与代码规范.md |
| 实施计划 -> docs/08-实施计划与风险.md |
| 技术决策 -> docs/09-技术决策附录.md |
| 执行步骤 -> docs/00-开发执行指南.md |
