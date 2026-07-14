package fun.commons.lotask4j.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fun.commons.lotask4j.dto.SubmitTaskRequest;
import fun.commons.lotask4j.dto.TaskDetailResponse;
import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.mapper.AstTaskTypeConfigMapper;
import fun.commons.lotask4j.service.impl.TaskServiceImpl;
import fun.commons.framework4j.web.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * TaskService 高级测试 - 边界条件、异常场景、并发测试
 *
 * 提高测试覆盖率到 90%+
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("任务服务高级测试")
class TaskServiceAdvancedTest {

    @Mock
    private AstTaskMapper astTaskMapper;

    @Mock
    private AstTaskTypeConfigMapper taskTypeConfigMapper;

    @Mock
    private fun.commons.framework4j.id.generator.SnowflakeDistributor snowflakeDistributor;

    @InjectMocks
    private TaskServiceImpl taskService;

    private SubmitTaskRequest validRequest;

    @BeforeEach
    void setUp() {
        // 使用反射设置 baseMapper
        ReflectionTestUtils.setField(taskService, "baseMapper", astTaskMapper);

        validRequest = new SubmitTaskRequest();
        validRequest.setType("data_export");
        validRequest.setPayload(new HashMap<>());
        validRequest.setPriority(10);

        lenient().when(snowflakeDistributor.nextId()).thenReturn(100001L);
        lenient().when(taskTypeConfigMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
    }

    // ==================== 边界条件测试 ====================

    @Test
    @DisplayName("提交任务 - 优先级最小值 (0)")
    void testSubmitTask_PriorityMinBoundary() {
        // Given
        validRequest.setPriority(0);
        when(astTaskMapper.insertTask(any(AstTask.class), anyString(), anyString())).thenReturn(1);

        // When
        Long taskId = taskService.submitTask(validRequest);

        // Then
        assertNotNull(taskId);
        verify(astTaskMapper, times(1)).insertTask(argThat((AstTask task) ->
            task.getPriority() == 0
        ), anyString(), anyString());
    }

    @Test
    @DisplayName("提交任务 - 优先级最大值 (100)")
    void testSubmitTask_PriorityMaxBoundary() {
        // Given
        validRequest.setPriority(100);
        when(astTaskMapper.insertTask(any(AstTask.class), anyString(), anyString())).thenReturn(1);

        // When
        Long taskId = taskService.submitTask(validRequest);

        // Then
        assertNotNull(taskId);
        verify(astTaskMapper, times(1)).insertTask(argThat((AstTask task) ->
            task.getPriority() == 100
        ), anyString(), anyString());
    }

    @Test
    @DisplayName("提交任务 - 优先级为 null 使用默认值")
    void testSubmitTask_PriorityNull() {
        // Given
        validRequest.setPriority(null);
        when(astTaskMapper.insertTask(any(AstTask.class), anyString(), anyString())).thenReturn(1);

        // When
        Long taskId = taskService.submitTask(validRequest);

        // Then
        assertNotNull(taskId);
        verify(astTaskMapper, times(1)).insertTask(argThat((AstTask task) ->
            task.getPriority() == 0  // 默认值
        ), anyString(), anyString());
    }

    @Test
    @DisplayName("提交任务 - Payload 为空 Map")
    void testSubmitTask_EmptyPayload() {
        // Given
        validRequest.setPayload(new HashMap<>());
        when(astTaskMapper.insertTask(any(AstTask.class), anyString(), anyString())).thenReturn(1);

        // When
        Long taskId = taskService.submitTask(validRequest);

        // Then
        assertNotNull(taskId);
        verify(astTaskMapper).insertTask(any(AstTask.class), anyString(), anyString());
    }

    @Test
    @DisplayName("提交任务 - Payload 包含大量数据")
    void testSubmitTask_LargePayload() {
        // Given
        Map<String, Object> largePayload = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            largePayload.put("key" + i, "value" + i);
        }
        validRequest.setPayload(largePayload);
        when(astTaskMapper.insertTask(any(AstTask.class), anyString(), anyString())).thenReturn(1);

        // When
        Long taskId = taskService.submitTask(validRequest);

        // Then
        assertNotNull(taskId);
        verify(astTaskMapper).insertTask(any(AstTask.class), anyString(), anyString());
    }

    @Test
    @DisplayName("提交任务 - 任务类型包含特殊字符")
    void testSubmitTask_SpecialCharactersInType() {
        // Given
        validRequest.setType("data_export_测试_2024");
        when(astTaskMapper.insertTask(any(AstTask.class), anyString(), anyString())).thenReturn(1);

        // When
        Long taskId = taskService.submitTask(validRequest);

        // Then
        assertNotNull(taskId);
    }

    @Test
    @DisplayName("提交任务 - 带 Callback URL")
    void testSubmitTask_WithCallbackUrl() {
        // Given
        validRequest.setCallbackUrl("https://example.com/callback");
        when(astTaskMapper.insertTask(any(AstTask.class), anyString(), anyString())).thenReturn(1);

        // When
        Long taskId = taskService.submitTask(validRequest);

        // Then
        assertNotNull(taskId);
        verify(astTaskMapper).insertTask(argThat((AstTask task) ->
            "https://example.com/callback".equals(task.getCallbackUrl())
        ), anyString(), anyString());
    }

    // ==================== 异常场景测试 ====================

    @Test
    @DisplayName("提交任务 - Type 为 null 也应该成功")
    void testSubmitTask_NullType() {
        // Given
        validRequest.setType(null);

        // When & Then - 业务代码允许 type 为 null，应该成功提交
        Long taskId = taskService.submitTask(validRequest);
        assertNotNull(taskId);
        assertTrue(taskId > 0);
    }

    @Test
    @DisplayName("提交任务 - Type 为空字符串也应该成功")
    void testSubmitTask_EmptyType() {
        // Given
        validRequest.setType("");

        // When & Then - 业务代码允许空字符串 type，应该成功提交
        Long taskId = taskService.submitTask(validRequest);
        assertNotNull(taskId);
        assertTrue(taskId > 0);
    }

    @Test
    @DisplayName("提交任务 - 数据库插入失败")
    void testSubmitTask_DatabaseInsertFailed() {
        // Given
        when(astTaskMapper.insertTask(any(AstTask.class), anyString(), anyString())).thenThrow(new RuntimeException("Database error"));

        // When & Then
        ApiException exception = assertThrows(ApiException.class, () -> {
            taskService.submitTask(validRequest);
        });
        assertTrue(exception.getMessage().contains("任务提交失败"));
    }

    @Test
    @DisplayName("查询任务 - TaskId 为 null")
    void testGetTaskDetail_NullTaskId() {
        // When & Then
        assertThrows(Exception.class, () -> {
            taskService.getTaskDetail(null);
        });
    }

    @Test
    @DisplayName("查询任务 - TaskId 为 0")
    void testGetTaskDetail_ZeroTaskId() {
        // Given
        when(astTaskMapper.selectByIdWithTypeName(0L)).thenReturn(null);

        // When & Then
        assertThrows(ApiException.class, () -> {
            taskService.getTaskDetail(0L);
        });
    }

    @Test
    @DisplayName("查询任务 - TaskId 不存在")
    void testGetTaskDetail_TaskNotExists() {
        // Given
        when(astTaskMapper.selectByIdWithTypeName(999999L)).thenReturn(null);

        // When & Then
        ApiException exception = assertThrows(ApiException.class, () -> {
            taskService.getTaskDetail(999999L);
        });
        assertEquals(20100, exception.getCode());
    }

    @Test
    @DisplayName("取消任务 - 任务状态为 SUCCESS 不能取消")
    void testCancelTask_StatusSuccess() {
        // Given
        AstTask task = new AstTask();
        task.setId(100001L);
        task.setStatus("SUCCESS");
        when(astTaskMapper.selectById(100001L)).thenReturn(task);

        // When & Then
        ApiException exception = assertThrows(ApiException.class, () -> {
            taskService.cancelTask(100001L);
        });
        assertEquals(20401, exception.getCode());
        assertTrue(exception.getMessage().contains("不允许取消"));
    }

    @Test
    @DisplayName("取消任务 - 任务状态为 FAILED 不能取消")
    void testCancelTask_StatusFailed() {
        // Given
        AstTask task = new AstTask();
        task.setId(100002L);
        task.setStatus("FAILED");
        when(astTaskMapper.selectById(100002L)).thenReturn(task);

        // When & Then
        assertThrows(ApiException.class, () -> {
            taskService.cancelTask(100002L);
        });
    }

    @Test
    @DisplayName("取消任务 - 任务状态为 CANCELLED 不能再次取消")
    void testCancelTask_StatusCancelled() {
        // Given
        AstTask task = new AstTask();
        task.setId(100003L);
        task.setStatus("CANCELLED");
        when(astTaskMapper.selectById(100003L)).thenReturn(task);

        // When & Then
        assertThrows(ApiException.class, () -> {
            taskService.cancelTask(100003L);
        });
    }

    @Test
    @DisplayName("取消任务 - 数据库更新失败")
    void testCancelTask_UpdateFailed() {
        // Given
        AstTask task = new AstTask();
        task.setId(100004L);
        task.setStatus("PENDING");
        when(astTaskMapper.selectById(100004L)).thenReturn(task);
        when(astTaskMapper.updateById(any(AstTask.class))).thenReturn(0);  // 更新失败

        // When
        boolean result = taskService.cancelTask(100004L);

        // Then
        assertFalse(result);
    }

    // ==================== 并发测试 ====================

    @Test
    @DisplayName("并发提交任务 - 10个线程同时提交")
    void testSubmitTask_Concurrent() throws InterruptedException {
        // Given
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        when(astTaskMapper.insertTask(any(AstTask.class), anyString(), anyString())).thenReturn(1);
        when(snowflakeDistributor.nextId()).thenAnswer(invocation ->
            System.currentTimeMillis() + successCount.incrementAndGet()
        );

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    SubmitTaskRequest request = new SubmitTaskRequest();
                    request.setType("data_export");
                    request.setPayload(new HashMap<>());
                    request.setPriority(10);

                    Long taskId = taskService.submitTask(request);
                    assertNotNull(taskId);
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then
        latch.await();
        executor.shutdown();

        verify(astTaskMapper, times(threadCount)).insertTask(any(AstTask.class), anyString(), anyString());
    }

    @Test
    @DisplayName("并发查询任务 - 10个线程同时查询同一任务")
    void testGetTaskDetail_Concurrent() throws InterruptedException {
        // Given
        AstTask task = new AstTask();
        task.setId(100005L);
        task.setTaskTypeKey("data_export");
        task.setStatus("RUNNING");
        task.setProgress(50);

        when(astTaskMapper.selectByIdWithTypeName(100005L)).thenReturn(task);

        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // When
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    TaskDetailResponse response = taskService.getTaskDetail(100005L);
                    assertNotNull(response);
                    assertEquals(100005L, response.getId());
                } finally {
                    latch.countDown();
                }
            });
        }

        // Then
        latch.await();
        executor.shutdown();

        verify(astTaskMapper, atLeast(threadCount)).selectByIdWithTypeName(100005L);
    }

    // ==================== 参数化测试 ====================

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "RUNNING"})
    @DisplayName("取消任务 - 可取消的状态")
    void testCancelTask_ValidStatuses(String status) {
        // Given
        AstTask task = new AstTask();
        task.setId(100006L);
        task.setStatus(status);
        when(astTaskMapper.selectById(100006L)).thenReturn(task);
        when(astTaskMapper.updateById(any(AstTask.class))).thenReturn(1);

        // When
        boolean result = taskService.cancelTask(100006L);

        // Then
        assertTrue(result);
        verify(astTaskMapper).updateById(argThat((AstTask t) ->
            "CANCELLING".equals(t.getStatus())
        ));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUCCESS", "FAILED", "CANCELLED"})
    @DisplayName("取消任务 - 不可取消的状态")
    void testCancelTask_InvalidStatuses(String status) {
        // Given
        AstTask task = new AstTask();
        task.setId(100007L);
        task.setStatus(status);
        when(astTaskMapper.selectById(100007L)).thenReturn(task);

        // When & Then
        assertThrows(ApiException.class, () -> {
            taskService.cancelTask(100007L);
        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 10, 50, 99, 100})
    @DisplayName("提交任务 - 各种有效优先级")
    void testSubmitTask_ValidPriorities(int priority) {
        // Given
        validRequest.setPriority(priority);
        when(astTaskMapper.insertTask(any(AstTask.class), anyString(), anyString())).thenReturn(1);

        // When
        Long taskId = taskService.submitTask(validRequest);

        // Then
        assertNotNull(taskId);
        verify(astTaskMapper).insertTask(argThat((AstTask task) ->
            task.getPriority() == priority
        ), anyString(), anyString());
    }

    // ==================== 性能测试 ====================

    @Test
    @DisplayName("性能测试 - 提交1000个任务")
    void testSubmitTask_Performance() {
        // Given
        when(astTaskMapper.insertTask(any(AstTask.class), anyString(), anyString())).thenReturn(1);
        when(snowflakeDistributor.nextId()).thenAnswer(invocation ->
            System.currentTimeMillis()
        );

        long startTime = System.currentTimeMillis();

        // When
        for (int i = 0; i < 1000; i++) {
            SubmitTaskRequest request = new SubmitTaskRequest();
            request.setType("test_type");
            request.setPayload(new HashMap<>());
            request.setPriority(10);

            taskService.submitTask(request);
        }

        long endTime = System.currentTimeMillis();

        // Then
        long duration = endTime - startTime;
        System.out.println("提交1000个任务耗时: " + duration + "ms");

        // 性能要求：1000个任务应该在5秒内完成
        assertTrue(duration < 5000, "性能测试失败：耗时 " + duration + "ms 超过5000ms");

        verify(astTaskMapper, times(1000)).insertTask(any(AstTask.class), anyString(), anyString());
    }

    @Test
    @DisplayName("性能测试 - 统计操作")
    void testCountOperations_Performance() {
        // Given
        when(astTaskMapper.countPendingTasks()).thenReturn(1000L);
        when(astTaskMapper.countRunningTasks()).thenReturn(500L);

        long startTime = System.currentTimeMillis();

        // When - 执行1000次统计
        for (int i = 0; i < 1000; i++) {
            taskService.getPendingTaskCount();
            taskService.getRunningTaskCount();
        }

        long endTime = System.currentTimeMillis();

        // Then
        long duration = endTime - startTime;
        System.out.println("执行2000次统计操作耗时: " + duration + "ms");

        assertTrue(duration < 2000, "统计操作性能测试失败");
    }
}
