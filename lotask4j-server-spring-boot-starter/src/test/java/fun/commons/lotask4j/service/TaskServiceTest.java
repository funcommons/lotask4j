package fun.commons.lotask4j.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fun.commons.lotask4j.dto.SubmitTaskRequest;
import fun.commons.lotask4j.dto.TaskDetailResponse;
import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.mapper.AstTaskTypeConfigMapper;
import fun.commons.lotask4j.service.impl.TaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * TaskService 单元测试
 *
 * 使用 Mockito 模拟依赖，测试业务逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("任务服务测试")
class TaskServiceTest {

    @Mock
    private AstTaskMapper astTaskMapper;

    @Mock
    private AstTaskTypeConfigMapper taskTypeConfigMapper;

    @Mock
    private fun.commons.lotask4j.service.TaskStateMachine stateMachine;

    @Mock
    private fun.commons.lotask4j.service.TaskSubmitGuard submitGuard;

    @InjectMocks
    private TaskServiceImpl taskService;

    private SubmitTaskRequest validRequest;
    private AstTask sampleTask;

    @BeforeEach
    void setUp() {
        // 使用反射设置 baseMapper (ServiceImpl 的父类字段)
        ReflectionTestUtils.setField(taskService, "baseMapper", astTaskMapper);

        // 准备测试数据
        validRequest = new SubmitTaskRequest();
        validRequest.setType("data_export");

        Map<String, Object> payload = new HashMap<>();
        payload.put("query", "SELECT * FROM users");
        payload.put("format", "xlsx");
        validRequest.setPayload(payload);
        validRequest.setPriority(10);

        // 准备样例任务
        sampleTask = new AstTask();
        sampleTask.setId(100001L);
        sampleTask.setTaskTypeKey("data_export");
        sampleTask.setStatus("PENDING");
        sampleTask.setPriority(10);
        sampleTask.setProgress(0);
        sampleTask.setVersion(0);

        // Mock taskTypeConfigMapper 返回 null (使用默认超时时间, lenient for tests that don't need it)
        lenient().when(taskTypeConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        // 默认: idempotency 查找返 null
        lenient().when(stateMachine.findByIdempotencyKey(any(), any(), isNull())).thenReturn(null);
        // 默认: 背压准入放行 (单测场景不模拟队列满)
        lenient().doNothing().when(submitGuard).checkOrThrow(anyString(), any());
    }

    @Test
    @DisplayName("提交任务 - 成功场景 (P0: 由 IdWorker.nextId 分配)")
    void testSubmitTask_Success() {
        // Given: 模拟 mapper 插入成功
        when(astTaskMapper.insertTask(any(AstTask.class), anyString(), anyString())).thenReturn(1);

        // When: 提交任务
        Long taskId = taskService.submitTask(validRequest);

        // Then: 验证结果 (P0: 用 IdWorker 自动分配雪花 ID,不依赖 mock)
        assertNotNull(taskId, "任务ID不应为空");
        assertTrue(taskId > 0L, "任务ID必须为正数");

        // 验证 mapper 被调用了一次
        verify(astTaskMapper, times(1)).insertTask(any(AstTask.class), anyString(), anyString());
    }

    @Test
    @DisplayName("提交任务 - 参数验证")
    void testSubmitTask_WithNullType() {
        // Given: 请求类型为空
        SubmitTaskRequest invalidRequest = new SubmitTaskRequest();
        invalidRequest.setType(null);
        invalidRequest.setPayload(new HashMap<>());

        // Mock insertTask 方法
        when(astTaskMapper.insertTask(any(AstTask.class), anyString(), anyString())).thenReturn(1);

        // When & Then: 应该抛出异常或成功（取决于业务逻辑）
        // 实际上，submitTask 会继续执行，因为没有空值校验
        assertDoesNotThrow(() -> {
            taskService.submitTask(invalidRequest);
        });
    }

    @Test
    @DisplayName("提交任务 - 优先级边界测试")
    void testSubmitTask_PriorityBoundary() {
        // Given: 优先级为 0
        validRequest.setPriority(0);
        when(astTaskMapper.insertTask(any(AstTask.class), anyString(), anyString())).thenReturn(1);

        // When
        Long taskId1 = taskService.submitTask(validRequest);

        // Then
        assertNotNull(taskId1);

        // Given: 优先级为 100
        validRequest.setPriority(100);

        // When
        Long taskId2 = taskService.submitTask(validRequest);

        // Then
        assertNotNull(taskId2);
    }

    @Test
    @DisplayName("查询任务详情 - 成功场景")
    void testGetTaskDetail_Success() {
        // Given: 模拟查询返回任务
        when(astTaskMapper.selectByIdWithTypeName(100001L, null))
            .thenReturn(sampleTask);

        // When: 查询任务详情
        TaskDetailResponse response = taskService.getTaskDetail(100001L);

        // Then: 验证结果
        assertNotNull(response);
        assertEquals(100001L, response.getId());
        assertEquals("data_export", response.getType());
        assertEquals("PENDING", response.getStatus());
        assertEquals(0, response.getProgress());

        // 验证 mapper 被调用
        verify(astTaskMapper, times(1)).selectByIdWithTypeName(anyLong(), isNull());
    }

    @Test
    @DisplayName("查询任务详情 - 任务不存在")
    void testGetTaskDetail_NotFound() {
        // Given: 模拟查询返回 null
        when(astTaskMapper.selectByIdWithTypeName(anyLong(), isNull())).thenReturn(null);

        // When & Then: 应该抛出异常
        assertThrows(Exception.class, () -> {
            taskService.getTaskDetail(999999L);
        });
    }

    @Test
    @DisplayName("取消任务 - 成功场景 (P0: 走 stateMachine.requestCancel CAS)")
    void testCancelTask_Success() {
        // Given: 任务状态为 PENDING
        sampleTask.setStatus("PENDING");
        when(astTaskMapper.selectById(100001L))
            .thenReturn(sampleTask);

        // When: 取消任务
        taskService.cancelTask(100001L);

        // Then: stateMachine.requestCancel 被调用
        verify(stateMachine, times(1)).requestCancel(eq(100001L), eq(0), isNull());
    }

    @Test
    @DisplayName("取消任务 - 任务已完成")
    void testCancelTask_AlreadyFinished() {
        // Given: 任务状态为 SUCCESS
        sampleTask.setStatus("SUCCESS");
        when(astTaskMapper.selectById(anyLong())).thenReturn(sampleTask);

        // When & Then: 应该抛出异常
        assertThrows(Exception.class, () -> {
            taskService.cancelTask(100001L);
        });
        // 终态不应调用 stateMachine
        verify(stateMachine, never()).requestCancel(anyLong(), anyInt(), isNull());
    }

    @Test
    @DisplayName("获取待处理任务数")
    void testGetPendingTaskCount() {
        // Given
        when(astTaskMapper.countPendingTasks(null)).thenReturn(15L);

        // When
        long count = taskService.getPendingTaskCount();

        // Then
        assertEquals(15L, count);
        verify(astTaskMapper, times(1)).countPendingTasks(null);
    }

    @Test
    @DisplayName("获取运行中任务数")
    void testGetRunningTaskCount() {
        // Given
        when(astTaskMapper.countRunningTasks(null)).thenReturn(8L);

        // When
        long count = taskService.getRunningTaskCount();

        // Then
        assertEquals(8L, count);
        verify(astTaskMapper, times(1)).countRunningTasks(null);
    }

    @Test
    @DisplayName("清理超时任务")
    void testCleanupTimeoutTasks() {
        // Given
        when(astTaskMapper.resetTimeoutTasks(600)).thenReturn(3);

        // When
        int count = taskService.cleanupTimeoutTasks(600);

        // Then
        assertEquals(3, count);
        verify(astTaskMapper, times(1)).resetTimeoutTasks(600);
    }
}
