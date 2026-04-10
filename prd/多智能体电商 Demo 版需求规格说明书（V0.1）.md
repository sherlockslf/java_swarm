# 多智能体电商 Demo 版需求规格说明书（V0.1）



---

# 多智能体电商协同平台 最小可行Demo版 需求规格说明书(RPD)

文档版本：V0.1  

编制日期：2026年04月05日  

文档状态：正式版

核心目标：2周内完成端到端核心流程验证，所有非必要功能全部砍掉

---

## 1. 项目概述

### 1.1 项目背景

为验证多智能体电商协同平台的核心技术可行性和业务价值，本项目将开发一个最小可行Demo。通过简化所有非核心流程，快速实现"用户创建智能体→智能体自动部署→智能体执行购物任务→智能体间对话交互"的完整闭环。

### 1.2 Demo核心目标

- 验证阿里云ACK动态部署智能体的能力

- 验证智能体自动注册到Nacos并被发现的能力

- 验证智能体调用内部电商API的能力

- 验证智能体之间自主对话交互的能力

- 验证用户画像对智能体行为的影响

### 1.3 范围定义

#### 必须包含的功能

- 手动输入用户ID完成"登录"

- 用户画像文档上传与解析

- 子智能体创建、部署、启动、停止

- 智能体自动注册到Nacos

- 预设商品API调用（含商品库、SPU-SKU、竞价查询）

- 智能体间对话与消息存储

- 任务执行（含商品购买、小红书宣传）

- 任务结果展示

- 商品信息系统核心查询功能

- 小红书社交模块核心功能（token获取、发帖、评论）

#### Demo阶段完全砍掉的功能

- 用户注册、密码登录、权限管理

- 资源配额管理、计费系统

- API用户注册、审核流程

- 用户自定义提示词、模板管理

- 复杂DAG任务编排、异常重试

- 监控告警、日志收集

- 多租户隔离（所有智能体运行在同一个K8s命名空间）

- 移动端支持

- 小红书账号绑定、token自动刷新

- 商品库批量导入、SKU库存管理

- 小红书帖子数据统计、评论审核

---

## 2. 系统整体架构

系统分为四层：

1. Web前端层：用户交互界面

2. 后端服务层：包含用户画像服务、子智能体管理服务、对话管理服务、任务执行服务、商品信息服务、小红书社交服务

3. 基础设施层：MySQL数据库、Nacos注册中心、阿里云ACK集群、阿里云百炼

4. 智能体运行时层：用户分身智能体Pod、商品导购智能体Pod

核心交互逻辑：

- 用户通过Web前端操作

- 后端服务处理业务逻辑

- 子智能体管理服务调用阿里云ACK部署智能体

- 智能体启动后自动注册到Nacos

- 智能体之间通过Nacos发现并通信

- 智能体调用商品信息服务、小红书社交服务及其他后端服务完成任务

---

## 3. 核心业务流程

完整端到端流程：

1. 用户在Web端输入用户ID，前端本地存储后进入系统

2. 用户上传用户画像文档

3. 用户画像服务保存文档，调用阿里云百炼解析并生成结构化画像

4. 用户创建"用户分身智能体"

5. 子智能体管理服务调用阿里云ACK部署Pod

6. 用户分身智能体启动后自动注册到Nacos

7. 用户创建"商品导购智能体"

8. 子智能体管理服务调用阿里云ACK部署Pod

9. 商品导购智能体启动后自动注册到Nacos

10. 用户点击"开始购物任务"或"开始小红书宣传任务"

11. 任务执行服务调用用户分身智能体发送任务指令

12. 用户分身智能体获取用户画像

13. 若为购物任务：用户分身智能体调用商品信息服务，查询商品库、SPU-SKU关联信息及竞价数据，与导购智能体协商价格

14. 若为小红书宣传任务：用户分身智能体调用小红书社交服务，获取用户token权限，查询商品信息编写文案，执行发帖及评论相关操作

15. 用户分身智能体返回最终结果给任务执行服务

16. 任务执行服务保存结果并展示给用户

---

## 4. 功能性需求详细说明

### 4.1 用户"登录"与画像管理

#### 4.1.1 手动登录功能

- 界面：Web首页只有一个输入框和"进入系统"按钮

- 逻辑：用户输入任意字符串作为user_id，点击按钮后，前端将user_id存储在localStorage中，跳转到主界面

- 无验证：不检查user_id是否存在，不做任何身份验证

- 会话保持：刷新页面不丢失登录状态

#### 4.1.2 用户画像管理

- 上传功能：支持上传PDF和TXT格式的文档

- 存储：文档存储在服务器本地文件系统，路径格式：/data/profiles/{user_id}/{文件名}

- 解析：调用阿里云百炼文档解析API，将文档内容转换为纯文本

- 结构化：调用大模型将纯文本转换为JSON格式的用户画像，包含：

```JSON
{
  "name": "张三",
  "age": 28,
  "occupation": "程序员",
  "budget": 3000,
  "preferences": ["性价比高", "续航好", "拍照清晰"],
  "dislikes": ["高价低配", "系统卡顿"]
}
```

- 查看功能：显示当前用户已上传的画像文档列表和解析后的结构化内容

### 4.2 子智能体管理服务

#### 4.2.1 智能体类型

仅支持两种预定义的智能体类型：

1. 用户分身智能体：代表用户执行任务，调用API和其他智能体

2. 商品导购智能体：提供商品咨询和价格谈判服务

#### 4.2.2 智能体创建

- 界面：下拉选择智能体类型，输入智能体名称，点击"创建"按钮

- 逻辑：

- 生成唯一instance_id：{agent_type}-{user_id}-{随机字符串}

- 生成service_name：{agent_type}-{user_id}

- 保存到数据库，状态为PENDING

- 调用ACK API创建Deployment和Service

- 监听Pod状态，变为Running后更新数据库状态为RUNNING

#### 4.2.3 智能体生命周期管理

- 列表查看：显示当前用户的所有智能体，包含名称、类型、状态、创建时间

- 启动/停止：点击按钮启动或停止智能体，更新数据库状态

- 删除：点击按钮删除智能体，同时删除ACK中的Deployment和Service

#### 4.2.4 智能体镜像

- 基础镜像：采用默认Java 21镜像，适配当前子智能体Java语言框架（后期可切换其他框架）

- 所有智能体实例均基于Java 21基础镜像构建，部署阶段的镜像详情将在后续开发中补充完善，当前以实现Demo落地为核心

### 4.3 API注册服务（极简版）

- 预配置API：系统预置4个核心API，硬编码在代码中，涵盖商品信息查询、小红书社交操作，无需用户注册，直接调用

- API信息：

- 1. 名称：查询热门手机

- 地址：http://product-service/api/products/hot

- 方法：GET

- 返回值：3款手机的列表，包含名称、价格、配置、图片

- 2. 名称：商品库信息查询

- 地址：http://product-service/api/products/search

- 方法：GET

- 参数：keyword（商品关键词）、pageNum（页码）、pageSize（每页条数）

- 返回值：商品列表，包含商品ID、SPU ID、SKU ID、名称、价格、配置、图片、库存状态

- 3. 名称：SPU-SKU关联信息查询

- 地址：http://product-service/api/products/spu/{spuId}

- 方法：GET

- 返回值：SPU基础信息（名称、描述、主图）、该SPU下所有SKU信息（SKU ID、规格、价格、库存），并自动返回3个关联推荐商品（同品类、同价位）

- 4. 名称：商品竞价信息查询

- 地址：http://product-service/api/products/bid/{productId}

- 方法：GET

- 返回值：当前商品不同渠道的竞价列表，包含渠道名称、报价、优惠力度、发货时效

- 5. 名称：小红书用户token获取

- 地址：http://xiaohongshu-service/api/token/get

- 方法：POST

- 参数：userId（平台用户ID）、xhAccount（小红书账号）、xhPassword（小红书密码，Demo版明文传输，仅用于测试）

- 返回值：access_token、expires_in（有效期）、refresh_token

- 6. 名称：小红书发帖

- 地址：http://xiaohongshu-service/api/post/publish

- 方法：POST

- 参数：access_token、title（帖子标题）、content（帖子内容）、productId（关联商品ID）、imageUrls（图片链接列表，Demo版用测试链接）

- 返回值：postId（帖子ID）、status（发布状态）、message（提示信息）

- 7. 名称：小红书帖子评论

- 地址：http://xiaohongshu-service/api/comment/publish

- 方法：POST

- 参数：access_token、postId（帖子ID）、content（评论内容）

- 返回值：commentId（评论ID）、status（发布状态）

- 8. 名称：小红书帖子评论查询

- 地址：http://xiaohongshu-service/api/comment/list/{postId}

- 方法：GET

- 参数：access_token、pageNum、pageSize

- 返回值：评论列表，包含commentId、userId（小红书用户ID）、content、createTime

调用方式：智能体通过任务执行服务调用所有API，无需额外鉴权（Demo版简化）

### 4.4 对话管理服务

#### 4.4.1 会话管理

- 对话参与方：用户、用户分身智能体，以及平台内其他相关智能体（如商品导购智能体）共同参与对话，形成协同交互模式

- 职责划分：用户主要负责核心决策、支付等必要操作；用户分身智能体负责任务拆解、多智能体交互、任务分发以及监督任务完整执行，无需用户干预中间流程

- 会话管理：不限制会话数量，支持用户及用户分身智能体同时发起多个会话，与不同智能体进行交互，满足多任务并行处理需求

- 会话ID：采用{user_id}_{user_agent_id}_{target_agent_id}_{随机字符串}格式，确保每个会话唯一，便于区分和查询

#### 4.4.2 消息管理

- 存储所有智能体间的消息，包含发送者ID、接收者ID、内容、时间戳

- 支持按会话ID查询所有历史消息

- 消息内容为纯文本，不支持其他格式

### 4.5 提示词管理服务（极简版）

- 硬编码提示词：为两种智能体提供固定的系统提示词，补充小红书相关提示逻辑

- 用户分身提示词：

```Plain Text
你是用户的购物助手，代表用户执行购物任务和小红书宣传任务。
用户画像：{user_profile}

你的任务是：
1. 购物任务：调用商品API查询商品库、SPU-SKU关联信息及竞价数据，与导购智能体协商价格，提交购买决策供用户确认
2. 小红书宣传任务：调用小红书API获取用户token，查询目标商品信息，编写符合用户画像的宣传文案，执行发帖及评论操作，处理简单异常
3. 全程自主拆解任务、分发任务，监督任务完成，无需用户干预中间流程
```

- 商品导购提示词：

```Plain Text
你是手机店的导购员，热情专业，善于推荐商品。
你可以根据用户需求推荐合适的手机，结合商品竞价数据进行价格谈判。
最低可以给用户打9折。
当用户分身智能体查询SPU-SKU信息时，需清晰说明各SKU规格差异及推荐理由。
```

- 动态注入：在智能体启动时，将用户画像注入到提示词中

### 4.6 任务执行服务（极简版）

- 任务清单决策：平台不固定任务流程，仅预置若干任务模板，具体执行的任务清单由用户分身智能体自主决定，适配不同场景需求

- 平台预置任务模板（Demo版），用户分身智能体可根据用户需求选择模板并调整任务细节，核心模板如下：
                  商品购买模板：包含核心任务步骤——① 调用商品库查询API，根据用户画像筛选商品；② 调用SPU-SKU关联查询API，获取目标商品及关联推荐商品；③ 调用竞价查询API，获取不同渠道价格；④ 对比分析数据（价格、规格、竞价优势）；⑤ 与导购智能体协商价格；⑥ 提交购买决策供用户确认

- 小红书宣传贴发布模板：包含核心任务步骤——① 调用商品库查询API，获取目标商品详细信息；② 根据用户画像及商品特点编写宣传文案（适配小红书风格，突出商品优势）；③ 调用小红书token获取API，获取用户账号权限（Demo版手动输入小红书账号密码，预留自动授权接口）；④ 调用小红书发帖API，上传文案及测试图片，执行发帖操作；⑤ 调用小红书评论查询API，获取帖子评论列表；⑥ 可根据评论内容，调用评论发布API回复核心评论；⑦ 监控发帖、评论结果，处理简单异常情况（如token过期、发帖失败，提示用户重新获取token）

- 任务提交：用户点击"开始任务"按钮后，由用户分身智能体根据用户需求（结合用户画像）选择对应预置模板，自主拆解任务清单并执行

- 状态跟踪：实时更新任务整体状态（PENDING/RUNNING/SUCCESS/FAILED）及各子任务执行状态，便于用户及用户分身智能体监控

- 结果展示：展示任务执行的完整过程、各子任务结果及最终整体结果，供用户查看和确认核心决策

### 4.7 商品信息系统设计（新增）

#### 4.7.1 核心功能

- 商品库内信息查询：支持按关键词模糊查询商品，返回商品完整基础信息，适配用户分身智能体筛选商品的需求，Demo版仅支持手机品类查询

- SPU-SKU层级关系查询：明确SPU（标准化产品单元）与SKU（库存单位）的关联关系，查询某一SPU时，返回该SPU下所有SKU的规格、价格、库存差异，并自动推荐3个同品类、同价位关联商品，辅助用户分身智能体对比选择

- 竞价信息查询：针对单个商品，返回不同渠道的竞价数据，包含渠道名称、报价、优惠力度、发货时效，为用户分身智能体与导购智能体砍价、选择最优购买渠道提供依据

#### 4.7.2 数据关联

- 商品库信息与智能体任务联动：用户分身智能体执行购物任务时，自动调用商品信息相关API，将查询结果同步给商品导购智能体，用于价格协商和推荐

- SPU-SKU数据与用户画像联动：根据用户画像中的预算、偏好，优先展示符合条件的SKU及关联推荐商品，提升任务执行效率

### 4.8 小红书社交模块设计（新增）

#### 4.8.1 用户token权限管理

- token获取：Demo版采用手动输入小红书账号密码的方式，调用token获取API，获取access_token（用于后续发帖、评论操作），暂不实现token自动刷新、权限校验等复杂逻辑

- token存储：获取到的token关联当前平台user_id，存储在数据库中，供用户分身智能体调用小红书相关API时直接使用，有效期内无需重复获取

- 权限范围：仅获取小红书账号的发帖、评论、查看评论权限，不获取其他敏感权限（如私信、账号管理）

#### 4.8.2 发帖及评论发布设计

- 发帖功能：用户分身智能体调用发帖API，传入token、标题、文案、商品ID及测试图片链接，完成帖子发布；Demo版不支持视频发布、话题标签、地理位置等附加功能，仅实现基础文本+图片发帖

- 评论发布功能：用户分身智能体可调用评论发布API，传入token、帖子ID、评论内容，完成对目标帖子的评论；支持对自己发布的帖子评论，暂不支持对他人帖子评论（简化逻辑）

- 异常处理：若token过期、发帖内容违规（Demo版仅做简单关键词校验），返回明确错误提示，由用户分身智能体提示用户重新获取token或修改文案

#### 4.8.3 帖子评论查询设计

- 查询功能：用户分身智能体调用评论查询API，传入token、帖子ID，分页获取该帖子下的所有评论，包含评论ID、发布者、内容、发布时间

- 联动逻辑：查询到评论后，用户分身智能体可根据评论内容，自主决定是否发布回复评论（如回复用户疑问、补充商品信息），实现评论互动的自动化

- 简化设计：暂不支持评论点赞、删除、筛选等功能，仅实现基础评论查询，满足Demo阶段任务需求

---

## 5. 数据模型设计

### 5.1 用户画像表

```SQL
CREATE TABLE user_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL COMMENT '手动输入的用户ID',
    file_name VARCHAR(256) NOT NULL COMMENT '原文件名',
    file_path VARCHAR(512) NOT NULL COMMENT '本地存储路径',
    file_size BIGINT NOT NULL COMMENT '文件大小(字节)',
    content TEXT COMMENT '文档纯文本内容',
    structured_content JSON COMMENT '结构化用户画像',
    status VARCHAR(32) NOT NULL DEFAULT 'UPLOADED' COMMENT 'UPLOADED/PROCESSING/READY/ERROR',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_id(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 5.2 智能体实例表

```SQL
CREATE TABLE agent_instances (
    instance_id VARCHAR(64) PRIMARY KEY COMMENT '智能体实例ID',
    user_id VARCHAR(64) NOT NULL COMMENT '所属用户ID',
    agent_type VARCHAR(32) NOT NULL COMMENT 'USER_AVATAR/PRODUCT_GUIDE',
    name VARCHAR(128) NOT NULL COMMENT '智能体名称',
    status VARCHAR(32) NOT NULL COMMENT 'PENDING/RUNNING/STOPPED/ERROR/DELETED',
    service_name VARCHAR(128) NOT NULL COMMENT 'Nacos服务名',
    pod_ip VARCHAR(64) COMMENT 'Pod IP地址',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id(user_id),
    INDEX idx_status(status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 5.3 会话表

```SQL
CREATE TABLE conversations (
    conversation_id VARCHAR(128) PRIMARY KEY COMMENT '会话ID',
    user_id VARCHAR(64) NOT NULL COMMENT '所属用户ID',
    user_agent_id VARCHAR(64) NOT NULL COMMENT '用户分身智能体ID',
    guide_agent_id VARCHAR(64) NOT NULL COMMENT '商品导购智能体ID',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_id(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 5.4 消息表

```SQL
CREATE TABLE messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    conversation_id VARCHAR(128) NOT NULL COMMENT '会话ID',
    sender_id VARCHAR(64) NOT NULL COMMENT '发送者智能体ID',
    receiver_id VARCHAR(64) NOT NULL COMMENT '接收者智能体ID',
    content TEXT NOT NULL COMMENT '消息内容',
    timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_conversation_id(conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 5.5 任务表

```SQL
CREATE TABLE demo_tasks (
    task_id VARCHAR(64) PRIMARY KEY COMMENT '任务ID',
    user_id VARCHAR(64) NOT NULL COMMENT '所属用户ID',
    user_agent_id VARCHAR(64) NOT NULL COMMENT '执行任务的智能体ID',
    task_type VARCHAR(32) NOT NULL COMMENT 'PRODUCT_PURCHASE/XIAOHONGSHU_PUBLISH',
    status VARCHAR(32) NOT NULL COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
    product_result TEXT COMMENT '商品查询结果',
    comparison_result TEXT COMMENT '对比结果',
    negotiation_result TEXT COMMENT '砍价结果',
    xiaohongshu_result TEXT COMMENT '小红书操作结果（含postId、commentId）',
    final_result TEXT COMMENT '最终结果',
    error_message TEXT COMMENT '错误信息',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

### 5.6 商品信息相关表（新增）

#### 商品表复用现有逻辑，暂不进行更新，表结构及其字段更新在其他文档中。

### 5.7 小红书相关表（新增）

#### 5.7.1 小红书用户token表

```SQL
CREATE TABLE xiaohongshu_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL COMMENT '平台用户ID',
    xh_account VARCHAR(128) NOT NULL COMMENT '小红书账号',
    access_token VARCHAR(256) NOT NULL COMMENT '访问令牌',
    refresh_token VARCHAR(256) COMMENT '刷新令牌',
    expires_in INT COMMENT '有效期（秒）',
    expire_time DATETIME COMMENT '过期时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_id(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

#### 5.7.2 小红书帖子表

```SQL
CREATE TABLE xiaohongshu_post (
    post_id VARCHAR(64) PRIMARY KEY COMMENT '小红书帖子ID',
    user_id VARCHAR(64) NOT NULL COMMENT '平台用户ID',
    title VARCHAR(256) NOT NULL COMMENT '帖子标题',
    content TEXT NOT NULL COMMENT '帖子内容',
    product_id VARCHAR(64) COMMENT '关联商品ID',
    image_urls JSON COMMENT '图片链接列表',
    status VARCHAR(32) NOT NULL COMMENT 'PUBLISHED/FAILED',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

#### 5.7.3 小红书评论表

```SQL
CREATE TABLE xiaohongshu_comment (
    comment_id VARCHAR(64) PRIMARY KEY COMMENT '评论ID',
    post_id VARCHAR(64) NOT NULL COMMENT '关联帖子ID',
    user_id VARCHAR(64) NOT NULL COMMENT '平台用户ID',
    xh_user_id VARCHAR(64) COMMENT '小红书用户ID',
    content TEXT NOT NULL COMMENT '评论内容',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_post_id(post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 6. 技术选型与环境配置

### 6.1 技术栈

|组件|技术选型|版本|说明|
|---|---|---|---|
|后端框架|Spring Boot|3.3.2|简化开发|
|服务注册发现|Nacos|2.4.2|使用云服务|
|数据库|MySQL|8.0.36|使用云服务|
|容器编排|阿里云ACK|1.29|使用云服务|
|镜像服务|阿里云ACR|-|使用云服务|
|大模型服务|阿里云百炼|-|通义千问3.5|
|K8s客户端|Fabric8 Kubernetes Client|6.12.0|Java客户端|
|前端框架|React|18.3.1||
|UI组件库|Ant Design|5.19.0||
|HTTP客户端|OkHttp|4.11.0|调用小红书API、商品API|
### 6.2 环境配置

- 开发环境：本地开发，连接阿里云测试环境

- 测试环境：阿里云ECS，部署所有后端服务

- K8s环境：阿里云ACK托管版，1个Master节点，2个Worker节点(2核4G)

- Nacos：部署在ACK集群中，单机模式

- MySQL：阿里云RDS，最小规格(1核2G)

- 小红书测试环境：使用小红书开放平台测试账号，模拟API调用（Demo版不接入正式环境）

---

## 7. 接口设计规范

### 7.1 通用响应格式

```JSON
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

### 7.2 核心接口列表

#### 7.2.1 用户画像接口

- POST /api/profiles/upload：上传用户画像文档

- GET /api/profiles/current：获取当前用户画像

#### 7.2.2 智能体管理接口

- POST /api/agents：创建智能体

- GET /api/agents：获取当前用户智能体列表

- PUT /api/agents/{id}/start：启动智能体

- PUT /api/agents/{id}/stop：停止智能体

- DELETE /api/agents/{id}：删除智能体

#### 7.2.3 任务执行接口

- POST /api/tasks：提交任务（支持商品购买、小红书宣传两种类型）

- GET /api/tasks/{id}：获取任务状态和结果

#### 7.2.4 商品信息接口（新增）

- GET /api/products/search：商品库信息查询（支持关键词、分页）

- GET /api/products/spu/{spuId}：SPU-SKU关联信息查询

- GET /api/products/bid/{productId}：商品竞价信息查询

#### 7.2.5 小红书社交接口（新增）

- POST /api/xiaohongshu/token/get：获取小红书用户token

- POST /api/xiaohongshu/post/publish：发布小红书帖子

- POST /api/xiaohongshu/comment/publish：发布小红书评论

- GET /api/xiaohongshu/comment/list/{postId}：查询小红书帖子评论
> （注：文档部分内容可能由 AI 生成）