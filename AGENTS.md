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
├── deploy/                          # 线上部署配置（Docker Compose + Nginx + 脚本）
│   ├── docker-compose.yml           # 容器编排：backend（内网）+ frontend（HTTPS 自动签发）
│   ├── .env.example                 # 环境变量模板（密钥占位，.env 本身不提交 git）
│   ├── deploy.sh                    # 服务器一键部署脚本
│   ├── returnvision-backend/
│   │   └── Dockerfile               # JDK21 JRE 镜像
│   └── nginx/user_conf.d/
│       └── default.conf             # Nginx 静态托管 + /api 反代 + SSE 配置
├── docs/                            # 设计文档目录（所有方案文档）
│   ├── 00-开发执行指南.md
│   ├── 基础框架规范.md
│   └── 01~12 方案文档
├── AGENTS.md                        # AI 智能体入口文件（根目录）
└── README.md                        # 项目说明（根目录）
```

**技术栈**：
- 后端：Java 21 + Spring Boot 3.x + MyBatis-Plus + Maven
- 前端：Vue 3 + Vite
- 数据库：MySQL 8.0（宿主机系统级安装，不放入 Docker）
- 图片存储：腾讯云 COS
- OCR引擎：智谱OCR（引擎A）+ 阿里云面单OCR（引擎B）
- LLM：DeepSeek V4 Flash
- 飞书：lark-oapi Java SDK + Webhook 机器人
- 部署：Docker Compose（backend 容器内网 + frontend 容器对外）
- Web 服务器：Nginx（frontend 容器内置，静态托管 + /api 反向代理）
- HTTPS：Let's Encrypt 自动签发与续期（jonasal/nginx-certbot 镜像内置 Certbot）

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

**新增/优化功能时必读**（不读直接写代码视为违规）：
- docs/10-产品演进与功能可行性方案.md（产品视角缺口与演进路线）
- docs/11-开发流程规范.md（影响面评估清单 + 端到端流程）
- docs/12-前端设计规范.md（前端技术栈、设计 token、页面脚手架）

**部署相关**：
- deploy/ 目录（Docker Compose + Nginx + deploy.sh），线上部署与配置以其中文件为准

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

- 仓库地址：`git@github.com:JunXing-Tech/ReturnVision.git`（SSH，已配置免密推送）
- 提交时机：检查点通过后，用户说"继续"之前
- 提交信息格式：`步骤N：简要描述`
- 提交后推送到 origin/main

```
步骤完成 -> 🔍 代码自审 -> ⏸️ 暂停 -> 用户检查 -> 用户说"继续" -> git commit + push -> 下一步骤
```

### 代码自审机制（分级审查 v2，2026-07-18 更新）

**每步代码生成后、提交检查点前，必须进行自审。** 自审按任务规模分级，长任务执行更严格的清单。

#### 任务规模判定

| 规模 | 判定标准 | 审查级别 |
|------|---------|---------|
| S | 单文件小改（如修一个 bug、加一个字段） | L1 基础自审 |
| M | 多文件功能（如加一个 CRUD、一个新接口） | L2 增强自审 |
| L | 跨模块/长任务（如 F01/F03 这种完整功能） | L3 完整审查 |

#### L1 基础自审（6 项，所有任务必做）

- **审查范围**：本步新增代码 + 与新代码有交互的已有代码
- **检查项**：
  1. 编译是否通过
  2. 逻辑是否正确，边界情况是否考虑
  3. 异常处理是否完善（抛 BizException 子类，不手动 try-catch）
  4. 编码规范是否符合（注释、日志、构造器注入等）
  5. 新代码与已有代码的交互是否兼容（方法签名、返回类型、依赖关系）
  6. 业务流程是否完整顺畅
- **处理**：发现问题立即修复，在检查点说明中报告审查结果（通过/修复了哪些问题）

#### L2 增强自审（8 项，中任务必做）

L1 的 6 项 + 以下 8 项扩展（共 14 项）：

**编译与构建**
- 7. 无未使用的 import（grep 检查每个新文件）
- 8. 无重复 import

**逻辑与边界**
- 9. 关键路径有日志（每个 public 方法入口/出口有 log）
- 10. 边界情况清单验证（列出至少 3 个边界场景及处理方式）

**兼容与回归**
- 11. 新代码不破坏现有接口契约（对比响应 JSON 结构）
- 12. 权限配置正确（grep SecurityConfig 路径权限，逐一对照接口）

#### L3 完整审查（14 项，长任务必做）

L2 的 14 项 + 以下扩展，**必须产出审查报告**（见下方模板）：

**编译与构建（证据型）**
- 13. 后端编译通过（贴 `mvn compile` exit 0 证据）
- 14. 前端构建通过（贴 `npm run build` 无 ERROR 证据）

**逻辑与边界**
- 15. 异常分支有日志（grep 检查 catch 块）
- 16. 事务边界正确（@Transactional 用在多写操作，单写不用）

**兼容与回归**
- 17. DDL 变更不破坏历史数据（ALTER 加列允许 NULL，CREATE INDEX IF NOT EXISTS）
- 18. 回归检查：新增功能不破坏已有功能（至少验证 2 个已有接口）

**业务与场景**
- 19. 业务流程完整（画出端到端流程，标注每步实现位置）
- 20. 权限矩阵正确（列出 3 角色 × N 接口的访问矩阵）
- 21. 验收要点逐条验证（对照 docs 中的验收要点，逐条 ✅）

#### L3 审查报告模板（长任务提交前必须产出）

```markdown
## L3 代码审查报告（功能名）

### 第一组：编译与构建
- [x] 1. 后端编译通过（mvn compile exit 0）
- [x] 2. 前端构建通过（npm run build 无 ERROR）
- [x] 3. 无未使用 import（已 grep 检查 N 个新文件）
- [x] 4. 无重复 import

### 第二组：逻辑与边界
- [x] 5. 关键路径有日志（grep log.info/error 共 N 处）
- [ ] 6. 边界情况：
  - 场景1：空输入 -> 处理方式
  - 场景2：超长输入 -> 处理方式
  - 场景3：并发 -> 处理方式
- [x] 7. 异常分支有日志
- [x] 8. 事务边界正确

### 第三组：兼容与回归
- [x] 9. 现有接口契约不破坏
- [x] 10. DDL 变更兼容历史数据
- [x] 11. 权限配置正确（列出权限矩阵）
- [x] 12. 回归检查（已验证 xxx 接口）

### 第四组：业务与场景
- [x] 13. 业务流程完整（附流程图）
- [x] 14. 权限矩阵正确
- [x] 15. 验收要点逐条 ✅

### 修复的问题
- 问题1：xxx（已修复）
- 问题2：xxx（已修复）

### 残留风险
- 风险1：xxx（可接受/需用户确认）
```

#### 证据型审查要求（L2/L3 关键项）

关键检查项**必须用 grep/编译输出作为证据**，不能只说"已检查"。例如：

```bash
# 检查每个用 @AuditLog 的文件是否 import 了 AuditLog
grep -l "@AuditLog" src/main/java/.../controller/*.java | while read f; do
  grep -q "import.*AuditLog" "$f" || echo "缺失 import: $f"
done
```

**双向 grep 要求**（针对"注解是否覆盖"类检查）：

当检查"@XxxLog 注解是否覆盖所有应审计方法"时，必须做双向验证：
- **正向 grep**：统计有注解的方法数（确认数量）
- **反向 grep**：对照 docs 中的"应审计操作清单"，逐一确认每个 action 都有注解

```bash
# 反向 grep 示例：列出所有 @PostMapping/@DeleteMapping 但没有 @AuditLog 的方法
grep -B1 -E "@(Post|Put|Delete)Mapping" Controller.java | grep -v "@AuditLog" | grep "Mapping"
```

**DDL 变更升级验证**（针对"加字段/改字段"类变更）：

当给已存在的表加字段时，**必须同时在 schema.sql 加对应的 ALTER 语句**，不能只改 CREATE TABLE。因为 `CREATE TABLE IF NOT EXISTS` 对已存在的表不生效，已部署的环境不会自动加列。

```sql
-- 错误做法：只改 CREATE TABLE，已部署环境升级后报 "Unknown column"
CREATE TABLE IF NOT EXISTS ocr_log (
    ...
    field_confidence JSON,  -- 新字段
    ...
);

-- 正确做法：CREATE TABLE 改完后，再加一条 ALTER（continue-on-error 忽略重复列错误）
CREATE TABLE IF NOT EXISTS ocr_log (
    ...
    field_confidence JSON,  -- 新环境建表用
    ...
);
-- 已存在的表用 ALTER 升级
ALTER TABLE ocr_log ADD COLUMN field_confidence JSON COMMENT 'F05 字段级置信度';
```

**审查第 10 项"DDL 变更兼容历史数据"必须包含**：
- [ ] 新表结构是否兼容（加列允许 NULL）
- [ ] **已存在的表如何升级**（schema.sql 是否有对应的 ALTER 语句）
- [ ] **已部署环境如何应用**（重启后 schema.sql 是否会自动执行 ALTER）

**原则**：用户看到的每个检查点都应是"已自审 + 已修复 + 有证据"的状态。

#### API 存在性验证（L2/L3 必做）

调用已有类（如 AlertService、AuthUser、AlertLevel 等非 JDK/框架类）的方法前，**必须先 grep 确认方法签名存在**：

```bash
# 调用 AlertLevel.INFO 前先确认枚举值存在
grep "INFO" AlertLevel.java
# 调用 authUser.getDisplayName() 前先确认方法存在
grep "getDisplayName\|displayName" AuthUser.java
```

**理由**：编译器能发现这类问题，但提前 grep 能避免"写完才发现要返工"的浪费。

#### 验收要点前移（L3 必做）

不要等代码写完再对照验收要点，应该在**文档先行阶段**就把"验收清单"列出来（写在 docs 对应章节的"验收要点"小节），实现时逐条打勾：

```
文档先行阶段：docs/04 写"验收要点"清单
  -> 实现阶段：每完成一项代码就回到 docs 打 ✅
  -> L3 审查：逐条确认所有 ✅ 都有代码支撑
```

**反向验证**：L3 审查时，对 docs 验收清单的每一项，grep 确认代码中确实有对应实现（不能只看 docs 打了 ✅ 就信）。

#### 机制有效性分析报告（长任务完成后产出）

每个 L3 任务完成后，产出一份**机制有效性分析报告**，评估：
1. 新机制是否有效发现问题（对比"自审发现"vs"编译/用户发现"）
2. 哪些检查项实际起作用、哪些流于形式
3. 可优化的点（新增/删除/合并检查项）
4. 下一个 L3 任务的改进建议

---

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
8. **依赖注入**：默认用构造器注入（字段用 `final`，无需 `@Autowired`）；当类有多个构造方法或注入逻辑较复杂时，可用 `@Autowired` 字段注入

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

## 新增/优化功能的开发流程

> 本节是精简版，**详细规则见 [docs/11-开发流程规范.md](docs/11-开发流程规范.md)**。前端设计规范见 [docs/12-前端设计规范.md](docs/12-前端设计规范.md)。

**接到任何新增/优化需求时，先按下面顺序走（不直接写代码）**：

1. **变更影响面评估（开工前必过）** —— 对照 docs/11 第二章 8 项清单，逐项确认：数据库 / API / 后端 / 前端 / 文档 / 配置 / 部署 / 安全合规。凡出现"是"的项，先处理或先讨论。
2. **端到端步骤** —— 评估通过后按 docs/11 第三章 9 步执行：
   `澄清 → 评估 → 文档先行 → DDL/API → 后端 → 前端 → 联调 → 自审 → 检查点暂停+提交`
3. **文档先行** —— 改表先改 docs/05、`schema.sql`；改接口先改 docs/06；改前端设计 token 必须同步 docs/12。文档与代码不同步是历史痛点。
4. **门禁红线（必须先讨论，不得自行决定）**：改 DDL、改公共设计 token（`App.vue :root`）、引入新组件库/路由库/状态库、破坏 `ResponseResult` 契约。

> 与本文件「执行规则」（检查点 / Git 提交 / 自审 / 设计反思 / 编码约定）衔接，不重复其内容；冲突以本文件为准，细节以 docs/11 为准。

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
| 产品演进 -> docs/10-产品演进与功能可行性方案.md |
| 开发流程 -> docs/11-开发流程规范.md |
| 前端规范 -> docs/12-前端设计规范.md |
