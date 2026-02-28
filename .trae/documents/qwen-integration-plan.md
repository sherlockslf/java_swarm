# Qwen（DashScope 兼容 OpenAI）接入计划（Plan）

## 目标

* 在现有最小双服务架构内接入 Qwen 模型，统一走 common 模块的 LlmClient 抽象。

* 请求基址使用 <https://dashscope.aliyuncs.com/compatible-mode/v1（OpenAI> Chat Completions 兼容协议）。

* 读取 d:/metaspace/trae/java\_swarm/common/src/main/java/com/example/common/key/keys.txt 的若干个 key，构建「Key 轮询池 + 并发限流 + 重试」机制。

* main-service 与 subagent-service 均不需要改调用点，只需通过 Spring Bean 注入切换到 Qwen 实现。

## 兼容协议概述（OpenAI Chat Completions）

* Endpoint: POST {base\_url}/chat/completions

* Header: Authorization: Bearer ${API\_KEY}, Content-Type: application/json

* Request（最小化）:

  ```
  {
    "model": "<模型名>", 
    "messages": [
      {"role":"system","content":"<系统提示词>"},
      {"role":"user","content":"<用户输入>"}
    ],
    "temperature": 0.2,
    "stream": false
  }
  ```

* Response（关心字段）:

  ```
  {
    "choices":[{"message":{"role":"assistant","content":"..."},"finish_reason":"stop"}],
    "usage":{"prompt_tokens":..., "completion_tokens":..., "total_tokens":...}
  }
  ```

## 设计与落地

1. LLM 实现

   * 新增 RealQwenLlmClient 实现 LlmClient：

     * generate(systemPrompt, userInput) → 组装 messages，调用 Chat Completions，同步返回 choices\[0].message.content

     * 后续可扩展支持工具调用、流式等，但首期以非流式、最小字段为主

   * 使用 Spring 的 RestTemplate（已引入）发起 HTTP 调用，fastjson2 进行序列化/反序列化

   * 配置项（全部环境变量或系统属性，避免 YAML 变更）：

     * QWEN\_BASE\_URL（默认：<https://dashscope.aliyuncs.com/compatible-mode/v1）>

     * QWEN\_MODEL（默认：qwen2.5-7b-instruct 或由你指定）

     * QWEN\_TEMPERATURE（默认：0.2）

     * QWEN\_MAX\_CONN（默认：keys.size \* 3）

     * QWEN\_TIMEOUT\_MS（默认：15000）

2. Key 池与并发限流

   * 新增 QwenKeyPool：

     * 启动时读取 keys.txt（逗号或换行分隔，去空白后过滤空值）

     * 使用无锁的 AtomicInteger + 数组取模或使用 ConcurrentLinkedQueue + 轮询

   * 并发限流：

     * 使用 java.util.concurrent.Semaphore 实现全局并发上限（默认 keys.size \* 3，可由 QWEN\_MAX\_CONN 覆盖）

     * 每次请求前 acquire，结束后 release；保证高压下不会超额请求

3. 重试与错误处理

   * 针对 401（认证失败）：切换下一个 key 并快速重试，最多 N 次（默认 2）

   * 针对 429（限流）：指数退避（如 200ms、400ms、800ms），切换 key 后重试，最多 M 次（默认 3）

   * 针对 4xx/5xx 其它错误：记录日志并返回空字符串或受控异常；保持调用方稳定

   * 日志仅记录必要上下文，不记录明文 key

4. Spring 注入与切换

   * 新增 LlmClientFactory：

     * 若检测到环境变量 LLM\_PROVIDER=qwen 且可读取到合法 keys，则注入 RealQwenLlmClient

     * 否则保持 MockLlmClient（默认行为）

   * main-service 与 subagent-service 均通过 @Bean LlmClient 注入，无需改业务代码

5. 安全与合规

   * keys.txt 仅用于本地开发演示；严禁提交真实 key

   * 线上/容器环境建议只用环境变量或密钥管理系统，不走文件

6. 性能与稳定性

   * 使用虚拟线程或 CompletableFuture 均可；现有 main-service 并发已基于虚拟线程，可复用

   * RestTemplate 配置超时（连接/读超时），避免请求悬挂

   * 响应只取 choices\[0]，后续可扩展为更复杂的选择逻辑

## 代码结构（拟新增）

```
common/
  src/main/java/com/example/common/llm/
    RealQwenLlmClient.java     // 实现 generate → Chat Completions
    LlmClientFactory.java      // 根据环境选择 Qwen 或 Mock
  src/main/java/com/example/common/llm/qwen/
    QwenKeyPool.java           // 读取 keys.txt，提供轮询取 key
    QwenApiModels.java         // 请求/响应模型（fastjson2 映射）
```

## 集成步骤（实施序）

1. 增加 QwenKeyPool：读取 keys.txt，提供 getNextKey()
2. 定义 QwenApiModels：ChatRequest、Message、ChatResponse（choices/message/usage）
3. 实现 RealQwenLlmClient：

   * 组装请求体 → 调用 → 解析 → 返回 content

   * 超时、Header、错误映射、重试策略
4. 实现 LlmClientFactory：根据 LLM\_PROVIDER 与 key 可用性选择 Bean
5. 在两个服务的配置类中使用工厂 Bean（替换直接 new Mock 的地方，仅修改 Bean 创建处）
6. 验证：

   * 使用 keys.txt（占位 key 也可，期望 401 可触发切换逻辑）

   * 正常 key 下，调用 main-service → subagent-service 全链路返回
7. 文档与开关：

   * README 说明环境变量与 keys.txt 的读取规则与优先级

   * 保持 application.properties 最小化，不新增 YAML

## 回归与验收

* 在 LLM\_PROVIDER=mock 与 LLM\_PROVIDER=qwen 两种模式下均可运行

* keys 池与并发上限有效；在 401/429 等场景下稳定重试与降级

* 现有最小购物场景端到端可用

## 风险与缓释

* 401/429：已设计 key 切换与退避重试

* keys.txt 未找到或为空：自动回退至 Mock（避免阻断业务）

* 第三方网络波动：设置合理超时 + 降级返回

