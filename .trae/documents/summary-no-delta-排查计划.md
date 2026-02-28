# summary no\_delta 排查计划

## 背景与现象

* 现象：`summaryFlux` 在服务内只产出 `event:summary` 的 fallback，`reason=no_delta`，而把同一份“提示词 + 用户输入”拿去直接跑模型能得到正常且较长的输出。

- <br />

- 已确认（来自线上日志）：summary 调用的请求参数本身是合理的（`model=qwen-plus`、`stream=true`、system 为判官提示词、user 为 `<hidden>/<GoodsInfo>/<subAgentReplay>` 拼好的输入）。

* 触发点：`OrchestratorService.java#L150-L166` 对 `llmStreamService.streamTokens(..., "summary")` 使用 `switchIfEmpty`，当上游流式 token 产出为 0 时会进入 `no_delta`。

## 关键结论（基于当前代码的推断）

* 另一类可能：Qwen 端返回为 SSE，但行格式包含 `event:` / 或 `data:` 后不是 OpenAI 兼容的 `choices[*].delta.content` 结构（例如 DashScope 风格在 `output.*` 下，或直接是纯文本 token），当前解析未覆盖，从而每行都提取不到内容。

- `no_delta` 代表：`RealQwenLlmClient.generateStreamFlux(system, user)` 返回的 Flux **没有任何元素**（不是异常），导致 `streamTokens` 也为空。

- 在 `RealQwenLlmClient` 中，`generateStreamFlux` 只有在 **能拿到 key 且 stream=true** 时才会走真实流式解析；此时若 HTTP 返回的每行数据 **不符合** **`extractDelta`** **支持的字段结构**，就会出现“HTTP 有返回但解析不到任何 delta”的空流现象。

1. 给出最小改动：让 summary 至少能产出 1 个非 fallback 的 `summary` 事件（最好仍保持流式）。

## 排查步骤（按优先级）

### Step 1：确认是否“请求已发出 + 有响应 + 但解析不到”

* 查看 main-service 日志中 `LLM stream begin` 与 `LLM stream req` 是否在 summary 阶段出现（同一次请求内）。

* 对比 `question` 阶段：

  * 如果 `question` 也是 fallback（不是模型输出），说明流式链路整体拿不到可解析 delta；

  * 如果 `question` 正常流式，而 `summary` no\_delta，说明差异来自 summary 的输入长度/内容或 Qwen 端的返回结构变化。

### Step 2：增加“只针对 summary 的最小可观测性”（实现后验证一次即可移除/降级）

实现思路（只做一次性排查用，避免日志爆炸）：

* 在 `RealQwenLlmClient.parseStreamChunksToDeltas` 里：

  * 统计前 N 行（例如 5 行）解析失败的样本（仅截断到 300 字符），打到 debug 日志；

  * 同时统计“总行数 / 成功提取 delta 行数 / 首个成功 delta 的长度”。

### Step 3：按样本补齐“行级解析规则”（先让流不为空）

基于流式常见变体，先补齐对 SSE 行格式的处理：

* 遇到 `event:` 行：直接忽略（不尝试 JSON 解析）。

* 遇到 `data:` 行：去掉前缀后：

  * 若是 JSON：走 `extractDelta`；

  * 若不是 JSON（纯文本 token）：直接把该文本当作 delta 输出（至少能保证不空流）。

验收：summary 至少能产出 1 条 token 事件（即使内容不完整，也能证明链路打通）。

### Step 4：补齐 `extractDelta` 的字段路径（按样本驱动）

* 或者在 `streamOnceFlux` 的 `chunks` 上对前 N 个 chunk 打 debug（同样截断）。

验收：能够拿到至少 1 条真实返回样本（例如以 `data:` 开头的一行 JSON），用来对照字段结构。

### Step 3：补齐 `extractDelta` 的字段路径（按样本驱动）

基于常见 Qwen/DashScope 变体，优先补齐以下路径（实际以 Step2 样本为准）：

* `output.choices[0].message.content`

### Step 5：确认 HTTP Header/协议是否导致“非 SSE 返回”

* `output.text` / `output_text`

* `choices[0].delta.content`（已有）

* `choices[0].message.content`（已有）

### Step 6：回归与保护

* `choices[0].content`（已有）

验收：同一份 summary 调用可以产出至少 1 个 delta，从而不会走 `no_delta` fallback。

## 风险与约束

* 不在日志中输出任何密钥/Authorization；只允许输出模型返回片段的截断内容与结构字段名。

* 只修改 Java 代码，不改动 yml/yaml 等配置文件。

## 最终验收标准

* 线上/本地同一输入下，summary 不再频繁出现 `reason=no_delta`（除非 Qwen 端确实无输出）。

* `mvn test` 通过。

