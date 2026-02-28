# SSE 事件缺失与前端展示修复计划（后端与前端协同）

## 背景与现象

* 控制台已能看到 `sumary` 事件，但页面未展示总结文本。

* 未收到 `subAgintInfo` 与 `subAgentReplay` 事件，导致中列没有“聊天室式”的代理对话视图。

* 事件名存在拼写问题：`sumary` 应更正为 `summary`（前后端一致）。

## 目标

1. 后端确保按阶段输出完整事件序列：`subAgintInfo` → 多个 `subAgentReplay`（含最后一次 text=DONE） → 多个 `summary` → `done`。
2. 前端按事件名正确渲染：

   * `subAgintInfo`：渲染代理列表

   * `subAgentReplay`：为各代理追加右侧对话气泡；遇到 DONE 标记该代理对话完成

   * `summary`：在总结区域实时追加文本
3. 修正拼写并加入必要日志，定位与追踪更清晰。

## 后端改动计划

1. 修正事件名

   * 将所有 `sumary` 统一更正为 `summary`（Orchestrator/LlmStreamService/Controller 如有直出）。
2. Orchestrator 事件管线稳健化

   * 获取代理列表 `agents` 后，使用 `share()`/`cache(…)` 避免多次订阅导致的副作用或遗漏：

     * `agentsShared = agents.share()`（或 `publish().refCount(1)`）

     * 一路 `collectList()` 生成并发送 `subAgintInfo`

     * 一路 `flatMap` 逐个请求子代理，产出 `subAgentReplay`（文本）与 `subAgentReplay`（text="DONE"）

   * 对目录拉取与子代理调用增加 `onErrorResume` 容错：

     * 目录失败：发送空数组的 `subAgintInfo`，继续流程但跳过 `subAgentReplay`

     * 单个子代理失败：仅对该代理输出一条 `subAgentReplay` text="DONE" 以结束其会话\
       PSA：为防止“只看到 summary”的情况，确保 `subAgintInfo`/`subAgentReplay` 分支永远在 `summary` 之前执行，且任何错误不会短路整个流。
3. 流程顺序与数据依赖

   * `public_question_token` 完成后再发起代理阶段（保留现有顺序）

   * 总结阶段仅在存在至少一个代理回复时触发；否则跳过 `summary`，直接 `done`
4. 日志增强（已部分具备）

   * 目录获取：开始/成功/失败

   * 子代理请求：开始/成功/失败（并打印 agentId）

   * 事件发送：`subAgintInfo` 条数、每条 `subAgentReplay` 的 agentId 与长度、`summary` 启动/结束

## 前端改动计划（Vue 单页）

1. 事件名对齐

   * 将 `sumary` 的监听与渲染统一改为 `summary`
2. 渲染完善

   * `subAgintInfo`：重置并填充代理列表（头像位/名称），准备各自对话容器

   * `subAgentReplay`：

     * 若 `text !== "DONE"`：将文本追加至该代理的右侧对话气泡

     * 若 `text === "DONE"`：标记该代理对话完成（例如“已完成”）

   * `summary`：在“总结”区域实时追加文本（非气泡样式）
3. 控制台日志

   * 继续保留 `[REQ]`、`[SSE]`、`[UI]` 日志，便于快速定位事件链路
4. 可选（若你需要）

   * 在页面右上角增加“调试开关”切换日志的详细程度

## 测试与验证

1. 后端集成测试

   * 访问 `/api/shopping/stream`，在 20 秒内断言出现 `subAgintInfo`、`subAgentReplay`、`summary`、`done` 中的若干关键事件
2. 手工验证（浏览器）

   * F12：观察 `subAgintInfo`、`subAgentReplay`（含 DONE）、`summary` 的到达与渲染

   * 断网/子代理服务不启动场景：仍能收到空 `subAgintInfo`，并在 `done` 结束（不出现 `summary` Sop）

## 交付标准

* 事件序列完整、顺序清晰、错误不短路；`summary` 拼写统一。

* 页面三列内容完整：中列像聊天室一样按代理分开展示、总结有可视化输出。

* 控制台日志覆盖请求和关键节点，便于排查。

