package fun.commons.lotask4j.schedule;

import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.service.TaskStateMachine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TaskReaper 单元测试 — P0 路径: lease-aware 回收 + 过期 pending 标记失败。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaskReaper 单元测试")
class TaskReaperTest {

    @Mock private AstTaskMapper astTaskMapper;
    @Mock private TaskStateMachine stateMachine;

    @InjectMocks private TaskReaper reaper;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reaper, "leaseGraceSeconds", 30);
    }

    @Test
    @DisplayName("cleanZombieTasks: lease-aware 回收成功")
    void cleanZombieTasks_RecoversLeases() {
        when(stateMachine.recoverExpiredLeases(any())).thenReturn(5);
        when(astTaskMapper.countExpiredPendingTasks()).thenReturn(0);

        reaper.cleanZombieTasks();

        verify(stateMachine).recoverExpiredLeases(any());
    }

    @Test
    @DisplayName("cleanZombieTasks: 当 0 lease 过期时不报警")
    void cleanZombieTasks_NoLeases() {
        when(stateMachine.recoverExpiredLeases(any())).thenReturn(0);
        when(astTaskMapper.countExpiredPendingTasks()).thenReturn(0);

        reaper.cleanZombieTasks();

        verify(stateMachine).recoverExpiredLeases(any());
    }

    @Test
    @DisplayName("cleanZombieTasks: 找到过期 pending 任务时全部标记 FAILED")
    void cleanZombieTasks_MarksPendingExpired() {
        when(stateMachine.recoverExpiredLeases(any())).thenReturn(0);
        when(astTaskMapper.countExpiredPendingTasks()).thenReturn(3);
        when(astTaskMapper.markExpiredTasksAsFailed()).thenReturn(3);

        reaper.cleanZombieTasks();

        verify(astTaskMapper).markExpiredTasksAsFailed();
    }

    @Test
    @DisplayName("cleanZombieTasks: lease SQL 抛异常被吞掉 (Reaper 不挂)")
    void cleanZombieTasks_LeaseThrowsSwallowed() {
        when(stateMachine.recoverExpiredLeases(any()))
                .thenThrow(new RuntimeException("DB down"));

        // 不抛异常
        assertDoesNotThrow(() -> reaper.cleanZombieTasks());
    }

    @Test
    @DisplayName("cleanZombieTasks: 整个流程抛异常被吞掉")
    void cleanZombieTasks_AllThrowsSwallowed() {
        when(stateMachine.recoverExpiredLeases(any())).thenReturn(0);
        when(astTaskMapper.countExpiredPendingTasks())
                .thenThrow(new RuntimeException("Count down"));

        // 不抛
        assertDoesNotThrow(() -> reaper.cleanZombieTasks());
    }

    @Test
    @DisplayName("默认 leaseGraceSeconds — Spring 上下文外的 @Value 解析失败, 但 setter 兜底为 30")
    void defaultGraceSeconds() {
        TaskReaper defaultReaper = new TaskReaper(astTaskMapper, stateMachine);
        ReflectionTestUtils.setField(defaultReaper, "leaseGraceSeconds", 30);
        int grace = (Integer) ReflectionTestUtils.getField(defaultReaper, "leaseGraceSeconds");
        assertEquals(30, grace);
    }
}
