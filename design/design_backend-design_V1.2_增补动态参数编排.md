# PSU全生命周期管理平台 - 后端设计文档 V1.2（增补：动态参数编排容器 & 审核锁定）

> 说明：本文档在原《backend-design.md》基础上增补：  
> 1) 动态参数编排容器（Composition）后端落库与接口；  
> 2) 按你描述的审核阶段“业务锁定/退回”状态机；  
> 3) 前后端联调所需的关键接口约束（请求/响应字段）。

---

## 1. 技术栈选型（不变）
- Spring Boot 3.2.0 / Java 17 / Maven
- MySQL 8.0 / Redis
- Spring Security（内部系统可先弱化权限，但接口仍需预留 userId 归属与审计）
- Spring Data JPA / Jackson

---

## 2. 新增模块：compose-module（动态参数编排容器）

### 2.1 设计目标
后端要解决三件事：
1) **保存**业务侧完成的“提示词文本 + 变量注入 + 组装顺序/注入顺序”（ComposeSpec）
2) **锁定与快照**：提交审核后不可编辑，且可回放审计
3) **测试接口**：使用“当前编排 + 指定测试集”进行在线调试（单条/批量）

### 2.2 核心数据模型（概念）

#### 2.2.1 Composition（可编辑编排）
- 作用：保存业务当前的“草稿态”编排（可反复保存）
- 典型字段：
  - psuId
  - status：DRAFT / SUBMITTED / DEV_REVIEWING / APPROVED / REJECTED
  - schemaVersion：提交时绑定（避免后续 schema 变化导致不可复现）
  - content：编辑器原文
  - specJson：ComposeSpec（tokens、injectionPlan、assembledFragments 等）
  - createdBy / updatedBy / createdAt / updatedAt

#### 2.2.2 CompositionRevision（不可变快照）
- 作用：在“提交审核/审核通过/驳回”等节点固化快照
- 字段：
  - compositionId
  - psuId
  - revisionNo（自增）
  - statusAtThatTime
  - schemaVersionAtThatTime
  - specJsonSnapshot
  - datasetSnapshot（可选：提交时选择的 datasetId 列表，或直接快照用例）
  - createdBy / createdAt

> 说明：你现有的 `VersionReview` 偏“审核记录”，建议 **CompositionRevision 负责“编排内容快照”**，`VersionReview` 负责“审核流程记录/代码生成记录”。两者关联即可。

---

## 3. 审核流程落地（按你的描述固化）

### 3.1 状态机（建议以后端为准）

**Composition.status**
- DRAFT：业务编辑中（可保存/可测试）
- SUBMITTED：业务提交审核（业务侧锁定，不可再修改）
- DEV_REVIEWING：开发进入审核（可生成代码预览、做落地检查）
- APPROVED：审核通过（允许代码生成最终版、登记 Git）
- REJECTED：驳回（需注明驳回原因、驳回类型）

**驳回类型（建议枚举）**
- BACK_TO_DEV：退回开发修改 PSU/Schema/拼装逻辑（业务侧无需再改编排或需新一轮适配）
- BACK_TO_BIZ：退回业务重编排（生成新 revision，避免覆盖历史）

### 3.2 锁定点
- SUBMITTED 之后：禁止修改 Composition.specJson/content（后端硬拦截）
- 若要再改：创建新 DRAFT（新 composition 或同 composition 新 draft revision），再提审

---

## 4. API 设计（增补，供前端联调）

### 4.1 Composition API（新增）

#### 4.1.1 获取当前草稿
`GET /api/compositions?psuId={psuId}`

响应（示例）：
```json
{
  "id": 10,
  "psuId": 123,
  "status": "DRAFT",
  "schemaVersion": 5,
  "content": "xxx {{userId}}",
  "specJson": {...},
  "updatedAt": "2026-04-15T12:00:00Z"
}
```

#### 4.1.2 保存草稿（仅 DRAFT 可保存）
`PUT /api/compositions?psuId={psuId}`

请求体：ComposeSpec（或 `{ content, specJson }` 包一层）

后端校验最小集：
- 占位符/Token 解析结果合法
- 引用字段存在于当前 schema（按 schemaVersion）
- injectionPlan 与 tokens 中 VAR 集合一致

#### 4.1.3 校验（不落库/或落库前预校验）
`POST /api/compositions/validate?psuId={psuId}`

返回：
```json
{ "ok": true, "errors": [], "warnings": [] }
```

#### 4.1.4 渲染（用于“预览最终 prompt”）
`POST /api/compositions/render?psuId={psuId}`

请求：
```json
{
  "compositionId": 10,
  "input": { "userId": "u01", "xxx": "..." }
}
```

响应：
```json
{
  "renderedPrompt": "你是... 用户ID=u01 ...",
  "missingVars": [],
  "usedVars": ["userId"]
}
```

#### 4.1.5 提交审核（锁定 + 生成快照）
`POST /api/compositions/submit?psuId={psuId}`

后端动作：
1) 校验通过  
2) Composition.status: DRAFT → SUBMITTED  
3) 写入 CompositionRevision 快照（revisionNo+1）  
4) 同步创建/更新 VersionReview（可选：将该 revision 关联到 version review）

---

### 4.2 测试运行 API（新增/推荐）

#### 4.2.1 批量运行测试集
`POST /api/test-runs?psuId={psuId}&datasetId={datasetId}`

请求（可选项）：
```json
{
  "compositionId": 10,
  "options": { "returnRenderedPrompt": true }
}
```

响应（建议）：
```json
{
  "runId": 9001,
  "items": [
    {
      "caseId": "c1",
      "input": {...},
      "renderedPrompt": "...",
      "modelOutput": "...",
      "success": true,
      "error": null,
      "latencyMs": 1200
    }
  ]
}
```

> V1.2 即便暂不做“规则引擎”，也建议保留 `success/error/latency/renderedPrompt`，否则前端调试体验会反复返工。

---

## 5. 服务层拆分建议（实现任务）

### 5.1 CompositionService
- `getDraft(psuId, userId)`
- `saveDraft(psuId, composeSpec, userId)`（仅 DRAFT）
- `validate(psuId, composeSpec)`
- `render(psuId, compositionId, input)`
- `submit(psuId, compositionId, userId)`（锁定 + revision）

### 5.2 TestRunService（可选）
- `runDataset(psuId, datasetId, compositionId)`
- （可选）`getRun(runId)`

### 5.3 VersionReviewService（对齐你的审核方式）
建议补齐：
- 审核开始：将关联 Composition 置为 DEV_REVIEWING
- 审核通过：APPROVED（并记录生成代码 + git hash）
- 审核驳回：REJECTED + rejectionType（BACK_TO_DEV / BACK_TO_BIZ）

---

## 6. 数据一致性与版本对齐（关键）

### 6.1 schemaVersion 绑定
- Composition 在 SUBMITTED 时必须绑定当前 schemaVersion（入参 schema）
- 后续即便开发修改 schema，已提交版本仍可复现（至少能提示“与当前 schema 不一致”）

### 6.2 变量路径校验口径
- 统一使用 `{{path}}` 协议
- path 的合法性校验：
  - 在 schema JSON 中可解析到对应字段（可先做“弱校验”：存在即可，不校验类型）
  - 运行时输入缺失则返回 missingVars（由前端提示）

