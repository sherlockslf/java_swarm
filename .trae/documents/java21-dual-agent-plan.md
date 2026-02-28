# Java 21 双服务 Agent 最小实现（Plan）

## 目标
- 基于 Java 21 与 Spring Boot 构建两个最小可运行服务：main-service 与 subagent-service
- 不接入数据库；以本地 JSON 文件作为配置与数据暂存
- main-service：接收用户场景输入（以购物为例），整理“公开提问 + 隐藏边界条件”，并并行调用多个 subAgent，汇总与再判断后给出最优回复
- subagent-service：管理所有 subAgent 元数据与各自 prompt；按不同 subAgent 的 prompt 与输入调用大模型，返回竞争性回复
- 兼顾后续扩展为更多场景（博弈等），并保持配置、prompt 可替换

## 技术栈与版本
- JDK: 21
- Spring Boot: 3.3.x
- 构建工具：Maven（多模块）或 Gradle（二选一；默认 Maven）
- 依赖（最小化）：
  - spring-boot-starter-web
  - spring-boot-starter-validation
  - com.alibaba.fastjson2:fastjson2
  - com.alibaba.fastjson2:fastjson2-extension（Spring 6 / Boot 3 支持）
  - spring-boot-starter-test（测试）
- LLM 调用：抽象接口 + 可插拔实现（默认 Mock 实现，不引入真实 SDK）；真实 Key 通过环境变量注入
 - HTTP JSON 序列化：注册 FastJsonHttpMessageConverter 为首选，覆盖默认 Jackson

## 总体架构
- main-service
  - 接口接收用户请求（购物场景）
  - 组装“公开问题”和“隐藏约束”
  - 调用 subagent-service 获取可用 subAgent 列表
  - 并行调用多个 subAgent 的回复接口
  - 压缩各回复 + 用户输入 + 隐藏约束，再次调用 LLM 做最优选择
- subagent-service
  - 管理 subAgent 元数据（JSON）
  - 为每个 subAgent 读取对应 prompt（文本）
  - 按 prompt 与输入调用 LLM 并返回
- 通信：HTTP/JSON
- 存储：模块内 data/ 目录下 JSON 与 .txt prompt 文件

## 模块与目录结构（建议）
```
java_swarm/
  main-service/
    src/...
    data/
      profiles/buyer/{profileId}.json
      scenarios/shopping/{scenarioId}/samples/*.json
      prompts/buyer/{profileId}.txt
  subagent-service/
    src/...
    data/
      subagents.json
      prompts/{agentId}.txt
  common/ (可选：共享 DTO 与工具)
  .trae/
    documents/
      java21-dual-agent-plan.md
```

说明：
- main-service 与 subagent-service 各自维护自身 data 目录，互不耦合
- 避免复杂 YAML 配置；如需端口等，使用最小 application.properties 或直接用默认值
- 文件读写与配置解析统一使用 fastjson2（JSON.parseObject/parseArray 等），避免混用多套 JSON 库

## JSON 与 Prompt 规范
- subagent-service/data/subagents.json
  - 字段：id, name, type（如 shopping）, promptPath, enabled
  - 示例：
    ```
    [
      {"id":"seller_a","name":"A 商家","type":"shopping","promptPath":"prompts/seller_a.txt","enabled":true},
      {"id":"seller_b","name":"B 商家","type":"shopping","promptPath":"prompts/seller_b.txt","enabled":true}
    ]
    ```
- subagent-service/data/prompts/{agentId}.txt：每个 subAgent 的系统提示词
- main-service/data/profiles/buyer/{profileId}.json：购物者身份与边界（预算、品牌偏好等）
- main-service/data/prompts/buyer/{profileId}.txt：采购方（购物者）提示词模板

## LLM 抽象
- 定义接口 LlmClient（prompt, input -> output）
- 提供 MockLlmClient（可配置 deterministic 输出，便于本地与测试）
- 预留 RealLlmClient（依赖环境变量注入 Key 与模型名；默认不启用）
- main-service 与 subagent-service 各自通过配置选择实现（默认 Mock）
- 统一 JSON 序列化策略：HTTP 层与本地文件读写均基于 fastjson2，实现一致的字段命名与容错

## 接口设计（初版）
- main-service
  - POST /api/shopping/submit
    - 入参 ShoppingRequest：
      ```
      {
        "items":[{"name":"iPhone 15","quantity":1}],
        "budget":6000,
        "brandsPreferred":["Apple"],
        "brandsExcluded":[],
        "notes":"支持分期",
        "profileId":"default"
      }
      ```
    - 返回 AggregatedDecision：
      ```
      {
        "bestAgentId":"seller_b",
        "bestAnswer":"...最终推荐...",
        "justification":"...选择理由...",
        "alternatives":[
          {"agentId":"seller_a","summary":"..."},
          {"agentId":"seller_c","summary":"..."}
        ],
        "debug":{
          "publicQuestion":"...主问题...",
          "hiddenConstraints":{"budget":6000,"brandsPreferred":["Apple"] }
        }
      }
      ```
  - GET /api/subagents?type=shopping（代理调用 subagent-service 或直接由 main 调用对方接口）
- subagent-service
  - GET /api/subagents?type=shopping
    - 返回 SubAgentInfo 列表（id、name、type）
  - POST /api/subagents/{id}/reply
    - 入参：
      ```
      {
        "question":"...公开问题...",
        "context":{
          "items":[...],
          "budget":6000,
          "brandsPreferred":["Apple"]
        }
      }
      ```
    - 返回：
      ```
      {
        "agentId":"seller_a",
        "answer":"...该商家竞争性回复...",
        "meta":{"model":"mock","tokens":123}
      }
      ```

## 核心流程（购物示例）
1) main-service 接收 ShoppingRequest
2) 读取 profileId 对应的购物者 prompt 与边界（预算、品牌等）
3) 调用 LLM 生成：
   - 公开问题 publicQuestion（可给 subAgent）
   - 隐藏约束 hiddenConstraints（不对外）
4) 调用 subagent-service 获取 type=shopping 的 subAgent 列表
5) 并行调用每个 subAgent 的 /reply（带上 publicQuestion + 基础上下文）
6) main-service 收集全部回复，分别做压缩摘要（LLM 或规则）
7) 将摘要 + 用户输入 + 隐藏约束再喂给 LLM，得到最优选择与理由
8) 返回聚合结果

## 异常与超时处理
- subAgent 单个失败或超时：记录并继续，以已返回的结果进行评估
- 当所有 subAgent 都失败：回退到“无法给出建议”的标准响应
- LLM 接口异常：在 Mock 模式下可回退为规则响应

## 运行与配置
- 端口（建议）：
  - main-service: 8080
  - subagent-service: 8081
- 配置最小化：
  - application.properties 可只保留 server.port
  - LLM 相关通过环境变量启用真实实现（如 OPENAI_API_KEY, MODEL_NAME），默认不配置则走 Mock
- 遵循“不随意创建/复制 YAML/Nacos 配置”的原则，仅保留本地最小启动必需项

## 实现步骤（执行顺序）
1) 初始化 Maven 多模块骨架（main-service、subagent-service、可选 common）
2) 定义 DTO 与 LlmClient 抽象 + Mock 实现；引入 fastjson2 依赖
   - 在两个服务中注册 FastJsonHttpMessageConverter（优先级最高），覆盖默认 Jackson
3) subagent-service：
   - 加载 subagents.json 与 prompts/{id}.txt
   - 实现 GET /api/subagents、POST /api/subagents/{id}/reply
4) main-service：
   - 加载 buyer profiles 与购物者 prompt
   - 实现 POST /api/shopping/submit：组装问题、并发调用 subAgent、压缩与最优选择
5) 提供示例数据与 prompt（最小样例）
6) 集成测试与端到端验证（Mock LLM）
7) 运行文档与接口说明

## 测试计划
- 单测：DTO 校验、JSON 加载器、并发聚合逻辑
- 集成测试：
  - subagent-service：按示例 prompt 返回稳定 Mock
  - main-service：端到端提交购物请求，校验最优选择结构
- 数据样例：提供 2–3 个 subAgent 与 1 个 buyer profile

## 验收标准
- 两个服务均可独立启动，接口可用
- 无外部 DB；JSON 配置可被加载，prompt 可被替换
- 在 Mock LLM 模式下，完整跑通购物流程并返回结构化最优结果
- 禁止提交任何真实密钥与敏感信息

## 后续扩展
- 新增“博弈”场景：在 main-service 增加新的场景处理器与 buyer prompt；在 subagent-service 增加 type=game 的 subAgent 与对应 prompt
- 引入评分维度：价格、品牌匹配度、售后等，最优选择可参考多指标
- 可替换 LLM 提供商：通过环境变量切换 RealLlmClient 实现

## 假设与约束
- 不引入数据库，仅使用本地 JSON 与文本
- 配置文件最小化，避免复杂 YAML/Nacos 依赖
- 真实 LLM Key 仅通过环境变量注入，不写入仓库
