package fun.commons.lotask4j.schedule;

import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.service.TaskStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 僵尸任务清理器 (Reaper) — P0: lease-aware 路径。
 *
 * 流程：
 * <ol>
 *   <li>扫描 lease_expire_at &lt; now() 的 RUNNING 任务</li>
 *   <li>走 {@link TaskStateMachine#recoverExpiredLeases}：
 *      - attempt &lt; max_attempts → 回到 PENDING, attempt+1, 标记重试</li>
 *      - attempt &gt;= max_attempts → 直接 FAILED</li>
 * </ol>
 *
 * 旧的基于 updated_at 的 resetTimeoutTasks 仍作为安全网保留，
 * 但 Reaper 优先调用新路径。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskReaper {

    private final AstTaskMapper taskMapper;
    private final TaskStateMachine stateMachine;

    /** Reaper 容忍多少秒 lease 过期才回收（避免时钟漂移误杀） */
    @Value("${app.asts.lease-grace-seconds:30}")
    private int leaseGraceSeconds;

    /**
     * 每 30 秒执行一次（lease 上限 120s，故 30s 一次足够及时）。
     */
    @Scheduled(fixedRateString = "${app.asts.reaper-interval:30000}")
    @Transactional
    public void cleanZombieTasks() {
        log.debug("Running zombie task cleanup (lease-aware)...");

        try {
            OffsetDateTime leaseCutoff = OffsetDateTime.now().minusSeconds(leaseGraceSeconds);
            int recovered = stateMachine.recoverExpiredLeases(leaseCutoff);
            if (recovered > 0) {
                log.warn("Reaper 回收 lease 过期任务: count={}, grace={}s",
                        recovered, leaseGraceSeconds);
            } else {
                log.debug("无 lease 过期任务");
            }

            cleanExpiredTasks();
        } catch (Exception e) {
            log.error("Reaper 清理失败", e);
        }
    }

    /**
     * 清理过期 pending 任务 (用户 expired_at 触发的失效)
     */
    private void cleanExpiredTasks() {
        try {
            int expiredCount = taskMapper.countExpiredPendingTasks();
            if (expiredCount > 0) {
                log.info("发现 {} 过期 PENDING 任务, 标记 FAILED", expiredCount);
                int updatedCount = taskMapper.markExpiredTasksAsFailed();
                log.info("已标记 {} 过期任务为 FAILED", updatedCount);
            }
        } catch (Exception e) {
            log.error("清理过期任务失败", e);
        }
    }
}
