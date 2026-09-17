# lotask4j-server-spring-boot-starter

ASTS 异步慢任务服务 starter — 家族「薄壳 + starter」接入模式 (issue #4)。**引用即嵌入**: 消费方壳应用只需 1 主类 + 1 yml + 本构件依赖, controller/service/mapper/config/定时任务/Flyway 迁移/控制台与 embed 静态资源全部自动装配。

## 坐标

```
fun.commons.lotask4j:lotask4j-server-spring-boot-starter:1.0.0-SNAPSHOT
```

构建: 仓库根 `mvn -pl lotask4j-server-spring-boot-starter -am install` (framework4j 走 JitPack, repository 声明在根 POM)。

## 最小接入

```java
@SpringBootApplication   // 主类放自家包 (勿用 fun.commons.lotask4j*, 见下「已知限制」)
public class TaskServerApplication {
    public static void main(String[] args) { SpringApplication.run(TaskServerApplication.class, args); }
}
```

```xml
<dependency>
    <groupId>fun.commons.lotask4j</groupId>
    <artifactId>lotask4j-server-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

## 配置契约

### 必填 (启动期 fail-fast 校验, 缺配拒绝启动)

| 键 | 说明 |
|---|---|
| `spring.application.name` | framework4j ID 生成 / Token Key 种子 |
| `framework4j.datasource.datasources.business.url` | 业务库连接 (framework4j.datasource.enabled 时) |
| `framework4j.redis.datasources.default.host` | 限流/签名 nonce/access-token 依赖 |
| `framework4j.access-token.secretKey` | ≥32 字符, TOKEN_SECRET |
| `framework4j.tenant.platform.client-id/secret` | 平台域凭据 (tenant+auth enabled 时) |
| `framework4j.sensitive.encryption-key` | 租户密钥 AES-256-GCM (enabled 时) |
| `lotask4j.flyway.url/user` | 迁移目标库 (lotask4j.flyway.enabled 时) |

完整样例见 `lotask4j-backend/src/main/resources/application.yml` (独立部署样例壳)。

### 键空间边界 (issue #4 决策)

- **`framework4j.*` — SDK 契约键, 保持原样不改名** (宿主侧声明, 与 benefit4j 等家族成员一致)
- **`lotask4j.*` — 本服务自有键** (原 `app.*` 收敛而来): `lotask4j.{async,asts,webhook,steps,web-embed,flyway,security}.*`

### 默认密钥告警

TOKEN_SECRET / AES_KEY / PLATFORM_SECRET 的 dev 占位默认值启动时仅 WARN; 设 `lotask4j.security.strict=true` 升级为拒绝启动 (生产口径)。

## 开关

| 开关 | 默认 | 作用 |
|---|---|---|
| `lotask4j.enabled` | true | 总开关; false 时整体退出且 AutoConfigurationImportFilter 放行原生 DataSource/MyBatisPlus/Druid/Redisson 装配 |
| `lotask4j.business.enabled` | true | 业务面 (controller/service/mapper/schedule); 测试可单独关掉验 light 层 |
| `lotask4j.flyway.enabled` | true | 启动期迁移; 家族 DDF 由宿主 psql 投放 DDL 时设 false |

## bean 名契约 (迁移前后不可变)

`asyncExecutor` / `webhookRestTemplate` / `callbackRestTemplate` / `stringRedisTemplate` (framework4j id worker `redis-name`) / 数据源别名 `default,primary` / Redis 别名 `default,cache`。

## 已知限制

- **宿主基包勿落在 `fun.commons.lotask4j` 下**: @SpringBootApplication 默认组件扫描会与 starter 的业务包双注册 (token-gateway 同款家族限制)。壳主类放自家包即可。
- **host 自带 `static/index.html` 会遮蔽控制台** (Spring Boot 静态资源 classpath 顺序)。
- 定时任务 (@Scheduled) 未加分布式锁, 多实例部署会重复执行 (TaskArchiver/WorkerCleaner/TaskReaper/OutboxPublisher); 任务领取并发安全由 PG `FOR UPDATE SKIP LOCKED` 保证。

## 测试范式

- `AstsServerAutoConfigurationTest` 等 runner 测试: light 层 (business.enabled=false)
- `AstsServerEmbedActivationTest`: 嵌入壳空 @SpringBootApplication 端到端 (真 PG/Redis, 引用即嵌入证明)
- `BackendShellSmokeTest` (lotask4j-backend): 壳主类 + 样例 yml + 装配锁步冒烟
