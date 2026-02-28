# 主服务 SSE 真流式输出与 summary 事件修复 Spec

## Why
当前 main-service 的对话流式输出在客户端仍可能表现为“长时间无输出，最后才集中输出”，同时 `summary` 事件未发布（仅看到 `subAgentInfo`/`subAgentReplay`/`done`）。这导致 UI 无法增量渲染总结内容，也无法验证真正的流式链路是否生效。

## What Changes
- 将 LLM 流式读取从“回调 + 手动 subscribe + Flux.create 缓冲”改为“返回 Flux 的纯响应式链路”，避免无背压缓冲与预取队列导致的集中吐出
- 保证每个增量 token/片段都能尽快形成独立的 SSE event 并由服务端写出（flush）
- 引入心跳 SSE（可选但推荐），对齐参考实现中的 heartbeat/merge 模式，降低中间层合并/闲置导致的长时间无输出
- 修复 OrchestratorService 中 heartbeat 结束条件导致的主流双订阅问题，避免同一次请求触发两套 LLM/subAgent 调用并引入时序异常与“看似被缓冲”的现象
- 修复 subAgent 请求入参在链路构建阶段提前取值导致的“question 为空快照”，确保 subAgent 使用最终生成的 publicQuestion
- 修复 `summary` 事件缺失：确保在已选出 bestAnswer 后，必定发布 `event: summary`（LLM 总结成功则流式输出；失败/无 token 时提供可观测的 fallback summary）
- **BREAKING**：调整 `RealQwenLlmClient` 的流式接口形态（新增 reactive 方法，并逐步替代旧回调方法；旧方法是否保留由实现阶段决定）

## Impact
- Affected specs: SSE 流式对话输出、LLM 流式消费、UI SSE 事件渲染体验
- Affected code:
  - main-service：`LlmStreamService`、`OrchestratorService`、`StreamingShoppingController`
  - common：`RealQwenLlmClient`
  - 可选：`chat_stream.html`（仅用于验证与观测，不强制改动）

## ADDED Requirements
### Requirement: 真流式 SSE 输出
系统 SHALL 在收到 LLM 的增量输出后，将其作为 SSE 事件尽快发送给客户端，而不是在服务端/客户端缓存到较大块后集中发送。

#### Scenario: question 流式输出成功
- **WHEN** `POST /api/shopping/stream` 正常返回并开始读取 LLM stream
- **THEN** 客户端可持续收到多个 `event: question` 的 SSE event（每个包含增量 `data`），而不是长时间无输出后一次性大量输出
- **AND** 在 `done` 到达前，`question` 的至少首个增量应已到达客户端（用于证明非“结束后统一输出”）

### Requirement: summary 事件必须发布
系统 SHALL 在选出最低报价方案（bestAnswer）后发布 `event: summary` 的 SSE 事件。

#### Scenario: summary 流式输出成功
- **WHEN** subAgent 结果已齐备且 bestAnswer 非空
- **THEN** 客户端在 `done` 之前可持续收到多个 `event: summary` 的 SSE event（每个包含增量 `data`）

#### Scenario: summary 生成失败或无增量
- **WHEN** 总结 LLM 流式调用失败、或返回 0 个有效 delta
- **THEN** 仍需在 `done` 之前至少发送 1 个 `event: summary`（fallback 内容可为 bestAnswer 的简要归纳或明确的错误提示）

### Requirement: Reactive 流式链路无手动 subscribe
系统 SHALL 不在 `RealQwenLlmClient` 的流式实现中通过 `subscribe(...)` 把响应式链路变成“推送回调”，而应返回 `Flux<String>`（或等价的响应式类型）交由下游订阅与背压控制。

#### Scenario: 背压与调度不引入额外缓冲
- **WHEN** 下游消费速度降低或网络抖动
- **THEN** 流式链路不应因 `Flux.create` 默认 BUFFER、`publishOn` 预取队列等机制放大“先攒后吐”的现象

### Requirement: 心跳事件（推荐）
系统 SHOULD 定期发送 `event: heartbeat` 的 SSE event，以维持连接活性并帮助客户端持续刷新 UI。

#### Scenario: LLM 长时间无 token
- **WHEN** LLM 在一段时间内没有输出增量 token
- **THEN** 客户端仍能收到心跳事件，避免 UI 长时间无任何增量导致误判为卡死

## MODIFIED Requirements
### Requirement: LLM 流式拆分策略
系统 SHALL 对上游返回的文本块按 SSE 行协议解析（`data:` 行），并在提取到 delta 内容时立即发出增量，避免在单个回调/单次处理内循环快速“排空”导致客户端一次性渲染大量内容。

### Requirement: OrchestratorService 单次请求仅执行一次主流
系统 SHALL 保证同一次 `POST /api/shopping/stream` 的主编排流（question → subAgent → summary → done）只被订阅一次，避免因 heartbeat 停止条件等实现细节导致的双订阅，从而触发重复上游调用与时序竞态。

## REMOVED Requirements
### Requirement: 回调式 generateStream 作为主路径
**Reason**：回调 + 手动订阅易引入缓冲、并隐藏背压与调度，导致集中输出现象难以控制。  
**Migration**：新增 `RealQwenLlmClient` 的 reactive 流式方法供 `LlmStreamService` 使用；旧回调方法可保留为兼容层，但不作为 main-service 的主链路实现。
