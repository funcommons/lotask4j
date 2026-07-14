package fun.commons.lotask4j.schedule;

import fun.commons.lotask4j.mapper.AstTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 僵尸任务清理器 (Reaper)
 * 定期扫描超时的 RUNNING 任务和过期任务，进行相应处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskReaper {

    private final AstTaskMapper taskMapper;

    /**
     * 每60秒执行一次僵尸任务清理
     * 检测超过10分钟(600秒)未更新的 RUNNING 任务
     */
    @Scheduled(fixedRate = 60000) // 每60秒执行一次
    @Transactional
    public void cleanZombieTasks() {
        log.debug("Running zombie task cleanup...");

        try {
            int timeoutSeconds = 600; // 默认超时时间: 10分钟
            int resetCount = taskMapper.resetTimeoutTasks(timeoutSeconds);

            if (resetCount > 0) {
                log.warn("Reset {} zombie tasks (timeout > {}s)", resetCount, timeoutSeconds);
            } else {
                log.debug("No zombie tasks found");
            }

            // 同时处理过期任务
            cleanExpiredTasks();

        } catch (Exception e) {
            log.error("Error during zombie task cleanup", e);
        }
    }

    /**
     * 清理过期任务
     * 将已过期的 PENDING 任务标记为 FAILED 状态
     */
    private void cleanExpiredTasks() {
        try {
            // 查找已过期的 PENDING 任务数量
            int expiredCount = taskMapper.countExpiredPendingTasks();

            if (expiredCount > 0) {
                log.info("Found {} expired PENDING tasks, marking as FAILED", expiredCount);

                // 将过期任务标记为 FAILED
                int updatedCount = taskMapper.markExpiredTasksAsFailed();
                log.info("Marked {} expired tasks as FAILED", updatedCount);
            }

        } catch (Exception e) {
            log.error("Error during expired tasks cleanup", e);
        }
    }
}
