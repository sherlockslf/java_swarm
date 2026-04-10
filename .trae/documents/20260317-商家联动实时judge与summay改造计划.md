# 商家联动、实时 Judge 与 Summay 改造计划（Plan）

## 1. 改造目标

* 对每个商家 Prompt 做小改造：要求在新一轮回复前，读取其他 subAgent 已有回复并做针对性调整，提升竞争力。

* 改造 judge 交互流程：主服务收到每条 `subAgentReplay` 后立即判定并输出结果，不再依赖“最终 summary 阶段里的 judge”。

* 新增 `summay.txt` 提示词与调用：当“当前轮次所有商家发言完成”后统一总结，点评每个商家并判断是否出现合适购买候选者，决定“结束对话/继续下一轮”。

* 增加轮次上限策略：若已执行超过 2 轮仍无合适候选者，则强制结束并输出多轮最终结论；若找到候选者，则输出选择理由与整体回顾。

## 2. 现状基线与差距

* 商家 Prompt 文件位于 `subagent-service/data/baonuanneiyi/prompts/seller_a.txt|seller_b.txt|seller_c.txt`，当前已支持结构化输出，但未强制“参考其他商家回复后再调整策略”。

* `judge.txt` 位于 `subagent-service/data/baonuanneiyi/prompts/judge.txt`，当前主流程是在 `OrchestratorService` 汇总回放后调用 LLM 输出 `summary`。

* `main-service/data/prompts/summary/summay.txt` 已存在但为空，当前代码没有读取与调用此文件。

* 当前 `stream` 主流程终止条件与 `summary` 事件绑定，需要改为：实时 judge 与轮次 summay 分离。

## 3. 详细实施步骤

### 步骤 A：重写三商家 Prompt 的“竞品感知”规则

1. 修改 `seller_a.txt`：加入“读取 `previousReplies/competitorReplies` 后，针对竞品短板强化自身优势并给出差异化报价说明”的硬约束。
2. 修改 `seller_b.txt`：加入“基于竞品报价与优惠结构做反制策略（券、打包、阈值说明）”规则，避免与上一轮重复话术。
3. 修改 `seller_c.txt`：强化多轮动态规则，要求明确说明“本轮相对其他商家的调整点”。
4. 三个 Prompt 保持现有 JSON 输出协议与字段，不改字段名与返回结构。

### 步骤 B：改造实时 judge 流程（按 replay 即时判定）

1. 在 `OrchestratorService` 的 `subAgentReplay` 事件链路中，新增“每收到一条 replay 就触发一次 judge 输出”的处理。
2. judge 输入改为“单条 replay + 当前用户约束 + 商品信息 + 必要上下文”，不再等待全量 replay 后统一 judge。
3. 新增独立事件输出（沿用或新增 event 名，按现有前端兼容策略执行）承载即时 judge 结果。
4. 将“judge 与 summary 强耦合”的旧逻辑拆开：保留 replay 记录能力，但移除“以 summary 作为 judge 唯一出口”的路径。
5. 更新 `judge.txt` 提示词：从“全量比选裁决”改为“单条回复即时审查 + 标准化判定输出”。

### 步骤 C：新增 Summay 阶段（轮次结束后统一总结与是否继续）

1. 在主流程新增读取 `main-service/data/prompts/summary/summay.txt` 的方法（支持环境变量覆盖，风格与现有 `readJudgePrompt` 一致）。
2. 设计并写入 `summay.txt`：要求对本轮每个 subAgent 回复做点评，重点判断是否存在合适购买候选者，并给出本轮优劣结论。
3. Summay 输出必须包含固定决策字段：是否结束、候选者信息、结束原因、整体回顾；其中“找到候选者/达到轮次上限/继续对话”三种状态可被代码稳定解析。
4. 在每轮全部发言完成后执行 summay：

   * 若判定存在合适候选者：结束对话，不展开下一轮；

   * 若不存在且当前轮次 ≤ 2：允许进入下一轮（结合现有 followUp 条件）。

   * 若不存在且当前轮次 > 2：强制结束，并输出多轮对话最终结果与失败原因归纳。
5. 当 summay 判定“已找到候选者”时，必须在总结中输出：候选者是谁、核心理由、价格与风险综合回顾。
6. 将流结束条件从“summary 到达即结束”改为“summay 决策或 error”驱动，避免误终止。

### 步骤 D：测试与回归验证

1. 更新/新增主流程测试，覆盖：

   * `subAgentReplay` 到达即触发 judge 事件；

   * 全员发言后触发 summay；

   * summay=结束 时不进入下一轮；

   * summay=继续 时进入下一轮（在 followUp 可用前提下）；

   * 超过 2 轮仍无候选者时，summay 强制结束并输出多轮最终结论；

   * 出现候选者时，summay 输出“选择理由 + 整体回顾”。
2. 运行 `main-service` 与 `subagent-service` 相关测试，验证事件顺序、字段结构与终止条件。
3. 进行一次端到端流式回放校验，确认事件序列符合：

   * `subAgentReplay`（多条）

   * 实时 `judge`（逐条）

   * 轮次末 `summay`

   * `done`

## 4. 涉及文件清单

* `subagent-service/data/baonuanneiyi/prompts/seller_a.txt`

* `subagent-service/data/baonuanneiyi/prompts/seller_b.txt`

* `subagent-service/data/baonuanneiyi/prompts/seller_c.txt`

* `subagent-service/data/baonuanneiyi/prompts/judge.txt`

* `main-service/data/prompts/summary/summay.txt`

* `main-service/src/main/java/com/example/mainservice/service/OrchestratorService.java`

* `main-service/src/test/java/com/example/mainservice/service/*`（按实际受影响用例调整）

## 5. 验收标准

* 三个商家 Prompt 均体现“参考其他 subAgent 回复后再改写本轮策略”的明确约束。

* 每条 `subAgentReplay` 后都能看到对应 judge 结果输出，不依赖最终 summary。

* `summay.txt` 被实际调用，且在“当前轮次全部发言后”输出对各商家点评与候选者判断。

* 当存在合适购买候选者时，流程在当前轮结束，并输出候选者理由与整体回顾。

* 当不存在候选者时，最多继续到第 2 轮；若超过 2 轮仍无候选者，流程强制结束并输出多轮最终结果。

* 全链路事件顺序稳定，无新增解析错误或字段断裂。

## 6. 风险与控制

* 风险：实时 judge 频率提升导致流事件变多。\
  控制：限制 judge 输出为紧凑结构，避免冗长自然语言。

* 风险：summay 决策字段不稳定影响轮次控制。\
  控制：在提示词中强制固定决策字段并在代码侧做兜底解析。

* 风险：Prompt 改动导致结构化输出漂移。\
  控制：保留现有 JSON 字段硬约束，必要时复用后端兜底校正。

