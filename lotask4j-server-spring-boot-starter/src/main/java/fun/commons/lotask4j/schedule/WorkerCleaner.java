package fun.commons.lotask4j.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fun.commons.lotask4j.entity.AstTaskTypeConfig;
import fun.commons.lotask4j.mapper.AstTaskTypeConfigMapper;
import fun.commons.lotask4j.mapper.AstWorkerNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Worker 节点清理定时任务
 * 功能：
 * 1. 标记超时 Worker 为 OFFLINE（2 倍任务超时时间）
 * 2. 物理删除严重超时 Worker（5 倍任务超时时间）
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkerCleaner {

    private final AstWorkerNodeMapper workerNodeMapper;
    private final AstTaskTypeConfigMapper taskTypeConfigMapper;

    /**
     * 每分钟执行一次 Worker 清理
     */
    @Scheduled(fixedRate = 60000)
    public void cleanExpiredWorkers() {
        log.debug("Running worker cleanup task...");

        // 查询所有已启用的任务类型配置
        LambdaQueryWrapper<AstTaskTypeConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AstTaskTypeConfig::getIsEnabled, 1)
               .eq(AstTaskTypeConfig::getIsDeleted, 0);

        List<AstTaskTypeConfig> configs = taskTypeConfigMapper.selectList(wrapper);

        int totalOffline = 0;
        int totalDeleted = 0;

        for (AstTaskTypeConfig config : configs) {
            String taskType = config.getTypeKey();
            Integer maxExecutionTime = config.getTimeoutSeconds();

            // 跳过没有配置超时时间的任务类型
            if (maxExecutionTime == null || maxExecutionTime <= 0) {
                log.debug("Task type {} has no timeout config, skipping", taskType);
                continue;
            }

            OffsetDateTime now = OffsetDateTime.now();

            // 1. 标记 2 倍超时为 OFFLINE
            OffsetDateTime offlineThreshold = now.minusSeconds(maxExecutionTime * 2L);
            int offlineCount = workerNodeMapper.markOfflineWorkers(taskType, offlineThreshold);
            totalOffline += offlineCount;

            // 2. 物理删除 5 倍超时
            OffsetDateTime deleteThreshold = now.minusSeconds(maxExecutionTime * 5L);
            int deletedCount = workerNodeMapper.deleteExpiredWorkers(taskType, deleteThreshold);
            totalDeleted += deletedCount;

            if (offlineCount > 0) {
                log.info("Marked {} workers OFFLINE for task type '{}' (threshold: 2x timeout = {}s)",
                        offlineCount, taskType, maxExecutionTime * 2);
            }
            if (deletedCount > 0) {
                log.warn("Deleted {} workers for task type '{}' (threshold: 5x timeout = {}s)",
                        deletedCount, taskType, maxExecutionTime * 5);
            }
        }

        if (totalOffline > 0 || totalDeleted > 0) {
            log.info("Worker cleanup summary: {} marked OFFLINE, {} deleted", totalOffline, totalDeleted);
        }
    }
}
