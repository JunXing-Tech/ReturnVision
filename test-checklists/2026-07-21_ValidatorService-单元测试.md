# 待测试清单 — ValidatorService + WaybillValidator 单元测试 — 2026-07-21

> 类型：**前置式清单**（先写清单 → 自审 → 再生成代码 → 回填执行记录）
> 目标：把这两个纯逻辑 Service 的所有分支用参数化单元测试钉住，作为 Phase 2 首个真实测试落地示范

---

## 〇、红绿灯标准

- P0 项全绿 = 可提交、可发版
- P1 项全绿 = 可提交
- P2 项容许黄/红（标注跳过理由）

本清单目标：**P0 全绿 + P1 全绿**才算 Phase 2 第一步完成。

---

## 一、基本信息

- **关联文档**：
  - [docs/04 第 4.6 节](file:///d:/Project/ReturnVision/docs/04-详细方案设计.md)（WaybillValidator 设计 + 4.6.8 验收要点）
  - [ValidatorService.java](file:///d:/Project/ReturnVision/returnvision-backend/src/main/java/tech/jxing/returnvision/service/ValidatorService.java)（5 项校验规则源码）
  - [WaybillValidator.java](file:///d:/Project/ReturnVision/returnvision-backend/src/main/java/tech/jxing/returnvision/service/WaybillValidator.java)（8 家快递前缀映射）
- **改动范围**：新增 `src/test/java/tech/jxing/returnvision/service/` 目录及测试文件，不改 main 代码
- **git commit**：（执行后回填）
- **影响面评估**（对照 docs/11 第二章 8 项）：
  - [ ] 数据库变更（否，纯单元测试）
  - [ ] API 变更（否）
  - [ ] 后端接口契约（否，不改 main）
  - [ ] 后端业务逻辑（否，仅加测试）
  - [ ] 前端（否）
  - [ ] 文档（否）
  - [ ] 配置（否，复用现有 application-test.yml）
  - [ ] 安全合规（否）
  - 总结：**零 main 代码改动，零风险**

---

## 二、自动化测试项

### 2.1 ValidatorService.validate() 测试项

| 测试ID | 测试目标 | 测试类型 | 优先级 | 状态 | 备注 |
|--------|---------|---------|--------|------|------|
| AT-01 | 全部字段合法 → passed=true, need_manual=false, errors=空 | 单元 | P0 | ⬜ | happy path |
| AT-02 | 运单号为空 → errors 含"运单号为空", passed=false | 单元 | P0 | ⬜ | 必填项 |
| AT-03 | 运单号格式异常（如 5 位短号 "12345"）→ warnings 含"运单号格式异常", need_manual=true | 单元 | P0 | ⬜ | need_manual 触发条件 |
| AT-04 | 运单号纯数字 8 位（"12345678"）→ 无 warning | 单元 | P1 | ⬜ | 边界：8 位刚好合法 |
| AT-05 | 运单号纯数字 20 位 → 无 warning | 单元 | P1 | ⬜ | 边界：20 位刚好合法 |
| AT-06 | 运单号纯数字 7 位 → warning | 单元 | P1 | ⬜ | 边界：7 位越下界 |
| AT-07 | 运单号纯数字 21 位 → warning | 单元 | P1 | ⬜ | 边界：21 位越上界 |
| AT-08 | 运单号字母开头（"SF12345678"）→ 无 warning | 单元 | P1 | ⬜ | 3 字母前缀 + 8 位数字 |
| AT-09 | 运单号 4 字母前缀（"ABCD12345678"）→ warning | 单元 | P1 | ⬜ | 正则只允许 0-3 字母 |
| AT-10 | 收件人电话为空 → warnings 含"收件人电话为空" | 单元 | P0 | ⬜ | warning 路径 |
| AT-11 | 收件人电话合法手机号（"13812345678"）→ 无 warning | 单元 | P1 | ⬜ | 1[3-9]xxxxxxxxx |
| AT-12 | 收件人电话合法座机（"010-12345678"）→ 无 warning | 单元 | P1 | ⬜ | 区号-号码 |
| AT-13 | 收件人电话非法格式（"12345"）→ warning | 单元 | P1 | ⬜ | 既非手机也非座机 |
| AT-14 | 地址为空 → errors 含"收件人地址为空", passed=false | 单元 | P0 | ⬜ | 必填项 |
| AT-15 | 地址非空但 <5 字（"北京"）→ warnings 含"收件人地址过短" | 单元 | P1 | ⬜ | 边界：<5 |
| AT-16 | 地址刚好 5 字（"北京市朝阳区"）→ 无 warning | 单元 | P1 | ⬜ | 边界：=5 合法 |
| AT-17 | 收件人姓名为空 → errors 含"收件人姓名为空", passed=false | 单元 | P0 | ⬜ | 必填项 |
| AT-18 | 快递公司为空 → warnings 含"快递公司为空" | 单元 | P0 | ⬜ | warning 路径 |
| AT-19 | 字段值为 null → getString 兜底为 "", 走对应空值分支 | 单元 | P1 | ⬜ | getString(null) |
| AT-20 | 字段值有前后空格（" SF1234 "）→ trim 后正常匹配 | 单元 | P1 | ⬜ | trim 行为 |
| AT-21 | 多 error 并存 → errors.size() 反映数量, passed=false | 单元 | P1 | ⬜ | 多错误聚合 |
| AT-22 | 仅 warnings 无 errors → passed=true, need_manual 取决于是否含"运单号格式异常" | 单元 | P0 | ⬜ | need_manual 精确逻辑 |
| AT-23 | 有 warnings 但不含"运单号格式异常" → need_manual=false | 单元 | P0 | ⬜ | need_manual 精确逻辑反例 |
| AT-24 | 返回 Map 包含 4 个 key（passed/need_manual/errors/warnings） | 单元 | P2 | ⬜ | 契约完整性 |

### 2.2 WaybillValidator.validate() 测试项

| 测试ID | 测试目标 | 测试类型 | 优先级 | 状态 | 备注 |
|--------|---------|---------|--------|------|------|
| AT-25 | 顺丰 + "SF1234" → null（匹配） | 单元 | P0 | ⬜ | 对应 docs 4.6.8 验收要点 |
| AT-26 | 顺丰 + "JD1234" → 非 null warning（不匹配） | 单元 | P0 | ⬜ | docs 4.6.8 "明显不匹配" |
| AT-27 | 京东 + "JD1234" → null | 单元 | P1 | ⬜ | |
| AT-28 | EMS + "EMS1234" → null | 单元 | P1 | ⬜ | |
| AT-29 | 极兔 + "JT1234" → null | 单元 | P1 | ⬜ | |
| AT-30 | 大小写不敏感："顺丰" + "sf1234" → null | 单元 | P1 | ⬜ | toUpperCase 处理 |
| AT-31 | 顺丰冷运前缀 "SFC"："顺丰" + "SFC1234" → null | 单元 | P1 | ⬜ | 多前缀 Set 验证 |
| AT-32 | 未登记的快递公司（"小快递"）+ 任意运单号 → null（跳过） | 单元 | P0 | ⬜ | 避免误报 |
| AT-33 | 运单号为空 → null（跳过） | 单元 | P1 | ⬜ | 空值跳过 |
| AT-34 | 公司为空 → null（跳过） | 单元 | P1 | ⬜ | 空值跳过 |
| AT-35 | 运单号 null → null（跳过，不 NPE） | 单元 | P1 | ⬜ | null 安全 |
| AT-36 | 不匹配时返回的 warning 字符串包含运单号与期望前缀 | 单元 | P2 | ⬜ | 字符串内容契约 |

---

## 三、人工必须验证项

| 验证ID | 验证点 | 人工理由 | 优先级 | 状态 | 负责人 | 验证时间 |
|--------|-------|---------|--------|------|--------|---------|

> 本清单为纯逻辑单元测试，**无人工验证项**。所有边界都能用代码表达，不依赖外部服务。

---

## 四、回归选择（AI 静态影响分析 + 人工补充）

| 影响模块 | 关联原因 | 应回归测试项 | 已跑 |
|---------|---------|------------|------|
| 上传主流程 | ValidatorService 被 UploadController 调用 | 当前无 upload 流程集成测试，本清单结束后不影响 | — |
| WaybillValidator 调用方 | 被 UploadController F11 埋点处调用 | 本次只测 WaybillValidator 自身，调用方不动 | — |

> 本清单不改 main 代码，无需回归已有功能。只需保证新测试自己跑绿即可。

---

## 五、自审记录

### 5.1 角色切换通读

本改动属"中改动"（新增 2 个测试类、覆盖 36 个测试点），按 README §5.1 走三轮通读。

**开发者视角**：
- 我读了 ValidatorService 和 WaybillValidator 全部源码，每个分支都列出了对应测试项。
- 是否漏分支？回头看 ValidatorService.validate():
  - 步骤1 运单号：空 / 格式异常 / 合法 → AT-02/AT-03/AT-01 ✅
  - 步骤2 电话：空 / 手机 / 座机 / 异常 → AT-10/AT-11/AT-12/AT-13 ✅
  - 步骤3 地址：空 / 过短 / 合法 → AT-14/AT-15/AT-16 ✅
  - 步骤4 姓名：空 → AT-17 ✅
  - 步骤5 公司：空 → AT-18 ✅
  - 步骤6 汇总：passed / need_manual 精确逻辑 → AT-22/AT-23 ✅
- WaybillValidator.validate(): 干 / 公司未登记 / 空值 / null / 多前缀 / 大小写 → AT-25~AT-36 ✅
- 漏了什么？`getString` 的 trim 行为没单独测 → 补 AT-20 ✅
- `Map` 返回结构完整性 → 补 AT-24 ✅

**测试者视角**：
- 跟着清单我能跑吗？每个测试项都明确了输入和期望，可以照着写参数化测试。
- 边界值清单是否清晰？AT-04~AT-07 把运单号 7/8/20/21 位的边界都列了 ✅
- 看得出断言点是什么吗？"passed/need_manual/errors/warnings"四元组的期望很明确 ✅

**用户视角**：
- 用户视角对本清单意义不大（测试本身不直接服务用户），但有一个点：如果运单号格式异常导致 need_manual=true，用户会在前端看到"需人工确认"提示。AT-03 是这个链路的关键节点，已列 P0 ✅
- 快递公司未登记（AT-32）也会影响用户体验（不让小快递被误判），已列 P0 ✅

**通读后补充**：
- 已补：AT-20（trim 行为）、AT-24（Map 结构契约）
- 无其他遗漏

### 5.2 固定检查表（5 项打勾）

- [x] 对照 docs 验收要点：docs/04 4.6.8 WaybillValidator 5 条验收要点 → AT-25/AT-26/AT-32/normal covered ✅
  - 同一运单号已 synced 拦截 → 不在本清单范围（属 UploadController，留待后续）
  - 已 confirmed 时 warning 不阻断 → 不在本清单范围（同上）
  - 前缀不匹配返回 warning → AT-26 ✅
  - 正常运单号不受影响 → AT-25/AT-27~AT-31 ✅
  - 批量重复处理 → 不在本清单范围（属 UploadController 批量接口）
- [x] 权限矩阵：本清单不涉权限，无 Controller 接口测试，N/A
- [x] DDL 变更：本清单无 DDL 变更，N/A
- [x] 外部依赖全部 mock：ValidatorService/WaybillValidator 是纯逻辑，零外部依赖，无需 mock
- [x] 人工验证项理由充分：本清单无人工项

### 5.3 AI 辅助反向核对

执行：grep `docs/04 4.6.8 验收要点` 5 条逐一比对清单：
- "同一运单号已 synced 拦截返回 2002" → 属 UploadController 异常路径，不在本清单 ✅（明确划归后续）
- "已 confirmed 时 warning 不阻断" → 同上 ✅
- "前缀明显不匹配返回 warning" → AT-26 ✅
- "正常运单号不受影响" → AT-25/AT-27~AT-31 ✅
- "批量重复某条标记 error" → 属批量接口，不在本清单 ✅

缺口报告：5 条验收要点中 3 条归属本清单、2 条归属 UploadController（后续清单处理），归属划分清晰，无遗漏。

### 5.4 自审结论

✅ 通过（5.1 通读补了 2 项 AT-20/AT-24，5.2 五项全勾，5.3 缺口已明确归属）

---

## 六、执行记录

**生成时间**：2026-07-21 13:14
**测试代码**：
- [ValidatorServiceTest.java](file:///d:/Project/ReturnVision/returnvision-backend/src/test/java/tech/jxing/returnvision/service/ValidatorServiceTest.java)
- [WaybillValidatorTest.java](file:///d:/Project/ReturnVision/returnvision-backend/src/test/java/tech/jxing/returnvision/service/WaybillValidatorTest.java)

**实测证据**（mvn test 输出）：
```
[INFO] Running tech.jxing.returnvision.SanityTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running tech.jxing.returnvision.service.ValidatorServiceTest
[INFO] Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running tech.jxing.returnvision.service.WaybillValidatorTest
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
[INFO] Results:
[INFO] Tests run: 54, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

| 测试ID | 执行结果 | 说明 |
|--------|---------|------|
| AT-01 | ✅ | happy path 通过 |
| AT-02 | ✅ | 运单号空被识别为 error |
| AT-03 | ✅ | 5 位短号触发 need_manual |
| AT-04/AT-05/AT-08 | ✅ | 用参数化压成 3 个用例,合法边界全覆盖 |
| AT-06/AT-07/AT-09 | ✅ | 用参数化压成 3 个用例,非法边界全覆盖 |
| AT-10 | ✅ | 电话为空 warning |
| AT-11/AT-12 | ✅ | 参数化覆盖手机/座机 4 个合法样本 |
| AT-13 | ✅ | 3 个非法电话样本全部 warning |
| AT-14 | ✅ | 地址为空 error |
| AT-15 | ✅ | "北京" 2 字触发过短 warning |
| AT-16 | ✅ | 修正了清单中的 5 字样本(原"北京市朝阳区"是 6 字) |
| AT-17 | ✅ | 姓名为空 error |
| AT-18 | ✅ | 公司为空 warning |
| AT-19 | ✅ | null 字段兜底走空值分支 |
| AT-20 | ✅ | " SF12345678 " 经 trim 后合法 |
| AT-21 | ✅ | 3 个错误同时存在,size=3 |
| AT-22 | ✅ | 运单号格式异常触发 need_manual |
| AT-23 | ✅ | 电话/公司为空不触发 need_manual |
| AT-24 | ✅ | Map 含 4 个 key,size=4 |
| AT-25/AT-27/AT-28/AT-29 | ✅ | 参数化覆盖 8 家快递命中前缀 |
| AT-26 | ✅ | 顺丰+JD 不匹配返回 warning 且含期望前缀 |
| AT-30 | ✅ | "sf1234" 小写经 toUpperCase 命中 |
| AT-31 | ✅ | 顺丰冷运 SFC 前缀命中 |
| AT-32 | ✅ | 未登记公司(小快递/百世/天天/未知)跳过 |
| AT-33/AT-34/AT-35 | ✅ | 用 CsvSource 空值/null 6 个组合跳过,无 NPE |
| AT-36 | ✅ | warning 字符串含运单号/公司/期望前缀 |

**参数化压缩效果**：36 个测试点压缩到 53 个测试方法(参数化样本会展开多个测试方法,实际比 36 多),代码体积控制在 ~230 行。无样本臃肿。

## 七、本清单的元目标观察

1. **清单先写 + 自审 + 再写代码的流程**：实测效果显著。只在写代码时遇到 2 个问题(未引入 Test import / 未用 Nested/NullAndEmptySource import),清单自审都无法预先发现这种"代码细节级"问题,但所有**业务边界**问题在清单阶段就一次性想清了,代码生成阶段几乎零返工。
2. **AT-16 自填错样本** 被代码生成阶段抓到——清单自审的局限:通读时我写"刚好 5 字"配的样本却是 6 字。说明清单+代码双重核对很必要。
3. **参数化测试** 对纯逻辑 Service 极度有效:单元代码量没膨胀但覆盖密度高。
4. **AI 辅助反向核对(5.3)** 在本清单作用有限(因为 docs 验收要点只有 5 条,且部分归属 UploadController),但建立了"验收要点对照"的肌肉记忆,后续 OcrCrossValidatorService 这种验收要点多的清单会更见效。
5. **结论**：清单先行流程在 Phase 2 第一份真实任务上验证有效,可推广。

---

## 七、本清单的元目标观察（不会强制每份清单都做，仅本清单观察一次）

- 清单先写 + 自审 + 再写代码的流程是否真能在 Phase 2 节省返工？→ 待代码生成后对比
- 36 个测试点是否过多？→ 待写完后看测试代码体积，若臃肿则下清单考虑用参数化压减