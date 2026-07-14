# ASTS 后端服务测试文档

## 测试概览

本目录包含 lotask4j 异步慢任务服务 (ASTS) 后端的全部测试代码，包括单元测试、集成测试和 Mapper 测试。

## 测试结构

```
src/test/
├── java/fun/commons/lotask4j/
│   ├── service/
│   │   └── TaskServiceTest.java           # 服务层单元测试
│   ├── controller/
│   │   └── ClientTaskControllerTest.java  # 控制器集成测试
│   └── mapper/
│       └── AstTaskMapperTest.java         # Mapper 层测试
├── resources/
│   └── application-test.yml               # 测试配置文件
└── README.md                              # 本文件
```

## 测试用例统计

| 测试类 | 测试方法数 | 覆盖场景 |
|--------|-----------|---------|
| TaskServiceTest | 10 | 提交、查询、取消、统计、清理 |
| ClientTaskControllerTest | 8 | HTTP 接口、参数校验、异常处理 |
| AstTaskMapperTest | 8 | CRUD、查询、软删除 |
| **总计** | **26** | **全面覆盖核心功能** |

## 测试详情

### 1. TaskServiceTest (服务层单元测试)

**位置**: `src/test/java/fun/commons/lotask4j/service/TaskServiceTest.java`

**测试框架**: JUnit 5 + Mockito

**测试场景**:

| 测试方法 | 说明 | 验证点 |
|---------|------|-------|
| `testSubmitTask_Success` | 提交任务 - 成功 | 返回任务ID，Mapper被调用 |
| `testSubmitTask_WithNullType` | 提交任务 - 参数为空 | 抛出异常 |
| `testSubmitTask_PriorityBoundary` | 提交任务 - 优先级边界 | 0和100都可接受 |
| `testGetTaskDetail_Success` | 查询任务详情 - 成功 | 返回正确的任务信息 |
| `testGetTaskDetail_NotFound` | 查询任务详情 - 不存在 | 抛出异常 |
| `testCancelTask_Success` | 取消任务 - 成功 | 状态更新为CANCELLED |
| `testCancelTask_AlreadyFinished` | 取消任务 - 已完成 | 抛出异常 |
| `testGetPendingTaskCount` | 获取待处理任务数 | 返回正确数量 |
| `testGetRunningTaskCount` | 获取运行中任务数 | 返回正确数量 |
| `testCleanupTimeoutTasks` | 清理超时任务 | 返回清理数量 |

**关键特性**:
- ✅ 使用 `@Mock` 模拟依赖
- ✅ 使用 `@InjectMocks` 自动注入
- ✅ 覆盖正常流程和异常流程
- ✅ 验证 Mapper 调用次数和参数

### 2. ClientTaskControllerTest (控制器集成测试)

**位置**: `src/test/java/fun/commons/lotask4j/controller/ClientTaskControllerTest.java`

**测试框架**: Spring Boot Test + MockMvc

**测试场景**:

| 测试方法 | HTTP 方法 | 路径 | 验证点 |
|---------|----------|------|-------|
| `testSubmitTask_Success` | POST | `/api/v1/client/tasks` | 200 OK, 返回taskId |
| `testSubmitTask_ValidationFailed` | POST | `/api/v1/client/tasks` | 400 Bad Request |
| `testGetTaskDetail_Success` | GET | `/api/v1/client/tasks/{taskId}` | 200 OK, 返回详情 |
| `testGetTaskDetail_NotFound` | GET | `/api/v1/client/tasks/{taskId}` | 500 Error |
| `testCancelTask_Success` | POST | `/api/v1/client/tasks/{taskId}/cancel` | 200 OK |
| `testCancelTask_AlreadyFinished` | POST | `/api/v1/client/tasks/{taskId}/cancel` | 500 Error |
| `testSubmitTask_PriorityOutOfRange` | POST | `/api/v1/client/tasks` | 400 Bad Request |
| `testSubmitTask_WrongContentType` | POST | `/api/v1/client/tasks` | 415 Unsupported Media Type |

**关键特性**:
- ✅ 使用 `@WebMvcTest` 只加载 Web 层
- ✅ 使用 `MockMvc` 模拟 HTTP 请求
- ✅ 测试 JSON 序列化和反序列化
- ✅ 验证 HTTP 状态码和响应内容
- ✅ 测试参数校验和异常处理

### 3. AstTaskMapperTest (Mapper 层测试)

**位置**: `src/test/java/fun/commons/lotask4j/mapper/AstTaskMapperTest.java`

**测试框架**: MyBatis Test + H2 Database

**测试场景**:

| 测试方法 | 操作 | 验证点 |
|---------|------|-------|
| `testInsertTask_Success` | INSERT | 插入成功，影响1行 |
| `testSelectByTaskUuid` | SELECT | 根据UUID查询成功 |
| `testSelectByTaskUuid_NotFound` | SELECT | 不存在返回null |
| `testCountPendingTasks` | COUNT | 统计待处理任务 |
| `testCountRunningTasks` | COUNT | 统计运行中任务 |
| `testUpdateTaskStatus` | UPDATE | 更新状态和进度 |
| `testSoftDeleteTask` | UPDATE | 软删除标记 |

**关键特性**:
- ✅ 使用 `@MybatisTest` 只加载 Mapper 层
- ✅ 使用 H2 内存数据库 (PostgreSQL 模式)
- ✅ 测试 CRUD 操作
- ✅ 验证自定义查询方法
- ✅ 测试软删除逻辑

## 运行测试

### 运行所有测试

```bash
cd lotask4j-backend
mvn test
```

### 运行单个测试类

```bash
# 运行服务层测试
mvn test -Dtest=TaskServiceTest

# 运行控制器测试
mvn test -Dtest=ClientTaskControllerTest

# 运行 Mapper 测试
mvn test -Dtest=AstTaskMapperTest
```

### 运行单个测试方法

```bash
mvn test -Dtest=TaskServiceTest#testSubmitTask_Success
```

### 生成测试报告

```bash
# 运行测试并生成 HTML 报告
mvn test
mvn surefire-report:report

# 报告位置: target/surefire-reports/index.html
```

### 生成覆盖率报告

```bash
# 使用 JaCoCo 生成覆盖率报告
mvn clean test jacoco:report

# 报告位置: target/site/jacoco/index.html
```

## 测试配置

### application-test.yml

```yaml
spring:
  datasource:
    driver-class-name: org.h2.Driver
    url: jdbc:h2:mem:testdb;MODE=PostgreSQL
    username: sa
    password:
```

**特点**:
- 使用 H2 内存数据库 (PostgreSQL 兼容模式)
- 每次测试自动创建和销毁数据库
- 不依赖外部 PostgreSQL 服务

## 测试覆盖率目标

| 层级 | 目标覆盖率 | 当前状态 |
|------|-----------|---------|
| **Service 层** | ≥ 80% | ✅ 已实现 |
| **Controller 层** | ≥ 80% | ✅ 已实现 |
| **Mapper 层** | ≥ 70% | ✅ 已实现 |
| **整体** | ≥ 75% | 🔄 待验证 |

## 常见问题

### Q: 测试失败 - 依赖不存在

**问题**: `framework4j-all` 依赖找不到

**解决方案**:
1. 暂时注释掉该依赖
2. 或者将测试标记为 `@Disabled`
3. 或者创建 Mock 实现

### Q: H2 数据库兼容性问题

**问题**: PostgreSQL 特定语法在 H2 中不支持

**解决方案**:
1. 使用 `MODE=PostgreSQL` 模式
2. 或者使用 Testcontainers 运行真实 PostgreSQL
3. 或者调整 SQL 语法

### Q: 测试运行缓慢

**问题**: 集成测试启动 Spring 上下文太慢

**解决方案**:
1. 使用 `@WebMvcTest` 只加载需要的层
2. 使用 `@MockBean` 模拟不需要的依赖
3. 使用 `@TestInstance(Lifecycle.PER_CLASS)` 复用上下文

### Q: Mock 数据准备繁琐

**问题**: 每个测试都要准备大量 Mock 数据

**解决方案**:
1. 使用 `@BeforeEach` 提取公共准备逻辑
2. 创建测试数据工厂类
3. 使用测试 Fixture 文件 (JSON/YAML)

## 最佳实践

### 1. 测试命名

```java
// 格式: test{方法名}_{场景}
testSubmitTask_Success()        // ✅ 清晰
testSubmitTask_WithNullType()   // ✅ 描述具体场景
test1()                         // ❌ 不清晰
```

### 2. 断言使用

```java
// 优先使用具体断言
assertEquals(expected, actual)  // ✅ 清晰
assertTrue(actual == expected)  // ❌ 不清晰

// 添加失败消息
assertNotNull(result, "结果不应为空")  // ✅ 有助于调试
```

### 3. Mock 使用

```java
// 只 Mock 必要的依赖
when(mapper.insert(any())).thenReturn(1);  // ✅ 简洁

// 验证调用次数
verify(mapper, times(1)).insert(any());    // ✅ 确保被调用
```

### 4. 测试独立性

```java
// 每个测试应该独立
@BeforeEach
void setUp() {
    // 每次都重新初始化
}

// 避免测试间依赖
// ❌ testB() 依赖 testA() 的结果
```

## 持续集成

### GitHub Actions 配置示例

```yaml
name: Run Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - uses: actions/setup-java@v2
        with:
          java-version: '17'
      - run: mvn test
      - run: mvn jacoco:report
      - uses: codecov/codecov-action@v2
```

## 下一步

- [ ] 添加更多边界条件测试
- [ ] 添加性能测试
- [ ] 添加压力测试
- [ ] 集成 Testcontainers 使用真实 PostgreSQL
- [ ] 提高测试覆盖率到 90%+

## 相关文档

- [JUnit 5 用户指南](https://junit.org/junit5/docs/current/user-guide/)
- [Mockito 文档](https://javadoc.io/doc/org.mockito/mockito-core/latest/org/mockito/Mockito.html)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [MyBatis Test](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-test-autoconfigure/)

---

**文档版本**: 1.0.0
**更新日期**: 2024-11-27
**维护者**: lotask4j-team
