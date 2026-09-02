package fun.commons.lotask4j.tenant;

import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.entity.AstsTenant;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.mapper.AstsTenantMapper;
import fun.commons.lotask4j.AstsApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 租户数据隔离行为测试 (中间件中台租户设计 §3.2 规约 3:
 * "租户 A 的数据对租户 B 的 token 不可见 — 每次涉及新查询路径时补一条")。
 *
 * mapper 层确定性验证 (tenant_id 显式传参, 无需请求上下文):
 * - 详情查询: B 查 A 的任务 → null (上层转 TASK_NOT_FOUND)
 * - worker poll: A 的 worker 抢不到 B 的 PENDING 任务, 各消其队
 * - 幂等查找: 同 idempotency_key 跨租户不互见 (分区内唯一约束的租户命名空间)
 * - CAS 上报: B 的 fencing 更新打不到 A 的任务 (0 行)
 */
@SpringBootTest(classes = AstsApplication.class)
@ActiveProfiles("test")
@Transactional
@DisplayName("租户数据隔离行为测试")
class TenantIsolationTest {

    @Autowired
    private AstTaskMapper taskMapper;

    @Autowired
    private AstsTenantMapper tenantMapper;

    @Autowired
    private fun.commons.lotask4j.mapper.AstTaskTypeConfigMapper taskTypeConfigMapper;

    private Long tenantA;
    private Long tenantB;
    private Long taskA;

    @BeforeEach
    void setUp() {
        AstsTenant a = new AstsTenant();
        a.setName("iso-a-" + System.nanoTime());
        a.setTenantSecret("secret-a");
        a.setStatus("ACTIVE");
        tenantMapper.insert(a);
        tenantA = a.getId();

        AstsTenant b = new AstsTenant();
        b.setName("iso-b-" + System.nanoTime());
        b.setTenantSecret("secret-b");
        b.setStatus("ACTIVE");
        tenantMapper.insert(b);
        tenantB = b.getId();

        taskA = insertTask(tenantA, "key-" + System.nanoTime());
    }

    private Long insertTask(Long tenantId, String idemKey) {
        AstTask t = new AstTask();
        t.setId(java.util.concurrent.ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE / 2));
        t.setTaskTypeKey("data_export");
        t.setStatus("PENDING");
        t.setPriority(0);
        t.setAttempt(1);
        t.setMaxAttempts(1);
        t.setVersion(0);
        t.setProgress(0);
        t.setIdempotencyKey(idemKey);
        t.setExecutionToken(777L);
        t.setIsDeleted(0);
        t.setTenantId(tenantId);
        t.setCreatedAt(OffsetDateTime.now());
        t.setUpdatedAt(OffsetDateTime.now());
        taskMapper.insertTask(t, "{}", "{}");
        return t.getId();
    }

    @Test
    @DisplayName("详情: B 查 A 的任务 → 不可见 (上层转 TASK_NOT_FOUND)")
    void detail_crossTenantInvisible() {
        assertThat(taskMapper.selectByIdWithTypeName(taskA, tenantA)).isNotNull();
        assertThat(taskMapper.selectByIdWithTypeName(taskA, tenantB)).isNull();
        // 无租户上下文 (后台/单测) 不过滤 — 兜底语义
        assertThat(taskMapper.selectByIdWithTypeName(taskA, null)).isNotNull();
    }

    @Test
    @DisplayName("worker poll: B 的 worker 抢不到 A 的 PENDING 任务")
    void poll_crossTenantInvisible() {
        AstTask stolen = taskMapper.pollAndLockTask("data_export", "PRIORITY", "10.0.0.9", tenantB);
        assertThat(stolen).as("B 不得消费 A 的任务").isNull();

        AstTask own = taskMapper.pollAndLockTask("data_export", "PRIORITY", "10.0.0.9", tenantA);
        assertThat(own).as("A 的 worker 消费自己的任务").isNotNull();
        assertThat(own.getId()).isEqualTo(taskA);
    }

    @Test
    @DisplayName("幂等键: 同 key 跨租户不互见 (租户隔离命名空间)")
    void idempotency_scopedByTenant() {
        String sharedKey = "shared-" + System.nanoTime();
        Long taskB = insertTask(tenantB, sharedKey);
        insertTask(tenantA, sharedKey);

        assertThat(taskMapper.findByIdempotencyKey("data_export", sharedKey, tenantA).getId())
                .isNotEqualTo(taskMapper.findByIdempotencyKey("data_export", sharedKey, tenantB).getId());
        assertThat(taskMapper.findByIdempotencyKey("data_export", sharedKey, tenantB).getId())
                .isEqualTo(taskB);
    }

    @Test
    @DisplayName("CAS 取消: B 的更新打不到 A 的任务 (0 行); A 正常")
    void cas_crossTenantZeroRows() {
        AstTask task = taskMapper.selectById(taskA);
        int rows = taskMapper.markCancelRequested(taskA, task.getVersion(),
                OffsetDateTime.now(), OffsetDateTime.now(), tenantB);
        assertThat(rows).as("跨租户 CAS 必须打空").isZero();

        rows = taskMapper.markCancelRequested(taskA, task.getVersion(),
                OffsetDateTime.now(), OffsetDateTime.now(), tenantA);
        assertThat(rows).as("同租户 CAS 正常").isEqualTo(1);
    }

    @Test
    @DisplayName("同 typeKey 跨租户共存: selectByTypeKey 按 tenantId 定向, 互不可见")
    void typeKey_coexistsAcrossTenants() {
        String shared = "shared-type-" + System.nanoTime();

        fun.commons.lotask4j.entity.AstTaskTypeConfig ca = newConfig(tenantA, shared);
        fun.commons.lotask4j.entity.AstTaskTypeConfig cb = newConfig(tenantB, shared);
        taskTypeConfigMapper.insert(ca);
        taskTypeConfigMapper.insert(cb);

        // 定向查询: 各自看到自己的 (timeoutSeconds 不同以区分)
        org.assertj.core.api.Assertions.assertThat(
                taskTypeConfigMapper.selectByTypeKey(shared, tenantA).getTimeoutSeconds()).isEqualTo(111);
        org.assertj.core.api.Assertions.assertThat(
                taskTypeConfigMapper.selectByTypeKey(shared, tenantB).getTimeoutSeconds()).isEqualTo(222);

        // 跨租户不可见: A 查 B 独有 key → null
        String bOnly = "b-only-" + System.nanoTime();
        taskTypeConfigMapper.insert(newConfig(tenantB, bOnly));
        org.assertj.core.api.Assertions.assertThat(
                taskTypeConfigMapper.selectByTypeKey(bOnly, tenantA)).isNull();
    }

    private fun.commons.lotask4j.entity.AstTaskTypeConfig newConfig(Long tenantId, String key) {
        fun.commons.lotask4j.entity.AstTaskTypeConfig c = new fun.commons.lotask4j.entity.AstTaskTypeConfig();
        c.setTenantId(tenantId);
        c.setTypeKey(key);
        c.setName("it");
        c.setConcurrencyLimit(1);
        c.setTimeoutSeconds(tenantId.equals(tenantA) ? 111 : 222);
        c.setMaxRetries(0);
        c.setIsEnabled(1);
        c.setIsDeleted(0);
        c.setCreatedAt(java.time.OffsetDateTime.now());
        c.setUpdatedAt(java.time.OffsetDateTime.now());
        return c;
    }
}
