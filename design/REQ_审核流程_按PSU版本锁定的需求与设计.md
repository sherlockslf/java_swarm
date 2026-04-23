# 审核流程需求与设计文档（按 PSU 版本锁定）

## 0. 目标
固化你描述的审核机制，明确：
- 谁在什么时候能改什么
- 锁定点在哪里
- 退回时回到哪里、由谁改
- 审核通过后“该 PSU 版本结束”的含义（哪些内容成为最终快照）

> 说明：权限体系暂不作为第一优先级，但流程状态必须由后端硬控制，前端只负责展示与禁用按钮。

---

## 1. 流程角色
- 开发（DEVELOPER）：创建 PSU、维护 Schema、审核版本、生成代码、登记 Git
- 产品/运营（BUSINESS）：编排提示词与参数注入、维护测试集、执行回归、提交审核

---

## 2. 流程对象（要区分，否则实现会混乱）

|对象|含义|是否可版本化|
|---|---|---|
|PSU|一个“提示词服务单元”的资产容器|是（通过 schemaVersion、compositionRevision、reviewVersion 关联）|
|Schema|入参/出参结构|是（schemaVersion）|
|Composition（编排）|业务侧的提示词文本+变量注入+顺序（ComposeSpec）|是（revisionNo 快照）|
|TestDataset|测试集（用例集合）|建议是（至少在提交审核时快照 datasetId 列表或用例内容）|
|VersionReview|审核记录（开发审核动作）|是（major/minor/patch）|

---

## 3. 状态机设计（推荐）

### 3.1 编排状态（Composition.status）
- DRAFT：业务编辑中（可保存、可测试）
- SUBMITTED：业务提交（业务锁定不可改）
- DEV_REVIEWING：开发审核中
- APPROVED：审核通过（允许代码生成最终版、登记 Git）
- REJECTED：驳回（需要 rejectionType）

### 3.2 审核记录状态（VersionReview.status）
- PENDING：待审核
- APPROVED：通过
- REJECTED：驳回

> 建议：Composition.status 与 VersionReview.status 同步推进，但职责不同：  
> - Composition.status 管“编排是否可编辑/是否锁定”  
> - VersionReview.status 管“开发是否给出结论/结论是什么”

---

## 4. 流程步骤（逐步 + 锁定点）

### 4.1 开发创建阶段
1) 开发创建 PSU
2) 开发创建/更新 Schema（生成 schemaVersion）
3) 开发可选创建基础 Prompt 片段/默认编排模板（便于业务起步）

锁定点：
- Schema 的编辑权归开发侧（业务只读）

### 4.2 业务编排与测试阶段
1) 业务进入 Prompt Composer（Composition=DRAFT）
2) 完成提示词编排与变量注入
3) 选择测试集批量运行回归
4) 回归通过后，点击“提交审核”

锁定点（关键）：
- 点击提交审核后：Composition 进入 SUBMITTED，业务侧不可再编辑编排内容
- 系统生成 CompositionRevision 快照（用于后续审核与复现）

### 4.3 开发审核阶段
1) 开发在“审核列表”中看到待审核的 PSU 版本
2) 开发进入审核详情（只读查看编排快照、schemaVersion、测试结果摘要）
3) 开发重点关注（按你描述）：
   - 代码生成后落到具体服务/数据库层面是否可执行
   - schema 是否满足落地（字段命名、类型、是否缺关键字段）
   - 是否需要补充 PSU 配置（参数拼装逻辑）

#### 4.3.1 审核通过
- VersionReview.status → APPROVED
- Composition.status → APPROVED
- 允许执行：
  - 生成代码最终版（绑定 compositionRevision + schemaVersion）
  - 记录 gitCommitHash（平台只做登记，不接管发布）

#### 4.3.2 审核驳回（两类）
驳回必须带：
- rejectionReason（原因）
- rejectionType（驳回类型）

**A. BACK_TO_DEV：退回开发修改**
- 触发：schema/PSU 配置问题、参数拼装缺失、落地服务需要结构补齐等
- 结果：
  - 业务侧仍不可修改已提交编排（保持审计一致）
  - 开发修 Schema/PSU 后，若 schemaVersion 发生变化：
    - 系统提示“编排可能需要重新适配”
    - 推荐由业务侧基于新 schemaVersion 生成新 DRAFT 再提审

**B. BACK_TO_BIZ：退回业务重编排**
- 触发：提示词话术/注入顺序/编排错误（而非结构缺失）
- 结果：
  - 系统基于最近一次 SUBMITTED 快照复制生成新 DRAFT（新 revision）
  - 业务侧重新调试后再提交

---

## 5. 必须固化的“版本结束”定义

你提到“开发审核完毕之后，此 psu 版本就算结束了”，建议系统将以下内容固化成“最终产物快照”：
1) psuId
2) schemaVersion（入参/出参）
3) compositionRevisionNo（编排快照）
4) 使用的 datasetId 列表（或用例快照 hash）
5) codeContent（生成代码内容）+ gitCommitHash（入库登记）

> 这 5 项决定了“可复现”和“可追溯”，后续你即使不做复杂权限，也能做到内部协作不扯皮。

---

## 6. 接口建议（与流程绑定）

### 6.1 业务侧
- `POST /api/compositions/submit?psuId={psuId}`

### 6.2 开发侧
- `POST /api/versions/{reviewId}/review`（approved/rejectionReason/rejectionType）
- `GET /api/versions/{psuId}/code`（基于 APPROVED 快照生成）
- `POST /api/versions/{reviewId}/git-commit`（登记 commitHash）

---

## 7. 验收标准（流程）
- 业务提交后无法再修改编排（前端禁用 + 后端拦截）
- 开发可对已提交版本进行通过/驳回
- 驳回类型不同会导致不同的回退路径（退回开发 vs 退回业务）
- 审核通过后能生成代码并登记 Git，且该版本“结束”不可再被覆盖
