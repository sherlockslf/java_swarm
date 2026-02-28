## 目标

把 [LlmStreamService.java](file:///d:/metaspace/trae/java_swarm/main-service/src/main/java/com/example/mainservice/service/LlmStreamService.java) 的 `streamTokens(...)` 从“固定 eventName 逐 token 输出”升级为“按流中携带的 status 动态切换 SSE event，并及时输出”，行为参考 [.trae/example/ReactiveChatService.java:L106-L239](file:///d:/metaspace/trae/java_swarm/.trae/example/ReactiveChatService.java#L106-L239)。

同时保证现有调用方（例如 [OrchestratorService](file:///d:/metaspace/trae/java_swarm/main-service/src/main/java/com/example/mainservice/service/OrchestratorService.java)）在输入是普通文本 token 时行为不变，现有单测继续通过。

## 背景与现状

* 当前 `streamTokens(...)` 仅做一件事：把 `RealQwenLlmClient.generateStreamFlux(system,user)` 的 delta 映射成 `ServerSentEvent(eventName, delta)`。

* 示例实现的关键点是：

  * 流式 chunk 中夹杂多个 JSON 对象，需要做“跨 chunk 累积 + JSON 对象切分”。

  * 每个 JSON 对象都包含 `status`，不同 status 对应不同 SSE event（text/phase/thinking/card/guess 等），并在 `STATUS_STOP` 时做尾部输出与收尾。

* 本仓库当前没有 `LLMStreamDataStatus / LLMStreamData / SseEventMapper` 这些业务类，因此需要在 `main-service` 内做一个“轻量通用版”的 status 路由与 event 构建，重点是“多 event 及时透传”，不引入示例中的商品查询/历史落库等强业务依赖。

## 方案概述（按 event 类型做“status 区分”）

在 `LlmStreamService.streamTokens(...)` 内引入两段式处理：

1. **兼容分支（保持原逻辑）**

   * 如果流中的片段无法解析为包含 `status` 的 JSON 对象，则按当前行为输出：`event = eventName`、`data = delta`。

   * 该分支保证 [LlmStreamServiceTest](file:///d:/metaspace/trae/java_swarm/main-service/src/test/java/com/example/mainservice/service/LlmStreamServiceTest.java) 不需要改或只做最小调整。

2. **status 路由分支（参考 ReactiveChatService + SseEventMapper）**

   * 对 `Flux<String>` 做累积缓冲，将 chunk 拼成字符串，并从中“逐个截取完整 JSON 对象”：

     * 复用示例的 `findJsonObjectEnd(...)`（基于括号深度、字符串/转义处理）来找到 `{...}` 的闭合位置。

   * 每个 JSON 对象用 `fastjson2` 解析为轻量结构（推荐用 `Map<String,Object>` 或新增一个简单 DTO：`status/content`）。

   * 根据 `status.toLowerCase(Locale.ROOT)` 决定输出哪些 `ServerSentEvent`，并且**用 SSE 的 event 名来表达节点类型**（text/phase/thinking/card/guess/heartbeat），从而达到你说的“根据 event 的类型来做类似 status 的区分”：

     * `full_context`：输出文本片段。

       * 推荐：输出 `text` 事件（与示例一致）。

       * 兼容：如果调用方显式传入 `eventName=question/summary`（当前 [OrchestratorService](file:///d:/metaspace/trae/java_swarm/main-service/src/main/java/com/example/mainservice/service/OrchestratorService.java) 的用法），则允许把 `full_context` 映射到该 `eventName`，避免破坏现有前端对 `question/summary` 的消费逻辑。

     * `study_start`：输出 `phase`（固定 JSON 串，沿用示例 `PHASE_LIST`）与 `thinking`（生成与示例相同结构的 JSON 串：name/desc/status）。

     * `study_end/match_start/match_end/search_start/search_end/offer_start/offer_end`：输出 `thinking`。

     * `tag_list/guess_list`：先缓存到 ctx；不直接输出。

     * `status_stop`：根据 ctx 中缓存内容，输出 `guess`（如果能解析出 `guess_list`）以及可选的 `card`（如果后续补齐 sku 逻辑时使用）；并结束本次流（不强制发 `done`，避免与上层编排重复）。

     * `status_error/interrupt`：输出 `text`（固定兜底文案，示例为“哎呀，这个我暂时还不会”）或输出 `error`（如果你希望前端单独处理错误），然后完成。

## 具体改动点（代码层面）

### 1. 更新 LlmStreamService.streamTokens(...)

* 增加内部缓冲与 JSON 对象切分逻辑（对标示例 `accRef + findJsonObjectEnd`）。

* 增加 status->event 的路由与产出（`ServerSentEvent.builder().event(...).data(...).build()`）。

* 保留“非 JSON/无 status”时的旧逻辑映射。

### 2. 新增/调整辅助方法（放在 LlmStreamService 内部）

* `private static int findJsonObjectEnd(String s, int start)`：从示例复制并适配。

* `private static String getLowerStatus(Map/DTO sd)`：统一做空值判断与 lower。

* `private static ServerSentEvent<String> sse(String event, String data)`：统一构建。

* `private static String thinkingPayload(String status)`：生成与示例同结构的 JSON（name/desc/status）。本仓库没有 `desc` 源，可先只输出 name/status，或用固定 desc 映射表（以 status 为 key）。

### 3. 引入 SseEventMapper（你补充要求参考的文件）

新增一个可在本工程使用的 `SseEventMapper`，实现参考：

* [.trae/example/SseEventMapper.java](file:///d:/metaspace/trae/java_swarm/.trae/example/SseEventMapper.java)

要点：

* 统一 event 名：`text/card/guess/heartbeat/thinking/phase`。

* `text(...)` 复刻示例对空格与换行的编码（`" " -> "&#32;"`、`"\n" -> "&#92n"`），确保“流式 + 前端解析”在不同渲染方式下稳定。

* `LlmStreamService` 的 status 路由分支尽量只调用 `SseEventMapper` 来构建 `ServerSentEvent`，减少散落的 builder 细节。

### 4. 单测补充（确保兼容 + 覆盖新能力）

在 `main-service/src/test/.../LlmStreamServiceTest.java` 增加新的用例，不改动现有用例语义：

* **兼容性用例**：维持现有 `a/b` token 时仍输出 eventName=question、data=a/b。

* **status 路由用例**：模拟 client 输出包含多个 JSON 对象的 chunk（并刻意拆分到多个 chunk），验证：

  * `FULL_CONTEXT` 产生 eventName（入参）或 `text` 的 SSE；

  * `STUDY_START` 产生 `phase` + `thinking`；

  * `GUESS_LIST` 不立即输出；

  * `STATUS_STOP` 时输出 `guess`（若 payload 合法）并结束。

## 兼容性与影响面

* 对外方法签名保持不变（避免改动 [OrchestratorService](file:///d:/metaspace/trae/java_swarm/main-service/src/main/java/com/example/mainservice/service/OrchestratorService.java) 的调用）。

* 默认情况下（大多数模型输出是普通文本 token），行为与现在一致。

* 仅当模型输出中确实包含示例那种 `{"status":"...","content":"..."}` 的 JSON 对象时，才进入多 event 分发逻辑。

## 验证方式

* 跑 `main-service` 的单测（重点是 `LlmStreamServiceTest` 与 `OrchestratorService*` 系列）。

* 通过静态页面 [chat\_stream.html](file:///d:/metaspace/trae/java_swarm/main-service/src/main/resources/static/chat_stream.html) 手工验证：

  * 原有 `question/summary/subAgentReplay/done/heartbeat` 仍正常；

  * 新增的 `phase/thinking/guess/card` 若前端未处理，将按“未知事件”落到通用显示（不会阻断）。

## 风险与回滚

* 风险：若模型输出偶发包含 `{...}` 但不是预期 JSON，对解析分支可能误判。

  * 缓解：只有在成功解析且 `status` 非空时才走路由；否则回退到“原样 token 输出”。

* 回滚：如果线上发现兼容性问题，可将“成功解析但 status 不在白名单”也回退到原样输出，或者通过开关禁用 JSON 路由。

