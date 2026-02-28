# WebFlux 全流式对话与前端页面方案（去除 MVC 与非流式路径）

## 背景与目标

* main-service 完整迁移至 Spring WebFlux，移除 MVC 与任何非流式路径。

* 全链路尽可能早返回：用户请求进入后即开始推送阶段性结果与 LLM token，避免阻塞网络 I/O。

* 在 `common` 的 `RealQwenLlmClient` 增加流式调用能力；主流程以流式为唯一路径。

* 提供一个仅支持流式展示的前端页面，实时可视化输出。

* 不改动 YAML/YML/Nacos 配置。

## 技术方案总览

* 运行栈

  * 移除 `spring-boot-starter-web`，引入 `spring-boot-starter-webflux`，使用 Netty。

  * 控制器使用响应式风格（注解 + `Mono/Flux` 或函数式路由）。

* 流式协议：Server-Sent Events（SSE，`text/event-stream`）

  * `public_question_token`：公共问题生成的 token 流

  * `subagent_reply`：子代理完成回复后推送片段

  * `summary_token`：最终总结 token 流

  * `done`：流程完成

* 下游访问统一使用 `WebClient`（子代理、外部服务）。

* `RealQwenLlmClient` 提供回调式流方法，main-service 通过 `Flux.create` 桥接为 `Flux<String>`。

## 后端改造

### 1) 扩展 RealQwenLlmClient（流式）

* 文件：`common/src/main/java/com/example/common/llm/RealQwenLlmClient.java`

* 保留现有 `generate()`（兼容其他模块），但 main-service 不再使用该阻塞方法。

* 新增流式方法（回调式）：

  * `void generateStream(String systemPrompt, String userInput, java.util.function.Consumer<String> onToken, java.util.function.Consumer<Throwable> onError, Runnable onComplete)`

  * 设置 `stream: true` 调用 Qwen 流式接口；解析 SSE `data:` 行，逐次推送 `delta`

  * 复用 Key 池与重试/退避；异常/完成时调用对应回调

  * 内部懒加载 `WebClient`（保留 `RestTemplate` 供其他模块使用）

### 2) main-service 全流式接口（SSE）

* 仅保留一个对外接口：

  * `POST /api/shopping/stream`，`produces = text/event-stream`，返回 `Flux<ServerSentEvent<?>>`

  * 入参与原 `POST /api/shopping/submit` 一致（沿用 DTO）

* 事件约定：

  * `event: public_question_token`，`data: <partial-text>`

  * `event: subagent_reply`，`data: {"agentId": "...", "answer": "...", "quote": 123.45}`

  * `event: summary_token`，`data: <partial-text>`

  * `event: done`，`data: {"bestAgentId": "...", "total": 123.45}` 或空

* Reactive 编排流程：

  1. 基于提示词与用户请求，使用 `generateStream(...)` 实时生成公共问题并推送 `public_question_token`；生成完毕得到完整问题字符串
  2. 用 `WebClient` 拉取子代理列表（`/api/subagents`），并行请求 `/reply`；每个返回立刻推送 `subagent_reply`
  3. 选最优/备选后，调用 `generateStream(...)` 生成总结 token，逐个推送为 `summary_token`
  4. 推送 `done`

* 编解码：

  * main-service 使用 WebFlux 默认 Jackson；与 subagent-service Fastjson2 JSON 互通

  * 如需统一至 Fastjson2，后续另行提供 WebFlux `HttpMessageReader/Writer`

### 3) WebFlux 化依赖与装配

* 依赖（main-service）：

  * 移除：`spring-boot-starter-web`

  * 添加：`spring-boot-starter-webflux`

  * 移除：`RestTemplate` Bean 与所有使用

* 配置：

  * 不改 YAML/YML/Nacos；沿用现有端口

* Bean：

  * 继续通过工厂创建 `RealQwenLlmClient`；在需要流式时直接调用 `generateStream`

## 前端页面（仅流式）

* 位置：`main-service/src/main/resources/static/chat_stream.html`

* 能力：

  * 使用 `fetch` + `ReadableStream` 解析 `text/event-stream`，实时渲染 token 与阶段事件

  * 输入参数与后端 DTO 对齐；仅调用 `/api/shopping/stream`

  * UI：输入区 + 事件流展示（`public_question_token`、`subagent_reply`、`summary_token`、`done`）

## 落地步骤

1. `common`：为 `RealQwenLlmClient` 增加 `generateStream(...)`
2. `main-service`：POM 切换 WebFlux，删除 MVC/`RestTemplate`
3. 使用 `WebClient` 重写 orchestrator（Reactive 合并子代理结果）
4. 实现 `POST /api/shopping/stream`（SSE 事件流）
5. 新增 `chat_stream.html` 并联通验证
6. 编写单元/集成测试并手工验证

## 验证与回滚

* 单元测试：验证流式解析与事件顺序

* 集成测试：

  * `common` 增加 `RealQwenLlmClientStreamingIT`（真实 Qwen）：

    * 前置：设置环境变量 `QWEN_KEYS_FILE` 指向密钥文件（每行一个 key）

    * 行为：调用 `generateStream(...)`，日志打印 token，断言收到若干 token 且最终完成

  * `main-service` 增加 `ShoppingStreamEndpointIT`（真实 Qwen）：

    * 启动 Spring 上下文，`WebTestClient` 以 `text/event-stream` 调用 `/api/shopping/stream`

    * 打印并断言收到 `public_question_token`、`subagent_reply`、`summary_token`、`done`

* 手工：浏览器访问 `http://localhost:9090/chat_stream.html`

* 回滚：以分支方式回滚到 MVC 分支（不在同一服务内保留非流式）

## 不修改配置说明

* 不动 YAML/YML/Nacos，全部通过依赖与代码调整完成。

## 交付物

* 后端：

  * `RealQwenLlmClient` 流式回调实现

  * `POST /api/shopping/stream`（WebFlux SSE）

  * Reactive orchestrator（`WebClient` 并发子代理调用）

* 前端：

  * `static/chat_stream.html`（仅流式）

