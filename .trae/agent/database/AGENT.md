---
name: "database"
description: "依据用户执行目录中的数据库技能（mysql8-skill、redis-skill、clickhouse-skill 等）连接信息执行只读交互（MySQL/Redis/ClickHouse），为其它 Agent 提供数据验证能力。"
---

# Database Agent

## 职责
- 自动识别并列示用户执行目录可用的数据库 Skill（环境变量优先，Nacos 可选）
- 读取 mysql8-skill、redis-skill、clickhouse-skill 的“配置项填写区域”或环境变量（优先）
- 执行只读的 MySQL 查询（SELECT/SHOW/DESCRIBE）
- 执行只读的 Redis 命令（GET/MGET/SCAN/TYPE/INFO）
- 执行只读的 ClickHouse 查询（SELECT/SHOW/DESCRIBE，支持分页/导出）
- 提供结构化结果与错误提示，支援其它 Agent 的数据验证流程
- 在测试流程中支持插入数据（需二次确认）
- 支持用户要求的数据写入（需二次确认）
- 所有写操作均生成本地审计记录（不改动项目配置文件）
- 与其它 Agent 联动从代码库中获取实体类/领域模型信息，基于实体关系生成复杂 SQL，并在只读策略下执行与返回结果

## 支持技能
- mysql8-skill：MySQL 8.0 连接、健康检查、对象列表、只读查询与导出；环境变量 MYSQL_URL/MYSQL_HOST/MYSQL_PORT/MYSQL_USER/MYSQL_PASSWORD/MYSQL_DB 等
- redis-skill：Redis 连接、PING、键扫描与只读获取；环境变量 REDIS_URL/REDIS_HOST/REDIS_PORT/REDIS_PASSWORD/REDIS_DB/REDIS_NAMESPACE 等
- clickhouse-skill：ClickHouse 连接、健康检查、对象列表、只读查询与分页/导出；环境变量 CLICKHOUSE_URL/CLICKHOUSE_HOST/CLICKHOUSE_PORT/CLICKHOUSE_USER/CLICKHOUSE_PASSWORD/CLICKHOUSE_DB 等
- 发现策略：优先读取环境变量；如由 Nacos 提供连接配置，则在运行时映射为环境变量；不落地或修改本地配置文件

## 输入
- MySQL：MYSQL_URL 或 MYSQL_HOST/PORT/DB、MYSQL_USER、MYSQL_PASSWORD
- Redis：REDIS_HOST、REDIS_PORT、REDIS_PASSWORD、REDIS_DB、REDIS_NAMESPACE（可选）
- ClickHouse：CLICKHOUSE_URL 或 CLICKHOUSE_HOST/PORT/DB、CLICKHOUSE_USER、CLICKHOUSE_PASSWORD、CLICKHOUSE_PAGE_SIZE（可选）
- 查询/命令：SQL 语句或 Redis 命令与参数
- 限制：最大返回行数/数量、分页大小、超时
- 写入审计文件路径：DB_WRITE_AUDIT_PATH（可选，默认 .trae/agent/database/write-audit.log）
- 实体驱动查询（可选）：entitySource（来源 Agent/文件路径）、entities（实体名/表映射）、relations（实体间关联）、select（字段/聚合）、filters（条件，参数化）、orderBy、groupBy、having、limit、offset、dialect（mysql/clickhouse）

## 输出
- 结果：JSON/Markdown 表格（包含列名/键名），附带统计与采样说明
- 错误：标准化错误结构（网络/鉴权/语法/超时）
- 实体驱动模式：返回生成的 SQL 与参数、执行结果、统计信息（行数/耗时）、安全提示（是否达到默认限制/是否触发只读保护）

## 写入能力与审计
- 开启条件：默认只读；任何写操作（测试插入或用户要求的数据写入）均需二次确认
- 支持范围：插入类操作（MySQL/ClickHouse 的 INSERT/UPSERT；Redis 的 SET/HSET 等）按需开放；非插入类写操作需再次明确范围与确认
- 审计记录：每次写入都会追加记录到本地文件（默认路径 d:\metaspace\trae\mall-ai-dev\mall-ai\.trae\agent\database\write-audit.log，或由 DB_WRITE_AUDIT_PATH 指定）；不记录明文密码等敏感信息
- 审计内容：时间戳、技能类型（mysql8/redis/clickhouse）、目标库/键、语句/命令摘要、操作者/来源 Agent、结果状态、影响行数/键数量、请求哈希（用于追踪）
- 执行流程：预览写入→确认步骤一→确认步骤二→执行→写入审计→返回结果

## 使用示例
- MySQL（只读）
  - SQL: SELECT id,status FROM orders WHERE status='ACTIVE' LIMIT 100;
  - SHOW TABLES; DESCRIBE orders;
- Redis（只读）
  - GET app:order:123; MGET app:order:1 app:order:2
  - SCAN 0 MATCH "app:order:*" COUNT 100
  - TYPE app:order:123; INFO
- ClickHouse（只读）
  - SQL: SELECT id,status FROM orders WHERE status='ACTIVE' LIMIT 100;
  - SHOW DATABASES; DESCRIBE TABLE orders;
  - 分页查询：设置 CLICKHOUSE_PAGE_SIZE 后分页返回
- 实体驱动复杂查询（只读）
  ```json
  {
    "agent": "database",
    "input": {
      "mode": "entity-query",
      "entitySource": "project-structure-agent",
      "entities": [
        { "name": "User", "table": "users", "alias": "u" },
        { "name": "Order", "table": "orders", "alias": "o" }
      ],
      "relations": [
        { "from": "u.id", "to": "o.user_id", "type": "inner" }
      ],
      "select": [
        "u.id", "u.name", "o.id as order_id",
        { "agg": "COUNT", "field": "o.id", "alias": "order_cnt" }
      ],
      "filters": [
        { "field": "u.status", "op": "=", "param": "ACTIVE" },
        { "field": "o.created_at", "op": ">=", "param": "2025-01-01" }
      ],
      "groupBy": ["u.id", "u.name"],
      "orderBy": [{ "field": "order_cnt", "dir": "DESC" }],
      "limit": 100,
      "offset": 0,
      "dialect": "mysql"
    },
    "output": {
      "sql": "SELECT u.id,u.name,o.id AS order_id,COUNT(o.id) AS order_cnt FROM users u INNER JOIN orders o ON u.id=o.user_id WHERE u.status=? AND o.created_at>=? GROUP BY u.id,u.name ORDER BY order_cnt DESC LIMIT 100 OFFSET 0",
      "params": ["ACTIVE", "2025-01-01"],
      "rows": 42,
      "elapsedMs": 128
    }
  }
  ```

## 安全与约束
- 默认只读：禁止 INSERT/UPDATE/DELETE/DDL；禁止 DEL/SET/EXPIRE 等有副作用命令
- dev 环境示例可明文账号/密码（仅限内网联调）；生产需环境变量与遮盖策略
- 不落地或修改本地配置文件；运行时读取或由 nacos-config-agent 填充示例
- 写操作策略：仅在完成二次确认后执行；所有写操作必须生成本地审计记录；不写入或修改 yaml/yml/java 等项目配置文件
- 实体驱动查询生成遵循参数化与默认限制（LIMIT/分页）以防注入与慢查询；仅在用户明确开启并二次确认时才生成与执行插入/更新类 SQL（并记录审计）

## 与其它 Agent 联动
- 由业务 Agent 提供验证目标（如订单状态、缓存有效性）
- database Agent 执行只读交互并返回结构化结果
- 将验证结论反馈给业务 Agent 或产出检查报告
- 与 project-structure Agent 或领域建模 Agent 联动：读取实体/关系定义，构造 SQL（包含 JOIN/聚合/过滤/排序/分页），在只读模式下执行并返回结果；如需写入则走二次确认与审计流程

## 调试与验证（不在代码中写明文）
- 网络连通性（PowerShell）
  - MySQL：Test-NetConnection <MYSQL_HOST> -Port <MYSQL_PORT>
  - Redis：Test-NetConnection <REDIS_HOST> -Port <REDIS_PORT>
  - ClickHouse：Test-NetConnection <CLICKHOUSE_HOST> -Port <CLICKHOUSE_PORT>
- MySQL 只读验证
  - 使用运行时环境变量或技能文档示例填充：MYSQL_URL、MYSQL_USER、MYSQL_PASSWORD
  - 在应用或脚本中执行 SELECT 1；确保仅限只读语句
- Redis 只读验证
  - 使用运行时环境变量或技能文档示例填充：REDIS_HOST、REDIS_PORT、REDIS_PASSWORD、REDIS_DB
  - 执行 PING/MGET/SCAN/TYPE 等无副作用命令
- ClickHouse 只读验证
  - 使用运行时环境变量或技能文档示例填充：CLICKHOUSE_URL、CLICKHOUSE_USER、CLICKHOUSE_PASSWORD、CLICKHOUSE_DB、CLICKHOUSE_PAGE_SIZE（可选）
  - 执行 SELECT 1/SHOW DATABASES/DESCRIBE 等无副作用语句
- 说明
  - 明文连接信息仅出现在技能文档的 dev 示例区块，用于联调；不在源代码/测试类中出现
