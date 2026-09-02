# FAQ

## 接入类

**Q：任务 ID 是数字还是字符串？**
对外均为 OpenID 混淆串（如 `Yk3xR9pQmZ2w`）。请原样回传，不要自行解析成数字。

**Q：Token 多久过期？如何续期？**
8 小时。请缓存复用；收到 401 重新换取即可。同租户重复换取会互斥旧 Token（单会话），多实例部署建议容忍互相踢或收敛刷新入口。

**Q：`callbackUrl` 可以配内网地址吗？**
服务端从部署网络发起回调，可达即可。生产建议 HTTPS；接收端务必[验签](best-practice/webhook-verify.md)。

**Q：提交时 `payload` 有大小限制吗？**
`payload` 存 JSONB，建议保持在百 KB 量级；大数据请传引用（OSS 地址等），由 Worker 自取。

## 任务类

**Q：任务一直 PENDING 不执行？**
依次检查：① 有无在线 Worker（管理台 Worker 监控）；② 任务类型是否被禁用；③ 是否背压打满（`20006`）。见[排障手册](admin-guide/monitoring.md)。

**Q：任务执行到一半 Worker 宕机了怎么办？**
租约过期后 TaskReaper 自动回收：未达 `maxAttempts` 则回 `PENDING` 重派（attempt+1），否则判 `FAILED`（`PO_TIMEOUT`）。因此 **Worker 逻辑必须幂等**。

**Q：为什么我查不到别的租户的任务？**
设计如此。跨租户访问一律返回 `TASK_NOT_FOUND`，不泄露存在性。需要跨租户治理请走平台身份 Admin API。

**Q：取消任务后状态一直是 CANCELLING？**
`CANCELLING` 等 Worker 协作确认。Worker 正常会尽快上报 `CANCELLED`；若 Worker 已消失，租约过期后系统自动兜底（重试或判死）。

**Q：重复提交会创建两个任务吗？**
带相同 `idempotencyKey`（租户+类型内）不会——返回已有任务 ID。不带幂等键则各建各的。

## Worker 类

**Q：上报报 20409 是什么意思？**
状态机冲突：任务已被重派/取消，或 `version` 落后。停止当前执行，丢弃本地状态，重新轮询。

**Q：一个 Worker 能消费多个任务类型吗？**
技术上可以（多开轮询参数），但推荐一种进程只消费一种类型，独立伸缩与故障域。

**Q：多台 Worker 会抢到同一个任务吗？**
不会。抢占走 PostgreSQL `SKIP LOCKED` + CAS，同一任务只会被一个 Worker 抢到一次（重派除外）。

## 回调类

**Q：Webhook 会重复投递吗？**
可能（重试语义）。以 `X-ASTS-Event-Id` 幂等去重。最多投递 8 次，仍失败则该事件投递终态 FAILED。

**Q：验签失败最常见原因？**
① 中间层改写了 body（签名基于 rawBody）；② 用错租户密钥；③ 服务端刚轮换密钥、接收端未更新（宽限期内建议双钥尝试）。

**Q：收不到 Webhook 怎么排查？**
看任务详情 `callbackStatus`：0=重试中，2=终态失败。再查接收端可达性/防火墙/验签日志。

## 安全类

**Q：租户密钥泄露了怎么办？**
立即在管理台 reset-secret：一步完成换钥 + 撤销全部会话。旧钥有 24 小时宽限期，紧急场景可接受业务方短暂重登。

**Q：嵌入组件的 accessKey 泄露了？**
管理台停用该配置即立即失效；必要时删除重建（新 configKey）。

## 相关文档

- [错误码](dev-guide/error-codes.md)
- [Webhook 验签与 verify-then-act](best-practice/webhook-verify.md)
- [监控与运维（排障手册）](admin-guide/monitoring.md)
