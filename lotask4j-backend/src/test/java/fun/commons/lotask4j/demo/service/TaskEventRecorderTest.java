package fun.commons.lotask4j.service;

import fun.commons.lotask4j.entity.AstTaskExecutionEvent;
import fun.commons.lotask4j.enums.TaskEventType;
import fun.commons.lotask4j.mapper.AstTaskExecutionEventMapper;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TaskEventRecorder 单元测试 — P1-3 审计轨迹。
 *
 * 注意：append-only 故障不应破坏主流程, 所以 record() 出错时只记日志不抛。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaskEventRecorder 单元测试")
class TaskEventRecorderTest {

    @Mock private AstTaskExecutionEventMapper eventMapper;

    @InjectMocks private TaskEventRecorder recorder;

    @BeforeEach
    void setUp() {
        lenient().when(eventMapper.insertDefault(any(AstTaskExecutionEvent.class), any()))
                .thenReturn(1);
    }

    // ==================== record ====================

    @Nested
    @DisplayName("record - 记录事件")
    class Record {

        @Test
        @DisplayName("正常记录: 调用 mapper.insertDefault")
        void record_Success() {
            Map<String, Object> detail = new HashMap<>();
            detail.put("key", "v");

            recorder.record(1L, TaskEventType.TASK_CREATED, null, null, "PENDING",
                    "user-1", detail);

            verify(eventMapper).insertDefault(any(AstTaskExecutionEvent.class), any());
        }

        @Test
        @DisplayName("DB 异常: 不抛 (append-only 故障不应破坏主流程)")
        void record_DbThrowsSwallowed() {
            doThrow(new RuntimeException("DB down")).when(eventMapper).insertDefault(any(), any());

            // 不抛
            assertDoesNotThrow(() ->
                    recorder.record(1L, TaskEventType.TASK_CREATED, null, null, "PENDING",
                            null, null));
        }

        @Test
        @DisplayName("重载 record 不带 detail: 仍然调用 mapper")
        void record_Overload() {
            recorder.record(1L, TaskEventType.CANCEL_REQUESTED, "PENDING", "CANCELLING");

            verify(eventMapper).insertDefault(any(AstTaskExecutionEvent.class), any());
        }

        @Test
        @DisplayName("null detail JSON 路径正常")
        void record_NullDetail() {
            assertDoesNotThrow(() ->
                    recorder.record(1L, TaskEventType.TASK_SUCCEEDED, 1, "RUNNING", "SUCCESS",
                            "wkr-1", null));
        }
    }

    // ==================== historyOf ====================

    @Nested
    @DisplayName("historyOf - 历史查询")
    class HistoryOf {

        @Test
        @DisplayName("正常查询")
        void historyOf_Success() {
            AstTaskExecutionEvent e1 = new AstTaskExecutionEvent();
            e1.setId(1L);
            e1.setTaskId(100L);
            e1.setEventType("TASK_CREATED");
            when(eventMapper.selectByTaskIdLimit(100L, 10)).thenReturn(List.of(e1));

            List<AstTaskExecutionEvent> result = recorder.historyOf(100L, 10);

            assertEquals(1, result.size());
            assertEquals(100L, result.get(0).getTaskId());
        }

        @Test
        @DisplayName("limit <= 0: 强制为 100")
        void historyOf_ZeroLimit() {
            when(eventMapper.selectByTaskIdLimit(eq(100L), eq(100))).thenReturn(List.of());

            recorder.historyOf(100L, 0);

            verify(eventMapper).selectByTaskIdLimit(eq(100L), eq(100));
        }

        @Test
        @DisplayName("limit > 1000: 强制为 100")
        void historyOf_TooLargeLimit() {
            when(eventMapper.selectByTaskIdLimit(eq(100L), eq(100))).thenReturn(List.of());

            recorder.historyOf(100L, 5000);

            verify(eventMapper).selectByTaskIdLimit(eq(100L), eq(100));
        }

        @Test
        @DisplayName("null limit: 走 mapper")
        void historyOf_NullLimit() {
            // historyOf(100, 0) 会被 coerce 成 100
            when(eventMapper.selectByTaskIdLimit(eq(100L), eq(100))).thenReturn(List.of());

            recorder.historyOf(100L, 0);
            verify(eventMapper).selectByTaskIdLimit(100L, 100);
        }
    }
}
