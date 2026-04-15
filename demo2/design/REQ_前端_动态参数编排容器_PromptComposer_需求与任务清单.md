# 前端需求开发文档：动态参数编排容器（Prompt Composer）

## 0. 文档目的
用于指导前端实现“动态参数编排容器”，并把与后端联调的接口、状态机、页面交互一次性定清楚，减少返工。

## 1. 背景与目标（摘要）
业务侧需要在平台内完成：
- 基于开发创建的 PSU + JSON Schema（已锁定结构）
- 编写/组装提示词文本
- 从 Schema 中选择字段并注入到提示词指定位置
- 一键复用开发配置的测试集进行在线调试（单条/批量）
- 测试通过后提交给开发审核（提交后业务侧锁定不可改）

## 2. 核心原则（前端必须遵守）
1) **单一占位符协议**：V1.2 默认使用 `{{path}}`  
2) **状态只读硬切换**：只要 `status != DRAFT`，编辑器/插入/保存按钮必须禁用  
3) **调试必须可定位**：测试结果必须展示 `renderedPrompt`（否则无法定位注入错误）  
4) **草稿不能丢**：必须有“未保存离开提示”或“自动保存”

## 3. 页面范围

### 3.1 页面清单
|页面|路由|面向角色|目标|
|---|---|---|---|
|PromptComposer|`/business/psus/:psuId/composer`|业务侧（主）|编排、注入、测试、提交审核|
|Composer只读查看（可选）|`/developer/psus/:psuId/composer`|开发侧|审核期查看业务提交的编排快照|

### 3.2 页面布局（PromptComposer）
三栏布局：
- 左：Schema 字段树 + 搜索 + 一键插入变量
- 中：编辑器（文本/可选高亮）
- 右：变量清单（注入顺序）+ 校验结果 + 状态条 + 操作按钮区

## 4. 交互流程（逐步）

### 4.1 进入页面
1) 获取 Schema：`GET /api/schemas/{psuId}`
2) 获取编排草稿：`GET /api/compositions?psuId={psuId}`
3) 根据 `status` 决定 UI：
   - DRAFT：可编辑
   - SUBMITTED/DEV_REVIEWING/APPROVED：只读，并展示“锁定原因”
   - REJECTED：展示驳回原因与驳回类型（若 BACK_TO_BIZ 显示“生成新草稿”）

### 4.2 插入变量（最小验收）
触发：点击 Schema 字段树叶子节点  
行为：在编辑器光标位置插入 `{{path}}`，并触发 token 重新解析。

### 4.3 解析 tokens（前端弱解析）
前端需在本地生成：
- tokens：TEXT/VAR（至少能抽取 `VAR.path`）
- injectionPlan：按出现顺序生成（path 去重；可保留 count）

规则：
- `{{ }}` 中间允许空格但保存时应 trim：`{{ userId }}` → path=`userId`
- 不支持嵌套花括号（遇到异常直接标红并提示“占位符格式错误”）

### 4.4 保存草稿
按钮：保存草稿（仅 DRAFT 可见/可点）
- `PUT /api/compositions?psuId={psuId}`
- 成功提示：右上角 toast + 更新时间

建议（非必须）：
- debounce 自动保存（3~5 秒）

### 4.5 校验
按钮：校验
1) 前端先做本地校验：
   - 是否存在未闭合的 `{{`
   - VAR 是否为空路径
2) 再调用后端强校验：`POST /api/compositions/validate`
3) 展示 errors/warnings：
   - 错误列表（点击可定位到编辑器位置，若能做到）
   - Schema 不存在字段：提示用户删除/改正

### 4.6 在线调试（使用测试集）
按钮：运行（单条/批量）
1) 获取测试集列表：`GET /api/test-datasets?psuId={psuId}`
2) 选择一个 datasetId
3) 批量运行：`POST /api/test-runs?psuId={psuId}&datasetId={datasetId}`
4) 展示：
   - 表格：用例名称/ID、success、latency、error 摘要
   - 行展开：input、renderedPrompt、modelOutput

### 4.7 提交审核（锁定）
按钮：提交审核
前端动作严格顺序：
1) 调用 validate（本地 + 后端）
2) validate ok → `POST /api/compositions/submit?psuId={psuId}`
3) 提交成功 → 切换页面只读态（按钮禁用、编辑器禁用）

## 5. 组件与数据结构（建议拆分）

### 5.1 组件
- SchemaVariablePanel：Schema 树、搜索、插入变量
- PromptTextEditor：编辑器 + 插入变量接口 + tokens 解析
- InjectionSummaryPanel：变量清单、缺参/不存在字段提示
- TestDatasetRunner：测试集选择 + 批量运行 + 结果表格

### 5.2 前端数据模型（建议）
```ts
type Token =
  | { type: 'TEXT'; value: string }
  | { type: 'VAR'; path: string; source: 'INPUT_SCHEMA' };

type CompositionDraft = {
  id?: number;
  psuId: number;
  status: 'DRAFT'|'SUBMITTED'|'DEV_REVIEWING'|'APPROVED'|'REJECTED';
  content: string;
  tokens: Token[];
  injectionPlan: { path: string; required: boolean }[];
  updatedAt?: string;
};
```

## 6. 前端任务清单（可直接用于排期）

### 6.1 第 1 周（MVP 可用）
1) PromptComposer 页面骨架 + 三栏布局  
2) Schema 获取与树渲染 + 搜索 + 插入变量  
3) 编辑器 content 维护 + 插入变量 + tokens 弱解析  
4) 草稿加载/保存（含只读态控制）  

### 6.2 第 2 周（联调闭环）
5) validate 接口联调 + 错误展示  
6) 测试集列表联调 + 批量运行联调 + 结果表格/展开详情  
7) 提交审核联调（validate → submit → 锁定）  
8) 未保存离开提示 / 自动保存（至少完成其一）  

### 6.3 第 3 周（体验增强，可选）
9) 高亮 `{{...}}` + 点击变量定位  
10) 历史运行/对比（若后端提供 runId 查询）

## 7. 验收标准（前端角度）
- 业务侧可从 Schema 插入变量并保存草稿，刷新后能回显
- 校验能提示“字段不存在/格式错误”
- 可选择测试集一键批量运行，并能看到每条用例的 renderedPrompt
- 提交审核后页面立即变只读且不可修改

