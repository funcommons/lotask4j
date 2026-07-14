# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository overview

This is the monorepo for **lotask4j 异步慢任务服务 (ASTS) — Asynchronous Slow Task Service**: a distributed platform for executing long-running (>10s) business logic (data export, video transcoding, etc.) with real-time progress reporting, cancellation, and a visual admin backend. The README.md is the authoritative project overview, run instructions, and architecture diagram — read it first for the high-level picture.

The repo root holds five sibling modules (no parent `pom.xml`, no npm workspaces — each module builds independently):

| Module | Stack | Purpose |
|--------|-------|---------|
| `lotask4j-backend` | Spring Boot 3.2.x / JDK 17+ / Maven | REST API server on **port 8080** — exposes client/worker/admin/web-embed APIs, scheduled jobs. `fun.commons.lotask4j.*` package root. |
| `lotask4j-demo` | Spring Boot / Maven | Standalone example showing how to integrate as a **client** (submit/get/cancel tasks) and as a **worker** (poll + execute). Separate from backend. |
| `lotask4j-frontend` | React 18 + Vite + TypeScript + Antd + Zustand | Main user console. Dev port **9081**; proxies `/api` → `:9080`, `/web-embed` → `:9082`. |
| `lotask4j-admin-frontend` | Vue 3 + Vite | Admin UI for task/worker/configuration management. |
| `lotask4j-web-embed-frontend` | Vue 3 + Vite | Embeddable task-list widget served **by the backend** as static assets. |

## Important non-obvious facts

- **No parent POM at the repo root.** The backend's `pom.xml` declares `<parent>fun.commons.lotask4j:lotask4j-parent:1.0.0-SNAPSHOT</parent>`, which is resolved from the **company's internal Maven repository** (configured via `~/.m2/settings.xml`). Building locally without that mirror configured will fail at dependency resolution.
- **The embed widget is bundled into the backend JAR.** The web-embed frontend is built (`npm run build`) and its `dist/` copied into `lotask4j-backend/src/main/resources/static/web-embed/`. The backend serves those static files from the same Spring Boot process. **Build the embed frontend before packaging the backend**, or the embed widget will be stale/missing.
- **README references `documents/` and `MAVEN_INIT_GUIDE.md` — neither exists in this repo.** They live elsewhere (likely an internal wiki). Do not waste time hunting for them here.
- **ID generation is centralised in the `fun.commons.fwk4j:fwk4j-sdk`-compatible SDK (migrated to `com.github.funcommons.framework4j:framework4j-all`)**, not implemented in this repo. Production uses the Redis worker strategy (`framework4j.id.worker.strategy: redis`); local dev can use `ip`. The backend excludes MyBatis Plus, DataSource, and Redisson auto-configurations explicitly in `application.yml` so the SDK's own beans wire up cleanly.
- **JaCoCo is enforced at ≥75% line coverage per package** at the `jacoco-check` phase. A green `mvn test` is not enough — coverage must clear the gate.
- **There's a `WebMvcConfig.java`** (so don't add `@EnableWebMvc`) and **`HttpClientConfig.java`** that registers the shared HTTP client used for worker callbacks and webhook delivery.
- **`@OpenId @PathVariable Long id` 必须显式写 `@PathVariable("id")`**：framework4j v1.1.3 的 `OpenIdFailFastValidator` 启动期检测 `@OpenId @PathVariable` 但 `@PathVariable` 没指定 `name` 又 class file 缺 `MethodParameters` attribute 的方法，**直接 fail-fast 启动崩溃**（不在 path 解析时 silent 抛 10106 了）。CI 也全绿（验证脚本见 https://github.com/funcommons/framework4j/issues/1）。
- **不要在 Maven 加 `<parameters>true</parameters>`**：详见下面 framework4j 行为合约。

## Backend architecture

The backend is a classic layered Spring Boot app: Controllers → Service interfaces → Service impls → MyBatis-Plus Mappers → XML mappers (`src/main/resources/mapper/business/`).

**Five external surfaces, each behind its own controller:**

| Controller | Audience | Purpose |
|-----------|----------|---------|
| `ClientTaskController` | Upstream business systems | Submit / query / cancel tasks. Path: `/api/v1/client/**` |
| `WorkerTaskController` | Worker processes | Poll pending tasks, report progress, report result, register/heartbeat the worker node. Path: `/api/v1/worker/**` |
| `AdminTaskController` | Admin frontend / operators | Task CRUD, worker-node registry, task-type config, statistics, archive view. |
| `AdminWebEmbedController` | Admin frontend | CRUD for `web_embed_config` rows (controls the embed widget's per-tenant config). |
| `WebEmbedController` | The embed widget itself | Public-facing config fetch used by the embedded UI. |

**Service layer uses interface-in-`service/` + impl-in-`service/impl/` convention.** The Spring beans are the `*Impl` classes (`TaskServiceImpl`, `AdminServiceImpl`, `WorkerServiceImpl`, `CallbackServiceImpl`, `WebhookServiceImpl`, `WebEmbedServiceImpl`, `AdminWebEmbedServiceImpl`). When modifying behavior, edit the `Impl`, not the interface.

**Scheduled jobs (`schedule/` package):**
- `TaskArchiver` — runs daily 02:00, flips `is_deleted=1` on tasks completed ≥7 days ago (PENDING/RUNNING are excluded). Logical delete — rows remain.
- `WorkerCleaner` — runs every minute, removes offline workers from `asts_worker_node`.
- `TaskReaper` — reaps stuck/timed-out tasks back to a recoverable state.

**Data model — PostgreSQL with heavy JSONB use.** `task.payload`, `task.result`, `task.steps_detail` and `task.steps_history` are JSONB; do not add new columns without first checking whether they belong inside one of those blobs. Tables:
- `asts_task` — primary task table (status: `PENDING/RUNNING/SUCCESS/FAILED/CANCELLING/CANCELLED`; progress 0–100)
- `asts_task_type_config` — type definitions (handler routing, timeouts, etc.)
- `asts_worker_node` — registered worker registry
- `asts_task_history` — archive cold-storage (read-only to frontend)
- `web_embed_config` — embed widget per-tenant config

Frontend surfaces differentiate "current tasks" (`is_deleted=0`) vs "archived tasks" (`is_deleted=1`, read-only).

## framework4j v1.1.3 behavior contract

This project pins `com.github.funcommons.framework4j:framework4j-all:v1.1.3` (JitPack mirror of GitHub `funcommons/framework4j`). The SDK's `GlobalExceptionHandler` decides HTTP status + business code for every exception. Knowing this contract avoids trial-and-error when adding endpoints.

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

`OpenIdPathVariableArgumentResolver` 在 `IdObfuscator.fromOpenId()` 抛 IAE 时主动 `throw new ApiException(ApiCode.BUSINESS_RULE_ERROR, "Invalid @OpenId path variable ...")`,**不**走 `handleIllegalArgumentException` 的 10102 分流。所以非法 OpenID 路径拿到的是 **`code=10106` 而非 `10102`**。两种合理,但跟 issue #1 的优先级 3 提案不完全一致。看到这个不要误以为又是 v1.1.2 的 IAE bug。

## Common commands

### Backend (`lotask4j-backend/`)

```bash
mvn clean install -DskipTests                         # build + install parent/sdk deps
mvn spring-boot:run                                   # run on :8080 (Swagger at /swagger-ui.html)
mvn clean package -DskipTests && \
  java -jar target/lotask4j-1.0.0-SNAPSHOT.jar   # packaged run

mvn test                                              # all tests + JaCoCo coverage gate
mvn test -Dtest=TaskServiceImplTest                   # single test class
mvn test -Dtest=TaskServiceImplTest#someMethod        # single test method
mvn test jacoco:report                                # write coverage report (target/site/jacoco/)
```

### Demo (`lotask4j-demo/`)

```bash
mvn spring-boot:run                                   # runs DemoApplication, separate from backend
```

See `WORKER_MIGRATION_GUIDE.md` in that module for the worker heartbeat migration instructions.

### Frontends (each one is independent)

```bash
cd lotask4j-frontend                # user console — dev :9081
cd lotask4j-admin-frontend          # admin UI
cd lotask4j-web-embed-frontend      # embed widget — build output is consumed by backend

npm install
npm run dev              # vite dev server
npm run build            # tsc + vite build → dist/
npm run lint             # eslint
npm run type-check       # tsc --noEmit
```

**Deploy order:** build `lotask4j-web-embed-frontend` first, copy its `dist/` into `lotask4j-backend/src/main/resources/static/web-embed/`, then build the backend.

## When you change things

- New client/worker/admin endpoints → controller in matching `*Controller`, request/response DTOs in `dto/`, business logic in `*ServiceImpl`, persistence (if any) in `mapper/` + XML.
- New scheduled work → add a Spring `@Component` in `schedule/`; follow the pattern of `TaskArchiver` (use `@Scheduled` with `cron` / `fixedRate` and `@SchedulerLock` if multi-instance safety matters — Redisson is on the classpath).
- New task data fields → strongly prefer extending the JSONB `payload`/`result`/`steps_detail` over adding columns; if a column is unavoidable, update the XML mapper AND the entity in `entity/` AND any DTO that surfaces it.
- New tables → add `entity/*Mapper.java` + matching `src/main/resources/mapper/business/*Mapper.xml`.
