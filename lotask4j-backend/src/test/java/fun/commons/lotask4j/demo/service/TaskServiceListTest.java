package fun.commons.lotask4j.service;

import fun.commons.lotask4j.dto.PageResponse;
import fun.commons.lotask4j.dto.TaskDetailResponse;
import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.entity.AstTaskTypeConfig;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.mapper.AstTaskTypeConfigMapper;
import fun.commons.lotask4j.service.impl.TaskServiceImpl;
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
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TaskServiceImpl 列表查询 / 详情映射 / 取消成功路径 / Reaper / 私有工具方法测试。
 * <p>
 * 补足 TaskServiceAdvancedTest 未覆盖的代码路径：
 * <ul>
 *   <li>getTaskDetail 完整字段映射 + durationSeconds 计算</li>
 *   <li>cancelTask PENDING/RUNNING 成功路径</li>
 *   <li>getTaskList 默认分页、归档过滤、字段映射</li>
 *   <li>cleanupTimeoutTasks count&gt;0 与 count=0 两条日志分支</li>
 *   <li>getPendingTaskCount / getRunningTaskCount</li>
 *   <li>getTaskTypeName 私有方法（空、配置存在、配置缺失、抛异常）</li>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("任务服务 - 列表/详情/取消/Reaper 测试")
class TaskServiceListTest {

    @Mock
    private AstTaskMapper astTaskMapper;

    @Mock
    private AstTaskTypeConfigMapper taskTypeConfigMapper;

    @InjectMocks
    private TaskServiceImpl taskService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(taskService, "baseMapper", astTaskMapper);
    }

    // ==================== getTaskDetail ====================

    @Nested
    @DisplayName("getTaskDetail - 字段映射")
    class GetTaskDetailMapping {

        @Test
        @DisplayName("完整字段映射，包含 durationSeconds 计算")
        void fullFieldMapping_WithDuration() {
            AstTask task = buildFinishedTask();

            when(astTaskMapper.selectByIdWithTypeName(123L)).thenReturn(task);

            TaskDetailResponse resp = taskService.getTaskDetail(123L);

            assertEquals(123L, resp.getId());
            assertEquals("video_transcode", resp.getType());
            assertEquals("视频转码", resp.getTypeName());
            assertEquals("SUCCESS", resp.getStatus());
            assertEquals(100, resp.getProgress());
            assertEquals("upload", resp.getCurrentStep());
            assertEquals(80, resp.getPriority());
            assertEquals(600, resp.getTimeoutSeconds());
            assertNotNull(resp.getDurationSeconds());
            // startedAt=10:00:00, finishedAt=10:00:30 → 30s
            assertEquals(30L, resp.getDurationSeconds());
        }

        @Test
        @DisplayName("任务未完成时 durationSeconds 为 null")
        void runningTask_DurationNull() {
            AstTask task = buildFinishedTask();
            task.setStartedAt(OffsetDateTime.of(2026, 6, 24, 10, 0, 0, 0, ZoneOffset.UTC));
            task.setFinishedAt(null); // 未完成

            when(astTaskMapper.selectByIdWithTypeName(99L)).thenReturn(task);

            TaskDetailResponse resp = taskService.getTaskDetail(99L);
            assertNull(resp.getDurationSeconds());
        }

        @Test
        @DisplayName("任务不存在抛 TASK_NOT_FOUND")
        void notFound_Throws() {
            when(astTaskMapper.selectByIdWithTypeName(anyLong())).thenReturn(null);

            ApiException ex = assertThrows(ApiException.class,
                    () -> taskService.getTaskDetail(-1L));
            assertTrue(ex.getMessage().contains("不存在") || ex.getMessage().contains("not")
                    || ex.getMessage().contains("找不到"));
        }
    }

    // ==================== cancelTask 成功路径 ====================

    @Nested
    @DisplayName("cancelTask - 成功路径")
    class CancelTaskSuccess {

        @Test
        @DisplayName("PENDING 任务可以取消")
        void pendingTask_CanCancel() {
            AstTask task = new AstTask();
            task.setId(1L);
            task.setStatus("PENDING");

            when(astTaskMapper.selectById(1L)).thenReturn(task);
            when(astTaskMapper.updateById(any(AstTask.class))).thenReturn(1);

            boolean result = taskService.cancelTask(1L);

            assertTrue(result);
            assertEquals("CANCELLING", task.getStatus());
            assertNotNull(task.getUpdatedAt());
        }

        @Test
        @DisplayName("RUNNING 任务可以取消")
        void runningTask_CanCancel() {
            AstTask task = new AstTask();
            task.setId(2L);
            task.setStatus("RUNNING");

            when(astTaskMapper.selectById(2L)).thenReturn(task);
            when(astTaskMapper.updateById(any(AstTask.class))).thenReturn(1);

            assertTrue(taskService.cancelTask(2L));
            assertEquals("CANCELLING", task.getStatus());
        }

        @Test
        @DisplayName("CANCELLING 状态不允许再次取消")
        void cancellingTask_NotAllowed() {
            AstTask task = new AstTask();
            task.setId(3L);
            task.setStatus("CANCELLING");

            when(astTaskMapper.selectById(3L)).thenReturn(task);

            ApiException ex = assertThrows(ApiException.class,
                    () -> taskService.cancelTask(3L));
            assertEquals(20401, ex.getCode());
        }

        @Test
        @DisplayName("任务不存在抛 TASK_NOT_FOUND")
        void notFound_Throws() {
            when(astTaskMapper.selectById(anyLong())).thenReturn(null);

            ApiException ex = assertThrows(ApiException.class,
                    () -> taskService.cancelTask(404L));
            // TASK_NOT_FOUND code = 20100
            assertEquals(20100, ex.getCode());
        }
    }

    // ==================== cleanupTimeoutTasks / 计数 ====================

    @Nested
    @DisplayName("Reaper 与计数接口")
    class ReaperAndCounts {

        @Test
        @DisplayName("cleanupTimeoutTasks 有任务被清理时返回 count 并记日志")
        void cleanup_HasExpired() {
            when(astTaskMapper.resetTimeoutTasks(600)).thenReturn(7);

            int count = taskService.cleanupTimeoutTasks(600);

            assertEquals(7, count);
        }

        @Test
        @DisplayName("cleanupTimeoutTasks count=0 不记日志分支")
        void cleanup_NoExpired() {
            when(astTaskMapper.resetTimeoutTasks(60)).thenReturn(0);

            assertEquals(0, taskService.cleanupTimeoutTasks(60));
        }

        @Test
        @DisplayName("getPendingTaskCount 委托给 mapper")
        void pendingCount_Delegates() {
            when(astTaskMapper.countPendingTasks()).thenReturn(42L);
            assertEquals(42L, taskService.getPendingTaskCount());
        }

        @Test
        @DisplayName("getRunningTaskCount 委托给 mapper")
        void runningCount_Delegates() {
            when(astTaskMapper.countRunningTasks()).thenReturn(5L);
            assertEquals(5L, taskService.getRunningTaskCount());
        }
    }

    // ==================== getTaskList ====================

    @Nested
    @DisplayName("getTaskList - 分页与过滤")
    class GetTaskList {

        @Test
        @DisplayName("null 分页参数时使用默认值 1/20")
        void defaultPagination_WhenNull() {
            when(astTaskMapper.countTasks(any(), any(), any(), any(), any(), any())).thenReturn(0L);
            when(astTaskMapper.selectPageWithTypeName(anyLong(), anyLong(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            PageResponse<TaskDetailResponse> page = taskService.getTaskList(
                    null, null, null, null, null, null, null, null);

            assertEquals(1, page.getPage());
            assertEquals(20, page.getPageSize());
            assertEquals(0L, page.getTotal());
            // offset = (1-1) * 20 = 0
            verify(astTaskMapper).selectPageWithTypeName(eq(0L), eq(20L), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("非法 page/pageSize（<=0）使用默认值")
        void invalidPagination_FallsBackToDefault() {
            when(astTaskMapper.countTasks(any(), any(), any(), any(), any(), any())).thenReturn(0L);
            when(astTaskMapper.selectPageWithTypeName(anyLong(), anyLong(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            taskService.getTaskList(null, null, null, null, null, null, 0, -5);

            // 默认 1/20
            verify(astTaskMapper).selectPageWithTypeName(eq(0L), eq(20L), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("第 3 页 pageSize=10 时 offset=20")
        void offsetCalculation() {
            when(astTaskMapper.countTasks(any(), any(), any(), any(), any(), any())).thenReturn(100L);
            when(astTaskMapper.selectPageWithTypeName(anyLong(), anyLong(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(Collections.emptyList());

            taskService.getTaskList(null, null, null, null, null, null, 3, 10);

            // offset = (3-1) * 10 = 20
            verify(astTaskMapper).selectPageWithTypeName(eq(20L), eq(10L), any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("归档任务列表 isArchived=true 透传给 mapper")
        void archivedFilter_Passed() {
            when(astTaskMapper.countTasks(any(), any(), any(), eq(true), any(), any())).thenReturn(1L);

            AstTask archived = buildFinishedTask();
            archived.setId(77L);
            when(astTaskMapper.selectPageWithTypeName(anyLong(), anyLong(), any(), any(), any(), eq(true), any(), any()))
                    .thenReturn(Arrays.asList(archived));

            PageResponse<TaskDetailResponse> page = taskService.getTaskList(
                    null, null, null, Boolean.TRUE, null, null, 1, 10);

            assertEquals(1, page.getList().size());
            assertEquals(77L, page.getList().get(0).getId());
            assertEquals(1, page.getTotalPages());
        }

        @Test
        @DisplayName("列表结果按 AstTask 字段映射为 TaskDetailResponse（含 durationSeconds）")
        void listResult_MappedToDetail() {
            AstTask task = buildFinishedTask();
            when(astTaskMapper.countTasks(any(), any(), any(), any(), any(), any())).thenReturn(1L);
            when(astTaskMapper.selectPageWithTypeName(anyLong(), anyLong(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(task));

            PageResponse<TaskDetailResponse> page = taskService.getTaskList(
                    null, null, null, null, null, null, 1, 10);

            assertEquals(1, page.getList().size());
            TaskDetailResponse r = page.getList().get(0);
            assertEquals("SUCCESS", r.getStatus());
            assertEquals(30L, r.getDurationSeconds());
            assertEquals("视频转码", r.getTypeName());
        }
    }

    // ==================== getTaskTypeName 私有方法 ====================

    @Nested
    @DisplayName("getTaskTypeName - 私有方法")
    class GetTaskTypeName {

        private String invoke(String typeKey) throws Exception {
            java.lang.reflect.Method m = TaskServiceImpl.class.getDeclaredMethod("getTaskTypeName", String.class);
            m.setAccessible(true);
            return (String) m.invoke(taskService, typeKey);
        }

        @Test
        @DisplayName("typeKey 为 null 返回空串")
        void nullKey_ReturnsEmpty() throws Exception {
            assertEquals("", invoke(null));
        }

        @Test
        @DisplayName("typeKey 为空字符串返回空串")
        void emptyKey_ReturnsEmpty() throws Exception {
            assertEquals("", invoke(""));
        }

        @Test
        @DisplayName("配置存在时返回配置名称")
        void configExists_ReturnsName() throws Exception {
            AstTaskTypeConfig cfg = new AstTaskTypeConfig();
            cfg.setName("视频转码");
            when(taskTypeConfigMapper.selectByTypeKey("video_transcode")).thenReturn(cfg);

            assertEquals("视频转码", invoke("video_transcode"));
        }

        @Test
        @DisplayName("配置不存在时返回 typeKey 本身")
        void configMissing_ReturnsTypeKey() throws Exception {
            when(taskTypeConfigMapper.selectByTypeKey("unknown")).thenReturn(null);

            assertEquals("unknown", invoke("unknown"));
        }

        @Test
        @DisplayName("mapper 抛异常时降级返回 typeKey")
        void mapperThrows_ReturnsTypeKey() throws Exception {
            when(taskTypeConfigMapper.selectByTypeKey("boom"))
                    .thenThrow(new RuntimeException("DB down"));

            assertEquals("boom", invoke("boom"));
        }
    }

    // ==================== fixtures ====================

    private AstTask buildFinishedTask() {
        AstTask task = new AstTask();
        task.setId(123L);
        task.setTaskTypeKey("video_transcode");
        task.setStatus("SUCCESS");
        task.setProgress(100);
        task.setCurrentStepKey("upload");
        task.setPriority(80);
        task.setTimeoutSeconds(600);
        task.setTypeName("视频转码");
        task.setCreatedAt(OffsetDateTime.of(2026, 6, 24, 9, 59, 50, 0, ZoneOffset.UTC));
        task.setStartedAt(OffsetDateTime.of(2026, 6, 24, 10, 0, 0, 0, ZoneOffset.UTC));
        task.setFinishedAt(OffsetDateTime.of(2026, 6, 24, 10, 0, 30, 0, ZoneOffset.UTC));
        task.setUpdatedAt(task.getFinishedAt());
        task.setExpiredAt(OffsetDateTime.of(2026, 6, 24, 11, 0, 0, 0, ZoneOffset.UTC));
        return task;
    }
}
