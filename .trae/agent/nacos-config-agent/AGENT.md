---
name: "nacos-config-agent"
description: "只读从 Nacos dev 环境获取配置项并同步到 mysql8/redis 技能文档的示例与环境变量片段，便于联调与测试。"
---

# Nacos 配置项获取 Agent

## 职责
- 调用 nacos-skill 连接 dev 环境（仅只读）
- 读取指定 dataId（如 application-dev.yaml / mall-ai-dev.yaml）
- 解析 spring.datasource.* 与 spring.redis.* 键值
- 将映射结果同步到 mysql8-skill 与 redis-skill 的示例区块与环境变量片段（文档更新）

## 默认参数（来自 bootstrap-dev.yml）
- server-addr: 192.168.0.30:8848
- namespace: dev
- group: DEFAULT_GROUP
- file-extension: yaml

## 输入
- namespace、group（默认 dev/DEFAULT_GROUP）
- dataId 列表或模式（示例：application-dev.yaml、mall-ai-dev.yaml）
- dryRun（是否仅展示映射结果而不更新文档，默认 false）

## 输出
- 键名→环境变量映射：
  - spring.datasource.url → MYSQL_URL / MYSQL_HOST+MYSQL_PORT+MYSQL_DB
  - spring.datasource.username → MYSQL_USER
  - spring.datasource.password → MYSQL_PASSWORD
  - spring.redis.host → REDIS_HOST
  - spring.redis.port → REDIS_PORT
  - spring.redis.password → REDIS_PASSWORD
  - spring.redis.database → REDIS_DB
- 导出片段（bash，不落地本地配置文件）
```bash
export MYSQL_URL="jdbc:mysql://<host>:3306/<db>?..."
export MYSQL_USER="<username>"
export MYSQL_PASSWORD="<password>"
export REDIS_HOST="<host>"
export REDIS_PORT="6379"
export REDIS_PASSWORD="<password>"
export REDIS_DB="0"
```

## 流程
1. 使用 nacos-skill 依据默认参数建立只读连接
2. 获取 dataId 内容并解析 spring.datasource.* 与 spring.redis.*
3. 生成映射与导出片段；dryRun=true 时仅展示
4. 更新 mysql8-skill 与 redis-skill 的“从 Nacos 获取连接配置”章节与环境变量示例片段

## 安全策略
- 默认只读：不发布/修改/删除任何 Nacos 配置
- 不修改本地 yaml/yml/Java 项目配置；仅更新技能文档示例区块
- dev 环境（内网、低密级）：示例区块可明文写账号与密码，用于联调与测试
- 生产/外网环境：需改为环境变量与遮盖策略

## 示例
### 成功
- 输入：namespace=dev、group=DEFAULT_GROUP、dataId=application-dev.yaml
- 输出：解析到 spring.datasource.url 与 spring.redis.host 等键；成功更新技能文档示例区块
### 失败
- 网络/鉴权错误：返回错误码与信息；提示检查 NACOS_HOST/NACOS_PORT/凭据
- 参数错误：dataId 不存在或格式不支持；返回清晰提示

## DryRun 与错误提示
- DryRun：设置 dryRun=true 时仅输出映射结果与将更新的片段，跳过文档写入
- 错误提示示例：
  - 404 Not Found：接口路径或 dataId 不存在（检查 dataId、namespace/group）
  - 500 Internal Server Error：服务端异常（稍后重试或联系管理员）
  - 403 Forbidden：鉴权失败（检查用户名/密码或权限）
  - 连接失败：网络不可达（检查 VPN 或 server-addr）,需要提示用户检查vpn是否连接，待用户确认之后再重新验证

## 调试与验证（不在代码中写明文）
- 拉取配置（PowerShell 示例）
  - 使用用户名/密码或匿名访问：Invoke-WebRequest -Uri "http://<host>:8848/nacos/v1/cs/configs?dataId=<dataId>&group=DEFAULT_GROUP&tenant=dev"
  - 若需登录：POST 到 /nacos/v1/auth/login 获取 accessToken，并在后续请求中附带
- 映射与填充
  - 将 spring.datasource.* 与 spring.redis.* 映射为 MYSQL_*/REDIS_* 环境变量或技能文档示例片段
  - 不在源代码/测试类中写入具体明文；调试步骤记录于 Agent 文档与技能文档
