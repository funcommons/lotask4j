package fun.commons.lotask4j.schedule;

import fun.commons.lotask4j.mapper.TaskPartitionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/**
 * asts_task 月分区维护器 (V2 分区迁移后的滚动预建)
 *
 * 独立 Service 而非并入 {@link TaskArchiver}: @Transactional 需经代理生效,
 * 同类内部调用 (self-invocation) 不走事务。归档与分区预建是两件事, 事务边界各自独立。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskPartitionMaintainer {

    private final TaskPartitionMapper partitionMapper;

    private static final DateTimeFormatter PARTITION_SUFFIX = DateTimeFormatter.ofPattern("yyyyMM");

    /**
     * 确保当月 + 下月分区存在 (每日 02:00 随归档任务执行; 应用启动时兜底执行一次)。
     * 单月失败不影响另一月。
     */
    public void ensureMonthlyPartitions() {
        ensureQuietly(YearMonth.now());
        ensureQuietly(YearMonth.now().plusMonths(1));
    }

    private void ensureQuietly(YearMonth month) {
        try {
            ensurePartitionFor(month);
        } catch (Exception e) {
            // ATTACH 瞬间的新写入可能引起 default 冲突; 下次执行重试
            log.error("预建月分区 {} 失败 (下次执行重试)", month, e);
        }
    }

    /**
     * 单个月份的分区保障。事务保证 copy + delete 两步搬运的原子性
     * (PG 支持事务性 DDL, CREATE/ATTACH 同样参与回滚)。
     */
    @Transactional
    public void ensurePartitionFor(YearMonth month) {
        String name = "asts_task_" + month.format(PARTITION_SUFFIX);
        String start = month.atDay(1).toString();                  // yyyy-MM-01
        String end = month.plusMonths(1).atDay(1).toString();

        if (partitionMapper.partitionExists(name)) {
            return;
        }

        int stuck = partitionMapper.countDefaultRowsInRange(start, end);
        if (stuck == 0) {
            partitionMapper.createPartition(name, start, end);
            log.info("已预建月分区 {} [{}, {})", name, start, end);
            return;
        }

        // default 有滞留行: 承接表 copy→delete (事务内原子) → ATTACH
        partitionMapper.createStandaloneLike(name);
        int moved = partitionMapper.copyDefaultRowsTo(name, start, end);
        partitionMapper.deleteDefaultRowsInRange(start, end);
        partitionMapper.attachPartition(name, start, end);
        log.info("月分区 {} 经承接表建立, 从 default 搬运 {} 行 [{}, {})", name, moved, start, end);
    }
}
