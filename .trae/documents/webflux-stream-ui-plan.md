# WebFlux 流式对话与前端页面方案

## 背景与目标

* 从 main-service 发起的用户请求支持“流式对话”：在编排调用各个 subagent 的过程中，及时把阶段性结果推送到前端；在最终总结时，模型输出改为流式返回。

* 在 `common` 模块的 `RealQwenLlmClient` 中增加多种调用模式（非流式/流式），以便主流程可选择性地使用。

* 提供一个简易前端页面，可直接在浏览器输入请求并查看结果（先支持非流式，随后支持流式展示）。

* 遵循现有约束：不改动 YAML/YML 等配置；保持对现有接口的向后兼容。

## 技术方案总览

* 服务端全面切换至 Spring WebFlux 架构（去除 MVC）：

  * 移除 `spring-boot-starter-web`，引入 `spring-boot-starter-webflux`，运行时使用 Netty

  * 控制器改为 Reactive 风格（`@RestController` 返回 `Mono/Flux` 或函数式 `RouterFunction`/`HandlerFunction`）

* 流式通道采用 Server-Sent Events（SSE，`text/event-stream`）：

  * `public_question`：面向子代理的公共问题生成结束后推送

  * `subagent_reply`：每个子代理完成回复后推送其结果

  * `summary_token`：最终总结调用 LLM 的流式 token（逐片推送）

  * `done`：整次流程完成

* 对下游/子代理与 LLM 的调用统一使用 `WebClient`

* `RealQwenLlmClient` 新增“流式”调用能力，保持现有 `generate()` 不变；并提供回调式 token 推送，main-service 通过桥接适配为 `Flux<String>`

## 后端改造

### 1) 扩展 RealQwenLlmClient 的调用模式

* 文件：`common/src/main/java/com/example/common/llm/RealQwenLlmClient.java`

* 保留现有同步方法：

  * `String generate(String systemPrompt, String userInput)`（不变）

* 新增流式方法（回调式，避免在 common 中引入 Reactor）：

  * `void generateStream(String systemPrompt, String userInput, java.util.function.Consumer<String> onToken, java.util.function.Consumer<Throwable> onError, Runnable onComplete)`

    * 请求体添加 `stream: true`，调用 Qwen `/chat/completions` 流式接口

    * 解析服务端 `data:` 行（SSE），提取内容增量并逐次调用 `onToken.accept(delta)`

    * 复用现有的 Key 池与重试/退避逻辑；在异常与完成时分别触发 `onError`/`onComplete`

  * 内部采用懒加载 `WebClient`（保留 `RestTemplate` 用于非流式）

  * 不修改接口 `LlmClient`，以免影响现有注入点；主流程可按需拿到具体实现使用流式方法

### 2) main-service 流式接口（SSE）

* 接口：

  * `POST /api/shopping/stream`，`produces = text/event-stream`，返回 `Flux<ServerSentEvent<?>>`

  * 入参与现有 `POST /api/shopping/submit` 一致（DTO 沿用当前定义）

* 事件约定：

  * 入参与现有 `POST /api/shopping/submit` 一致（DTO 沿用当前定义）

- 返回事件约定：

* 编排逻辑（与现有 `ShoppingOrchestrator` 语义对齐，改为响应式）：

  * `event: public_question`，`data: <string>`

  1. 使用 `WebClient` 拉取子代理列表（`/api/subagents`），并行请求各 subagent 的 `/reply`；每个返回后推送一次 `subagent_reply`（`flatMap`/`merge`）
  2. 聚合出最优与备选，进入总结步骤：

     * 调用 `RealQwenLlmClient.generateStream(...)` 并通过 `Flux.create` 桥接为 `Flux<String>`

     * 将每个 token 包装为 `summary_token` 事件
  3. 完成后推送 `done`

* 消息编解码：

  * WebFlux 默认采用 Jackson。为降低风险，main-service 暂以 Jackson 为 JSON 编解码（与 subagent-service 的 Fastjson2 兼容）

  * 后续如需统一 Fastjson2，可再引入 WebFlux 对应的 `HttpMessageReader/Writer` 扩展（不在本次范围）

### 3) 依赖与装配（WebFlux 化）

* 依赖变更（main-service）：

  * 移除：`spring-boot-starter-web`

  * 添加：`spring-boot-starter-webflux`

  * 移除 `RestTemplate` Bean 与相关使用，统一改用 `WebClient`

* 配置与端口：

  * 不调整 YAML/YML/Nacos；沿用现有 `application.properties` 的端口

* Bean 装配：

  * 保持 `RealQwenLlmClient` 的装配方式不变（通过工厂）；在需要流式时获取其具体实现并调用 `generateStream`

## 前端页面

### 1) 非流式演示页（优先交付）

* 位置：`main-service/src/main/resources/static/chat.html`

* 功能：在同源下调用 `POST /api/shopping/submit`，将返回 JSON 直接展示

* 目的：不改动后端即可快速联通，验证业务链路与编排正确性

### 2) 流式演示页

* 位置：`main-service/src/main/resources/static/chat_stream.html`

* 功能：使用 `fetch` + `ReadableStream` 解析 `text/event-stream`：

  * 左侧为输入区，右侧实时滚动显示事件：

    * `public_question`：显示为“公共问题”

    * `subagent_reply`：按子代理分组显示

    * `summary_token`：实时拼接显示“总结”文本

    * `done`：显示“完成”与汇总信息

* 说明：因 `EventSource` 仅支持 GET，考虑到后端沿用现有 DTO，流式端点采用 `POST` + `text/event-stream`，前端用 `fetch` 流式读取。

## 落地步骤

1. 在 `common` 扩展 `RealQwenLlmClient`，新增 `generateStream(...)` 回调式流方法；保持现有 `generate()` 不变
2. main-service 依赖切换到 WebFlux，删除 MVC 依赖与 `RestTemplate` Bean
3. 使用 `WebClient` 重写：

   * 获取子代理列表

   * 调用各 subagent `/reply`
4. 新增 `POST /api/shopping/stream`（`Flux<ServerSentEvent<?>>`）：

   * 推送 `public_question`、`subagent_reply`、`summary_token`、`done`
5. （可选）保留一个非流式 `POST /api/shopping/submit` 的 Reactive 版本（返回 `Mono<AggregatedDecision>`），对外行为不变
6. 新增前端页面：

   * `chat.html`（非流式，快速联通）

   * `chat_stream.html`（流式，可视化事件与 token）
7. 自测与回归（见下）

## 验证与回滚

* 单元测试：

  * 为 `RealQwenLlmClient` 的 SSE 解析编写测试（模拟 `data:` 行，验证 token 回调顺序与完结）

* 集成测试：

  * 使用 `WebTestClient`（或 `MockMvc` + 自定义解析）验证 `POST /api/shopping/stream` 的事件序列与事件格式

* 手工验证：

  * 启动 main-service，打开 `http://localhost:9090/chat.html` 与 `http://localhost:9090/chat_stream.html` 分别验证非流式与流式

  * 观察阶段性事件与总结 token 是否按预期到达

* 回滚策略：

  * 所有新增能力均为增量：保留 `POST /api/shopping/submit` 不变；如流式接口出现问题，可暂时隐藏路由或由前端切换回非流式页面

## 风险与应对

* 风险：WebFlux 切换对现有控制器与 Bean 的影响

  * 应对：逐一迁移 MVC 控制器到 Reactive 方式；删除 `RestTemplate` 相关代码；保留 DTO 与业务逻辑不变

* 风险：Qwen 流式返回格式变化或网络抖动

  * 应对：保留 MVC 作为主栈，仅将新接口写为 `Flux<ServerSentEvent<?>>`；如运行期发现不生效或行为异常，降级到 `SseEmitter` 实现

- 风险：Qwen 流式返回格式变化或网络抖动

## 不修改配置说明

* 全过程不改动 YAML/YML/Nacos 等配置；仅进行代码与依赖层改造，符合当前项目约束。

## 交付物

* 后端：

  * `RealQwenLlmClient` 的流式回调方法

  * `POST /api/shopping/stream` 流式接口（SSE）

* 前端：

  * `static/chat.html`（非流式）

  * `static/chat_stream.html`（流式）

