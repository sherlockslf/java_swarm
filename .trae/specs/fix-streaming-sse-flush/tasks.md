# Tasks
- [x] Task 1: 增加 reactive LLM 流式接口并移除手动订阅
  - [x] SubTask 1.1: 在 RealQwenLlmClient 中新增返回 Flux 的流式方法（解析 data 行并产出 delta）
  - [x] SubTask 1.2: 保持 key pool acquire/release 生命周期在响应式链路内可控（避免泄漏）
  - [x] SubTask 1.3: 删除/替换流式实现中的 publishOn 与 subscribe 主路径（保留旧方法仅作兼容可选）

- [x] Task 2: 改造 main-service 的 SSE 输出链路为真流式
  - [x] SubTask 2.1: LlmStreamService 使用新的 reactive 流式方法映射为 ServerSentEvent（逐增量发送）
  - [x] SubTask 2.2: 为主流添加可选 heartbeat 事件并与主流合并输出（对齐参考实现）
  - [x] SubTask 2.3: 确保 OrchestratorService 的 question/summary 在 done 前可持续产出事件

- [x] Task 3: 增加可复现/可验证的自动化校验
  - [x] SubTask 3.1: 为 Qwen stream 解析增加单元测试（给定 chunk 输入，输出 delta 序列）
  - [x] SubTask 3.2: 为 LlmStreamService 增加测试或最小集成验证（确保在 complete 前已有事件发出）

- [x] Task 4: 手动验收与回归检查
  - [x] SubTask 4.1: 使用 chat_stream.html 观察 question/summary 的增量渲染（不应末尾集中输出）
  - [x] SubTask 4.2: 对照 console4.txt 现象，确认事件到达与渲染的时间分布更均匀

- [x] Task 5: 修复 OrchestratorService 的双订阅与时序问题
  - [x] SubTask 5.1: 让 heartbeat 的停止条件与主流共享同一订阅（避免 mainFlux 被订阅两次）
  - [x] SubTask 5.2: 修复 subAgent 请求使用空 question 快照的问题（确保取到最终 publicQuestion）
  - [x] SubTask 5.3: 将 agentReplay 的错误处理改为“单个 subAgent 失败不影响整体”（避免整体变空）

- [x] Task 6: 保证 summary 事件必定发布（含 fallback）
  - [x] SubTask 6.1: bestAnswer 非空时，summary 在 done 前至少发布 1 个事件
  - [x] SubTask 6.2: summary LLM 流式无增量/报错时，发送可观测的 fallback summary

- [x] Task 7: 增加回归校验并执行代码质量检查
  - [x] SubTask 7.1: 增加针对 OrchestratorService 的回归测试（覆盖 summary 发布与主流不重复执行）
  - [x] SubTask 7.2: 使用 chat_stream.html 复现 console5.txt 并对齐参考实现的“持续到达”效果
  - [x] SubTask 7.3: 运行 Maven 质量检查（至少 mvn test）并确保通过

- [x] Task 8: 修复 question 事件缺失并提供兜底输出
  - [x] SubTask 8.1: question LLM 流式无增量/报错时，至少发送 1 个 question 事件（fallback）
  - [x] SubTask 8.2: 增加回归测试覆盖 question fallback 与 subAgent 入参不为空

# Task Dependencies
- Task 2 depends on Task 1
- Task 3 depends on Task 1 and Task 2
- Task 4 depends on Task 2
- Task 5 depends on Task 2
- Task 6 depends on Task 5
- Task 7 depends on Task 5 and Task 6
- Task 8 depends on Task 5
