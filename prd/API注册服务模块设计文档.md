# API注册服务模块设计文档

## 1. 模块概述

API注册服务模块负责管理和提供系统预置的核心API接口。在Demo版本中，该模块采用极简设计，将所有API信息硬编码在代码中，无需用户注册或审核流程。智能体通过任务执行服务直接调用这些预置API，完成商品信息查询和小红书社交操作。

## 2. 功能需求

### 2.1 核心功能
- **API管理**: 管理8个预置的核心API接口
- **API调用**: 提供统一的API调用接口
- **参数验证**: 验证API调用参数的合法性
- **结果处理**: 处理API调用结果并返回标准化响应
- **错误处理**: 统一处理API调用过程中的各种异常

### 2.2 预置API列表

#### 商品信息相关API
1. **查询热门手机**
   - 地址: `http://product-service/api/products/hot`
   - 方法: GET
   - 返回值: 3款手机的列表，包含名称、价格、配置、图片

2. **商品库信息查询**
   - 地址: `http://product-service/api/products/search`
   - 方法: GET
   - 参数: keyword（商品关键词）、pageNum（页码）、pageSize（每页条数）
   - 返回值: 商品列表，包含商品ID、SPU ID、SKU ID、名称、价格、配置、图片、库存状态

3. **SPU-SKU关联信息查询**
   - 地址: `http://product-service/api/products/spu/{spuId}`
   - 方法: GET
   - 返回值: SPU基础信息（名称、描述、主图）、该SPU下所有SKU信息（SKU ID、规格、价格、库存），并自动返回3个关联推荐商品（同品类、同价位）

4. **商品竞价信息查询**
   - 地址: `http://product-service/api/products/bid/{productId}`
   - 方法: GET
   - 返回值: 当前商品不同渠道的竞价列表，包含渠道名称、报价、优惠力度、发货时效

#### 小红书社交相关API
5. **小红书用户token获取**
   - 地址: `http://xiaohongshu-service/api/token/get`
   - 方法: POST
   - 参数: userId（平台用户ID）、xhAccount（小红书账号）、xhPassword（小红书密码，Demo版明文传输，仅用于测试）
   - 返回值: access_token、expires_in（有效期）、refresh_token

6. **小红书发帖**
   - 地址: `http://xiaohongshu-service/api/post/publish`
   - 方法: POST
   - 参数: access_token、title（帖子标题）、content（帖子内容）、productId（关联商品ID）、imageUrls（图片链接列表，Demo版用测试链接）
   - 返回值: postId（帖子ID）、status（发布状态）、message（提示信息）

7. **小红书帖子评论**
   - 地址: `http://xiaohongshu-service/api/comment/publish`
   - 方法: POST
   - 参数: access_token、postId（帖子ID）、content（评论内容）
   - 返回值: commentId（评论ID）、status（发布状态）

8. **小红书帖子评论查询**
   - 地址: `http://xiaohongshu-service/api/comment/list/{postId}`
   - 方法: GET
   - 参数: access_token、pageNum、pageSize
   - 返回值: 评论列表，包含commentId、userId（小红书用户ID）、content、createTime

## 3. 技术架构

### 3.1 系统架构
```
API注册服务
├── Web层 (Spring Boot Controller)
├── 业务逻辑层 (API Registry Service)
├── HTTP客户端层 (OkHttp Client)
├── API配置层 (硬编码API定义)
└── 结果处理层 (Response Standardization)
```

### 3.2 技术栈
- **框架**: Spring Boot 3.3.2
- **HTTP客户端**: OkHttp 4.11.0
- **JSON处理**: Jackson
- **配置管理**: Spring Configuration
- **日志**: SLF4J + Logback

## 4. 详细设计

### 4.1 API配置设计

#### 4.1.1 API定义类
```java
// ApiDefinition.java
public class ApiDefinition {
    private String id;
    private String name;
    private String url;
    private String method;
    private List<ApiParameter> parameters;
    private ApiResponse response;
    private boolean requiresAuth;
    
    // getters/setters
}

// ApiParameter.java
public class ApiParameter {
    private String name;
    private String type; // string, integer, boolean, object, array
    private boolean required;
    private String description;
    
    // getters/setters
}

// ApiResponse.java
public class ApiResponse {
    private String type; // object, array, string, integer
    private String description;
    private Map<String, Object> example;
    
    // getters/setters
}
```

#### 4.1.2 预置API配置
```java
// ApiRegistryConfig.java
@Configuration
public class ApiRegistryConfig {
    
    @Bean
    public Map<String, ApiDefinition> predefinedApis() {
        Map<String, ApiDefinition> apis = new HashMap<>();
        
        // 商品信息API
        apis.put("hot_products", createHotProductsApi());
        apis.put("search_products", createSearchProductsApi());
        apis.put("spu_sku_info", createSpuSkuInfoApi());
        apis.put("product_bidding", createProductBiddingApi());
        
        // 小红书API
        apis.put("xh_get_token", createXhGetTokenApi());
        apis.put("xh_publish_post", createXhPublishPostApi());
        apis.put("xh_publish_comment", createXhPublishCommentApi());
        apis.put("xh_list_comments", createXhListCommentsApi());
        
        return apis;
    }
    
    private ApiDefinition createHotProductsApi() {
        ApiDefinition api = new ApiDefinition();
        api.setId("hot_products");
        api.setName("查询热门手机");
        api.setUrl("http://product-service/api/products/hot");
        api.setMethod("GET");
        api.setRequiresAuth(false);
        // ... 设置响应格式
        return api;
    }
    
    // ... 其他API创建方法
}
```

### 4.2 API接口设计

#### 4.2.1 通用响应格式
```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

#### 4.2.2 具体接口

**POST /api/apis/call**
- **功能**: 调用指定的预置API
- **请求参数**:
  ```json
  {
    "apiId": "hot_products",
    "userId": "user123",
    "parameters": {
      "keyword": "手机",
      "pageNum": 1,
      "pageSize": 10
    }
  }
  ```
- **响应数据**:
  ```json
  {
    "apiId": "hot_products",
    "statusCode": 200,
    "response": {
      // API实际返回的数据
    },
    "timestamp": "2026-04-06T10:30:00Z"
  }
  ```

**GET /api/apis/list**
- **功能**: 获取所有可用的预置API列表
- **请求参数**: 无
- **响应数据**:
  ```json
  [
    {
      "id": "hot_products",
      "name": "查询热门手机",
      "method": "GET",
      "requiresAuth": false,
      "parameters": []
    }
  ]
  ```

### 4.3 业务流程设计

#### 4.3.1 API调用流程
1. 任务执行服务调用 `/api/apis/call` 接口
2. 服务端根据 `apiId` 查找对应的API定义
3. 验证参数是否符合API定义要求
4. 构建HTTP请求（设置URL、方法、参数、Headers）
5. 使用OkHttp客户端发送请求
6. 处理响应结果（成功/失败/超时）
7. 返回标准化的响应数据

#### 4.3.2 参数验证流程
1. 获取API定义中的参数列表
2. 遍历所有必需参数，检查是否提供
3. 验证参数类型是否匹配
4. 对于路径参数，验证格式是否正确
5. 对于查询参数，验证值是否在合理范围内
6. 如果验证失败，返回参数错误信息

#### 4.3.3 错误处理流程
1. **网络错误**: 连接超时、DNS解析失败等
2. **HTTP错误**: 4xx/5xx状态码
3. **参数错误**: 参数缺失、类型不匹配等
4. **认证错误**: Token过期、权限不足等
5. **业务错误**: API返回业务层面的错误
6. 统一转换为标准错误格式返回

### 4.4 类设计

#### 4.4.1 DTO类
```java
// CallApiRequest.java
public class CallApiRequest {
    private String apiId;
    private String userId;
    private Map<String, Object> parameters;
    
    // getters/setters
}

// CallApiResponse.java
public class CallApiResponse {
    private String apiId;
    private int statusCode;
    private Object response;
    private LocalDateTime timestamp;
    
    // getters/setters
}

// ApiInfoDTO.java
public class ApiInfoDTO {
    private String id;
    private String name;
    private String method;
    private boolean requiresAuth;
    private List<ApiParameter> parameters;
    
    // getters/setters
}
```

#### 4.4.2 Service类
```java
// ApiRegistryService.java
@Service
public class ApiRegistryService {
    
    private final Map<String, ApiDefinition> predefinedApis;
    private final OkHttpClient httpClient;
    
    public List<ApiInfoDTO> getAllApis() {
        // 转换API定义为DTO
    }
    
    public CallApiResponse callApi(String apiId, String userId, Map<String, Object> parameters) {
        // 参数验证
        // API调用
        // 结果处理
    }
    
    private void validateParameters(ApiDefinition api, Map<String, Object> parameters) {
        // 参数验证逻辑
    }
    
    private CallApiResponse executeHttpRequest(ApiDefinition api, Map<String, Object> parameters) {
        // HTTP请求执行
    }
}
```

#### 4.4.3 HTTP客户端工具类
```java
// HttpClientUtil.java
@Component
public class HttpClientUtil {
    
    private final OkHttpClient client;
    
    public HttpResponse get(String url, Map<String, String> headers, Map<String, Object> queryParams) {
        // GET请求实现
    }
    
    public HttpResponse post(String url, Map<String, String> headers, Object requestBody) {
        // POST请求实现
    }
    
    public HttpResponse buildUrl(String baseUrl, Map<String, Object> pathParams, Map<String, Object> queryParams) {
        // URL构建
    }
}
```

### 4.5 异常处理

#### 4.5.1 异常类型
- **API不存在异常**: 无效的apiId
- **参数验证异常**: 参数缺失、类型错误、值超出范围
- **网络异常**: 连接超时、DNS解析失败
- **HTTP异常**: 4xx/5xx状态码
- **认证异常**: Token无效、权限不足
- **业务异常**: API返回业务错误

#### 4.5.2 错误码设计
| 错误码 | 错误信息 | HTTP状态码 |
|--------|----------|------------|
| 3001 | API不存在 | 404 |
| 3002 | 缺少必需参数 | 400 |
| 3003 | 参数类型错误 | 400 |
| 3004 | 参数值超出范围 | 400 |
| 3005 | 网络连接失败 | 503 |
| 3006 | HTTP请求失败 | 502 |
| 3007 | 认证失败 | 401 |
| 3008 | 业务逻辑错误 | 400 |

## 5. 配置管理

### 5.1 应用配置
```properties
# HTTP客户端配置
http.client.connect-timeout=10000
http.client.read-timeout=30000
http.client.write-timeout=10000

# API服务地址配置
product.service.url=http://product-service
xiaohongshu.service.url=http://xiaohongshu-service

# 安全配置
api.security.enabled=false
```

### 5.2 环境变量
- `PRODUCT_SERVICE_URL`: 商品信息服务地址
- `XIAOHONGSHU_SERVICE_URL`: 小红书服务地址
- `HTTP_CLIENT_TIMEOUT`: HTTP客户端超时配置

## 6. 安全考虑

### 6.1 安全措施
- **参数验证**: 严格验证所有输入参数
- **URL安全**: 防止URL注入攻击
- **敏感信息**: 不在日志中记录敏感参数（如密码）
- **访问控制**: 只允许任务执行服务调用API
- **错误信息**: 不暴露内部系统信息

### 6.2 权限控制
- Demo版本简化权限控制，所有API调用都需要提供有效的userId
- 不同用户的API调用完全隔离
- 敏感操作（如小红书发帖）需要有效的access_token

## 7. 性能优化

### 7.1 性能考虑
- **连接池**: 使用OkHttp连接池复用HTTP连接
- **缓存**: 对频繁调用的只读API进行缓存（可选）
- **异步调用**: 支持异步API调用（未来扩展）
- **批量处理**: 支持批量API调用（未来扩展）

### 7.2 监控指标
- API调用成功率
- API调用平均耗时
- HTTP客户端连接池使用情况
- 错误率统计

## 8. 测试策略

### 8.1 单元测试
- API定义配置
- 参数验证逻辑
- URL构建逻辑
- 响应处理逻辑

### 8.2 集成测试
- 完整的API调用流程
- 商品信息服务集成
- 小红书服务集成
- 错误场景处理

### 8.3 性能测试
- 并发API调用能力
- HTTP客户端性能
- 大量参数处理性能

## 9. 部署说明

### 9.1 部署要求
- **内存**: 至少1GB RAM
- **网络**: 能够访问商品信息服务和小红书服务
- **依赖**: Java 21, Spring Boot 3.3.2+

### 9.2 部署步骤
1. 配置商品信息服务和小红书服务地址
2. 启动Spring Boot应用
3. 验证API调用功能

## 10. 扩展性考虑

### 10.1 未来扩展点
- **动态API注册**: 支持用户动态注册API
- **API网关**: 集成API网关进行流量控制
- **认证授权**: 实现完整的OAuth2.0认证
- **API版本管理**: 支持API版本控制
- **监控告警**: 集成APM监控系统

### 10.2 架构演进
- **微服务拆分**: 将商品信息和小红书API拆分为独立服务
- **消息队列**: 引入消息队列处理异步API调用
- **服务网格**: 集成Istio等服务网格技术
- **Serverless**: 迁移到Serverless架构

## 11. 与其他模块的集成

### 11.1 依赖关系
- **上游依赖**: 
  - 任务执行服务（调用API执行任务）
- **下游依赖**: 
  - 商品信息服务（提供商品相关API）
  - 小红书社交服务（提供小红书相关API）

### 11.2 集成接口
- **REST API**: 通过标准HTTP接口提供服务
- **同步调用**: 所有API调用都是同步阻塞的
- **错误处理**: 统一的错误码和响应格式

## 12. 开发里程碑

### 12.1 第一阶段（2天）
- [ ] 基础框架搭建
- [ ] API定义配置
- [ ] 基础API接口实现
- [ ] HTTP客户端集成

### 12.2 第二阶段（2天）
- [ ] 参数验证机制实现
- [ ] 完整的API调用流程
- [ ] 错误处理机制
- [ ] 商品信息服务集成

### 12.3 第三阶段（1天）
- [ ] 小红书服务集成
- [ ] 完整测试用例编写
- [ ] 性能优化和安全加固
- [ ] 文档完善和部署验证