# Prompt服务单元（PSU）全生命周期管理平台 V1.2（增补：动态参数编排容器）

> 说明：本文档在《Prompt服务单元（PSU）全生命周期管理平台 V1.0 正式定稿PRD（全约束对齐版）》基础上，**增补“动态参数编排容器（Prompt Composer）”能力**与“审核流程定稿版（按你描述的锁定/退回机制）”。  
> 本次增补重点用于：降低前后端联调返工、统一数据结构与接口口径、明确前端任务清单与验收点。

---

## 0. 文档信息

- 文档密级：内部研发涉密 · 禁止对外同步
- 文档版本：V1.2（动态编排增补版）
- 生效日期：2026-04-15
- 部署形态：私有化部署、仅手工/半自动化发布、不依赖平台侧流水线

### 0.1 技术栈（不变）
- 前端：Vue 3.4 + Pinia 2.1.7 + Element Plus 2.4.0 + Vue Router 4.2.5 + Axios 1.6.0 + Vite 5.0
- 后端：Java 17 + Spring Boot 3.2.0 + Spring Security + Spring Data JPA + MySQL 8.0 + Redis + JWT + Lombok + SpringDoc OpenAPI

---

## 1. 核心定位与约束（摘要，不变）

平台目标：围绕 PSU（Prompt Service Unit）完成 **Schema 锁定 → Prompt 编排/注入 → 测试集在线调试 → 提审 → 开发审核 → 代码生成 → Git 入库** 的轻量闭环，确保产研协作一致性与可追溯性。

> V1.2 仍遵循 V1.0 的“功能裁剪清单”，本次仅补齐“动态参数编排容器”与“审核阶段锁定策略”。

---

## 2. 核心名词增补

|名词|释义|
|---|---|
|Prompt Composer（动态参数编排容器）|业务侧使用的“文本编辑器 + 参数注入 + 片段顺序/注入顺序配置 + 在线调试”的统一容器。它产出一份**可保存、可锁定、可快照、可回放**的编排规格（ComposeSpec）。|
|ComposeSpec（编排规格）|前后端统一的结构化数据：描述 prompt 由哪些片段组成、纯文本与变量占位符的顺序、变量来源、注入位置、渲染策略等。|
|渲染（Render）|将 ComposeSpec + 输入参数（测试用例）转换成最终 Prompt 文本（或消息数组）。|
|编排锁定（Lock）|提交审核后，业务侧不允许再编辑编排内容（包括 Prompt 文本/片段顺序/注入顺序）。若发现问题，按流程退回至开发侧调整 PSU/Schema 或开放新一轮编排。|

---

## 3. 角色与协作流程（按你描述的审核机制固化）

### 3.1 流程总览（V1.2 固化）

1) **开发**：创建 PSU + 定义/更新 JSON Schema（入参/出参） → “锁定结构”（业务侧只读）  
2) **产品/运营**：基于该 PSU 的 Schema，在 Prompt Composer 中完成提示词文本编写、变量注入、组装顺序设置 → 选择测试集一键回归 → **提交审核**  
3) **提交后锁定**：产品侧不再可修改该 PSU 的编排内容  
4) **开发审核**：重点检查（a）生成代码落地是否可执行；（b）schema/参数拼装是否满足服务与数据库落地；（c）必要时退回开发侧调整 PSU/Schema/参数拼装逻辑  
5) **开发审核通过**：该 PSU 的该版本流程结束（形成最终快照，可用于代码生成与入库）

### 3.2 退回策略（必须明确，减少扯皮）

- 若问题属于**Schema/PSU 配置缺失/不合理**：只能退回开发侧修改 Schema 或 PSU 配置（业务侧无权改结构）。  
- 若问题属于**提示词话术/变量引用/编排顺序**：退回后允许业务侧进入新一轮编排（系统生成新 draft 版本，避免覆盖历史快照）。  

---

## 4. 动态参数编排容器（V1.2 新增核心需求）

### 4.1 用户目标

业务侧在一个统一编辑器内完成：
1. 文本编写（Prompt 文本/多片段组合）
2. 从当前 PSU 的 JSON Schema 中选择字段并插入到文本中（变量占位符）
3. 配置“组装顺序”和“注入顺序”（用于后端渲染与可追溯回放）
4. 选择开发已配置好的测试集，一键在线调试（单条/批量）

### 4.2 前后端统一的“占位符协议”

为了降低实现复杂度、并与现有 demo 兼容，V1.2 推荐默认协议：
- **占位符语法**：`{{path}}`（Mustache 风格）
- **path 规则**：以“输入根对象 input”为根（或直接以 schema 字段路径为根），例如：  
  - `{{userId}}`（一级字段）  
  - `{{order.items[0].name}}`（数组/嵌套）  
  - `{{ext.biz.scene}}`（扩展字段）  

> 可扩展：未来可支持 `{}` 或 `@{}` 等多种标记，但 **V1.2 必须统一一个默认协议**，否则前后端联调会反复返工。

### 4.3 ComposeSpec（编排规格）——核心数据结构（必须落库）

编排容器不仅存“编辑器纯文本”，还要存可解释的结构化数据（便于回放、锁定、差异对比、代码生成）。

**建议的最小结构（V1.2）**：
```json
{
  "psuId": 123,
  "specVersion": 1,
  "editorMode": "TEXT", 
  "content": "你是... 用户ID={{userId}} ...",
  "tokens": [
    {"type":"TEXT","value":"你是... 用户ID="},
    {"type":"VAR","path":"userId","source":"INPUT_SCHEMA"},
    {"type":"TEXT","value":" ..."}
  ],
  "injectionPlan": [
    {"path":"userId","required":true,"defaultValue":null}
  ],
  "assembledFragments": [
    {"fragmentKey":"core_rules","sortOrder":1},
    {"fragmentKey":"message_template","sortOrder":2}
  ],
  "metadata": {
    "updatedBy": 10001,
    "updatedAt": "2026-04-15T12:00:00Z"
  }
}
```

字段解释：
- `content`：编辑器可回显的原始文本（便于体验）
- `tokens`：前端解析后的 token 序列（后端可直接渲染；也可由后端二次解析校验）
- `injectionPlan`：按出现顺序/约定顺序保存变量注入清单（用于校验、提示缺参、测试回放）
- `assembledFragments`：若你仍保留“片段式 Prompt”能力，该字段用于记录片段参与顺序（也可作为未来块编辑器的基础）

### 4.4 功能需求拆解（业务配置空间新增/强化）

#### 4.4.1 编排编辑器（Prompt Composer）
- 输入：psuId
- 展示：
  - 左侧：Schema 字段树（只读） + 搜索
  - 中间：文本编辑器（支持高亮 `{{}}` 变量）
  - 右侧：变量清单（从 tokens/injectionPlan 生成）、缺参提示、顺序展示
- 交互：
  - 点击字段树节点 → 在光标处插入 `{{path}}`
  - 编辑后实时解析 tokens（防止保存后解析失败）
  - “一键校验”：提示未引用字段/引用不存在字段/重复引用/格式错误
- 保存策略：
  - 支持“草稿保存”（DRAFT）
  - 提交审核后编排锁定（LOCKED）

#### 4.4.2 在线调试（绑定测试集）
- 支持选择一个测试集（或多个）进行：
  - 单条运行（展示输入参数、渲染后的 Prompt、LLM 返回、基础耗时）
  - 批量运行（表格展示 pass/fail、失败原因、返回摘要）
- V1.2 最小可验收：
  - 后端必须返回 “renderedPrompt”（便于前端定位注入问题）
  - 批量运行必须返回每条用例的 id/name 与结果，方便做回归表格

---

## 5. 审核流程（V1.2 新增：锁定点与快照点）

### 5.1 状态机（建议）

以“编排版本（Composition）”为主线：
- DRAFT（业务编辑中）
- SUBMITTED（业务提交，锁定编辑）
- DEV_REVIEWING（开发审核中）
- APPROVED（审核通过，允许生成代码/入库）
- REJECTED（退回：可能回到开发调整或业务重编排）

### 5.2 锁定规则（必须写死，减少争议）

- SUBMITTED 后：
  - 业务侧不可修改：`content/tokens/injectionPlan/assembledFragments`
  - 若需改动：只能创建新 DRAFT（新 revision），并重新提审  
- DEV_REVIEWING 阶段：
  - 开发侧可以修改：PSU/Schema（如果你允许），但**必须产生新的 schemaVersion**并留审计  
  - 若 schemaVersion 变化导致业务编排失效：系统应提示“编排需要重新适配”，并要求业务重编排

---

## 6. API 设计增补（V1.2）

> 说明：尽量复用你现有 `/api/prompts/**`、`/api/test-datasets/**`、`/api/versions/**` 体系；新增接口集中在 `/api/compositions/**` 与 `/api/test-runs/**`。

### 6.1 编排（Composition）接口（新增）

- `GET /api/compositions?psuId={psuId}`  
  获取当前 PSU 的最新编排草稿（或返回空）
- `PUT /api/compositions?psuId={psuId}`  
  保存草稿（请求体为 ComposeSpec）
- `POST /api/compositions/validate?psuId={psuId}`  
  校验：占位符语法、变量是否存在于 schema、注入顺序是否可解析
- `POST /api/compositions/render?psuId={psuId}`  
  渲染：入参为 `{ "input": {...}, "compositionId": optional }`，输出 `renderedPrompt`
- `POST /api/compositions/submit?psuId={psuId}`  
  提交审核：将编排状态从 DRAFT → SUBMITTED，并生成不可变快照（snapshot）

### 6.2 测试运行接口（新增/或扩展现有 test）

- `POST /api/test-runs?psuId={psuId}&datasetId={id}`  
  批量运行：用当前编排（或指定 compositionId）对测试集逐条执行  
  返回：runId + items（每条用例结果）
- `GET /api/test-runs/{runId}`  
  获取运行详情（用于前端刷新/重试/查看历史）

> 若你暂时不做“历史运行留存”，也可以在 V1.2 先只做 `POST /api/compositions/test-run` 同步返回结果，但建议至少保留 runId，避免前端无法追溯。

---

## 7. 数据库设计增补（V1.2）

### 7.1 新增表（建议）

1) `ai_prompt_compositions`：当前可编辑编排（草稿态）
2) `ai_prompt_composition_revisions`：编排版本快照（提交/审核时固化）
3) （可选）`ai_prompt_test_runs`、`ai_prompt_test_run_items`：测试运行记录

> 详细表结构见《数据库设计文档 V1.2 增补》。

---

## 8. 验收标准增补（V1.2）

### 8.1 动态编排容器验收
- 能从 Schema 字段树插入变量到编辑器，并以 `{{path}}` 展示
- 保存后再次打开能完整回显（content + tokens + 注入清单）
- 选择测试集可以一键批量运行，并至少返回：
  - 每条用例的输入、渲染后的 prompt、模型输出摘要/原文、耗时
- 提交审核后，业务侧入口变为只读（不可再编辑）

### 8.2 审核流程验收（按你描述）
- 业务提交后锁定编排
- 开发审核通过后，该版本流程结束，并可生成代码
- 退回时能明确区分：
  - 退回开发改 Schema/PSU（导致 schemaVersion 变化）
  - 退回业务重编排（产生新的 composition revision）

