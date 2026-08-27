package fun.commons.lotask4j.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskMetrics 单元测试 — 验证 Micrometer 埋点行为。
 *
 * 不走 Spring 上下文,直接构造 SimpleMeterRegistry。
 */
@DisplayName("TaskMetrics Micrometer 单元测试")
class TaskMetricsTest {

    private SimpleMeterRegistry registry;
    private TaskMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new TaskMetrics(registry);
        metrics.registerActiveWorkersGauge();
    }

    @Nested
    @DisplayName("Counter")
    class CounterTests {

        @Test
        @DisplayName("submitted 按 taskType 标签分桶")
        void submitted_Bucketed() {
            metrics.submitted("data_export").increment();
            metrics.submitted("data_export").increment(2);
            metrics.submitted("video_transcode").increment();

            Counter c1 = registry.find("lotask4j.tasks.submitted.total").tag("type", "data_export").counter();
            Counter c2 = registry.find("lotask4j.tasks.submitted.total").tag("type", "video_transcode").counter();
            assertNotNull(c1);
            assertNotNull(c2);
            assertEquals(3.0, c1.count());
            assertEquals(1.0, c2.count());
        }

        @Test
        @DisplayName("failed 带 error_code 标签")
        void failed_WithErrorCode() {
            metrics.failed("data_export", "PO_DB_TIMEOUT").increment();
            metrics.failed("data_export", "PO_TIMEOUT").increment(2);
            metrics.failed("data_export", "PO_DB_TIMEOUT").increment();

            Counter dbErr = registry.find("lotask4j.tasks.failed.total")
                    .tag("type", "data_export")
                    .tag("error_code", "PO_DB_TIMEOUT")
                    .counter();
            Counter timeout = registry.find("lotask4j.tasks.failed.total")
                    .tag("type", "data_export")
                    .tag("error_code", "PO_TIMEOUT")
                    .counter();
            assertNotNull(dbErr);
            assertNotNull(timeout);
            assertEquals(2.0, dbErr.count());
            assertEquals(2.0, timeout.count());
        }

        @Test
        @DisplayName("canceled / timeout / retry counter 各自独立")
        void otherCounters() {
            metrics.canceled("data_export").increment();
            metrics.timeout("data_export").increment(2);
            metrics.retry("data_export").increment(5);

            assertEquals(1.0, registry.find("lotask4j.tasks.canceled.total").tag("type", "data_export").counter().count());
            assertEquals(2.0, registry.find("lotask4j.tasks.timeout.total").tag("type", "data_export").counter().count());
            assertEquals(5.0, registry.find("lotask4j.tasks.retry.total").tag("type", "data_export").counter().count());
        }

        @Test
        @DisplayName("null taskType 不抛, 标签降级为 unknown")
        void nullType_Fallback() {
            metrics.submitted(null).increment();
            assertNotNull(registry.find("lotask4j.tasks.submitted.total")
                    .tag("type", "unknown").counter());
        }
    }

    @Nested
    @DisplayName("Timer")
    class TimerTests {

        @Test
        @DisplayName("queueDelay 记录执行时长")
        void queueDelay_record() {
            metrics.recordQueueDelay("data_export", Duration.ofMillis(500));
            metrics.recordQueueDelay("data_export", Duration.ofSeconds(2));

            Timer t = registry.find("lotask4j.task.queue_delay_seconds").tag("type", "data_export").timer();
            assertNotNull(t);
            assertEquals(2, t.count());
            assertEquals(2_500_000_000L, t.totalTime(TimeUnit.NANOSECONDS));
        }

        @Test
        @DisplayName("exec / e2e 记录执行时长")
        void exec_and_e2e() {
            metrics.recordExec("data_export", Duration.ofSeconds(10));
            metrics.recordE2E("data_export", Duration.ofSeconds(15));

            assertNotNull(registry.find("lotask4j.task.exec_seconds").timer());
            assertNotNull(registry.find("lotask4j.task.e2e_seconds").timer());
        }

        @Test
        @DisplayName("null Duration: 跳过, 不抛")
        void nullDuration_skipped() {
            // 不抛异常即可 (timer 没 sample 不算错)
            assertDoesNotThrow(() -> {
                metrics.recordQueueDelay("data_export", null);
                metrics.recordExec("data_export", null);
                metrics.recordE2E("data_export", null);
            });
        }

        @Test
        @DisplayName("负 Duration: 视为无效, 跳过")
        void negativeDuration_skipped() {
            // 负值被业务层视为无效 (时钟漂移), 不抛异常
            assertDoesNotThrow(() -> {
                metrics.recordQueueDelay("data_export", Duration.ofMillis(-100));
                metrics.recordExec("data_export", Duration.ofMillis(-100));
                metrics.recordE2E("data_export", Duration.ofMillis(-100));
            });
        }
    }

    @Nested
    @DisplayName("Gauge")
    class GaugeTests {

        @Test
        @DisplayName("activeWorkers 增减反映在 gauge 上")
        void activeWorkers_inc_dec() {
            Gauge g = registry.find("lotask4j.workers.active").gauge();
            assertNotNull(g);
            assertEquals(0.0, g.value());

            metrics.workerAcquired();
            metrics.workerAcquired();
            metrics.workerAcquired();
            assertEquals(3.0, g.value());

            metrics.workerReleased();
            assertEquals(2.0, g.value());
        }

        @Test
        @DisplayName("gauge 不会降到负值")
        void activeWorkers_noNegative() {
            Gauge g = registry.find("lotask4j.workers.active").gauge();
            metrics.workerReleased();
            metrics.workerReleased();
            assertEquals(0.0, g.value());
            assertTrue(g.value() >= 0);
        }
    }
}
