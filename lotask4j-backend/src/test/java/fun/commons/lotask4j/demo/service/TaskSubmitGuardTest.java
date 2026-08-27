package fun.commons.lotask4j.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fun.commons.lotask4j.entity.AstTaskTypeConfig;
import fun.commons.lotask4j.enums.BusinessCode;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.mapper.AstTaskTypeConfigMapper;
import fun.commons.framework4j.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TaskSubmitGuard 单元测试 — P1-5 背压准入。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaskSubmitGuard 背压准入测试")
class TaskSubmitGuardTest {

    @Mock private AstTaskMapper taskMapper;
    @Mock private AstTaskTypeConfigMapper typeConfigMapper;

    @InjectMocks private TaskSubmitGuard guard;

    private AstTaskTypeConfig enabledConfig;

    @BeforeEach
    void setUp() {
        enabledConfig = new AstTaskTypeConfig();
        enabledConfig.setTypeKey("data_export");
        enabledConfig.setIsEnabled(1);
    }

    // ==================== 无配置: 不背压 ====================

    @Nested
    @DisplayName("无 type config 时不限制")
    class NoConfig {

        @Test
        @DisplayName("没有 type config 记录: 放行")
        void noConfig_Pass() {
            when(typeConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // 不抛
            assertDoesNotThrow(() -> guard.checkOrThrow("data_export"));
            // 不应查询任务计数
            verify(taskMapper, never()).countInFlightByType(anyString());
        }

        @Test
        @DisplayName("null taskType: 放行")
        void nullTaskType_Pass() {
            assertDoesNotThrow(() -> guard.checkOrThrow(null));
            verify(taskMapper, never()).countInFlightByType(anyString());
        }

        @Test
        @DisplayName("空字符串 taskType: 放行")
        void emptyTaskType_Pass() {
            assertDoesNotThrow(() -> guard.checkOrThrow(""));
            verify(taskMapper, never()).countInFlightByType(anyString());
        }
    }

    // ==================== 配置无 max_queued / max_concurrency ====================

    @Nested
    @DisplayName("配置无并发限制时: 放行")
    class NoLimitsConfig {

        @Test
        @DisplayName("两个限都没设: 不查询任务, 不抛")
        void noLimits_Pass() {
            when(typeConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(enabledConfig);

            assertDoesNotThrow(() -> guard.checkOrThrow("data_export"));
            verify(taskMapper, never()).countInFlightByType(anyString());
        }

        @Test
        @DisplayName("max_queued=null, max_concurrency=null")
        void bothNull() {
            enabledConfig.setMaxQueued(null);
            enabledConfig.setConcurrencyLimit(null);
            when(typeConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(enabledConfig);

            assertDoesNotThrow(() -> guard.checkOrThrow("data_export"));
        }

        @Test
        @DisplayName("max_queued=0 也视为不限制 (配置上等同 null)")
        void maxQueuedZero_Pass() {
            enabledConfig.setMaxQueued(0);
            when(typeConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(enabledConfig);

            assertDoesNotThrow(() -> guard.checkOrThrow("data_export"));
            // max_queued=0 视为不生效, 不查 inFlight
            verify(taskMapper, never()).countInFlightByType(anyString());
        }
    }

    // ==================== max_queued 触发 ====================

    @Nested
    @DisplayName("max_queued 触发 QUEUE_FULL")
    class MaxQueuedTriggered {

        @Test
        @DisplayName("inFlight >= max_queued: 抛 20006 QUEUE_FULL")
        void exceeds_Throws() {
            enabledConfig.setMaxQueued(10);
            when(typeConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(enabledConfig);
            when(taskMapper.countInFlightByType("data_export")).thenReturn(10L);

            ApiException ex = assertThrows(ApiException.class,
                    () -> guard.checkOrThrow("data_export"));
            assertEquals(BusinessCode.QUEUE_FULL.getCode(), ex.getCode());
            assertTrue(ex.getMessage().contains("max_queued"));
            assertTrue(ex.getMessage().contains("data_export"));
        }

        @Test
        @DisplayName("inFlight > max_queued: 也抛")
        void farExceeds_Throws() {
            enabledConfig.setMaxQueued(5);
            when(typeConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(enabledConfig);
            when(taskMapper.countInFlightByType("data_export")).thenReturn(50L);

            ApiException ex = assertThrows(ApiException.class,
                    () -> guard.checkOrThrow("data_export"));
            assertEquals(20006, ex.getCode());
        }

        @Test
        @DisplayName("inFlight < max_queued: 放行")
        void under_Pass() {
            enabledConfig.setMaxQueued(10);
            when(typeConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(enabledConfig);
            when(taskMapper.countInFlightByType("data_export")).thenReturn(5L);

            assertDoesNotThrow(() -> guard.checkOrThrow("data_export"));
        }
    }

    // ==================== max_concurrency 触发 ====================

    @Nested
    @DisplayName("max_concurrency 触发 QUEUE_FULL")
    class MaxConcurrencyTriggered {

        @Test
        @DisplayName("inFlight >= max_concurrency: 抛 20006 (max_concurrency 提示)")
        void exceeds_Throws() {
            enabledConfig.setMaxQueued(100); // 充分大, 不应触发
            enabledConfig.setConcurrencyLimit(5);
            when(typeConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(enabledConfig);
            when(taskMapper.countInFlightByType("data_export")).thenReturn(5L);

            ApiException ex = assertThrows(ApiException.class,
                    () -> guard.checkOrThrow("data_export"));
            assertEquals(20006, ex.getCode());
            assertTrue(ex.getMessage().contains("max_concurrency"));
        }

        @Test
        @DisplayName("max_concurrency 未达: 放行")
        void under_Pass() {
            enabledConfig.setConcurrencyLimit(10);
            when(typeConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(enabledConfig);
            when(taskMapper.countInFlightByType("data_export")).thenReturn(3L);

            assertDoesNotThrow(() -> guard.checkOrThrow("data_export"));
        }
    }

    // ==================== 边界 ====================

    @Nested
    @DisplayName("边界与组合")
    class Boundary {

        @Test
        @DisplayName("max_queued 与 max_concurrency 都达阈值: 抛 max_queued 优先")
        void bothReached_PrioritisesQueued() {
            enabledConfig.setMaxQueued(10);
            enabledConfig.setConcurrencyLimit(5);
            when(typeConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(enabledConfig);
            when(taskMapper.countInFlightByType("data_export")).thenReturn(15L);

            ApiException ex = assertThrows(ApiException.class,
                    () -> guard.checkOrThrow("data_export"));
            assertEquals(20006, ex.getCode());
            // max_queued 分支先判断, 故报错信息反映它
            assertTrue(ex.getMessage().contains("max_queued"));
        }
    }
}
