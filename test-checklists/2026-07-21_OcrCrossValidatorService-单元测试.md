# 待测试清单 — OcrCrossValidatorService 双引擎交叉验证单元测试 — 2026-07-21

> 类型：**前置式清单**（先写清单 → 自审 → 再生成代码 → 回填执行记录）
> 目标：覆盖核心业务逻辑的 5 种仲裁分支 + 字段级比对规则 + 阿里云置信度阈值（80）仲裁 + 飞书埋点写入行为
> 这是 AGENTS.md 明确要求"步骤6 必须暂停"的核心业务点

---

## 〇、红绿灯标准

- P0 项全绿 = 可发版
- P1 项全绿 = 可提交

本清单目标：**P0 全绿 + P1 全绿**。涉及核心业务,任何一项不达标都不通过。

---

## 一、基本信息

- **关联文档**：
  - [docs/04 第 4.1.1 节 双引擎交叉验证策略](file:///d:/Project/ReturnVision/docs/04-详细方案设计.md)（一致→自动采用/部分差异→高亮差异/完全不一致→转人工）
  - [docs/04 第 4.1.6 节 交叉验证引擎](file:///d:/Project/ReturnVision/docs/04-详细方案设计.md)（设计示例代码）
  - [OcrCrossValidatorService.java](file:///d:/Project/ReturnVision/returnvision-backend/src/main/java/tech/jxing/returnvision/service/OcrCrossValidatorService.java)
  - [OcrLogWriter.java](file:///d:/Project/ReturnVision/returnvision-backend/src/main/java/tech/jxing/returnvision/ocrstats/OcrLogWriter.java)（埋点接口签名）
- **改动范围**：新增 `OcrCrossValidatorServiceTest.java`,零 main 代码改动
- **git commit**：（执行后回填）
- **影响面评估**（对照 docs/11 第二章 8 项）：
  - [ ] 数据库变更（否）
  - [ ] API 变更（否）
  - [ ] 后端接口契约（否）
  - [ ] 后端业务逻辑（否,只加测试）
  - [ ] 前端（否）
  - [ ] 文档（否）
  - [ ] 配置（否）
  - [ ] 安全合规（否）
  - 总结：**零 main 代码改动,测试隔离。需要 Mockito mock 3 个依赖（OcrZhipuService/OcrAliyunService/OcrLogWriter）**

---

## 二、自动化测试项

### 2.1 五种仲裁分支(核心,P0)

| 测试ID | 测试目标 | 测试类型 | 优先级 | 状态 | 备注 |
|--------|---------|---------|--------|------|------|
| AT-01 | 双引擎均成功且全部字段一致 → action=accept, source=cross_validated, confidence=high | 单元 | P0 | ⬜ | happy path(docs 4.1.1 "自动采用(高置信)") |
| AT-02 | 双引擎均成功但运单号冲突 → action=manual, reason="运单号双引擎结果不一致", confidence=low | 单元 | P0 | ⬜ | docs 4.1.1 "完全不一致→转人工录入"; 关键字段不可自动仲裁 |
| AT-03 | 双引擎均成功且非运单号字段有差异 → action=review, source=cross_validated, confidence=medium, diff_fields 非空 | 单元 | P0 | ⬜ | docs 4.1.1 "部分差异→高亮差异供客服核对" |
| AT-04 | 智谱失败阿里云成功 → action=accept, source=aliyun_only, confidence=medium, note 含"智谱失败" | 单元 | P0 | ⬜ | 单引擎降级 |
| AT-05 | 阿里云失败智谱成功 → action=accept, source=zhipu_only, confidence=medium, note 含"阿里云失败" | 单元 | P0 | ⬜ | 单引擎降级反例 |
| AT-06 | 双引擎均失败 → action=manual, reason="双引擎均识别失败", confidence=low, data 为空 Map | 单元 | P0 | ⬜ | docs 4.1.1 "完全失败→转人工录入" |

### 2.2 字段比对规则(P0/P1)

| 测试ID | 测试目标 | 测试类型 | 优先级 | 状态 | 备注 |
|--------|---------|---------|--------|------|------|
| AT-07 | 两引擎某字段一致 → chosenData 取该值 | 单元 | P0 | ⬜ | 一致分支 |
| AT-08 | 智谱有值、阿里云空 → 取智谱值,不算 diff | 单元 | P1 | ⬜ | 一侧空值分支1 |
| AT-09 | 阿里云有值、智谱空 → 取阿里云值,不算 diff | 单元 | P1 | ⬜ | 一侧空值分支2 |
| AT-10 | 两引擎均空 → chosenData 该字段="",不算 diff | 单元 | P1 | ⬜ | 两均空分支 |
| AT-11 | 两引擎不一致(非运单号)且阿里云 prob≥80 → chosenData 取阿里云值,chosen=aliyun | 单元 | P0 | ⬜ | 置信度仲裁的关键阈值(=80 也算阿里云) |
| AT-12 | 两引擎不一致(非运单号)且阿里云 prob<80 → chosenData 取智谱值,chosen=zhipu | 单元 | P0 | ⬜ | 置信度仲裁的关键阈值反例 |
| AT-13 | 两引擎不一致 → diff_detail 含 zhipu/aliyun/aliyun_prob/chosen 四子字段 | 单元 | P1 | ⬜ | 差异记录结构契约 |
| AT-14 | 多字段不一致(非运单号) → diff_fields 包含所有冲突字段 | 单元 | P1 | ⬜ | 多差异聚合 |

### 2.3 飞书埋点行为(P1)

| 测试ID | 测试目标 | 测试类型 | 优先级 | 状态 | 备注 |
|--------|---------|---------|--------|------|------|
| AT-15 | 全部一致 → writeSimpleLog(zhipu, true) + writeLog(aliyun, true, fieldConfidence非空) 各 1 次 | 单元 | P1 | ⬜ | 双成功埋点 |
| AT-16 | 智谱失败阿里云成功 → writeSimpleLog(zhipu, false) + writeLog(aliyun, true) 各 1 次 | 单元 | P1 | ⬜ | 单失败埋点 |
| AT-17 | 阿里云失败智谱成功 → writeSimpleLog(zhipu, true) + writeSimpleLog(aliyun, false) 各 1 次 | 单元 | P1 | ⬜ | 单失败埋点反例 |
| AT-18 | 双失败 → writeSimpleLog 2 次,均 success=false, errorMsg 非空 | 单元 | P1 | ⬜ | 双失败埋点 |
| AT-19 | 运单号冲突 → 双成功埋点(各 success=true),但 action=manual | 单元 | P1 | ⬜ | 冲突但已是双成功路径 |

### 2.4 边界与契约(P0/P1)

| 测试ID | 测试目标 | 测试类型 | 优先级 | 状态 | 备注 |
|--------|---------|---------|--------|------|------|
| AT-20 | 阿里云 prob 刚好 80 → 取阿里云(≥80 含等号边界) | 单元 | P0 | ⬜ | `prob >= CONFIDENCE_THRESHOLD` 边界 |
| AT-21 | 阿里云 prob 刚好 79 → 取智谱(越下界) | 单元 | P1 | ⬜ | 阈值左侧边界 |
| AT-22 | resultAliyun 没有 confidence 字段 → 不报错,冲突字段按 prob=0 处理(取智谱) | 单元 | P1 | ⬜ | confidence 缺失兜底 |
| AT-23 | imageUrl 传入 mock 验证 → 两个 OCR Service 都被调用,参数为同一 URL | 单元 | P1 | ⬜ | 验证调用契约 |
| AT-24 | FIELDS 9 个字段全部被处理(验证字段清单完整性) | 单元 | P0 | ⬜ | 默认全部应比对 |
| AT-25 | extractFieldConfidence 只提取 prob>0 的字段 | 单元 | P1 | ⬜ | F05 埋点细节 |

---

## 三、人工必须验证项

| 验证ID | 验证点 | 人工理由 | 优先级 | 状态 | 负责人 | 验证时间 |
|--------|-------|---------|--------|------|--------|---------|

> 本清单为纯单元测试(mock 全部外部依赖),无人工验证项。

---

## 四、回归选择（AI 静态影响分析 + 人工补充）

| 影响模块 | 关联原因 | 应回归测试项 | 已跑 |
|---------|---------|------------|------|
| UploadController 主流程 | 调用 OcrCrossValidatorService.dualEngineOcr | 本次不改 main,无需回归 | — |
| 现有 ValidatorServiceTest/WaybillValidatorTest | 测试体系同源,会用同一 `mvn test` | 应一起跑绿 | ⬜ 待 mvn test |

---

## 五、自审记录

### 5.1 角色切换通读

本改动属"中改动"(核心业务逻辑、跨多分支、需 mock),按 README §5.1 走三轮完整通读。

**开发者视角**：
- 5 种仲裁分支已全覆盖:一致/部分差异/运单号冲突/单失败/双失败 → AT-01~AT-06 ✅
- 字段比对 4 种状态(均一致/一侧空/均空/不一致):AT-07~AT-14 ✅
- 置信度阈值 80 边界:AT-11/AT-12/AT-20/AT-21 ✅
- 埋点行为:AT-15~AT-19 ✅
- FIELDS 字段清单完整性:AT-24 ✅
- 漏了什么?...思考中...
  - `extractFieldConfidence` 只针对阿里云结果,智谱不提取 → AT-25 已含
  - mock 验证 imageUrl 透传 → AT-23 ✅
  - 想到 CompletableFuture 的特殊性:`supplyAsync`用默认 ForkJoinPool,异步执行。若 OCR mock 同步返回,CompletableFuture 仍走异步线程。要不要测"一定收敛"行为? → 这是被测代码的实现细节,不是契约;只要 `dualEngineOcr` 返回正确即可,并发性本身不测。不补测试。

**测试者视角**：
- 跟着清单能跑吗? 每个测试项都给出 mock 行为 + 期望返回四元组(action/source/confidence/diff_fields),可照着写。
- AT-15~AT-19 埋点验证用的什么手段? 用 `Mockito.verify(ocrLogWriter, times(N)).writeSimpleLog(...)` 即可。
- AT-24 "FIELDS 9 字段完整性" 怎么验证? 给两个 resultZhipu/resultAliyun,让 9 个字段各有不同值差异,断言 diff_fields 含 9 字段(若全设为非运单号字段差异)。等等,FIELDS 含 waybill_no,若它有差异会走 AT-02 manual 分支,不会走 review。所以 AT-24 应该把 9 字段全设为"两引擎一致但非空"来验证 chosenData 含 9 字段。或者固定一个差异字段(非 waybill_no),验证 diff_fields 不会缺失某字段。**修订 AT-24**:验证 chosenData 含全部 9 个 FIELDS 字段(无论是否一致),证明没有漏处理某字段。

**用户视角**：
- 用户感知层面:用户上传一张面单,得到"自动采用"/"需复核"/"转人工"3 种结果。这 3 种结果对应的 action = accept / review / manual 都已覆盖(at AT-01~AT-06)。✅
- 双引擎降级 note(用户看到"智谱失败,仅阿里云结果")→ AT-04/AT-05 已含。
- 想到盲点:**布尔类型的 success 字段不需要用户感知,但 confidence 字段("high"/"medium"/"low")会被前端用于显示不同 UI 提示**。这3个 confidence 值分别在 AT-01/Accept|AT-03~AT-05/AT-02|AT-06 中体现 ✅

**通读后补充**：
- 修订 AT-24:从"9字段都被处理"改为更具体的"chosenData 含 9 个 FIELDS 字段,且无重复无遗漏"
- 补 AT-26:验证 3 种 confidence 取值("high"/"medium"/"low")分布正确
- 不需要补并发测试,CompletableFuture 实现细节不测

### 5.2 固定检查表(按需勾选,据 v1.1 规范)

- [x] 对照 docs 验收要点:docs/04 没有独立 4.1.6 验收要点节,但 4.1.1 策略图三档(一致/部分差异/完全不一致)分别对应 AT-01/AT-03/AT-02 ✅。F05 埋点验收要点(4.10.x)归属后续集成测试,本清单只验证埋点被正确调用。
- [ ] 权限矩阵:N/A(本 Service 不是 Controller,无权限)
- [ ] DDL 变更:无,N/A
- [x] 外部依赖全部 mock:OcrZhipuService / OcrAliyunService / OcrLogWriter 全部 Mockito mock,零真实调用 ✅
- [x] 人工验证项理由:本清单无人工项,N/A

### 5.3 AI 辅助反向核对(按需触发)

判据:docs 验收要点不足 10 条且属单一模块服务层 → **不需触发 5.3**。已在 5.2 中对照 4.1.1 策略图三档逐一映射。

### 5.4 自审结论

✅ 通过(5.1 通读补了 AT-26 + 修订 AT-24,5.2 相关项全勾,5.3 不需触发)

---

## 六、执行记录

**生成时间**:2026-07-21 13:33
**测试代码**:[OcrCrossValidatorServiceTest.java](file:///d:/Project/ReturnVision/returnvision-backend/src/test/java/tech/jxing/returnvision/service/OcrCrossValidatorServiceTest.java)

**实测证据**(mvn test 输出):
```
[INFO] Running tech.jxing.returnvision.service.OcrCrossValidatorServiceTest
[INFO] Tests run: 24, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.290 s
[INFO] Running tech.jxing.returnvision.service.ValidatorServiceTest
[INFO] Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running tech.jxing.returnvision.service.WaybillValidatorTest
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
[INFO] Results:
[INFO] Tests run: 78, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

回归验证:本次新增 24 个测试,ValidatorServiceTest(28)/WaybillValidatorTest(25)/SanityTest 仍全绿,**零回归破坏**。

| 测试ID | 执行结果 | 说明 |
|--------|---------|------|
| AT-01 | ✅ | 全一致 accept/cross_validated/high,且无 diff_fields |
| AT-02 | ✅ | 运单号冲突 manual/low,reason 含"运单号双引擎结果不一致" |
| AT-03 | ✅ | 非运单差异 review/cross_validated/medium |
| AT-04 | ✅ | 智谱失败 aliyun_only,note 含"智谱失败" |
| AT-05 | ✅ | 阿里云失败 zhipu_only,note 含"阿里云失败" |
| AT-06 | ✅ | 双失败 manual/low,data 空 Map |
| AT-07 | ✅ | 一致字段取该值 |
| AT-08 | ✅ | 智谱有值阿里云空取智谱,无 diff |
| AT-09 | ✅ | 阿里云有值智谱空取阿里云,无 diff |
| AT-10 | ✅ | 两均空字段="",无 diff |
| AT-11/AT-20 | ✅ | 合并为 1 个测试,prob=80 边界取阿里云 |
| AT-12/AT-21 | ✅ | 合并为 1 个测试,prob=79 越下界取智谱 |
| AT-13 | ✅ | diff_detail 含 zhipu/aliyun/aliyun_prob/chosen 四子字段 |
| AT-14 | ✅ | 多非运单差异 diff_fields 含 3 字段 |
| AT-15 | ✅ | 全一致埋点:writeSimpleLog(zhipu,true) + writeLog(aliyun,true,非空 fieldConfidence) |
| AT-16 | ✅ | 智谱失败埋点:writeSimpleLog(zhipu,false) + writeLog(aliyun,true,非空) |
| AT-17 | ✅ | 阿里云失败埋点:writeSimpleLog(zhipu,true) + writeSimpleLog(aliyun,false) |
| AT-18 | ✅ | 双失败埋点:2 次 writeSimpleLog 均 success=false,且从未调 writeLog |
| AT-19 | ✅ | 运单号冲突埋点:仍写双成功(success=true),action=manual |
| AT-22 | ✅ | 阿里云无 confidence,冲突按 prob=0 处理取智谱,chosen=zhipu |
| AT-23 | ✅ | 两个 OCR Service 都被调 1 次,参数同 imageUrl |
| AT-24 | ✅ | chosenData 含全部 9 个 FIELDS 字段 |
| AT-25 | ✅ | extractFieldConfidence 只保留 prob>0 字段,prob=0 的 rec_name 不进 fieldConfidence |
| AT-26 | ✅ | 1 个测试覆盖 5 个场景:high/low×2/medium×2,confidence 分布正确 |

**实测统计**:清单 26 项 → 测试方法 24 个(AT-11/AT-20 合一、AT-12/AT-21 合一)。全部绿。

---

## 七、本清单的元目标观察(与 v1.1 调整后的预期对比)

- **预测**:v1.1 调整后,OcrCrossValidatorService 这种复杂业务应能真正发挥清单先行的优势(5.3 不触发判断及时,5.1 通读产出新项 AT-26)。
- **观察**:
  - 5.3 不触发的判断对了吗? → 待执行后评估
  - 5.1 通读产出 AT-26 修订 AT-24 是否真的有价值? → 待执行后评估
  - 复杂业务能否撑起完整 6 节仪式? → 待执行后评估