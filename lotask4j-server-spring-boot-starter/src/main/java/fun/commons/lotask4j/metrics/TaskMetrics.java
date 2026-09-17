package fun.commons.lotask4j.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * P1-1: 任务专用 Micrometer 指标容器。
 *
 * 命名空间：lotask4j.*
 *
 * Counter:
 *   - lotask4j.tasks.submitted.total{type}
 *   - lotask4j.tasks.succeeded.total{type}
 *   - lotask4j.tasks.failed.total{type,error_code}
 *   - lotask4j.tasks.canceled.total{type}
 *   - lotask4j.tasks.timeout.total{type}
 *   - lotask4j.tasks.retry.total{type}
 *
 * Timer:
 *   - lotask4j.task.queue_delay_seconds (started_at - created_at)
 *   - lotask4j.task.exec_seconds (finished_at - started_at)
 *   - lotask4j.task.e2e_seconds (finished_at - created_at)
 *
 * Gauge:
 *   - lotask4j.workers.active (Worker 当前持有 lease 数)
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Component
@RequiredArgsConstructor
public class TaskMetrics {

    private final MeterRegistry registry;

    /** 当前活跃 Worker 数（按 workerId 持有 lease 的数量） */
    @Getter private final AtomicInteger activeWorkers = new AtomicInteger(0);

    public Counter submitted(String taskType) {
        return Counter.builder("lotask4j.tasks.submitted.total")
                .tag("type", safe(taskType))
                .register(registry);
    }

    public Counter succeeded(String taskType) {
        return Counter.builder("lotask4j.tasks.succeeded.total")
                .tag("type", safe(taskType))
                .register(registry);
    }

    public Counter failed(String taskType, String errorCode) {
        return Counter.builder("lotask4j.tasks.failed.total")
                .tags(Tags.of("type", safe(taskType), "error_code", safe(errorCode)))
                .register(registry);
    }

    public Counter canceled(String taskType) {
        return Counter.builder("lotask4j.tasks.canceled.total")
                .tag("type", safe(taskType))
                .register(registry);
    }

    public Counter timeout(String taskType) {
        return Counter.builder("lotask4j.tasks.timeout.total")
                .tag("type", safe(taskType))
                .register(registry);
    }

    public Counter retry(String taskType) {
        return Counter.builder("lotask4j.tasks.retry.total")
                .tag("type", safe(taskType))
                .register(registry);
    }

    public Timer queueDelay(String taskType) {
        return Timer.builder("lotask4j.task.queue_delay_seconds")
                .tag("type", safe(taskType))
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);
    }

    public Timer execSeconds(String taskType) {
        return Timer.builder("lotask4j.task.exec_seconds")
                .tag("type", safe(taskType))
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);
    }

    public Timer e2eSeconds(String taskType) {
        return Timer.builder("lotask4j.task.e2e_seconds")
                .tag("type", safe(taskType))
                .publishPercentiles(0.5, 0.9, 0.99)
                .register(registry);
    }

    /** 注册 active workers gauge（启动时一次） */
    public void registerActiveWorkersGauge() {
        Gauge.builder("lotask4j.workers.active", activeWorkers, AtomicInteger::doubleValue)
                .register(registry);
    }

    public void recordQueueDelay(String taskType, Duration d) {
        if (d != null && !d.isNegative()) {
            queueDelay(taskType).record(d.toNanos(), TimeUnit.NANOSECONDS);
        }
    }

    public void recordExec(String taskType, Duration d) {
        if (d != null && !d.isNegative()) {
            execSeconds(taskType).record(d.toNanos(), TimeUnit.NANOSECONDS);
        }
    }

    public void recordE2E(String taskType, Duration d) {
        if (d != null && !d.isNegative()) {
            e2eSeconds(taskType).record(d.toNanos(), TimeUnit.NANOSECONDS);
        }
    }

    public void workerAcquired() {
        activeWorkers.incrementAndGet();
    }

    public void workerReleased() {
        // clamp at 0 — 即使 leak 导致减到负, 也不让 gauge 出现负值
        activeWorkers.updateAndGet(prev -> Math.max(0, prev - 1));
    }

    private static String safe(String s) {
        return s == null || s.isEmpty() ? "unknown" : s;
    }
}
