# 准备工作

## 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 推荐 OpenJDK 17 / Amazon Corretto 17 |
| Maven | 3.9+ | 父 POM 来自内部 Maven 仓库（需配置 `~/.m2/settings.xml`） |
| PostgreSQL | 12+ | 主存储，需支持 JSONB；JDBC 连接串需 `?stringtype=unspecified` |
| Redis | 6+ | 限流 / 会话 / 分布式锁（默认 3 个实例：cache / business / lock） |
| Node.js + pnpm | 20+ / 9.12 | 仅构建管理前端与嵌入组件时需要 |

## 部署服务端

数据库结构由 Flyway 管理，启动即自动迁移（存量库自动打 baseline）。

```bash
# 方式一：Maven 直接运行（开发）
cd lotask4j-backend
mvn spring-boot:run          # 监听 :9080

# 方式二：打包运行（生产）
mvn clean package -DskipTests
java -jar target/lotask4j-1.0.0-SNAPSHOT.jar
```

部署嵌入组件随 JAR 发布时，需先构建前端：

```bash
cd frontend
pnpm install
pnpm build:embed && pnpm sync-embed   # 产物复制进后端 static/web-embed/
```

## 关键环境变量

| 变量 | 必填 | 说明 |
|------|------|------|
| `PLATFORM_CLIENT_SECRET` | 是（生产） | 平台运营身份凭据（`client_id=PLATFORM`） |
| `PLATFORM_CLIENT_ID` | 否 | 默认 `PLATFORM` |
| 数据库 / Redis 连接 | 是 | 见 `application.yml`，支持多数据源 |

> **注意**
> 平台凭据仅用于管理域（租户管理、任务类型配置等），请妥善保管，不要下发给业务方。

## 创建租户并获取凭据

业务方接入的第一步是由平台运营创建租户。租户密钥**明文仅在创建响应中出现一次**，落库为 AES-256-GCM 密文。

```bash
BASE=http://localhost:9080

# 1. 平台身份换取 Token
PLATFORM_TOKEN=$(curl -s -X POST $BASE/api/v1/auth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'grant_type=client_credentials&client_id=PLATFORM&client_secret=<平台凭据>' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["data"]["access_token"])')

# 2. 创建租户（保存返回的 tenantSecret！）
curl -X POST $BASE/api/v1/admin/tenants \
  -H "Authorization: Bearer $PLATFORM_TOKEN" -H "Content-Type: application/json" \
  -d '{"name":"order-service","description":"订单中心接入"}'
```

响应示例：

```json
{
  "code": 0,
  "data": {
    "id": "x1Y2z3...",
    "name": "order-service",
    "tenantSecret": "一次展示的明文密钥（40 位）"
  }
}
```

> **说明**
> 密钥遗失时可在管理台执行 reset-secret 重置；旧密钥有 24 小时宽限期，重置会立即撤销该租户全部存量会话。详见[密钥管理与轮换](../security/credential-rotation.md)。

## 验证连通性

```bash
# 用租户凭据换 Token，成功即接入就绪
curl -s -X POST $BASE/api/v1/auth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d 'grant_type=client_credentials&client_id=order-service&client_secret=<tenantSecret>'
```

## 相关文档

- [提交第一个任务](first-task.md)
- [认证与凭据](../dev-guide/auth.md)
