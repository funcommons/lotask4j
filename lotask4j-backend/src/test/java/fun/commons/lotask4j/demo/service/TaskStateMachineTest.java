package fun.commons.lotask4j.service;

import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.enums.BusinessCode;
import fun.commons.lotask4j.enums.TaskStatus;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.framework4j.id.generator.SnowflakeDistributor;
import fun.commons.framework4j.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TaskStateMachine 单元测试 — P0 中心服务的状态机语义校验。
 *
 * 覆盖矩阵：
 *   - dispatch: 成功 / CAS 失败
 *   - extendLease: 成功 / 失败
 *   - reportProgress: 成功 / 失败
 *   - requestCancel: 成功 / 失败
 *   - confirmCancel: 成功 / 失败
 *   - completeAs: SUCCESS / FAILED / CANCELLED / 参数校验失败
 *   - recoverExpiredLeases: 成功
 *   - findByIdempotencyKey: 命中 / 未命中 / 跳过 null key
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskStateMachine 中心服务测试")
class TaskStateMachineTest {

    @Mock private AstTaskMapper taskMapper;
    @Mock private SnowflakeDistributor snowflakeDistributor;
    @Mock private fun.commons.lotask4j.metrics.TaskMetrics metrics;
    @Mock private fun.commons.lotask4j.service.TaskEventRecorder eventRecorder;

    @InjectMocks private TaskStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        // 默认 lease 配置
        ReflectionTestUtils.setField(stateMachine, "defaultLeaseSeconds", 120);
        lenient().when(snowflakeDistributor.nextId()).thenReturn(99999L);
        lenient().when(metrics.submitted(anyString())).thenReturn(mock(io.micrometer.core.instrument.Counter.class));
        lenient().when(metrics.succeeded(anyString())).thenReturn(mock(io.micrometer.core.instrument.Counter.class));
        lenient().when(metrics.failed(anyString(), anyString())).thenReturn(mock(io.micrometer.core.instrument.Counter.class));
        lenient().when(metrics.canceled(anyString())).thenReturn(mock(io.micrometer.core.instrument.Counter.class));
        lenient().when(metrics.retry(anyString())).thenReturn(mock(io.micrometer.core.instrument.Counter.class));
        lenient().when(metrics.queueDelay(anyString())).thenReturn(mock(io.micrometer.core.instrument.Timer.class));
        lenient().when(metrics.execSeconds(anyString())).thenReturn(mock(io.micrometer.core.instrument.Timer.class));
        lenient().when(metrics.e2eSeconds(anyString())).thenReturn(mock(io.micrometer.core.instrument.Timer.class));
        // P1-3 事件 recorder 默认 no-op (不抛异常)
        lenient().doNothing().when(eventRecorder).record(anyLong(), any(), any(), any(), any(), any(), any());
        lenient().doNothing().when(eventRecorder).record(anyLong(), any(), any(), any());
        // Mock read-before-write 在 dispatch/complete 中需要返非空
        AstTask probe = new AstTask();
        probe.setId(1L);
        probe.setTaskTypeKey("data_export");
        probe.setCreatedAt(OffsetDateTime.now().minusSeconds(10));
        lenient().when(taskMapper.selectById(anyLong())).thenReturn(probe);
    }

    // ==================== dispatch ====================

    @Nested
    @DisplayName("dispatch - 派发任务")
    class Dispatch {

        @Test
        @DisplayName("CAS 成功: 返回 fencing token")
        void dispatch_Success() {
            when(taskMapper.dispatchTask(eq(1L), eq(0), eq("wkr-1"), anyLong(), anyLong(), eq(120), any(), isNull()))
                    .thenReturn(1);

            Long token = stateMachine.dispatch(1L, 0, "wkr-1", null);

            assertNotNull(token);
            // 二次 dispatch 生成不同 token
            when(taskMapper.dispatchTask(eq(2L), eq(0), eq("wkr-1"), anyLong(), anyLong(), eq(120), any(), isNull()))
                    .thenReturn(1);
            when(snowflakeDistributor.nextId()).thenReturn(88888L);
            Long token2 = stateMachine.dispatch(2L, 0, "wkr-1", null);
            assertNotEquals(token, token2);
        }

        @Test
        @DisplayName("CAS 失败 (rows=0): 抛 20409")
        void dispatch_CasFailure() {
            when(taskMapper.dispatchTask(anyLong(), anyInt(), anyString(), anyLong(), anyLong(), anyInt(), any(), isNull()))
                    .thenReturn(0);

            ApiException ex = assertThrows(ApiException.class,
                    () -> stateMachine.dispatch(1L, 0, "wkr-1", null));
            assertEquals(BusinessCode.TASK_STATE_INVALID.getCode(), ex.getCode());
        }
    }

    // ==================== extendLease ====================

    @Nested
    @DisplayName("extendLease - 续约")
    class ExtendLease {

        @Test
        @DisplayName("CAS 成功: 不抛")
        void extendLease_Success() {
            when(taskMapper.extendLease(eq(1L), eq(0), eq(7L), eq(120), any(), isNull())).thenReturn(1);

            assertDoesNotThrow(() -> stateMachine.extendLease(1L, 0, 7L, null));
            verify(taskMapper).extendLease(eq(1L), eq(0), eq(7L), eq(120), any(), isNull());
        }

        @Test
        @DisplayName("CAS 失败 (rows=0): 抛 20409")
        void extendLease_CasFailure() {
            when(taskMapper.extendLease(anyLong(), anyInt(), anyLong(), anyInt(), any(), isNull())).thenReturn(0);

            ApiException ex = assertThrows(ApiException.class,
                    () -> stateMachine.extendLease(1L, 0, 7L, null));
            assertEquals(BusinessCode.TASK_STATE_INVALID.getCode(), ex.getCode());
        }
    }

    // ==================== reportProgress ====================

    @Nested
    @DisplayName("reportProgress - 进度上报")
    class ReportProgress {

        private static final long TASK_ID = 1L;

        @BeforeEach
        void setUp() {
        }

        @Test
        @DisplayName("CAS 成功: 不抛")
        void reportProgress_Success() {
            when(taskMapper.progressWithVersion(eq(TASK_ID), eq(0), eq(7L),
                    eq("step1"), eq(50), anyString(), eq(50), any(), isNull()))
                    .thenReturn(1);

            ArrayList<Map<String, Object>> steps = new ArrayList<>();
            assertDoesNotThrow(() -> stateMachine.reportProgress(
                    TASK_ID, 0, 7L, "step1", 50, steps, 50, null));
        }

        @Test
        @DisplayName("CAS 失败 (rows=0): 抛 20409")
        void reportProgress_CasFailure() {
            lenient().when(taskMapper.progressWithVersion(anyLong(), anyInt(), anyLong(),
                    anyString(), anyInt(), anyString(), anyInt(), any(), isNull())).thenReturn(0);

            ApiException ex = assertThrows(ApiException.class,
                    () -> stateMachine.reportProgress(TASK_ID, 0, 7L, "step1", 50, null, 50, null));
            assertEquals(BusinessCode.TASK_STATE_INVALID.getCode(), ex.getCode());
        }
    }

    // ==================== requestCancel ====================

    @Nested
    @DisplayName("requestCancel - 用户请求取消")
    class RequestCancel {

        @Test
        @DisplayName("CAS 成功: 不抛")
        void requestCancel_Success() {
            when(taskMapper.markCancelRequested(anyLong(), anyInt(), any(), any(), isNull())).thenReturn(1);

            assertDoesNotThrow(() -> stateMachine.requestCancel(1L, 0, null));
        }

        @Test
        @DisplayName("CAS 失败 (rows=0): 抛 20409")
        void requestCancel_CasFailure() {
            when(taskMapper.markCancelRequested(anyLong(), anyInt(), any(), any(), isNull())).thenReturn(0);

            ApiException ex = assertThrows(ApiException.class,
                    () -> stateMachine.requestCancel(1L, 0, null));
            assertEquals(BusinessCode.TASK_STATE_INVALID.getCode(), ex.getCode());
        }
    }

    // ==================== confirmCancel ====================

    @Nested
    @DisplayName("confirmCancel - Worker 确认取消")
    class ConfirmCancel {

        @Test
        @DisplayName("CAS 成功: 不抛")
        void confirmCancel_Success() {
            when(taskMapper.confirmCancel(anyLong(), anyInt(), anyLong(), any(), isNull())).thenReturn(1);

            assertDoesNotThrow(() -> stateMachine.confirmCancellation(1L, 0, 7L, null));
        }

        @Test
        @DisplayName("CAS 失败 (rows=0): 抛 20409")
        void confirmCancel_CasFailure() {
            when(taskMapper.confirmCancel(anyLong(), anyInt(), anyLong(), any(), isNull())).thenReturn(0);

            ApiException ex = assertThrows(ApiException.class,
                    () -> stateMachine.confirmCancellation(1L, 0, 7L, null));
            assertEquals(BusinessCode.TASK_STATE_INVALID.getCode(), ex.getCode());
        }
    }

    // ==================== completeAs ====================

    @Nested
    @DisplayName("completeAs - 终态提交")
    class CompleteAs {

        @Test
        @DisplayName("SUCCESS: CAS 成功")
        void completeAs_Success() {
            when(taskMapper.completeWithToken(anyLong(), anyInt(), anyLong(),
                    eq("SUCCESS"), any(), any(), any(), any(), any(), isNull())).thenReturn(1);

            Map<String, Object> result = new HashMap<>();
            result.put("rows", 100);

            assertDoesNotThrow(() -> stateMachine.completeAs(1L, 0, 7L, TaskStatus.SUCCESS,
                    result, null, null, null, null));
        }

        @Test
        @DisplayName("FAILED: 携带 errorMsg")
        void completeAs_Failed() {
            when(taskMapper.completeWithToken(anyLong(), anyInt(), anyLong(),
                    eq("FAILED"), any(), any(), any(), any(), any(), isNull())).thenReturn(1);

            assertDoesNotThrow(() -> stateMachine.completeAs(1L, 0, 7L, TaskStatus.FAILED,
                    null, "DB timeout", "PO_DB_TIMEOUT", "Connection lost", null));
        }

        @Test
        @DisplayName("CANCELLED: 携带 errorCode = PO_USER_CANCEL")
        void completeAs_Cancelled() {
            when(taskMapper.completeWithToken(anyLong(), anyInt(), anyLong(),
                    eq("CANCELLED"), any(), any(), any(), any(), any(), isNull())).thenReturn(1);

            assertDoesNotThrow(() -> stateMachine.completeAs(1L, 0, 7L, TaskStatus.CANCELLED,
                    null, null, null, null, null));
        }

        @Test
        @DisplayName("非法状态 (PENDING): 抛 IllegalArgumentException")
        void completeAs_InvalidStatus() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> stateMachine.completeAs(1L, 0, 7L, TaskStatus.PENDING,
                            null, null, null, null, null));
            assertTrue(ex.getMessage().contains("completeAs"));
        }

        @Test
        @DisplayName("CAS 失败: 抛 ApiException 20409")
        void completeAs_CasFailure() {
            when(taskMapper.completeWithToken(anyLong(), anyInt(), anyLong(),
                    anyString(), any(), any(), any(), any(), any(), isNull())).thenReturn(0);

            ApiException ex = assertThrows(ApiException.class,
                    () -> stateMachine.completeAs(1L, 0, 7L, TaskStatus.SUCCESS,
                            null, null, null, null, null));
            assertEquals(BusinessCode.TASK_STATE_INVALID.getCode(), ex.getCode());
        }
    }

    // ==================== recoverExpiredLeases ====================

    @Nested
    @DisplayName("recoverExpiredLeases - Reaper 入口")
    class RecoverLeases {

        @Test
        @DisplayName("调用 mapper.resetExpiredLeases 并返回行数")
        void recover_ForwardsCall() {
            OffsetDateTime cutoff = OffsetDateTime.now();
            when(taskMapper.resetExpiredLeases(any(), any())).thenReturn(3);

            int rows = stateMachine.recoverExpiredLeases(cutoff);

            assertEquals(3, rows);
            verify(taskMapper).resetExpiredLeases(any(), any());
        }

        @Test
        @DisplayName("无过期任务时返 0")
        void recover_ZeroRows() {
            when(taskMapper.resetExpiredLeases(any(), any())).thenReturn(0);

            int rows = stateMachine.recoverExpiredLeases(OffsetDateTime.now());
            assertEquals(0, rows);
        }
    }

    // ==================== findByIdempotencyKey ====================

    @Nested
    @DisplayName("findByIdempotencyKey - 幂等检查")
    class FindByIdempotencyKey {

        @Test
        @DisplayName("命中: 返回已有任务")
        void findByKey_Hit() {
            AstTask existing = new AstTask();
            existing.setId(123L);
            existing.setTaskTypeKey("data_export");
            when(taskMapper.findByIdempotencyKey("data_export", "ord-2024-001", null)).thenReturn(existing);

            AstTask result = stateMachine.findByIdempotencyKey("data_export", "ord-2024-001", null);
            assertNotNull(result);
            assertEquals(123L, result.getId());
        }

        @Test
        @DisplayName("未命中: 返 null")
        void findByKey_Miss() {
            when(taskMapper.findByIdempotencyKey(anyString(), anyString(), isNull())).thenReturn(null);

            AstTask result = stateMachine.findByIdempotencyKey("data_export", "ord-2024-999", null);
            assertNull(result);
        }

        @Test
        @DisplayName("key=null: 直接返 null, 不查 DB")
        void findByKey_NullKeySkips() {
            AstTask result = stateMachine.findByIdempotencyKey("data_export", null, null);
            assertNull(result);
            verifyNoInteractions(taskMapper);
        }

        @Test
        @DisplayName("key=\"\" 空串: 直接返 null")
        void findByKey_EmptyKeySkips() {
            AstTask result = stateMachine.findByIdempotencyKey("data_export", "", null);
            assertNull(result);
            verifyNoInteractions(taskMapper);
        }
    }

    // ==================== TaskStatus.canTransition ====================

    @Nested
    @DisplayName("TaskStatus 状态机规则")
    class StateMachineRules {

        @Test
        @DisplayName("PENDING → RUNNING 合法")
        void pending_Running() {
            assertTrue(TaskStatus.canTransition("PENDING", "RUNNING"));
        }

        @Test
        @DisplayName("PENDING → CANCELLING 合法")
        void pending_Cancelling() {
            assertTrue(TaskStatus.canTransition("PENDING", "CANCELLING"));
        }

        @Test
        @DisplayName("RUNNING → CANCELLING 合法")
        void running_Cancelling() {
            assertTrue(TaskStatus.canTransition("RUNNING", "CANCELLING"));
        }

        @Test
        @DisplayName("CANCELLING → CANCELLED 合法")
        void cancelling_Cancelled() {
            assertTrue(TaskStatus.canTransition("CANCELLING", "CANCELLED"));
        }

        @Test
        @DisplayName("CANCELLING → FAILED 合法 (取消过程中出错)")
        void cancelling_Failed() {
            assertTrue(TaskStatus.canTransition("CANCELLING", "FAILED"));
        }

        @Test
        @DisplayName("SUCCESS → 任何终态 都非法")
        void success_AnyTerminal_Invalid() {
            assertFalse(TaskStatus.canTransition("SUCCESS", "PENDING"));
            assertFalse(TaskStatus.canTransition("SUCCESS", "FAILED"));
            assertFalse(TaskStatus.canTransition("SUCCESS", "CANCELLED"));
        }

        @Test
        @DisplayName("CANCELLED → 任意 都非法")
        void cancelled_Any_Invalid() {
            assertFalse(TaskStatus.canTransition("CANCELLED", "RUNNING"));
            assertFalse(TaskStatus.canTransition("CANCELLED", "CANCELLING"));
        }

        @Test
        @DisplayName("同状态 → 同状态 非法")
        void sameState_Invalid() {
            assertFalse(TaskStatus.canTransition("PENDING", "PENDING"));
            assertFalse(TaskStatus.canTransition("RUNNING", "RUNNING"));
        }

        @Test
        @DisplayName("null 参数: 非法")
        void null_Args() {
            assertFalse(TaskStatus.canTransition(null, "RUNNING"));
            assertFalse(TaskStatus.canTransition("PENDING", null));
        }

        @Test
        @DisplayName("TERMINAL 集合正确")
        void terminal_Set() {
            assertTrue(TaskStatus.TERMINAL.contains(TaskStatus.SUCCESS));
            assertTrue(TaskStatus.TERMINAL.contains(TaskStatus.FAILED));
            assertTrue(TaskStatus.TERMINAL.contains(TaskStatus.CANCELLED));
            assertFalse(TaskStatus.TERMINAL.contains(TaskStatus.PENDING));
            assertFalse(TaskStatus.TERMINAL.contains(TaskStatus.RUNNING));
            assertFalse(TaskStatus.TERMINAL.contains(TaskStatus.CANCELLING));
        }

        @Test
        @DisplayName("isCancellable 仅在 PENDING/RUNNING 时返 true")
        void isCancellable() {
            assertTrue(TaskStatus.PENDING.isCancellable());
            assertTrue(TaskStatus.RUNNING.isCancellable());
            assertFalse(TaskStatus.SUCCESS.isCancellable());
            assertFalse(TaskStatus.FAILED.isCancellable());
            assertFalse(TaskStatus.CANCELLING.isCancellable());
            assertFalse(TaskStatus.CANCELLED.isCancellable());
        }
    }
}
