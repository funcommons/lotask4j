package fun.commons.lotask4j.mapper;

import fun.commons.lotask4j.entity.AstTask;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.sql.DataSource;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * pollAndLockTask 并发抢占集成测试 (真实 PostgreSQL)
 *
 * 真正验证 SKIP LOCKED + FOR UPDATE 在多 Worker 并发下的语义:
 *   - 同一任务不会被两个 Worker 同时抢到
 *   - 全部任务最终都被消费完
 *   - PRIORITY 策略按优先级降序分发
 *   - 过期任务被跳过
 *   - 跨任务类型隔离
 *
 * 不用 Testcontainers (与本地 Docker Desktop 4.74 不兼容, docker-java API 协商失败)。
 * 直接连本地 pg-17 容器, 通过 ASTS_PG_TEST_URL 环境变量启用:
 *   ASTS_PG_TEST_URL=jdbc:postgresql://localhost:5432/my_database
 *   ASTS_PG_TEST_USER=admin
 *   ASTS_PG_TEST_PASSWORD=test@2026
 *
 * CI 跑 mvn test 时若没设这些变量, 测试自动 skip, 不阻断构建。
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "ASTS_PG_TEST_URL", matches = ".+")
@DisplayName("pollAndLockTask 并发抢占集成测试 (PostgreSQL)")
class AstTaskMapperConcurrencyTest {

    @DynamicPropertySource
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("ASTS_PG_TEST_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("ASTS_PG_TEST_USER"));
        registry.add("spring.datasource.password", () -> System.getenv("ASTS_PG_TEST_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.datasource.druid.enabled", () -> "false");
    }

    @Autowired private AstTaskMapper taskMapper;
    @Autowired private DataSource dataSource;

    @BeforeAll
    static void initSchema(@Autowired DataSource ds) {
        var populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("schema-postgres.sql"));
        populator.execute(ds);
    }

    @BeforeEach
    void cleanData() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE asts_task RESTART IDENTITY");
        } catch (Exception e) {
            fail("Failed to truncate asts_task: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("10 任务 / 20 Worker 并发: 无重复, 全消费")
    void concurrentPoll_NoDuplicateAllConsumed() throws Exception {
        final int TASK_COUNT = 10;
        final int WORKER_COUNT = 20;
        final String taskType = "data_export";

        for (int i = 0; i < TASK_COUNT; i++) {
            insertPendingTask(1000L + i, taskType, (i % 5) * 10);
        }

        Set<Long> acquiredIds = ConcurrentHashMap.newKeySet();
        AtomicInteger duplicateCount = new AtomicInteger(0);
        CountDownLatch startBarrier = new CountDownLatch(1);
        CountDownLatch finishBarrier = new CountDownLatch(WORKER_COUNT);

        ExecutorService pool = Executors.newFixedThreadPool(WORKER_COUNT);
        for (int w = 0; w < WORKER_COUNT; w++) {
            final String workerIp = "10.0.0." + (w + 1);
            pool.submit(() -> {
                try {
                    startBarrier.await();
                    while (true) {
                        AstTask t = taskMapper.pollAndLockTask(taskType, "PRIORITY", workerIp);
                        if (t == null) break;
                        if (!acquiredIds.add(t.getId())) {
                            duplicateCount.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishBarrier.countDown();
                }
            });
        }

        startBarrier.countDown();
        finishBarrier.await();
        pool.shutdown();

        assertEquals(TASK_COUNT, acquiredIds.size(),
                "所有 " + TASK_COUNT + " 个任务都应被抢占");
        assertEquals(0, duplicateCount.get(),
                "SKIP LOCKED 必须保证同一任务不会被重复抢占, 发现 " + duplicateCount.get() + " 次冲突");
    }

    @Test
    @DisplayName("PRIORITY 策略: 优先级高的任务先被抢占")
    void priorityStrategy_HigherPriorityFirst() {
        final String taskType = "data_export";
        final String workerIp = "10.0.0.100";

        long[] ids = {2001L, 2002L, 2003L, 2004L, 2005L};
        int[] priorities = {10, 80, 30, 100, 50};
        for (int i = 0; i < ids.length; i++) {
            insertPendingTask(ids[i], taskType, priorities[i]);
        }

        // priority DESC: 2004(100) -> 2002(80) -> 2005(50) -> 2003(30) -> 2001(10)
        long[] expectedOrder = {2004L, 2002L, 2005L, 2003L, 2001L};
        for (long expectedId : expectedOrder) {
            AstTask t = taskMapper.pollAndLockTask(taskType, "PRIORITY", workerIp);
            assertNotNull(t);
            assertEquals(expectedId, t.getId(),
                    "PRIORITY 策略下, priority 高的任务应先被抢占");
        }
        assertNull(taskMapper.pollAndLockTask(taskType, "PRIORITY", workerIp));
    }

    @Test
    @DisplayName("过期任务不会被抢占 (expired_at <= now)")
    void expiredTaskNotPolled() {
        insertPendingTask(3001L, "data_export", 50);
        insertExpiredPendingTask(3002L, "data_export", 50);

        AstTask t = taskMapper.pollAndLockTask("data_export", "PRIORITY", "10.0.0.200");
        assertNotNull(t);
        assertEquals(3001L, t.getId(), "过期任务应被跳过");
        assertNull(taskMapper.pollAndLockTask("data_export", "PRIORITY", "10.0.0.200"),
                "过期的任务不应被抢占");
    }

    @Test
    @DisplayName("跨任务类型隔离: type=A 的 Worker 抢不到 type=B 的任务")
    void crossTypeIsolation() {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO asts_task_type_config (id, type_key, type_name, name, is_enabled) " +
                    "VALUES (2, 'video_transcode', '视频转码', 'video_transcode', 1) " +
                    "ON CONFLICT (type_key) DO NOTHING");
        } catch (Exception e) {
            fail("Failed to seed second task type: " + e.getMessage());
        }

        insertPendingTask(4001L, "data_export", 50);
        insertPendingTask(4002L, "video_transcode", 50);

        AstTask t = taskMapper.pollAndLockTask("data_export", "PRIORITY", "10.0.0.1");
        assertNotNull(t);
        assertEquals(4001L, t.getId(), "data_export Worker 不应抢到 video_transcode 任务");
    }

    private void insertPendingTask(long id, String taskType, int priority) {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(
                     "INSERT INTO asts_task (id, task_type_key, status, priority, created_at, updated_at) " +
                             "VALUES (?, ?, 'PENDING', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            ps.setLong(1, id);
            ps.setString(2, taskType);
            ps.setInt(3, priority);
            ps.executeUpdate();
        } catch (Exception e) {
            fail("Failed to insert task " + id + ": " + e.getMessage());
        }
    }

    private void insertExpiredPendingTask(long id, String taskType, int priority) {
        try (var conn = dataSource.getConnection();
             var ps = conn.prepareStatement(
                     "INSERT INTO asts_task (id, task_type_key, status, priority, expired_at, created_at, updated_at) " +
                             "VALUES (?, ?, 'PENDING', ?, CURRENT_TIMESTAMP - INTERVAL '1 hour', " +
                             "CURRENT_TIMESTAMP - INTERVAL '2 hours', CURRENT_TIMESTAMP - INTERVAL '2 hours')")) {
            ps.setLong(1, id);
            ps.setString(2, taskType);
            ps.setInt(3, priority);
            ps.executeUpdate();
        } catch (Exception e) {
            fail("Failed to insert expired task " + id + ": " + e.getMessage());
        }
    }
}
