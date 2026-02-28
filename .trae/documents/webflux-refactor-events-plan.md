# WebFlux 控制器瘦身与事件协议迭代计划

## 背景与目标

* 当前 `StreamingShoppingController.stream(...)` 方法体过长，业务逻辑过多堆叠在 Controller 层，不符合分层职责划分原则，维护与扩展性较差。

* 需要将主要业务逻辑下移至 service 层，Controller 仅负责请求入参校验、调用编排服务并返回 SSE Flux。

* 同时迭代流式事件协议，按你的指定对阶段进行拆分，确保前端按事件名与数据结构正确渲染。

## 需求要点（按你的要求逐条对齐）

1. 重构控制器

* 目标：将 `StreamingShoppingController.stream` 拆分为多个 service 调用，Controller 保持“薄”，仅串联 orchestrator 输出。

1. 事件协议与阶段

* 注意：严格使用你指定的事件名（包括可能的拼写），避免前后端不一致。

  * a) 获取适配 subAgent 列表阶段

    * event 名称：`subAgintInfo`

    * data：JSON 数组（SubAgentInfo\[]），元素字段沿用现有 DTO（id、name、type、enabled）

    * 输出时机：成功获取列表后立即推送一条；若获取失败，保持现有 error/done 语义

  * b) 逐个请求 subAgent 阶段

    * event 名称：`subAgentReplay`

    * data：JSON 对象，形如：`{"agentId":"xxx","text":"<本次文本>"}`；为方便展示效果，采用“一次完整文本 + DONE”的二步事件：

      * 第一步：`{"agentId":"xxx","text":"<完整回复文本>"}`（可后续升级为真实分片流）

      * 第二步（结束信号）：`{"agentId":"xxx","text":"DONE"}` —— 前端据此完成该 agent 对话的渲染闭环

    * 输出时机：每个 subAgent 返回后立即推送文本事件；随后再推送该 agent 的 DONE 事件

  * c) 总结阶段

    * event 名称：`sumary`

    * data：文本分片（token）或累积文本。为保持流式体验，采用“多次 sumary 事件按 token 推送”的方式

    * 输出时机：所有 subAgent 结束后，开始总结，期间持续推送 sumary；总结结束后发送 done

  * 其他事件继续保留：

    * `error`：阶段性错误提示（保持现有）

    * `done`：全流程结束（可附带 least quote 等简要信息）

1. 前端适配（Vue 3 单页）

* 左/中/右三列布局保持不变。

* 中间列渲染：

  * 接收到 `subAgintInfo`：展示当前参与对话的 subAgent 列表（头像/名称）

  * 接收到 `subAgentReplay`：

    * 若 text != "DONE"：在右侧以“头像框 + 文本回复框”形式为对应 agent 新增消息气泡

    * 若 text == "DONE"：将对应 agent 对话气泡标记为完成（例如加上“已完成”或停止对该 agent 的追加）

  * 接收到 `sumary`：在一个“总结区域”内直接滚动拼接文本（非聊天式）

* 右侧统计继续保留，新增“subAgent 数量”展示（来自 `subAgintInfo` 数组长度）

## 技术方案与分层设计

1. Controller 层（薄）

* `StreamingShoppingController.stream(req)`：

  * 仅负责：参数校验 → 调用 OrchestratorService 产出 `Flux<ServerSentEvent<String>>` → 返回

1. Service 层（新/拆分）

* `PromptService`

  * 读取 buyerPrompt 与 profile，拼装面向 LLM 的 system 与 user 文本

* `LlmStreamService`

  * 封装对 `RealQwenLlmClient.generateStream(...)` 的流式调用，提供统一的 token → 事件适配

* `SubAgentDirectoryService`

  * 通过 WebClient 获取 subAgent 列表 `Flux<SubAgentInfo>`

* `SubAgentDialogueService`

  * 负责对每个 subAgent 发起一次调用（现有后端是一次性返回），并将结果封装为两条事件：

    * `subAgentReplay`（完整文本）

    * `subAgentReplay`（DONE 结束）

* `SummaryService`

  * 汇总最佳方案文本，调用 LLM 流式总结（输出 `sumary` token 流）

* `OrchestratorService`

  * 组合上述服务：

    * 输出 1：`subAgintInfo`（数组）

    * 输出 2：并发触发多 agent 对话，按 agent 完成情况推送 `subAgentReplay` 与 `subAgentReplay/DONE`

    * 输出 3：总结阶段 `sumary` token 流

    * 最终 `done`

1. 日志与可观察性

* 保持已加的 Log4j2 日志点：用户请求接收/解析、目录获取成功/失败、每个 subAgent 调用成功/失败、LLM 流开始/结束/错误

* 在 OrchestratorService 中补充阶段起止日志，便于串联追踪

## 数据结构与事件样例

1. subAgintInfo

```json
[
  {"id":"a1","name":"Vendor A","type":"shopping","enabled":true},
  {"id":"b2","name":"Vendor B","type":"shopping","enabled":true}
]
```

1. subAgentReplay（文本分发与结束）

```json
{"agentId":"a1","text":"这是一段完整的回复文本"}
{"agentId":"a1","text":"DONE"}
```

1. sumary（多次）

```
event: sumary
data: 选型建议如下：

event: sumary
data: 1）…
```

## 代码改动清单

后端（main-service）

* 新增 services：

  * `service/PromptService`

  * `service/LlmStreamService`

  * `service/SubAgentDirectoryService`

  * `service/SubAgentDialogueService`

  * `service/SummaryService`

  * `service/OrchestratorService`

* 重写 `StreamingShoppingController.stream`：

  * 移除内联大段逻辑，改为调用 `OrchestratorService.stream(req)` 返回 Flux\<SSE<String>>

* 调整 Orchestrator 输出事件名与数据格式，按“subAgintInfo / subAgentReplay / sumary / error / done”

前端（Vue 3 单页）

* 在中间列新增三块渲染逻辑：

  * 渲染 subAgintInfo 列表（头像 + 名称）

  * 为每个 agent 维护一段对话 UI（右侧 agent 气泡），遇到 DONE 时将该 agent 的会话标记完成

  * 渲染总结区域，按 `sumary` 逐 token 追加

测试

* 更新/新增集成测试（条件：真实 Qwen Key）：

  * 验证事件序列至少包含：subAgintInfo → 若干 subAgentReplay（含 DONE）→ 若干 sumary → done

  * 验证 subAgintInfo 数组非空（在有已启用子代理的前提下）

## 兼容性与注意事项

* 事件名严格按照你指定的拼写：`subAgintInfo`、`subAgentReplay`、`sumary`

* 保持 `error`/`done` 原有语义，以便前端稳健处理异常与流程结束

* LLM 流式解析保持“原始字符串读取 + 手动解析 data:”的做法，避免 `[DONE]` 解码问题

## 实施步骤

1. 新建 service 类并迁移逻辑；为 Orchestrator 组合输出对应事件流
2. 改造 Controller，缩短方法体至“组装调用 + 返回 SSE”
3. 调整前端 Vue 事件解析与 UI 渲染（新增 subAgintInfo、subAgentReplay/DONE、sumary 处理）
4. 自测与联调：页面端可清晰看到 agent 列表、每个 agent 的对话气泡与 DONE 收尾、总结区域文本滚动
5. 更新集成测试断言新的事件序列

## 验收标准

* Controller 方法体精简，主要流程转移至 service 层（可读性显著提升）

* 事件序列与数据形态符合规范：`subAgintInfo` → 多个 `subAgentReplay`（含 DONE）→ 多个 `sumary` → `done`

* 前端按阶段正确渲染：列表、每 agent 对话、总结与统计

* 日志打印完整：请求接收/解析、子代理目录获取、子代理调用成功/失败、LLM 流开始/结束/错误

