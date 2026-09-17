# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository overview

This is the monorepo for **lotask4j 异步慢任务服务 (ASTS) — Asynchronous Slow Task Service**: a distributed platform for executing long-running (>10s) business logic (data export, video transcoding, etc.) with real-time progress reporting, cancellation, and a visual admin backend. The README.md is the authoritative project overview, run instructions, and architecture diagram — read it first for the high-level picture.

The repo root holds a Maven aggregator `pom.xml` (`fun.commons.lotask4j:lotask4j-parent`, BOM-imports spring-boot-dependencies, declares the jitpack.io repository; module order: starter → backend → demo; no npm workspaces). **`frontend/` is the current, authoritative frontend** (2026-08 起取代下列三个旧前端; 旧目录保留并行, 不再演进):

| Module | Stack | Purpose |
|--------|-------|---------|
| `lotask4j-server-spring-boot-starter` | Spring Boot 3.5.x / JDK 17+ / Maven | **全部业务代码所在** (issue #4 starter 化, 2026-09-17): controller/service/mapper/config/schedule + Flyway V1-V5 + 控制台与 embed 静态资源, 经 `AstsServerAutoConfiguration` (AutoConfiguration.imports) 引用即嵌入。库构件 — 无 repackage。`fun.commons.lotask4j.*` package root, 装配入口在 `boot/` 子包。嵌入契约见其 README。 |
| `lotask4j-backend` | Spring Boot 3.5.x / Maven | **独立部署样例薄壳** (壳内零业务代码): 1 主类 (`fun.commons.lotask4j.app.AstsApplication`) + 1 样例 yml + 1 冒烟测试, repackage 后即 :9080 可执行 JAR。家族接入照此壳形态。 |
| `lotask4j-demo` | Spring Boot / Maven | Standalone example showing how to integrate as a **client** (submit/get/cancel tasks) and as a **worker** (poll + execute). Separate from backend. |
| `frontend` | **Vue 3.5 + Vite 7 + TS + Element Plus + Pinia + pnpm@9.12** | 统一前端: 控制台 + 管理后台 + 嵌入组件 (单仓多模式构建)。基于 benefit4j/frontend copy-裁剪-改造。Dev port **9083**; proxies `/api` `/web-embed` `/swagger-ui` → `:9080`。 |
| `lotask4j-frontend` | *(legacy)* React 18 + Antd | 旧主控制台 (残缺)。已被 `frontend` 取代。 |
| `lotask4j-admin-frontend` | *(legacy)* Vue 3 | 旧管理后台 (**package.json 已丢失, 无法构建**)。已被 `frontend` 取代。 |
| `lotask4j-web-embed-frontend` | *(legacy)* Vue 3 | 旧嵌入组件。已被 `frontend` 的 `--mode embed` 构建取代。 |

## Important non-obvious facts

- **Parent POM = 仓库根 `pom.xml` (`lotask4j-parent:1.0.0-SNAPSHOT`)**, 各模块 `<relativePath>../pom.xml` 引用; jitpack.io repository 也声明在根 POM (framework4j 解析)。(2026-09-17 修正: 早前「根目录无 parent POM、parent 来自内部仓库」的记载已过期。)
- **前端双产物随 starter JAR 发布 (issue #4)。** `cd frontend && pnpm build:embed && pnpm sync-embed && pnpm build && pnpm sync-console` 构建并同步 embed (`static/web-embed/`) 与控制台 (`static/index.html + assets/`) 到 `lotask4j-server-spring-boot-starter/src/main/resources/static/` (整个目录 gitignore, 本地/CI 重建)。**打包 starter 前先同步**, 否则控制台/embed 过期缺失 (CI 已自动化双构建)。
- **多租户三域鉴权 (2026-09 起, framework4j-tenant)。** `POST /api/v1/auth/token` 由框架内置 `TenantAuthEndpoint` 接管 (client_credentials; client_id=租户 id/name 或 `PLATFORM`, secret=租户密钥/平台凭据), 统一签发 TENANT 型 JWT (claim `tenant_id`; 平台身份=0)。三域守卫: admin 三 controller 挂 `@PlatformDomain` (tenant_id=0)、ClientTask/WorkerTaskController 挂 `@TenantDomain` (tenant_id>0), 与 `@RequiresToken("TENANT")` 同挂。凭据环境变量: `PLATFORM_CLIENT_SECRET` (平台) / 租户密钥在 asts_tenant (AES-GCM)。client GET 也不再开放 — embed 走短期 token (ASTS_EMBED_TOKEN cookie)。业务表 tenant_id 只从 claim 取 (`TenantIdentity.currentTenantId(null)`), PG RLS POLICY 兜底。
- **frontend dev 零后端可跑**: dev-mock 走 axios **adapter 层**短路 (`src/mock/dev-interceptor.ts`), 拦截 auth/client/admin/embed-config 只读端点; 命中即合成 response, 不走网络。
- **控制台双域路由 (2026-09 起, 仿 benefit4j 四入口裁剪)**: `/platform/**` (平台治理, admin API) 与 `/tenant/**` (租户业务, client/worker API) 两棵路由树 + `meta.domain`; 守卫按登录身份弹回所属域首页 (`store/auth.ts` 的 `identity`, 锚点 = 前端解码 JWT `payload.claims.tenant_id`, 0=平台/>0=租户, framework4j v1.7.0 Issue #23 起签发侧嵌入; 整页刷新由守卫 `ensureIdentity()` 补解码, v1.5.1 存量 token 无 claim → 放行)。页面按域各建薄页 (`PlatformTasks` 走 admin `/tasks?tenantId=` 收窄 vs `TaskList` 走 client 域), guide 类组件双树复用 (子跳转跟随当前域前缀)。dev-mock 登录按 client_id 签发同形嵌套 claims 的 JWT token (`ADMIN`→平台 / 其余→租户), e2e 用同形 token 预置登录态。**前端注入 Bearer 的公开面收敛为精确 `/api/v1/auth/token`** — 勿再用 `includes('/api/v1/auth/')` 宽匹配 (历史教训: 曾致 /auth/me 身份反查拿不到 token)。
- **frontend 的 SDK 组件是零改动豁免区**: `src/components/sdk/**` 与 `src/views/dev/**` 来自 benefit4j, eslint 已豁免; **12 个 SDK 测试套件上游已红** (element-plus 类名漂移 + jsdom 29, 与 benefit4j 原仓同败), 已从默认 vitest 排除, `pnpm test:sdk` 可观察。���务代码必须用 Fc* 组件 / fc-* class (eslint 强制)。
- **README references `documents/` and `MAVEN_INIT_GUIDE.md` — neither exists in this repo.** They live elsewhere (likely an internal wiki). Do not waste time hunting for them here.
- **ID generation is centralised in the `fun.commons.fwk4j:fwk4j-sdk`-compatible SDK (migrated to `com.github.funcommons.framework4j:framework4j-all`)**, not implemented in this repo. Production uses the Redis worker strategy (`framework4j.id.worker.strategy: redis`); local dev can use `ip`. 对 MyBatis Plus / DataSource / Druid / Redisson 四项自动装配的排除由 starter 的 `AstsServerAutoConfigurationExcludeFilter` (AutoConfigurationImportFilter, spring.factories 注册) 接管 — 壳 yml 零配置; `lotask4j.enabled=false` 时放行原生装配。
- **配置键空间两分 (issue #4)**: `framework4j.*` 是 SDK 契约键 (宿主侧声明, 保持原样不改名); `lotask4j.*` 是本服务自有键 (原 `app.*` 收敛), 由 `AstsServerProperties` 绑定。启动期 `AstsRequiredPropertiesChecker` 聚合 fail-fast 双命名空间必填项 (含 `lotask4j.asts.reaper-interval/outbox-interval ≥1000ms` 防单位事故; 默认密钥 WARN, `lotask4j.security.strict=true` 升级为拒绝启动)。@Scheduled 占位符读不到 properties bean — `reaper-interval/outbox-interval` 在 schedule 包内以字面量占位符引用, 改默认值需与 properties 双侧同步。
- **JaCoCo is enforced at 100% line coverage — 门禁在 starter 模块 pom** (bundle 级 + 核心包级, `jacoco-check` 绑定 `verify` 阶段; `mvn test` 不触发门禁, 需 `mvn verify`)。backend 装配壳豁免 (家族惯例)。2026-09 收紧: 309→431 用例补齐全部行缺口; starter 化后门禁随业务代码落在 starter, `mvn -pl lotask4j-server-spring-boot-starter,lotask4j-backend -am verify` 为本地全量门禁命令。
- **装配两层结构** (token-gateway WorkerAssembly 家族范式): `AstsServerAutoConfiguration` (light: properties/checker/Flyway) + `AstsBusinessAssembly` (业务面 `lotask4j.business.enabled` 独立开关, @ComponentScan 显式列子包 + @MapperScan)。runner 测试关掉 business 面即可测 light 层; 全栈断言走 `AstsServerEmbedActivationTest` (嵌入壳空 @SpringBootApplication, 真 PG/Redis)。测试夹具 `AstsServerTestApplication` 用 @SpringBootConfiguration+@EnableAutoConfiguration 裸组合 (禁 @SpringBootApplication — 默认扫描会与业务包双注册)。
- **There's a `WebMvcConfig.java`** (so don't add `@EnableWebMvc`) and **`HttpClientConfig.java`** that registers the shared HTTP client used for worker callbacks and webhook delivery.
- **`@OpenId @PathVariable Long id` 必须显式写 `@PathVariable("id")`**：framework4j v1.2.1 的 `OpenIdFailFastValidator` 启动期检测 `@OpenId @PathVariable` 但 `@PathVariable` 没指定 `name` 又 class file 缺 `MethodParameters` attribute 的方法，**直接 fail-fast 启动崩溃**（不在 path 解析时 silent 抛 10106 了）。CI 也全绿（验证脚本见 https://github.com/funcommons/framework4j/issues/1）。
- **不要在 Maven 加 `<parameters>true</parameters>`**：详见下面 framework4j 行为合约。

## Backend architecture

The backend is a classic layered Spring Boot app: Controllers → Service interfaces → Service impls → MyBatis-Plus Mappers → XML mappers (`src/main/resources/mapper/business/`).

**Five external surfaces, each behind its own controller:**

| Controller | Audience | Purpose |
|-----------|----------|---------|
| `ClientTaskController` | Upstream business systems | Submit / query / cancel tasks. Path: `/api/v1/client/**` |
| `WorkerTaskController` | Worker processes | Poll pending tasks, report progress, report result, register/heartbeat the worker node. Path: `/api/v1/worker/**` |
| `AdminTaskController` | Admin frontend / operators | Task CRUD, worker-node registry, task-type config, statistics, archive view. |
| `AdminTenantController` | Admin frontend | Tenant lifecycle: create (one-time secret) / reset-secret (24h grace) / enable-suspend / delete. |
| `AdminWebEmbedController` | Admin frontend | CRUD for `web_embed_config` rows (controls the embed widget's per-tenant config; create requires `tenantId`). |
| `WebEmbedController` | The embed widget itself | Public-facing config fetch + short-term TENANT token issuance used by the embedded UI. |

**Service layer uses interface-in-`service/` + impl-in-`service/impl/` convention.** The Spring beans are the `*Impl` classes (`TaskServiceImpl`, `AdminServiceImpl`, `WorkerServiceImpl`, `CallbackServiceImpl`, `WebhookServiceImpl`, `WebEmbedServiceImpl`, `AdminWebEmbedServiceImpl`). When modifying behavior, edit the `Impl`, not the interface.

**Scheduled jobs (`schedule/` package):**
- `TaskArchiver` — runs daily 02:00, flips `is_deleted=1` on tasks completed ≥7 days ago (PENDING/RUNNING are excluded). Logical delete — rows remain.
- `WorkerCleaner` — runs every minute, removes offline workers from `asts_worker_node`.
- `TaskReaper` — reaps stuck/timed-out tasks back to a recoverable state.
- `TaskPartitionMaintainer` — ensures current + next month partitions exist (runs with the archiver).
- `OutboxPublisher` — scans `asts_outbox` every 5s and delivers webhooks with exponential backoff (max 8 attempts).

**Data model — PostgreSQL with heavy JSONB use.** `task.payload`, `task.result`, `task.steps_detail` and `task.steps_history` are JSONB; do not add new columns without first checking whether they belong inside one of those blobs. Tables:
- `asts_task` — primary task table (按月 RANGE 分区; status: `PENDING/RUNNING/SUCCESS/FAILED/CANCELLING/CANCELLED`; progress 0–100; 归档 = `is_deleted=1` 逻辑删, **无独立 history 表**)
- `asts_task_type_config` — type definitions (`(tenant_id, type_key)` 租户内唯一; typeKey 跨租户可共存 — selectByTypeKey/guard 均按 claim/request tenantId 定向, admin 域缺省为全局语义)
- `asts_task_execution_event` — append-only 执行事件 audit
- `asts_worker_node` — worker registry (租户级)
- `asts_web_embed_config` — embed widget per-tenant config (accessKey → 租户归属)
- `asts_tenant` — 租户表 (framework4j-tenant 契约; 密钥 AES-256-GCM; 由 asts_application 演进)
- `asts_outbox` — webhook 可靠投递 outbox (跨租户事件总线, 无 tenant_id)

Frontend surfaces differentiate "current tasks" (`is_deleted=0`) vs "archived tasks" (`is_deleted=1`, read-only).

## framework4j v1.7.0 behavior contract

This project pins `com.github.funcommons.framework4j:framework4j-all:v1.7.0` (另显式引入 `framework4j-tenant`/`framework4j-tenant-tck` v1.7.0 — **不在 framework4j-all 聚合中**) (JitPack mirror of GitHub `funcommons/framework4j`). The SDK's `GlobalExceptionHandler` decides HTTP status + business code for every exception. Knowing this contract avoids trial-and-error when adding endpoints.

**v1.2.1 升级带来的影响**（与 v1.1.3 对比）：

- **SDK API contract 不变**（[v1.2.0 release notes](https://github.com/funcommons/framework4j/releases/tag/v1.2.0) 明确说"API contract unchanged"），下面表格逐行适用。
- **v1.2.1 patch commit**：仅修 v1.2.0 引入的 `framework4j-redis` 编译失败（`GenericObjectPoolConfig<?>` → `GenericObjectPoolConfig<StatefulConnection<?, ?>>`），不影响运行时行为。
- **周边依赖对齐**：`pom.xml` 里 `spring-boot 3.2.0 → 3.5.16`、`Redisson 3.25.0 → 4.6.1`、`Druid 1.2.20 → 1.2.28`（artifactId 也从 `druid-spring-boot-starter` 切到 `druid-spring-boot-3-starter`）、`PostgreSQL JDBC 42.7.1 → 42.7.11`（CVE-2026-42198）。这些不影响 framework4j-web / -id 行为契约，但意味着我们自己的 application.yml 里如果写死 `druid-spring-boot-starter` 这种 artifact 名需要同步切到 `-3-`。
- **PR D/E 状态**：issue #1 评论里汇报的 priority-3（OpenID-specific 失败 → 10102）和 BeanPostProcessor-static 已分别提 PR（#4 / #3），**当前 v1.2.1 仍未合入**。所以下面"OpenID-specific path validation"那段仍然准确：非法 OpenID 路径现在还走 `code=10106`。

### HTTP status vs business code

| Exception / path | HTTP | business code | semantic |
|---|---|---|---|
| **Service throws `ApiException(BusinessCode.X, msg)`** | 200 | `BusinessCode.X.getCode()` | the canonical "success=false" path |
| `IllegalStateException` / `RuntimeException` (裸, 非 IllegalArgumentException 子类) | **500** | **10001 SYSTEM_BUSY** | server code bug; client 应该当 server-side error 处理 |
| `IllegalArgumentException` containing `"For input string:"` or `NumberFormatException` | 200 | **10102 PARAM_FORMAT_ERROR** | path / query 解析失败 (e.g. `Long.valueOf("abc")`) |
| `IllegalArgumentException` containing `"Name for argument of type"` | 200 | **10005 MIDDLEWARE_ERROR** | 编译时缺 `-parameters` — CI 必须 fail |
| 其他 `IllegalArgumentException` (业务校验、assertion) | 200 | 10106 BUSINESS_RULE_ERROR |
| `HttpRequestMethodNotSupportedException` | **405** | 10104 METHOD_NOT_SUPPORTED | body 仍 envelope |
| `HttpMediaTypeNotSupportedException` | **415** | 10105 MEDIA_TYPE_NOT_SUPPORTED | body 仍 envelope |
| `NoResourceFoundException` / `NoHandlerFoundException` | **404** | 10400 NOT_FOUND | body 仍 envelope |
| 路由层 4xx 都保留原 HTTP 码;业务异常统一 200。**`@ControllerAdvice` 之外的 HttpStatus 注解会被 framework4j-web 覆盖** |

### Service-layer throw rule (strictly enforced)

```java
// ✅ CORRECT — always throws BusinessCode enum
throw new ApiException(BusinessCode.TASK_NOT_FOUND.getCode(), "任务不存在: " + id);
throw new ApiException(BusinessCode.TASK_STATE_INVALID.getCode(), "任务状态非 RUNNING");

// ❌ WRONG — magic int (legacy code review will reject this in 2026+)
throw new ApiException(20404, "任务不存在: " + id);

// ❌ WRONG — leads to 500 SYSTEM_BUSY (not what you wanted)
throw new IllegalStateException("task not in RUNNING");
throw new RuntimeException("internal error");
```

**所有 service impl 类**目前都用 `BusinessCode` enum 显式引用。看 `WorkerServiceImpl` / `AdminServiceImpl` / `TaskServiceImpl` 是标杆。如果发现 magic int 在 PR review 直接打回。

### `@OpenId` on `@PathVariable` resolution order

`OpenIdPathVariableArgumentResolver` 处理顺序：

1. 如果 `@PathVariable("name")` **显式给 name** → 直接从 URI template 变量取,完全不依赖反射。
2. 否则回退到 `MethodParameter.getParameterName()` — **需要 javac `-parameters` flag 才不出 IAE**。

启动期 `OpenIdFailFastValidator` 扫描整个 context 找出走 case 2 的 method,如果 class 缺 `MethodParameters` attribute,直接抛出 fail-fast (loud failure 比 silent failure 强多了)。

### Test assertion examples

```java
// 业务校验失败 (service 抛 ApiException)
.andExpect(jsonPath("$.code").value(BusinessCode.TASK_NOT_FOUND.getCode()))

// 路由层异常 (framework4j-web 保留原 HTTP,业务 code 在 body)
.andExpect(status().isMethodNotAllowed())  // 405
.andExpect(jsonPath("$.code").value(10104))
.andExpect(status().isUnsupportedMediaType())  // 415
.andExpect(jsonPath("$.code").value(10105))

// Service 抛裸 RuntimeException (code bug, 现在走 500)
.andExpect(status().isInternalServerError())  // 500
.andExpect(jsonPath("$.code").value(10001))  // SYSTEM_BUSY

// Happy path
.andExpect(status().isOk())
.andExpect(jsonPath("$.code").value(0))
```

### OpenID-specific path validation (作业码偏差注意)

`OpenIdPathVariableArgumentResolver` 在 `IdObfuscator.fromOpenId()` 抛 IAE 时主动 `throw new ApiException(ApiCode.BUSINESS_RULE_ERROR, "Invalid @OpenId path variable ...")`,**不**走 `handleIllegalArgumentException` 的 10102 分流。所以非法 OpenID 路径拿到的是 **`code=10106` 而非 `10102`**。两种合理,但跟 issue #1 的优先级 3 提案不完全一致。看到这个不要误以为又是 v1.2.1 之前的 IAE bug。

- **CI & 文档站 & 联调栈 (2026-09-02)**: `.github/workflows/ci.yml` 双 job (backend job: starter+shell `mvn verify` + 100% LINE 门禁 + 前端双产物构建同步 / frontend verify + e2e, e2e 需 `--grep-invert "visual"` 因视觉基线是 darwin 专属) + gitleaks/dependency-review; `docs.yml` 发布 VitePress 文档站到 GitHub Pages (base `/lotask4j/`); `docker-compose.yml` + `scripts/smoke.sh` (24 断言) + `scripts/poll_bench.py` 为发版前标准联调; 控制台登录后前端对 submit/cancel 做 HMAC 签名 (secret 存 sessionStorage, 见 store/auth)。

## Common commands

### Backend / Starter (`lotask4j-server-spring-boot-starter/` + `lotask4j-backend/`)

```bash
# 前端双产物同步 (打包前必做, static/ gitignore)
cd frontend && pnpm build:embed && pnpm sync-embed && pnpm build && pnpm sync-console && cd ..

mvn -pl lotask4j-server-spring-boot-starter -am install   # install starter (壳/家族消费)
mvn -pl lotask4j-backend -am spring-boot:run              # 独立运行样例壳 on :9080 (Swagger at /swagger-ui.html)
mvn -pl lotask4j-server-spring-boot-starter,lotask4j-backend -am verify   # 全量测试 + JaCoCo 100% LINE 门禁 (本地标准门禁)
mvn -pl lotask4j-server-spring-boot-starter,lotask4j-backend package -DskipTests && \
  java -jar lotask4j-backend/target/lotask4j-backend-1.0.0-SNAPSHOT.jar    # packaged run

mvn -pl lotask4j-server-spring-boot-starter test -Dtest=TaskServiceImplTest            # single test class
mvn -pl lotask4j-server-spring-boot-starter test jacoco:report                          # coverage report (starter target/site/jacoco/)
```

测试需真 PG:5432 (`lotask4j` 库, admin/test@2026) + Redis:6379 (CI 用 service container; 本地 docker 起同款)。

### Demo (`lotask4j-demo/`)

```bash
mvn spring-boot:run                                   # runs DemoApplication, separate from backend
```

See `WORKER_MIGRATION_GUIDE.md` in that module for the worker heartbeat migration instructions.

### Frontend (`frontend/` — pnpm, 当前唯一维护的前端)

```bash
cd frontend
pnpm install
pnpm dev                 # vite dev server :9083 (零后端可跑, dev-mock adapter 短路)
pnpm verify              # typecheck (vue-tsc) + eslint + vitest — 提交前必过
pnpm build               # 主应用 → dist/ (base '/')
pnpm build:embed         # 嵌入组件 → dist-embed/ (base '/web-embed/', 仅 /embed/* 路由)
pnpm sync-embed          # dist-embed/ → starter static/web-embed/ (gitignore, 本地重建)
pnpm sync-console        # dist/ → starter static/ (index.html + assets/, gitignore)
pnpm test                # vitest (SDK 上游红名单已排除)
pnpm test:sdk            # SDK 全量测试 (含上游已红套件, 观察用)
pnpm test:e2e:smoke      # playwright smoke (需先 pnpm dev; 走 dev-mock 无需后端)
```

**Deploy order:** `pnpm build:embed && pnpm sync-embed && pnpm build && pnpm sync-console` first, then package the starter/backend (双产物随 starter JAR 发布)。

**Frontend conventions:** 页面在 `src/views/lotask/` (业务) 与 `src/views/dev/` (参考页, 零改动豁免); i18n 文案按域放 `src/locales/pages/{tasks,system,guides,embed}.{zh,en}.ts`; API 层 `src/api/{client,admin,embedConfig,worker,auth}.ts` (统一 http client, envelope 已解包, **embed-config 分页例外用 `items`**); 新页面找最接近的页 COPY 改造, 禁从 0 写; SDK 规则见 eslint (`Fc*` 组件 / `fc-*` class 强制)。

*(旧三前端 lotask4j-frontend / lotask4j-admin-frontend / lotask4j-web-embed-frontend 已废弃保留并行, 勿在其中开发。)*

## When you change things

业务代码一律改 `lotask4j-server-spring-boot-starter/` (薄壳 `lotask4j-backend/` 只动主类/样例 yml):

- New client/worker/admin endpoints → controller in matching `*Controller`, request/response DTOs in `dto/`, business logic in `*ServiceImpl`, persistence (if any) in `mapper/` + XML.
- New scheduled work → add a Spring `@Component` in `schedule/`; follow the pattern of `TaskArchiver` (use `@Scheduled` with `cron` / `fixedRate` and `@SchedulerLock` if multi-instance safety matters — Redisson is on the classpath).
- New task data fields → strongly prefer extending the JSONB `payload`/`result`/`steps_detail` over adding columns; if a column is unavoidable, update the XML mapper AND the entity in `entity/` AND any DTO that surfaces it.
- New tables → add `entity/*Mapper.java` + matching `src/main/resources/mapper/business/*Mapper.xml`.
- New config keys → `lotask4j.*` 命名空间 (加进 `AstsServerProperties` + checker 必填/范围校验 + 壳样例 yml); **勿新建 `app.*` 或改名 `framework4j.*`** (SDK 契约)。
- New required autoconfig exclusions → `AstsServerAutoConfigurationExcludeFilter` VETOED 集合 (勿往壳 yml 写 spring.autoconfigure.exclude)。
