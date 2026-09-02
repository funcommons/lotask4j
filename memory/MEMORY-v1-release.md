---
name: v1-release-and-ci-2026-09
description: "v1.0.0 已发布 (tag+GitHub Release), CI 双 job 全绿, compose 冒烟/压测栈可复用, 联调再揪 5 个 bug"
metadata:
  type: project
---

2026-09-02 发布 **v1.0.0**（tag + [GitHub Release](https://github.com/funcommons/lotask4j/releases/tag/v1.0.0)）。全部工作已推送，工作区干净。

**新增基础设施**（全部可复用）：
- `docker-compose.yml` + `Dockerfile`（thin JAR）: PG16+Redis7+backend，端口 19080/15432，Flyway 全量迁移
- `scripts/smoke.sh` + `webhook_receiver.py`: 全链路冒烟 24 断言（含 HMAC 签名提交、Webhook 真实投递验签）
- `scripts/poll_bench.py`: 并发 poll 压测（0 重复硬断言 + 吞吐基线；实测 16.5 任务/s, p50 15ms）
- `.github/workflows/ci.yml`: backend(mvn verify + 100% 门禁, services 起 PG/Redis) + frontend(verify + e2e)；**e2e 需 `--grep-invert "visual"`**（视觉基线是 darwin 专属，Linux 必挂）
- `deploy/grafana-dashboard.json`: 7 面板看板

**联调再揪 5 个 bug**（累计 14 个）：①spring-boot-plugin 缺 repackage → java -jar 不可启动 ②druid 3-starter 未排除 → 打包启动失败 ③dispatchTask CAS 只认 PENDING 但 pollAndLockTask 已置 RUNNING → 首次 poll 必 20409 ④progressWithVersion 不递增 version → result 永远 fencing 失败 ⑤selectByTypeKey 漏 tenant_id 列 + V5 NOT NULL 引爆类型创建缺归属（已补 tenantId 必填 + 冲突守卫 + 前端下拉）。

**Why**: "仓库根无父 POM"是过时事实（已入库），依赖全走 Aliyun 公共镜像 + JitPack，托管 runner 可构建。

**How to apply**: 发版前跑 smoke + bench；改 poll/dispatch SQL 必跑 bench（mock 组合测不出 CAS 组合语义）；新增带 tenantId 的管理接口照 embed/type-config 模式（创建必填/update 缺省保留）。见 [[coverage-100-2026-09]]、[[tenant-isolation-2026-09]]。
