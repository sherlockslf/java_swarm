# 购物流式事件改造实施计划（Plan）

## 1. 目标与范围

* 目标A：`/api/shopping/stream` 不再向接口请求方输出 `agentJudge` 事件。

* 目标B：重定义 `subAgentReplay` 事件 `data` JSON 字段，仅保留并输出以下字段：\
  `subAgentId, quote, round, tag, skuId, message, price, coupon`

* 范围模块：

  * `main-service`（SSE 事件编排与输出、回放缓存、总结输入构造）

  * `main-service` 测试（service/web）

  * 前端演示页解析（`simple_web` 与 `chat_stream.html`）的兼容性验证（按需最小改动）

## 2. 现状定位结论

* `agentJudge` 当前在 `OrchestratorService.runRound` 中由 `buildAgentJudgeEvent` 构造并输出。

* `subAgentReplay` 当前在 `SubAgentDialogueService.toEvents` 里构造，字段包含 `agentId/product_id/reason_tags/coupon_info/...` 等旧结构。

* `OrchestratorService.cacheReplay` 仅缓存 `subAgentReplay` 事件，后续 `buildJudgeUserInput` 依赖其旧字段（如 `agentId/product_id/coupon_info/risk_flags`）拼装总结输入。

* 现有测试已显式断言 `subAgentReplay` 与部分字段语义，需要同步更新断言。

## 3. 详细实施步骤

### 步骤1：移除对外 `agentJudge` 事件输出（仅外发层）

1. 在 `OrchestratorService.runRound` 删除 `buildAgentJudgeEvent` 的外发拼接逻辑，不再把 `agentJudge` 事件并入返回流。
2. 保留内部判定流程（`pass/severe/reasons/risk_flags`）用于重试与业务决策，确保行为不退化。
3. 清理无用方法/引用（若 `buildAgentJudgeEvent` 失去用途则删除），保证代码整洁。

### 步骤2：重定义 `subAgentReplay` 事件字段结构

1. 在 `SubAgentDialogueService.toEvents` 按新协议改造 replay payload：

   * `subAgentId` ← 旧 `agentId`

   * `quote` ← 旧 `quote`

   * `round` ← 旧 `round`

   * `tag` ← 旧 `reason_tags`（数组）

   * `skuId` ← 旧 `product_id`

   * `message` ← 旧 `message`

   * `price` ← 从 `meta` 中优先提取成交价语义字段；若缺失，按既有报价逻辑回退到 `quote`

   * `coupon` ← 旧 `coupon_info`
2. 删除 replay payload 中未在新协议列出的字段（如 `risk_flags/cta/judge` 等），确保对外结构收敛。
3. 保持 `event` 名称仍为 `subAgentReplay`，避免前端事件分支失效。

### 步骤3：同步调整编排层对 replay 的消费逻辑

1. 更新 `OrchestratorService.cacheReplay` 的回放解析兼容新字段命名。
2. 更新 `buildJudgeUserInput` 的字段读取映射：

   * `agentId` 改读 `subAgentId`

   * `product_id` 改读 `skuId`

   * `coupon_info` 改读 `coupon`

   * `reason_tags` 改读 `tag`
3. 针对缺省字段做兜底（空字符串/空数组/空对象）以避免总结阶段 NPE 或序列化异常。

### 步骤4：更新测试与前端最小兼容

1. 更新 main-service 相关测试断言：

   * 不再断言出现 `agentJudge` 事件；

   * `subAgentReplay` 改断言新字段（`subAgentId/tag/skuId/price/coupon`）。
2. 检查并修正前端演示解析（若引用旧键名则改为新键名，或增加读取兼容）。
3. 保持 `subAgentDone/summary/done` 等其他事件行为不变。

### 步骤5：按项目规范完成全量验证与结果沉淀

1. 编译检查：执行 `mvn clean compile`，记录各模块结果。
2. 测试检查：执行 `mvn test`，记录通过率与失败修复。
3. 服务联调验证：

   * 启动 `subagent-service`（9091）、`main-service`（9090）、`simple_web`（5173）；

   * 调 `POST /api/shopping/stream` 验证流中不再出现 `agentJudge`，且 `subAgentReplay` 为新字段结构；

   * 调 `GET /api/subagents?type=shopping` 与 `POST /api/subagents/{id}/reply` 验证基础能力未受影响。
4. 结果文件：按规范生成唯一结果集\
   `.trae/requestResult/YYYYMMDD-shopping-stream-result-HHMMSS.md`，完整记录改动与验证日志。

## 4. 验收标准

* SSE 响应中不再出现 `event:agentJudge`。

* 每条 `subAgentReplay` 的 `data` 仅包含并可正确解析以下键：\
  `subAgentId, quote, round, tag, skuId, message, price, coupon`

* 总结阶段可正常产出 `summary`，且流以 `done` 正常结束。

* 全量编译/测试通过；联调接口状态码与结构化字段符合预期。

* 结果集文件生成且内容可追溯、无伪造数据。

* 三个微服务运行后，必须使用给定模拟入参完成联调测试，并将测试全过程与原始结果完整保存到同一个结果集文件中，不得遗漏、不得拆分或生成多个结果文件。

## 5. 风险与规避

* 风险1：总结输入仍依赖旧键，导致总结质量下降或空值异常。\
  规避：在 `buildJudgeUserInput` 做新旧映射与兜底，并补充回归测试。

* 风险2：`price` 字段来源在部分代理回复中缺失。\
  规避：优先读取成交价语义字段，缺失回退 `quote`，并在结果集中记录策略。

* 风险3：前端展示存在旧字段硬编码。\
  规避：完成 `simple_web` 与静态页联调，必要时做兼容读取。

