# shopping-stream 迭代重构实施计划（Plan）

## 1. 目标与范围

### 1.1 目标

1. 按“用户两轮结构化输入 + 多 subAgent 逐个回复 + 单轮 judge + 末轮全局 judge”重构请求流程。
2. 显式管理过程状态（轮次、agent 结果、judge 结果、历史上下文），避免流程混杂。
3. 输出可读、可审计的日志与调试结果，避免出现仅有 `fallback/no_delta` 的“无参考意义JSON摘要”。
4. 为重构内容补齐测试与联调方法，保障基本正确性与回归稳定性。

### 1.2 本次范围

* `main-service`：编排流程、上下文管理、judge调用时机、SSE事件规范、日志增强。

* `subagent-service`：单Agent回复流程对齐、提示词输入输出约束。

* `common`：必要 DTO/上下文字段补充。

* `.trae/requestResult`：新增“有业务结论”的调试报告格式。

### 1.3 非范围

* 不改 Nacos/yaml 配置策略。

* 不做线上部署与灰度发布动作。

* 不强制接入 Redis（仅预留扩展点，待你提供连接信息后接入）。

## 2. 目标流程（与需求逐条对齐）

### 2.1 第一轮

1. 用户提交第一轮结构化入参。
2. main-service 调 subagent-service 获取参与列表。
3. 按 agent 顺序/并发策略执行：`subAgent reply -> 单Agent judge`。
4. 每个 agent 的单轮处理结束后，写入“第一轮历史”。
5. 第一轮结束后，形成 round1 聚合快照（用户入参 + 所有 agent 回复 + 每个 judge 结论）。

### 2.2 第二轮

1. 接受用户第二轮结构化入参。
2. 对每个 agent 执行第二轮：输入=第二轮用户诉求 + 第一轮历史 + 竞品对手结果。
3. 每个 agent 继续执行：`subAgent reply -> 单Agent judge`。
4. 聚合 round2 结果并写入上下文。

### 2.3 全局审视

1. 所有 agent 完成第二轮后，触发全局 judge。
2. 全局 judge 输入：两轮用户入参 + 两轮 agent 回复 + 所有单Agent judge结果。
3. 输出：面向用户的最终建议（推荐对象、理由、风险说明、价格合理性说明）。

## 3. 架构与数据设计

### 3.1 新增/重构核心模型

1. `ConversationRoundContext`：roundNo、userInput、agentResults、judgeResults、timestamps。
2. `AgentRoundResult`：agentId、structuredReply、judgeDecision、riskFlags、latency。
3. `GlobalDecision`：recommendation、reasoning、complianceSummary、priceSummary。

### 3.2 线程上下文管理（本地版）

1. 默认先用 main-service 请求级上下文（request scope + 内存对象）存放 round1/round2 历史。
2. 严格定义生命周期：请求开始创建、结束释放，避免跨请求污染。
3. 评估并预留 Redis 接口：`ConversationStateStore`（InMemory 实现 + Redis 实现占位）。

### 3.3 Redis 接入评估建议

1. 触发条件：需要跨实例共享会话、长会话恢复、异步回放。
2. 推荐键设计：`shopping:conv:{traceId}:round:{n}`，设置 TTL。
3. 数据结构建议：Hash(元信息)+JSON(轮次快照)。
4. 一致性策略：以 traceId + roundNo 幂等写入，防止重复提交。

## 4. 编排实现计划（代码改造顺序）

### 阶段A：流程拆分与状态骨架

1. 把现有 `OrchestratorService` 抽为阶段化方法：`prepare -> round1 -> round2 -> globalJudge -> finalize`。
2. 明确单Agent处理函数：`processAgentRound(agent, roundContext)`。
3. 统一 judge 调用入口，确保“每个 agent 回复后立即 judge”。

### 阶段B：历史注入与对手信息

1. 在 round2 组装“第一轮历史 + 对手摘要 + 用户第二轮诉求”输入模板。
2. 约束 subAgent 提示词输入块，避免自由拼接导致字段污染。
3. 保证每个 agent 看到同构上下文，但保留自身差异化策略空间。

### 阶段C：全局 judge 与最终推荐

1. 全局 judge 只在 round2 完成后触发一次。
2. 输出结构化全局结论，再由展示层渲染为可读文案。
3. 增加兜底：若流式无有效delta，回退同步结果时必须给出“可读业务结论”，禁止仅输出 `fallback/no_delta` JSON。

### 阶段D：SSE事件与日志规范

1. 标准化事件：`roundStart/agentReply/agentJudge/roundDone/globalJudge/done/error`。
2. 每次 LLM 调用记录：调用时间、输入摘要、请求体预览、响应摘要、耗时、traceId。
3. 子Agent调用记录：agentId、roundNo、输入摘要、输出摘要、异常。

## 5. 测试与验收计划

### 5.1 自动化测试

1. 单元测试：

   * 单Agent流程测试（reply->judge）。

   * round2 历史注入正确性测试。

   * 全局 judge 触发时机与输入完整性测试。
2. 回归测试：

   * 事件序列稳定性（A/B/C 都有 round1，含 followUp 时有 round2）。

   * fallback 场景不再输出“无意义摘要”。

### 5.2 联调测试（按你指定）

1. 入参A：`shoppingRequestExample.json`。
2. 入参B：`shoppingRequestRound2.json`。
3. 输出报告落盘到 `.trae/requestResult`，命名 `20260316-shopping-stream-result-xxxxxx.md`。
4. 报告必须包含：请求体、事件统计、可读 summary、done、完整 SSE、关键日志摘录。

### 5.3 验收标准

1. 两轮流程完整可达，且单Agent judge 与全局 judge 都有结果。
2. 报告中 summary 为可读业务结论，不再只有 `chosenLen/no_delta` 这类技术占位。
3. 日志可用来追踪每次 LLM 调用全过程。

## 6. 风险与处理

1. **运行时依赖漂移**：先构建 common 再启动服务，避免 `NoSuchMethodError`。
2. **LLM流式空增量**：保留同步兜底并强制“可读结论渲染”。
3. **上下文过大**：对历史摘要进行裁剪与字段白名单控制。
4. **并发污染**：traceId 贯穿与请求级上下文隔离。

## 7. 本次开发完成后的系统检查清单

1. `mvn -pl common,main-service,subagent-service -am test` 全量通过。
2. 双服务启动后，A/B 入参联调跑通。
3. `.trae/requestResult` 新报告满足模板与可读性要求。
4. 检查本次改动文件（代码、提示词、测试）无遗漏并输出变更清单。

