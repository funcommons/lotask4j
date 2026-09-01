package fun.commons.lotask4j.demo.schedule;

import fun.commons.lotask4j.mapper.TaskPartitionMapper;
import fun.commons.lotask4j.schedule.TaskPartitionMaintainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TaskPartitionMaintainer 单元测试 — 月分区预建三分支 + 失败吞并
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaskPartitionMaintainer 单元测试")
class TaskPartitionMaintainerTest {

    @Mock
    private TaskPartitionMapper partitionMapper;

    @InjectMocks
    private TaskPartitionMaintainer maintainer;

    @Test
    @DisplayName("分区已存在 → 不再创建")
    void ensure_partitionExists() {
        when(partitionMapper.partitionExists("asts_task_202609")).thenReturn(true);

        maintainer.ensurePartitionFor(YearMonth.of(2026, 9));

        verify(partitionMapper, never()).createPartition(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("default 无滞留行 → 直接 CREATE 分区")
    void ensure_noStuckRows() {
        when(partitionMapper.partitionExists("asts_task_202609")).thenReturn(false);
        when(partitionMapper.countDefaultRowsInRange("2026-09-01", "2026-10-01")).thenReturn(0);

        maintainer.ensurePartitionFor(YearMonth.of(2026, 9));

        verify(partitionMapper).createPartition("asts_task_202609", "2026-09-01", "2026-10-01");
        verify(partitionMapper, never()).attachPartition(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("default 有滞留行 → 承接表 copy→delete→ATTACH")
    void ensure_stuckRows_movedViaStaging() {
        when(partitionMapper.partitionExists("asts_task_202610")).thenReturn(false);
        when(partitionMapper.countDefaultRowsInRange("2026-10-01", "2026-11-01")).thenReturn(3);
        when(partitionMapper.copyDefaultRowsTo("asts_task_202610", "2026-10-01", "2026-11-01")).thenReturn(3);

        maintainer.ensurePartitionFor(YearMonth.of(2026, 10));

        verify(partitionMapper).createStandaloneLike("asts_task_202610");
        verify(partitionMapper).copyDefaultRowsTo("asts_task_202610", "2026-10-01", "2026-11-01");
        verify(partitionMapper).deleteDefaultRowsInRange("2026-10-01", "2026-11-01");
        verify(partitionMapper).attachPartition("asts_task_202610", "2026-10-01", "2026-11-01");
    }

    @Test
    @DisplayName("单月失败 → 吞并不外抛, 不影响另一月")
    void ensureMonthlyPartitions_swallowsFailure() {
        when(partitionMapper.partitionExists(anyString())).thenThrow(new RuntimeException("pg busy"));

        assertDoesNotThrow(() -> maintainer.ensureMonthlyPartitions());
    }

    @Test
    @DisplayName("默认分支: 承接路径 attach 失败 → ensureMonthlyPartitions 吞并")
    void ensureMonthlyPartitions_attachFails_swallows() {
        when(partitionMapper.partitionExists(anyString())).thenReturn(false);
        when(partitionMapper.countDefaultRowsInRange(anyString(), anyString())).thenReturn(1);
        org.mockito.Mockito.doThrow(new RuntimeException("attach conflict"))
                .when(partitionMapper).attachPartition(anyString(), anyString(), anyString());

        assertDoesNotThrow(() -> maintainer.ensureMonthlyPartitions());
        verify(partitionMapper, org.mockito.Mockito.atLeastOnce())
                .createStandaloneLike(anyString());
    }
}
