# 后端需求开发文档：动态参数编排容器（Composition）与在线测试接口

## 0. 文档目的
明确后端需要实现的“可保存的编排顺序/注入顺序”与“在线测试接口”，并给出最小可用的接口与数据结构，供前端联调。

## 1. 范围与非范围

### 1.1 范围（V1.2 必做）
1) 保存编排草稿（content + ComposeSpec）  
2) 提交审核后锁定草稿（禁止业务侧再编辑）  
3) 编排快照（提交审核时固化 revision，保证可复现）  
4) 渲染接口（输入参数 → renderedPrompt）  
5) 运行测试集接口（批量）并返回 renderedPrompt 与模型输出

### 1.2 非范围（V1.2 暂不做/可后置）
- 权限体系精细化（仍建议后端保留 userId 归属字段）
- 规则引擎断言/评分体系
- CI/CD、自动部署

## 2. 核心设计点

### 2.1 为什么必须保存“注入顺序”
前端编辑器的“变量出现顺序”就是业务真实意图的一部分：  
它直接影响：
- 参数缺失提示（按出现顺序提示）
- 渲染回放与差异对比
- 后续代码生成（把变量清单固化为校验/拼装逻辑）

因此后端落库必须至少保存：
- tokens（TEXT/VAR 顺序）
- injectionPlan（变量列表，按出现/约定顺序）

### 2.2 为什么必须做“提交时快照”
提交审核后业务锁定，开发审核时看到的编排内容必须稳定。  
所以需要 `composition_revisions`（不可变快照），否则会出现：
> 前端看的是 A，开发审核时却变成 B（或 Schema 版本漂移），导致返工与追溯失败。

## 3. 数据结构（后端口径）

### 3.1 ComposeSpec（请求体/落库 spec_json）
最小结构（建议）：
```json
{
  "content": "你是... 用户ID={{userId}} ...",
  "tokens": [
    {"type":"TEXT","value":"你是... 用户ID="},
    {"type":"VAR","path":"userId","source":"INPUT_SCHEMA"},
    {"type":"TEXT","value":" ..."}
  ],
  "injectionPlan": [
    {"path":"userId","required":true,"defaultValue":null}
  ],
  "assembledFragments": []
}
```

### 3.2 Composition 状态机
- DRAFT：可保存、可测试
- SUBMITTED：锁定（不可保存）
- DEV_REVIEWING：开发审核中
- APPROVED：审核通过
- REJECTED：驳回（需 rejectionType）

## 4. 接口清单（V1.2）

### 4.1 编排草稿

#### 4.1.1 获取草稿
`GET /api/compositions?psuId={psuId}`

返回：Composition（含 id/status/content/specJson/schemaVersion）

#### 4.1.2 保存草稿（仅 DRAFT）
`PUT /api/compositions?psuId={psuId}`

后端校验：
- content 非空（允许空但需明确是否支持）
- tokens 中 VAR 的 path 非空
- injectionPlan 与 tokens 中 VAR 集合一致（至少包含）
- path 在 Schema 中存在（强校验；若暂不做强校验，至少在 validate 中做）

#### 4.1.3 校验
`POST /api/compositions/validate?psuId={psuId}`

返回：
```json
{
  "ok": false,
  "errors": [
    {"code":"VAR_NOT_FOUND","path":"userId","message":"Schema中不存在该字段"}
  ],
  "warnings": []
}
```

#### 4.1.4 渲染
`POST /api/compositions/render?psuId={psuId}`

请求：
```json
{ "compositionId": 10, "input": { ... } }
```

响应（最小）：
```json
{ "renderedPrompt": "...", "missingVars": ["userId"], "usedVars": ["userId"] }
```

渲染规则（V1.2）：
- 仅支持 `{{path}}` 替换
- path 解析建议支持：
  - 一级字段：`userId`
  - 嵌套：`order.item.name`
  - 数组：`items[0].name`（可后置；若不做需在 validate 中报错）

#### 4.1.5 提交审核（锁定 + 快照）
`POST /api/compositions/submit?psuId={psuId}`

动作：
1) validate 必须通过  
2) 将 status 置为 SUBMITTED  
3) 写入 composition_revision（revisionNo+1）  
4) 写入/更新 version_review（可选：绑定 revisionNo）

### 4.2 测试接口（批量）

#### 4.2.1 批量运行测试集
`POST /api/test-runs?psuId={psuId}&datasetId={datasetId}`

后端处理步骤（建议固定管线）：
1) 取 composition（若 status!=DRAFT 也允许运行，但使用 revision 快照更好）  
2) 取测试集用例列表  
3) 对每条用例：render → 调用 LLM（或 stub）→ 组装 item 结果  
4) 返回 items（必要时落库 runId）

返回每条用例必含：
- input
- renderedPrompt
- modelOutput（或 error）
- success/latencyMs

## 5. 实现任务清单（后端）
1) 新增 entity/repo：Composition、CompositionRevision（或用 JSON 字段落库）  
2) 新增 CompositionService：get/save/validate/render/submit  
3) 新增 Controller：/api/compositions/**  
4) 新增 TestRunService + Controller：/api/test-runs/**（或在 PromptController 中扩展）  
5) 与 VersionReviewService 对齐：submit 时建立关联、review 时更新状态、驳回类型入库  

