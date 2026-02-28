# 购物场景 GoodsInfo 注入与判官 Summary Spec

## Why
现有子导购提示词已改为依赖 `<GoodsInfo>`，但调用链路未注入商品信息；同时需要引入“消费判官”对各商家话术做合规与匹配度评估，作为 summary 流式输出。

## What Changes
- 子 agent 调用时在 prompt 中注入 `<GoodsInfo>` 商品信息段落（来源：`subagent-service/data/baonuanneiyi/goodsinfo/goos.txt`）。
- 子 agent 的 promptPath 改为使用已重构的 `baonuanneiyi/prompts/*`。
- main-service 重写 `summaryFlux`：使用判官提示词 `baonuanneiyi/prompts/judge.txt`，结合 `<hidden>` 潜台词、`<subAgentReplay>` 商家回复、`<GoodsInfo>` 商品信息生成总结评判。
- 修复 summary 所需的 subAgent 上下文采集：`replayMap` 仅保存“完整回复”，避免被 `DONE` 标记覆盖。
- 输出契约补齐：确保流式链路最终会发送 1 个 `done` 事件（包含 bestQuote 与 error 标记），并保证 heartbeat 不会导致主流双订阅。

## Impact
- Affected specs: 购物场景子导购 prompt 组装、判官总结输出、SSE 事件序列稳定性
- Affected code:
  - subagent-service：`SubAgentController`、`SubAgentRepository`、`subagents.json`
  - main-service：`OrchestratorService`（summaryFlux 与 replayMap 采集）
  - common：可能新增轻量 DTO/工具（仅在需要结构化 GoodsInfo 时）

## ADDED Requirements
### Requirement: 子导购调用注入商品信息
系统 SHALL 在调用 shopping 类型子 agent 时，把商品信息以 `<GoodsInfo>...</GoodsInfo>` 的形式拼接进发送给 LLM 的输入内容中。

#### Scenario: 子导购正常接收商品信息
- **WHEN** main-service 调用 `/api/subagents/{id}/reply` 获取导购回复
- **THEN** subagent-service 发送给 LLM 的 merged 输入包含 `<GoodsInfo>` 标签包裹的 `goos.txt` 原始内容（JSON 文本）

### Requirement: 判官 summaryFlux
系统 SHALL 在所有子 agent replay 流结束后，基于判官提示词生成 `summary` SSE 事件流，并最终发送 `done` 事件。

#### Scenario: 判官评判成功
- **WHEN** 至少一个子 agent 产生有效导购回复（非 DONE 标记）
- **THEN** summaryFlux 使用 `judge.txt` 作为 system prompt
- **AND** summaryFlux 的 user 输入包含：
  - 用户显性输入（商品/预算/喜恶）
  - `<hidden>`：来自 `ShoppingRequest.hidding`（空则输出空标签或省略）
  - `<GoodsInfo>`：来自 `goos.txt`
  - 多个 `<subAgentReplay>`：每个商家一段，包含商家ID、商家类型（shopping）、导购完整回复文本

#### Scenario: 判官流式无增量或出错
- **WHEN** 判官 LLM 流式返回为空或发生异常
- **THEN** 系统仍 SHALL 输出 1 个可观测的 `summary` fallback 事件（沿用现有 fallback 结构）
- **AND** 系统最终仍 SHALL 输出 1 个 `done` 事件

## MODIFIED Requirements
### Requirement: replayMap 上下文可用于 summary
系统 SHALL 在采集子 agent replay 时仅保存“完整回复内容”，不得被 `DONE` 标记覆盖，确保 summary 使用的数据真实可用。

## REMOVED Requirements
（无）

