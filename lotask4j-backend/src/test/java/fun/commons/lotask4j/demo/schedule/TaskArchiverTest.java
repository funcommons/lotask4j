package fun.commons.lotask4j.schedule;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaskArchiver 定时归档任务测试")
class TaskArchiverTest {

    @Mock
    private AstTaskMapper taskMapper;

    @Mock
    private fun.commons.lotask4j.schedule.TaskPartitionMaintainer partitionMaintainer;

    @InjectMocks
    private TaskArchiver archiver;

    @BeforeAll
    static void initLambdaCache() {
        // LambdaUpdateWrapper 需要实体表信息缓存；MyBatis-Plus 在 Spring 启动时会自动初始化，
        // 纯单元测试场景下需要手动初始化一次。
        org.apache.ibatis.session.Configuration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(assistant, AstTask.class);
    }

    @Test
    @DisplayName("archiveTasksOlderThan 返回 mapper 更新行数")
    void manualArchive_ReturnsCount() {
        when(taskMapper.update(isNull(), any())).thenReturn(15);

        int result = archiver.archiveTasksOlderThan(7);

        assertEquals(15, result);
    }

    @Test
    @DisplayName("archiveTasksOlderThan 0 天也能正常执行（边界）")
    void manualArchive_ZeroDays() {
        when(taskMapper.update(isNull(), any())).thenReturn(0);

        assertEquals(0, archiver.archiveTasksOlderThan(0));
    }

    @Test
    @DisplayName("archiveOldTasks 定时任务有归档时走 info 分支")
    void scheduled_HasArchived_LogsInfo() {
        lenient().when(taskMapper.update(isNull(), any())).thenReturn(5);

        archiver.archiveOldTasks(); // 不抛异常即可
    }

    @Test
    @DisplayName("archiveOldTasks 定时任务无归档走 debug 分支")
    void scheduled_NoArchived_LogsDebug() {
        lenient().when(taskMapper.update(isNull(), any())).thenReturn(0);

        archiver.archiveOldTasks();
    }

    @Test
    @DisplayName("archiveOldTasks mapper 抛异常被吞掉（不影响下次调度）")
    void scheduled_MapperThrows_NoPropagate() {
        lenient().when(taskMapper.update(isNull(), any())).thenThrow(new RuntimeException("DB down"));

        archiver.archiveOldTasks(); // 不抛
    }
}
