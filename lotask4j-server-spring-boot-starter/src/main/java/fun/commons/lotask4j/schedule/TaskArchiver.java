package fun.commons.lotask4j.schedule;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * 任务归档定时任务
 * 功能：
 * 1. 归档 7 天前已完成的任务 (SUCCESS/FAILED/CANCELLED)
 * 2. 保留 PENDING/RUNNING 状态的任务不归档
 * 3. 滚动预建 asts_task 月分区 (委托 {@link TaskPartitionMaintainer})
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskArchiver {

    private final AstTaskMapper taskMapper;
    private final TaskPartitionMaintainer partitionMaintainer;

    /**
     * 归档天数配置
     */
    private static final int ARCHIVE_DAYS = 7;

    /**
     * 启动时立即确保当月/下月分区存在 (冷启动兜底: V2 迁移预建的分区可能已滞后)
     */
    @PostConstruct
    public void ensurePartitionsOnStartup() {
        partitionMaintainer.ensureMonthlyPartitions();
    }

    /**
     * 每天凌晨 2:00 执行任务归档 + 滚动预建月分区
     * cron 表达式: 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void archiveOldTasks() {
        partitionMaintainer.ensureMonthlyPartitions();
        log.info("开始执行任务归档任务...");

        try {
            // 计算归档时间点：7天前
            OffsetDateTime archiveThreshold = OffsetDateTime.now().minusDays(ARCHIVE_DAYS);

            // 构建归档条件：
            // 1. 状态为 SUCCESS/FAILED/CANCELLED
            // 2. 完成时间在 7 天前
            // 3. 未被删除
            LambdaUpdateWrapper<AstTask> wrapper = new LambdaUpdateWrapper<>();
            wrapper.in(AstTask::getStatus, "SUCCESS", "FAILED", "CANCELLED")
                   .le(AstTask::getFinishedAt, archiveThreshold)
                   .eq(AstTask::getIsDeleted, 0)
                   .set(AstTask::getIsDeleted, 1)
                   .set(AstTask::getUpdatedAt, OffsetDateTime.now());

            int archivedCount = taskMapper.update(null, wrapper);

            if (archivedCount > 0) {
                log.info("任务归档完成：已归档 {} 个任务（{}天前完成的任务）", archivedCount, ARCHIVE_DAYS);
            } else {
                log.debug("任务归档完成：没有需要归档的任务");
            }
        } catch (Exception e) {
            log.error("任务归档失败", e);
        }
    }

    /**
     * 手动触发归档（用于测试或紧急归档）
     *
     * @param days 归档指定天数前的任务
     * @return 归档的任务数量
     */
    public int archiveTasksOlderThan(int days) {
        log.info("手动触发任务归档：归档 {} 天前的任务", days);

        OffsetDateTime archiveThreshold = OffsetDateTime.now().minusDays(days);

        LambdaUpdateWrapper<AstTask> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(AstTask::getStatus, "SUCCESS", "FAILED", "CANCELLED")
               .le(AstTask::getFinishedAt, archiveThreshold)
               .eq(AstTask::getIsDeleted, 0)
               .set(AstTask::getIsDeleted, 1)
               .set(AstTask::getUpdatedAt, OffsetDateTime.now());

        int archivedCount = taskMapper.update(null, wrapper);
        log.info("手动归档完成：已归档 {} 个任务", archivedCount);

        return archivedCount;
    }
}
