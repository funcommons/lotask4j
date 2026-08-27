package fun.commons.lotask4j.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fun.commons.lotask4j.entity.AstTaskTypeConfig;
import fun.commons.lotask4j.mapper.AstTaskTypeConfigMapper;
import fun.commons.lotask4j.mapper.AstWorkerNodeMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WorkerCleaner 单元测试。
 *
 * P0: 验证 Worker 节点的 OFFLINE 标记 + 物理删除逻辑。
 * 未来可考虑基于 last_heartbeat_at 而不是 timeoutSeconds,
 * 现在仅维护现有行为的测试覆盖。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorkerCleaner 单元测试")
class WorkerCleanerTest {

    @Mock private AstTaskTypeConfigMapper typeConfigMapper;
    @Mock private AstWorkerNodeMapper workerNodeMapper;

    @InjectMocks private WorkerCleaner cleaner;

    @Test
    @DisplayName("无任务类型配置: 跳过整个流程")
    void cleanExpiredWorkers_NoConfigs() {
        when(typeConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        cleaner.cleanExpiredWorkers();

        verify(typeConfigMapper).selectList(any(LambdaQueryWrapper.class));
        verifyNoInteractions(workerNodeMapper);
    }

    @Test
    @DisplayName("任务类型无 timeout 配置: 跳过该类型")
    void cleanExpiredWorkers_SkipNoTimeout() {
        AstTaskTypeConfig cfg = new AstTaskTypeConfig();
        cfg.setTypeKey("unknown_type");
        cfg.setTimeoutSeconds(null);

        when(typeConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(cfg));

        cleaner.cleanExpiredWorkers();

        verify(workerNodeMapper, never()).markOfflineWorkers(anyString(), any());
        verify(workerNodeMapper, never()).deleteExpiredWorkers(anyString(), any());
    }

    @Test
    @DisplayName("任务类型 timeout=0: 跳过该类型")
    void cleanExpiredWorkers_SkipZeroTimeout() {
        AstTaskTypeConfig cfg = new AstTaskTypeConfig();
        cfg.setTypeKey("zero_type");
        cfg.setTimeoutSeconds(0);

        when(typeConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(cfg));

        cleaner.cleanExpiredWorkers();

        verify(workerNodeMapper, never()).markOfflineWorkers(anyString(), any());
    }

    @Test
    @DisplayName("正常路径: 标记 OFFLINE + 物理删除")
    void cleanExpiredWorkers_NormalPath() {
        AstTaskTypeConfig cfg = new AstTaskTypeConfig();
        cfg.setTypeKey("data_export");
        cfg.setTimeoutSeconds(60);

        when(typeConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(cfg));
        when(workerNodeMapper.markOfflineWorkers(eq("data_export"), any(OffsetDateTime.class)))
                .thenReturn(2);
        when(workerNodeMapper.deleteExpiredWorkers(eq("data_export"), any(OffsetDateTime.class)))
                .thenReturn(1);

        cleaner.cleanExpiredWorkers();

        verify(workerNodeMapper).markOfflineWorkers(eq("data_export"), any());
        verify(workerNodeMapper).deleteExpiredWorkers(eq("data_export"), any());
    }

    @Test
    @DisplayName("多个任务类型: 累加 OFFLINE 与删除计数")
    void cleanExpiredWorkers_MultipleTypes() {
        AstTaskTypeConfig c1 = new AstTaskTypeConfig();
        c1.setTypeKey("a");
        c1.setTimeoutSeconds(60);
        AstTaskTypeConfig c2 = new AstTaskTypeConfig();
        c2.setTypeKey("b");
        c2.setTimeoutSeconds(120);

        when(typeConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(c1, c2));
        when(workerNodeMapper.markOfflineWorkers(eq("a"), any())).thenReturn(1);
        when(workerNodeMapper.deleteExpiredWorkers(eq("a"), any())).thenReturn(0);
        when(workerNodeMapper.markOfflineWorkers(eq("b"), any())).thenReturn(3);
        when(workerNodeMapper.deleteExpiredWorkers(eq("b"), any())).thenReturn(2);

        cleaner.cleanExpiredWorkers();

        verify(workerNodeMapper).markOfflineWorkers(eq("a"), any());
        verify(workerNodeMapper).markOfflineWorkers(eq("b"), any());
        verify(workerNodeMapper).deleteExpiredWorkers(eq("b"), any());
    }

    @Test
    @DisplayName("无 OFFLINE/删除: 不打 info 日志分支 (但执行 SQL)")
    void cleanExpiredWorkers_ZeroChanges() {
        AstTaskTypeConfig cfg = new AstTaskTypeConfig();
        cfg.setTypeKey("data_export");
        cfg.setTimeoutSeconds(60);

        when(typeConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(cfg));
        when(workerNodeMapper.markOfflineWorkers(anyString(), any())).thenReturn(0);
        when(workerNodeMapper.deleteExpiredWorkers(anyString(), any())).thenReturn(0);

        cleaner.cleanExpiredWorkers();

        verify(workerNodeMapper).markOfflineWorkers(eq("data_export"), any());
        verify(workerNodeMapper).deleteExpiredWorkers(eq("data_export"), any());
    }
}
