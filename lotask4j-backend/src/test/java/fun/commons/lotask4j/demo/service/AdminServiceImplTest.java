package fun.commons.lotask4j.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fun.commons.lotask4j.dto.*;
import fun.commons.lotask4j.dto.*;
import fun.commons.lotask4j.enums.BusinessCode;
import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.entity.AstTaskTypeConfig;
import fun.commons.lotask4j.entity.AstWorkerNode;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.mapper.AstTaskTypeConfigMapper;
import fun.commons.lotask4j.mapper.AstWorkerNodeMapper;
import fun.commons.lotask4j.service.impl.AdminServiceImpl;
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
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminServiceImpl 测试")
class AdminServiceImplTest {

    @Mock
    private AstWorkerNodeMapper workerNodeMapper;

    @Mock
    private AstTaskTypeConfigMapper taskTypeConfigMapper;

    @Mock
    private AstTaskMapper taskMapper;

    @Mock
    private TaskService taskService;

    @Mock
    private Environment environment;

    @InjectMocks
    private AdminServiceImpl adminService;

    // ==================== getOnlineWorkers ====================

    @Nested
    @DisplayName("getOnlineWorkers")
    class GetOnlineWorkers {

        @Test
        @DisplayName("返回 WorkerNodeResponse 列表，workerKey 优先 workerId")
        void mapsWorkerId_WhenPresent() {
            AstWorkerNode w = new AstWorkerNode();
            w.setId(1L);
            w.setWorkerId("worker-abc");
            w.setWorkerIp("10.0.0.1");
            w.setHostname("host-1");
            w.setStatus("ONLINE");
            w.setLastHeartbeatAt(OffsetDateTime.now());

            when(workerNodeMapper.selectOnlineWorkers()).thenReturn(List.of(w));

            List<WorkerNodeResponse> result = adminService.getOnlineWorkers();

            assertEquals(1, result.size());
            assertEquals("worker-abc", result.get(0).getWorkerKey());
            assertEquals("10.0.0.1", result.get(0).getWorkerIp());
            assertEquals("host-1", result.get(0).getHostname());
        }

        @Test
        @DisplayName("workerId 为 null 时降级使用 id")
        void fallsBackToId_WhenWorkerIdNull() {
            AstWorkerNode w = new AstWorkerNode();
            w.setId(42L);
            w.setWorkerId(null);

            when(workerNodeMapper.selectOnlineWorkers()).thenReturn(List.of(w));

            List<WorkerNodeResponse> result = adminService.getOnlineWorkers();
            assertEquals("42", result.get(0).getWorkerKey());
        }

        @Test
        @DisplayName("空列表透传")
        void emptyList_PassesThrough() {
            when(workerNodeMapper.selectOnlineWorkers()).thenReturn(Collections.emptyList());
            assertTrue(adminService.getOnlineWorkers().isEmpty());
        }
    }

    // ==================== saveTaskTypeConfig ====================

    @Nested
    @DisplayName("saveTaskTypeConfig")
    class SaveTaskTypeConfig {

        @Test
        @DisplayName("已存在配置走 update 路径")
        void existingConfig_GoesUpdate() {
            TaskTypeConfigRequest req = buildRequest("video_transcode", true);

            AstTaskTypeConfig existing = new AstTaskTypeConfig();
            existing.setTypeKey("video_transcode");
            when(taskTypeConfigMapper.selectByTypeKey("video_transcode")).thenReturn(existing);

            adminService.saveTaskTypeConfig(req);

            verify(taskTypeConfigMapper).updateById(existing);
            verify(taskTypeConfigMapper, never()).insert(any(AstTaskTypeConfig.class));
            assertEquals("视频转码", existing.getName());
            assertEquals(1, existing.getIsEnabled());
        }

        @Test
        @DisplayName("已存在配置更新时 isEnabled=false → 0")
        void existingConfig_UpdateDisabled() {
            TaskTypeConfigRequest req = buildRequest("video_transcode", false);

            AstTaskTypeConfig existing = new AstTaskTypeConfig();
            existing.setTypeKey("video_transcode");
            when(taskTypeConfigMapper.selectByTypeKey("video_transcode")).thenReturn(existing);

            adminService.saveTaskTypeConfig(req);

            verify(taskTypeConfigMapper).updateById(existing);
            assertEquals(0, existing.getIsEnabled());
        }

        @Test
        @DisplayName("新配置走 insert 路径")
        void newConfig_GoesInsert() {
            TaskTypeConfigRequest req = buildRequest("new_type", false);

            when(taskTypeConfigMapper.selectByTypeKey("new_type")).thenReturn(null);

            adminService.saveTaskTypeConfig(req);

            verify(taskTypeConfigMapper).insert((AstTaskTypeConfig) argThat(c ->
                    c instanceof AstTaskTypeConfig
                            && "new_type".equals(((AstTaskTypeConfig) c).getTypeKey())
                            && ((AstTaskTypeConfig) c).getIsEnabled() == 0
                            && ((AstTaskTypeConfig) c).getIsDeleted() == 0
                            && ((AstTaskTypeConfig) c).getCreatedAt() != null
            ));
            verify(taskTypeConfigMapper, never()).updateById(any(AstTaskTypeConfig.class));
        }

        @Test
        @DisplayName("isEnabled=null 时存为 0")
        void isEnabledNull_TreatedAsFalse() {
            TaskTypeConfigRequest req = buildRequest("t3", null);
            when(taskTypeConfigMapper.selectByTypeKey("t3")).thenReturn(null);

            adminService.saveTaskTypeConfig(req);

            verify(taskTypeConfigMapper).insert((AstTaskTypeConfig) argThat(c ->
                    c instanceof AstTaskTypeConfig
                            && ((AstTaskTypeConfig) c).getIsEnabled() == 0
            ));
        }

        private TaskTypeConfigRequest buildRequest(String key, Boolean isEnabled) {
            TaskTypeConfigRequest r = new TaskTypeConfigRequest();
            r.setTypeKey(key);
            r.setName("视频转码");
            r.setConcurrencyLimit(5);
            r.setTimeoutSeconds(600);
            r.setMaxRetries(3);
            r.setIsEnabled(isEnabled);
            r.setStepsConfig(new java.util.ArrayList<>());
            return r;
        }
    }

    // ==================== submitTask (admin wrapper) ====================

    @Test
    @DisplayName("submitTask 委托给 TaskService 并包装为 SubmitTaskResponse")
    void submitTask_DelegatesAndWraps() {
        TaskTypeConfigRequest _unused = null; // keep imports intact
        SubmitTaskRequest req = new SubmitTaskRequest();
        req.setType("video_transcode");
        req.setPayload(new java.util.HashMap<>());

        when(taskService.submitTask(req)).thenReturn(999L);

        SubmitTaskResponse resp = adminService.submitTask(req);

        assertEquals(999L, resp.getId());
    }

    // ==================== getStatsOverview ====================

    @Nested
    @DisplayName("getStatsOverview")
    class GetStatsOverview {

        @Test
        @DisplayName("聚合今日任务统计与 Worker 在线数")
        void aggregatesTodayStats_AndWorkerCount() {
            when(taskMapper.countPendingTasks(null)).thenReturn(3L);
            when(taskMapper.countRunningTasks(null)).thenReturn(2L);

            AstTask successTask = taskWithStatus("SUCCESS");
            AstTask failedTask = taskWithStatus("FAILED");
            AstTask cancelledTask = taskWithStatus("CANCELLED");
            when(taskMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(successTask, failedTask, cancelledTask));

            AstWorkerNode online = workerWithHeartbeat(OffsetDateTime.now());
            AstWorkerNode offline = workerWithHeartbeat(OffsetDateTime.now().minusSeconds(120));
            when(workerNodeMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Arrays.asList(online, offline));

            StatsOverviewResponse resp = adminService.getStatsOverview();

            assertEquals(3L, resp.getTotalPending());
            assertEquals(2L, resp.getTotalRunning());
            assertEquals(1L, resp.getTodayStats().getSuccess());
            assertEquals(1L, resp.getTodayStats().getFailed());
            assertEquals(1L, resp.getTodayStats().getCancelled());
            assertEquals(1, resp.getWorkerCount().getOnline());
            assertEquals(1, resp.getWorkerCount().getOffline());
        }

        @Test
        @DisplayName("心跳为 null 的 Worker 计为 offline")
        void workerNullHeartbeat_CountsOffline() {
            when(taskMapper.countPendingTasks(null)).thenReturn(0L);
            when(taskMapper.countRunningTasks(null)).thenReturn(0L);
            when(taskMapper.selectList(any())).thenReturn(Collections.emptyList());

            AstWorkerNode w = new AstWorkerNode();
            w.setLastHeartbeatAt(null);
            when(workerNodeMapper.selectList(any())).thenReturn(List.of(w));

            StatsOverviewResponse resp = adminService.getStatsOverview();
            assertEquals(0, resp.getWorkerCount().getOnline());
            assertEquals(1, resp.getWorkerCount().getOffline());
        }

        private AstTask taskWithStatus(String status) {
            AstTask t = new AstTask();
            t.setStatus(status);
            return t;
        }

        private AstWorkerNode workerWithHeartbeat(OffsetDateTime heartbeat) {
            AstWorkerNode w = new AstWorkerNode();
            w.setLastHeartbeatAt(heartbeat);
            return w;
        }
    }

    // ==================== TaskTypeConfig 查询/删除 ====================

    @Nested
    @DisplayName("TaskTypeConfig CRUD")
    class TaskTypeConfigCrud {

        @Test
        @DisplayName("getAllTaskTypeConfigs 转换为 Response 列表")
        void list_ConvertedToResponse() {
            AstTaskTypeConfig cfg = new AstTaskTypeConfig();
            cfg.setId(1L);
            cfg.setTypeKey("video");
            cfg.setName("视频转码");
            cfg.setIsEnabled(1);
            cfg.setIsDeleted(0);
            cfg.setCreatedAt(OffsetDateTime.now());

            when(taskTypeConfigMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(cfg));

            List<TaskTypeConfigResponse> result = adminService.getAllTaskTypeConfigs();

            assertEquals(1, result.size());
            assertEquals("video", result.get(0).getTypeKey());
            assertTrue(result.get(0).getIsEnabled());
        }

        @Test
        @DisplayName("getTaskTypeConfig 存在时返回 Response")
        void getExisting_ReturnsResponse() {
            AstTaskTypeConfig cfg = new AstTaskTypeConfig();
            cfg.setTypeKey("t");
            cfg.setName("test");
            cfg.setIsEnabled(0);

            when(taskTypeConfigMapper.selectByTypeKey("t")).thenReturn(cfg);

            TaskTypeConfigResponse resp = adminService.getTaskTypeConfig("t");
            assertEquals("test", resp.getName());
            assertFalse(resp.getIsEnabled());
        }

        @Test
        @DisplayName("getTaskTypeConfig 不存在抛 ApiException")
        void getMissing_Throws() {
            when(taskTypeConfigMapper.selectByTypeKey("missing")).thenReturn(null);

            ApiException ex = assertThrows(ApiException.class,
                    () -> adminService.getTaskTypeConfig("missing"));
            assertEquals(BusinessCode.TASK_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("deleteTaskTypeConfig 不存在抛 ApiException")
        void deleteMissing_Throws() {
            when(taskTypeConfigMapper.selectByTypeKey("missing")).thenReturn(null);

            ApiException ex = assertThrows(ApiException.class,
                    () -> adminService.deleteTaskTypeConfig("missing"));
            assertEquals(BusinessCode.TASK_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("deleteTaskTypeConfig 存在时执行逻辑删除 (走 deleteById — @TableLogic 字段 updateById 不可更新)")
        void deleteExisting_SoftDeletes() {
            AstTaskTypeConfig cfg = new AstTaskTypeConfig();
            cfg.setTypeKey("t");
            cfg.setId(88L);

            when(taskTypeConfigMapper.selectByTypeKey("t")).thenReturn(cfg);

            adminService.deleteTaskTypeConfig("t");

            verify(taskTypeConfigMapper).deleteById(88L);
            verify(taskTypeConfigMapper, never()).updateById(any(AstTaskTypeConfig.class));
        }
    }

    // ==================== getTaskList (Admin) ====================

    @Nested
    @DisplayName("getTaskList - 管理端")
    class AdminGetTaskList {

        @Test
        @DisplayName("默认分页 1/20，isArchived 固定为 false")
        void defaultPagination_FixedNotArchived() {
            when(taskMapper.countTasks(any(), any(), any(), eq(false), any(), any(), isNull())).thenReturn(0L);
            when(taskMapper.selectPageWithTypeName(anyLong(), anyLong(), any(), any(), any(), eq(false), any(), any(), isNull()))
                    .thenReturn(Collections.emptyList());

            PageResponse<TaskDetailResponse> page = adminService.getTaskList(null, null, null, null, null);

            assertEquals(1, page.getPage());
            assertEquals(20, page.getPageSize());
            verify(taskMapper).countTasks(isNull(), isNull(), isNull(), eq(false), isNull(), isNull(), isNull());
            verify(taskMapper).selectPageWithTypeName(eq(0L), eq(20L), isNull(), isNull(), isNull(), eq(false), isNull(), isNull(), isNull());
        }

        @Test
        @DisplayName("任务列表包含 durationSeconds 字段映射")
        void listResult_HasDurationMapping() {
            AstTask task = new AstTask();
            task.setId(1L);
            task.setStatus("SUCCESS");
            task.setStartedAt(OffsetDateTime.parse("2026-06-24T10:00:00Z"));
            task.setFinishedAt(OffsetDateTime.parse("2026-06-24T10:00:45Z"));

            when(taskMapper.countTasks(any(), any(), any(), eq(false), any(), any(), isNull())).thenReturn(1L);
            when(taskMapper.selectPageWithTypeName(anyLong(), anyLong(), any(), any(), any(), eq(false), any(), any(), isNull()))
                    .thenReturn(List.of(task));

            PageResponse<TaskDetailResponse> page = adminService.getTaskList(null, null, null, 1, 10);

            assertEquals(45L, page.getList().get(0).getDurationSeconds());
        }
    }

    // ==================== getSystemConfig ====================

    @Nested
    @DisplayName("getSystemConfig - 系统配置只读视图")
    class GetSystemConfig {

        @BeforeEach
        void stubEnvironment() {
            // 提供 buildDatabaseConfig / buildRedisConfig 必需的属性
            when(environment.getProperty(eq("spring.datasource.druid.url"), anyString()))
                    .thenReturn("jdbc:postgresql://user:pass@host:5432/db");
            when(environment.getProperty(eq("spring.datasource.druid.max-active"), anyString()))
                    .thenReturn("30");
            when(environment.getProperty(eq("spring.data.redis.host"), anyString()))
                    .thenReturn("redis-host");
            when(environment.getProperty(eq("spring.data.redis.port"), anyString()))
                    .thenReturn("6379");
            when(environment.getProperty(eq("spring.data.redis.database"), anyString()))
                    .thenReturn("5");
        }

        @Test
        @DisplayName("聚合所有子块不抛异常")
        void aggregatesAllSections_NoException() {
            when(taskMapper.countTasks(any(), any(), any(), eq(false), any(), any(), isNull())).thenReturn(10L);
            when(taskService.getPendingTaskCount()).thenReturn(3L);
            when(taskService.getRunningTaskCount()).thenReturn(2L);
            when(taskTypeConfigMapper.selectCount(any())).thenReturn(4L);
            when(workerNodeMapper.selectCount(any())).thenReturn(2L);

            SystemConfigResponse resp = adminService.getSystemConfig();

            assertNotNull(resp.getSystemInfo());
            assertNotNull(resp.getDatabaseConfig());
            assertNotNull(resp.getRedisConfig());
            assertNotNull(resp.getJvmInfo());
            assertNotNull(resp.getTaskStats());
        }

        @Test
        @DisplayName("数据库 URL 中敏感信息被遮罩")
        void databaseUrl_Masked() {
            when(taskMapper.countTasks(any(), any(), any(), anyBoolean(), any(), any(), isNull())).thenReturn(0L);
            when(taskService.getPendingTaskCount()).thenReturn(0L);
            when(taskService.getRunningTaskCount()).thenReturn(0L);
            when(taskTypeConfigMapper.selectCount(any())).thenReturn(0L);
            when(workerNodeMapper.selectCount(any())).thenReturn(0L);

            SystemConfigResponse resp = adminService.getSystemConfig();

            String url = resp.getDatabaseConfig().getUrl();
            assertTrue(url.contains("***:***@"), "URL 应该遮罩密码: " + url);
            assertFalse(url.contains("user:pass"));
            assertEquals(30, resp.getDatabaseConfig().getMaxPoolSize());
        }

        @Test
        @DisplayName("Redis 配置从 environment 读取")
        void redisConfig_ReadsFromEnvironment() {
            when(taskMapper.countTasks(any(), any(), any(), anyBoolean(), any(), any(), isNull())).thenReturn(0L);
            when(taskService.getPendingTaskCount()).thenReturn(0L);
            when(taskService.getRunningTaskCount()).thenReturn(0L);
            when(taskTypeConfigMapper.selectCount(any())).thenReturn(0L);
            when(workerNodeMapper.selectCount(any())).thenReturn(0L);

            SystemConfigResponse resp = adminService.getSystemConfig();

            assertEquals("redis-host:6379", resp.getRedisConfig().getHost());
            assertEquals(5, resp.getRedisConfig().getDatabase());
            assertEquals("Connected", resp.getRedisConfig().getStatus());
        }

        @Test
        @DisplayName("TaskStats 聚合各状态计数")
        void taskStats_AggregatesByStatus() {
            when(taskMapper.countTasks(isNull(), isNull(), isNull(), eq(false), isNull(), isNull(), isNull())).thenReturn(100L);
            when(taskMapper.countTasks(isNull(), eq("SUCCESS"), isNull(), eq(false), isNull(), isNull(), isNull())).thenReturn(80L);
            when(taskMapper.countTasks(isNull(), eq("FAILED"), isNull(), eq(false), isNull(), isNull(), isNull())).thenReturn(15L);
            when(taskMapper.countTasks(isNull(), eq("CANCELLED"), isNull(), eq(false), isNull(), isNull(), isNull())).thenReturn(5L);
            when(taskService.getPendingTaskCount()).thenReturn(3L);
            when(taskService.getRunningTaskCount()).thenReturn(2L);
            when(taskTypeConfigMapper.selectCount(any())).thenReturn(8L);
            when(workerNodeMapper.selectCount(any())).thenReturn(4L);

            SystemConfigResponse.TaskStats stats = adminService.getSystemConfig().getTaskStats();

            assertEquals(100L, stats.getTotalTasks());
            assertEquals(80L, stats.getSuccessTasks());
            assertEquals(15L, stats.getFailedTasks());
            assertEquals(5L, stats.getCancelledTasks());
            assertEquals(3L, stats.getPendingTasks());
            assertEquals(2L, stats.getRunningTasks());
            assertEquals(8, stats.getTaskTypeCount());
            assertEquals(4, stats.getOnlineWorkerCount());
        }
    }

    // ==================== formatDuration 间接测试（通过 buildSystemInfo）====================

    @Test
    @DisplayName("getSystemConfig 在 environment 返回 null 时仍能工作（默认值兜底）")
    void systemConfig_WorksWithMissingProperties() {
        // buildDatabaseConfig/buildRedisConfig 调用 environment 时都给默认值兜底
        when(taskMapper.countTasks(any(), any(), any(), anyBoolean(), any(), any(), isNull())).thenReturn(0L);
        when(taskService.getPendingTaskCount()).thenReturn(0L);
        when(taskService.getRunningTaskCount()).thenReturn(0L);
        when(taskTypeConfigMapper.selectCount(any())).thenReturn(0L);
        when(workerNodeMapper.selectCount(any())).thenReturn(0L);
        when(environment.getProperty(eq("spring.datasource.druid.url"), anyString())).thenReturn("");
        when(environment.getProperty(eq("spring.datasource.druid.max-active"), anyString())).thenReturn("20");
        when(environment.getProperty(eq("spring.data.redis.host"), anyString())).thenReturn("localhost");
        when(environment.getProperty(eq("spring.data.redis.port"), anyString())).thenReturn("6379");
        when(environment.getProperty(eq("spring.data.redis.database"), anyString())).thenReturn("0");

        assertDoesNotThrow(() -> adminService.getSystemConfig());
    }

    // ==================== formatDuration 直接测试 ====================

    @Test
    @DisplayName("formatDuration: 天/小时/分钟 三分支格式")
    void formatDuration_Branches() {
        // 1 天 2 小时 3 分
        long dayBranch = ((1 * 24 + 2) * 60 + 3) * 60_000L;
        assertEquals("1天2小时3分",
                ReflectionTestUtils.invokeMethod(adminService, "formatDuration", dayBranch));

        // 5 小时 30 分 (无天)
        long hourBranch = (5 * 60 + 30) * 60_000L;
        assertEquals("5小时30分",
                ReflectionTestUtils.invokeMethod(adminService, "formatDuration", hourBranch));

        // 42 分钟 (无天无小时)
        long minuteBranch = 42 * 60_000L;
        assertEquals("42分钟",
                ReflectionTestUtils.invokeMethod(adminService, "formatDuration", minuteBranch));
    }
}
