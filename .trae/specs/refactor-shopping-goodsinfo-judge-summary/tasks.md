# Tasks
- [x] Task 1: 让 subagent-service 使用 baonuanneiyi 的 seller prompts
  - [x] SubTask 1.1: 更新 subagent-service/data/subagents.json 的 promptPath 指向 baonuanneiyi/prompts
  - [x] SubTask 1.2: 增加回归校验：subagent 列表与 prompt 读取不报错

- [x] Task 2: 在子导购 prompt 组装时注入 GoodsInfo
  - [x] SubTask 2.1: 在 subagent-service 读取 goos.txt 并以 <GoodsInfo> 段落拼接到 merged 输入
  - [x] SubTask 2.2: 增加最小测试/验证：merged 中包含 <GoodsInfo> 与商品 JSON

- [x] Task 3: main-service 重写 summaryFlux（判官评判）
  - [x] SubTask 3.1: 从 replayMap 汇总各商家“完整回复”（过滤 DONE）
  - [x] SubTask 3.2: 构造 judge 的 user 输入（显性输入 + <hidden> + <GoodsInfo> + <subAgentReplay>）
  - [x] SubTask 3.3: summaryFlux 使用 llmStreamService.streamTokens 输出 summary，并保留 fallback

- [x] Task 4: 修复流式输出契约与回归测试
  - [x] SubTask 4.1: 确保 1 个 done 事件最终发送（含 bestQuote/error）
  - [x] SubTask 4.2: 确保 heartbeat 不导致主流双订阅（保持上游只执行一次）
  - [x] SubTask 4.3: 更新/新增单测覆盖：DONE 不覆盖 replayMap、summary 输入包含关键标签

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 2
- Task 4 depends on Task 3
