# 项目结构 Agent（project-structure）

## 职责
- 为用户与其它 Agent 生成“完整的、具备结构层次信息的项目描述文档”
- 文档内容涵盖：
  - 项目代码逻辑概要与典型调用流程
  - 项目文件结构（树状图，遵循 .gitignore）
  - 最近一次 Git 提交记录与变更内容摘要（commit/diff）
  - 常规 README 中的各个章节（概述、技术栈、使用方式、目录结构、约定、维护等）
- 支持模块/层次筛选与忽略规则，便于针对性分析
- 在用户指定时，读取工作区并生成 MD 文档输出到工作目录
- 在与其它 Agent 联动时，先检查项目是否存在新的提交或代码变更；若无变更可直接使用历史 MD，否则必须重新执行完整生成流程

## 依赖
- 基础技能：readme-structure-skill（用于解析 README 与目录结构）
- Git 仓库可读访问（用于获取最近一次提交记录与 diff）
- 项目文件：README.md（作为需求与目录的主要来源之一）
- 忽略规则：遵循 .gitignore 中的路径与模式

## 输入
- action：执行模式（describe | generate-md | respond-with-md，默认 describe）
- modules：按模块名筛选（如 "core, ui, api"）
- layers：按层次筛选（如 "domain, application, infrastructure"）
- include/exclude：包含或排除的路径模式（glob/正则）
- maxDepth：目录遍历最大深度（默认 6）
- includeGit：是否包含最近一次提交与变更内容（默认 true）
- includeCallFlow：是否分析典型调用流程（默认 true）
- format：输出格式（markdown | json，默认 markdown）
- outputFile：生成文档文件名/路径（默认 "PROJECT_DESCR.md"，位于工作目录）
- readonly：是否只读（默认 true；当 action=generate-md 时仅允许“写入说明文档”）
- cachePolicy：联动策略（prefer-existing | force-refresh，默认 prefer-existing）

## 输出
- Markdown：完整项目描述文档，包含：
  - 概述与技术栈
  - 目录树与模块/层次说明
  - 代码逻辑与典型调用流程
  - 最近一次 Git 提交与变更摘要（提交信息、涉及文件、关键 diff 摘要）
  - 常规 README 章节（安装/运行、环境要求、约定、测试/发布指引）
- JSON：结构化摘要（root、directories、files、ignored、stats、notes、latestCommit、generatedAt）
- 当 action=generate-md：返回生成的 MD 文件路径与统计信息
- error：统一错误结构（code、message、hint）

## 调用示例
```json
{
  "agent": "project-structure",
  "input": {
    "action": "generate-md",
    "modules": ["core", "api"],
    "layers": ["domain", "application"],
    "include": ["src/**", "docs/**"],
    "exclude": ["**/node_modules/**", "**/.git/**"],
    "maxDepth": 6,
    "includeGit": true,
    "includeCallFlow": true,
    "format": "markdown",
    "outputFile": "PROJECT_DESCR.md",
    "readonly": false
  },
  "output": {
    "file": "d:/project/PROJECT_DESCR.md",
    "stats": { "dirs": 12, "files": 87 },
    "latestCommit": { "hash": "abc123", "author": "dev", "subject": "feat: add order flow" }
  }
}
```

```json
{
  "agent": "project-structure",
  "input": {
    "action": "respond-with-md",
    "cachePolicy": "prefer-existing",
    "format": "markdown"
  },
  "output": {
    "fileUsed": "d:/project/PROJECT_DESCR.md",
    "generatedAt": "2026-03-05T10:00:00Z",
    "lastCommitWhenGenerated": "abc123",
    "rechecked": true,
    "hasNewChanges": false
  }
}
```

## 安全约束
- 默认严格只读：不进行写入、删除或修改任何代码与文档
- 当用户明确指定 action=generate-md 时，仅允许在工作目录写入新的说明文档（MD），不改动现有代码与配置
- 遵循 .gitignore：对被忽略的路径不做扫描与输出
- 不触碰配置：不创建/修改本地配置副本（yaml/yml/properties 等）
- 不输出或记录敏感信息（密钥、口令、令牌等）

## 声明
- 本 Agent 提供结构描述与说明文档生成，不包含数据库/缓存连接调试与验证流程
- 连接调试与验证请参见：nacos-config-agent 与 database Agent 的“调试与验证”章节

## 生成规则
- 文档默认命名为 PROJECT_DESCR.md，位于工作目录；可通过 outputFile 覆盖
- 文档内容建议包含以下章节：
  - 项目概述与技术栈
  - 目录结构树（遵循 .gitignore，按 maxDepth 限制）
  - 模块与分层说明（modules/layers 过滤后）
  - 代码逻辑概要与典型调用流程
  - 最近一次 Git 提交记录与关键变更摘要（commit 信息、涉及文件、重要 diff）
  - 常规 README 章节（安装、运行、配置、约定、测试、发布）
- 文档头部写入元信息（YAML Front Matter）：generatedAt、lastCommit、scanPaths、filters（modules/layers）

## 联动规则
- 当其它 Agent 请求“使用历史 MD 回复”时：
  - 读取现有文档的元信息（generatedAt、lastCommit）
  - 对比当前仓库 HEAD 与工作区变更（commit hash、工作区未提交修改）
  - 若无新提交且无工作区变更，则直接采用历史 MD
  - 若存在新提交或变更，则按本 Agent 职责重新执行完整生成流程，并更新 MD
