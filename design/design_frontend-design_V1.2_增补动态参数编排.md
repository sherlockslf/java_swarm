# PSU全生命周期管理平台 - 前端设计文档 V1.2（增补：动态参数编排容器）

> 目标：补齐“Prompt 编排 + 变量注入 + 一键测试集调试”的前端实现细节，减少前后端联调返工。  
> 说明：权限暂不作为第一优先级，但本设计会把“可编辑/锁定/只读”状态在 UI 上明确出来，以便后续补权限时不重构页面结构。

---

## 1. 技术栈选型（不变）
- Vue 3.4 + Pinia 2.1.7 + Element Plus 2.4.0 + Vue Router 4.2.5
- Axios 1.6.0 + Vite 5.0

---

## 2. 新增核心能力：Prompt Composer（动态参数编排容器）

### 2.1 核心交互目标
业务侧在一个页面完成：
1) 选择 PSU  
2) 从该 PSU 的 JSON Schema 中选择字段（只读）  
3) 在编辑器中插入变量占位符（默认协议：`{{path}}`）  
4) 保存草稿 / 校验 / 预览渲染结果  
5) 选择测试集 → 单条/批量运行 → 查看渲染 prompt 与模型输出  
6) 全部通过后提交审核（提交后 UI 进入锁定只读态）

---

## 3. 页面与路由增补

### 3.1 业务配置空间新增页面

建议在 `views/business/` 下新增：

1) `PromptComposer.vue`（主页面）
- route: `/business/psus/:psuId/composer`
- 说明：取代/升级原“PromptManagement”概念，将“编排 + 测试”聚合到同一容器里

2) `TestRunHistory.vue`（可选，V1.2 可不做）
- route: `/business/psus/:psuId/test-runs`
- 说明：展示历史运行结果/对比（若后端落库 runId）

### 3.2 研发工程空间的只读入口（建议）
开发审核时需要“查看业务提交的编排快照 + 测试结果摘要”：
- `views/developer/CompositionReview.vue`（只读）
- route: `/developer/psus/:psuId/reviews/:reviewId`

---

## 4. 组件拆分（前端任务清单核心）

### 4.1 PromptComposer 页面布局（推荐三栏）

#### 左栏：SchemaVariablePanel
组件：`components/composer/SchemaVariablePanel.vue`
- 输入：`psuId`
- 数据来源：`GET /api/schemas/{psuId}`（业务只读）
- 功能：
  - Schema 树展示（对象/数组/叶子字段）
  - 搜索（按字段名/路径）
  - 点击字段插入变量占位符到编辑器：`{{path}}`
- 输出事件：
  - `onInsertVariable(path: string)`

#### 中栏：PromptTextEditor
组件：`components/composer/PromptTextEditor.vue`
- 建议选型：
  - MVP：Element Plus `el-input` + `textarea`（最快落地）
  - 体验增强：CodeMirror 6 / Monaco（支持高亮、光标插入、token 装饰）
- 必做能力：
  - 维护 `content`（原始文本）
  - 支持插入：在光标位置插入 `{{path}}`
  - 实时解析 tokens（至少能抽取出所有 `{{...}}` 变量）
- 输出事件：
  - `onContentChange(content)`
  - `onTokensChange(tokens, injectionPlanPreview)`

> Token 解析建议：前端用正则先做“弱解析”（识别 `{{...}}`），由后端 `validate` 做“强校验”（是否存在于 schema）。

#### 右栏：InjectionSummaryPanel
组件：`components/composer/InjectionSummaryPanel.vue`
- 输入：
  - `tokens`
  - `schemaFieldIndex`（用于判断引用是否存在）
- 展示：
  - 变量清单（按出现顺序）
  - 缺参/不存在字段提示
  - 重复引用提示
- 交互：
  - 点击某变量 → editor 定位到对应位置（若编辑器支持 range）

---

## 5. 与后端联调的接口契约（前端必须按此实现）

### 5.1 Composition 草稿加载/保存

1) 获取草稿：
- `GET /api/compositions?psuId={psuId}`
- 前端处理：
  - 200 + data=null：初始化空草稿
  - 200 + data.status!=DRAFT：进入只读模式（禁用编辑与保存按钮）

2) 保存草稿：
- `PUT /api/compositions?psuId={psuId}`
- 请求体（推荐前端统一结构）：
```json
{
  "content": "...",
  "tokens": [...],
  "injectionPlan": [...],
  "assembledFragments": [...]
}
```

按钮与交互：
- “保存草稿”按钮：仅 DRAFT 可点击
- 自动保存（可选）：每 3~5 秒 debounce 保存一次

### 5.2 校验 / 渲染预览

1) 校验：
- `POST /api/compositions/validate?psuId={psuId}`
- 返回：
```json
{ "ok": true, "errors": [{ "code":"VAR_NOT_FOUND", "message":"xxx", "path":"userId"}], "warnings": [] }
```

2) 渲染预览：
- `POST /api/compositions/render?psuId={psuId}`
- 请求：
```json
{ "compositionId": 10, "input": {...} }
```
- 前端展示：
  - renderedPrompt（必须可复制）
  - missingVars（以红色提示）

### 5.3 一键使用测试集在线调试（批量）

1) 测试集列表：
- `GET /api/test-datasets?psuId={psuId}`

2) 批量运行：
- `POST /api/test-runs?psuId={psuId}&datasetId={datasetId}`
- 结果展示（V1.2 最小验收）：
  - 表格：caseId/name、success、latency、error 摘要
  - 点击行展开：input、renderedPrompt、modelOutput

---

## 6. 页面状态与交互约束（减少返工的关键）

### 6.1 状态枚举（前端统一）
- `DRAFT`：可编辑、可保存、可测试、可提交
- `SUBMITTED / DEV_REVIEWING / APPROVED`：只读（编辑器 disabled），仅可查看测试结果/渲染结果
- `REJECTED`：
  - 若后端给出 `rejectionType=BACK_TO_BIZ`：允许“一键生成新草稿”按钮（复制旧内容到新 DRAFT）
  - 若 `BACK_TO_DEV`：提示“已退回开发修改 Schema/PSU，等待开发处理”

### 6.2 提交审核（按钮行为）
- 点击“提交审核”：
  1) 先调用 `validate`
  2) 校验通过 → 调用 `POST /api/compositions/submit`
  3) 提交成功 → 切换到只读态

### 6.3 防止丢稿（必做）
- 路由切换/刷新前检测 unsaved changes
- 弹窗提示：保存 / 放弃

---

## 7. 前端实现任务清单（可直接拆 Jira/Tapd）

### 7.1 页面与路由
- [ ] 新增 `PromptComposer.vue` 路由与页面骨架（三栏布局）
- [ ] PSU 切换能力（从 PSU 列表进入 composer；或页面内 PSU selector）

### 7.2 Schema 侧边栏
- [ ] Schema 拉取与缓存（psuId 维度）
- [ ] Schema 树渲染 + 搜索
- [ ] 点击字段插入 `{{path}}` 到编辑器（保留光标位置）

### 7.3 编辑器能力
- [ ] content 双向绑定
- [ ] placeholder 弱解析（抽取 tokens 与变量路径列表）
- [ ] 高亮（可选增强：把 `{{...}}` 标色）

### 7.4 编排保存/加载
- [ ] 获取草稿 / 初始化草稿
- [ ] 保存草稿（手动 + debounce 自动保存）
- [ ] 状态只读控制（status!=DRAFT 禁用所有编辑操作）

### 7.5 校验/渲染预览
- [ ] 校验接口对接与错误定位展示
- [ ] 渲染预览：输入参数编辑器（JSON textarea）+ renderedPrompt 展示

### 7.6 测试集联动与批量运行
- [ ] 测试集列表选择器
- [ ] 批量运行按钮与 loading/进度展示
- [ ] 结果表格（行展开查看 input/renderedPrompt/modelOutput）
- [ ] 失败用例筛选与复制（便于业务复测）

### 7.7 提交审核与锁定
- [ ] 提交审核按钮：validate → submit
- [ ] 提交后页面锁定只读态 + 状态提示条

