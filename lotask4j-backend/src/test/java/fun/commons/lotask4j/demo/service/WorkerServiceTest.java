package fun.commons.lotask4j.service;

import fun.commons.lotask4j.dto.*;
import fun.commons.lotask4j.dto.*;
import fun.commons.lotask4j.enums.BusinessCode;
import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.entity.AstTaskTypeConfig;
import fun.commons.lotask4j.entity.AstWorkerNode;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.mapper.AstTaskTypeConfigMapper;
import fun.commons.lotask4j.mapper.AstWorkerNodeMapper;
import fun.commons.lotask4j.service.impl.WorkerServiceImpl;
import fun.commons.framework4j.web.ApiException;
import fun.commons.framework4j.id.generator.SnowflakeDistributor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WorkerService 单元测试
 *
 * 覆盖 WorkerServiceImpl 的业务逻辑层:
 *   - pollTask: 任务类型校验、心跳容错、strategy 默认、空队列、响应构造
 *   - getTaskStatus: 任务不存在、字段映射
 *   - reportProgress: 状态校验、step 新增/更新、updateTaskProgress 返回 0
 *   - reportResult: 状态校验、回调触发、updateTaskResult 返回 0
 *   - calculateGlobalProgress: 无步骤定义、权重计算
 *
 * 注意: pollAndLockTask 的 SKIP LOCKED + 并发抢占语义需要 PostgreSQL 集成测试,
 *      这里只锁 Service 层行为。Mapper 层 SQL 用了 ::inet 类型转换, H2 不支持。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Worker 服务测试")
class WorkerServiceTest {

    @Mock private AstWorkerNodeMapper workerNodeMapper;
    @Mock private AstTaskMapper taskMapper;
    @Mock private AstTaskTypeConfigMapper taskTypeConfigMapper;
    @Mock private WebhookService webhookService;
    @Mock private SnowflakeDistributor snowflakeDistributor;
    @Mock private fun.commons.lotask4j.service.TaskStateMachine stateMachine;

    @InjectMocks private WorkerServiceImpl workerService;

    private PollTaskRequest pollRequest;
    private AstTaskTypeConfig enabledTypeConfig;
    private AstTask sampleTask;

    @BeforeEach
    void setUp() {
        pollRequest = new PollTaskRequest();
        pollRequest.setTaskType("data_export");
        pollRequest.setStrategy("PRIORITY");
        pollRequest.setWorkerId("wkr-test-001");

        enabledTypeConfig = new AstTaskTypeConfig();
        enabledTypeConfig.setTypeKey("data_export");
        enabledTypeConfig.setIsEnabled(1);

        sampleTask = new AstTask();
        sampleTask.setId(100001L);
        sampleTask.setTaskTypeKey("data_export");
        sampleTask.setStatus("RUNNING");
        sampleTask.setPriority(50);
        sampleTask.setProgress(0);
        sampleTask.setVersion(0);

        lenient().when(snowflakeDistributor.nextId()).thenReturn(999L);
        // 默认 stub: dispatch 返回 1L token, 不抛异常 (P0 state machine 已在独立单元测试覆盖)
        lenient().when(stateMachine.dispatch(anyLong(), anyInt(), anyString(), isNull())).thenReturn(1L);
    }

    // ==================== pollTask ====================

    @Nested
    @DisplayName("pollTask - Worker 抢占任务")
    class PollTask {

        @Test
        @DisplayName("正常抢占: 返回响应包含 id/type/payload/priority/executionToken/version")
        void pollTask_Success() {
            when(taskTypeConfigMapper.selectByTypeKey("data_export")).thenReturn(enabledTypeConfig);
            when(workerNodeMapper.upsertWorkerHeartbeat(any(AstWorkerNode.class))).thenReturn(1);

            AstTask task = new AstTask();
            task.setId(100001L);
            task.setTaskTypeKey("data_export");
            task.setPriority(80);
            task.setVersion(0);
            Map<String, Object> payload = new HashMap<>();
            payload.put("query", "SELECT 1");
            task.setPayload(payload);
            when(taskMapper.pollAndLockTask("data_export", "PRIORITY", "10.0.0.1", null)).thenReturn(task);
            when(stateMachine.dispatch(anyLong(), anyInt(), anyString(), isNull())).thenReturn(1234L);
            // 重读返回新 token / version
            AstTask reloaded = new AstTask();
            reloaded.setId(100001L);
            reloaded.setTaskTypeKey("data_export");
            reloaded.setPriority(80);
            reloaded.setPayload(payload);
            reloaded.setAttempt(1);
            reloaded.setVersion(1);
            when(taskMapper.selectByIdWithTypeName(100001L, null)).thenReturn(reloaded);

            PollTaskResponse resp = workerService.pollTask(pollRequest, "10.0.0.1");

            assertNotNull(resp);
            assertEquals(100001L, resp.getId());
            assertEquals("data_export", resp.getType());
            assertEquals(80, resp.getPriority());
            assertNotNull(resp.getPayload());
            assertEquals("SELECT 1", resp.getPayload().get("query"));
            assertEquals(1234L, resp.getExecutionToken());
            assertEquals(1, resp.getVersion());
        }

        @Test
        @DisplayName("strategy 为 null 时默认 PRIORITY")
        void pollTask_StrategyDefaultsToPriority() {
            pollRequest.setStrategy(null);
            when(taskTypeConfigMapper.selectByTypeKey("data_export")).thenReturn(enabledTypeConfig);
            when(workerNodeMapper.upsertWorkerHeartbeat(any())).thenReturn(1);
            when(taskMapper.pollAndLockTask("data_export", "PRIORITY", "10.0.0.1", null)).thenReturn(null);

            workerService.pollTask(pollRequest, "10.0.0.1");

            verify(taskMapper).pollAndLockTask("data_export", "PRIORITY", "10.0.0.1", null);
        }

        @Test
        @DisplayName("strategy=FIFO 透传给 mapper")
        void pollTask_FifoStrategy() {
            pollRequest.setStrategy("FIFO");
            when(taskTypeConfigMapper.selectByTypeKey("data_export")).thenReturn(enabledTypeConfig);
            when(workerNodeMapper.upsertWorkerHeartbeat(any())).thenReturn(1);
            when(taskMapper.pollAndLockTask("data_export", "FIFO", "10.0.0.1", null)).thenReturn(null);

            workerService.pollTask(pollRequest, "10.0.0.1");

            verify(taskMapper).pollAndLockTask("data_export", "FIFO", "10.0.0.1", null);
        }

        @Test
        @DisplayName("任务类型未知: 抛 20101")
        void pollTask_UnknownType() {
            when(taskTypeConfigMapper.selectByTypeKey("unknown_type")).thenReturn(null);
            pollRequest.setTaskType("unknown_type");

            ApiException ex = assertThrows(ApiException.class,
                    () -> workerService.pollTask(pollRequest, "10.0.0.1"));
            assertEquals(20101, ex.getCode());
            verifyNoInteractions(taskMapper);
        }

        @Test
        @DisplayName("任务类型已禁用 (isEnabled=0): 抛 20102")
        void pollTask_DisabledType() {
            enabledTypeConfig.setIsEnabled(0);
            when(taskTypeConfigMapper.selectByTypeKey("data_export")).thenReturn(enabledTypeConfig);

            ApiException ex = assertThrows(ApiException.class,
                    () -> workerService.pollTask(pollRequest, "10.0.0.1"));
            assertEquals(20102, ex.getCode());
            verifyNoInteractions(taskMapper);
        }

        @Test
        @DisplayName("任务类型 isEnabled=null: 抛 20102 (防御 null)")
        void pollTask_IsEnabledNull() {
            enabledTypeConfig.setIsEnabled(null);
            when(taskTypeConfigMapper.selectByTypeKey("data_export")).thenReturn(enabledTypeConfig);

            ApiException ex = assertThrows(ApiException.class,
                    () -> workerService.pollTask(pollRequest, "10.0.0.1"));
            assertEquals(20102, ex.getCode());
        }

        @Test
        @DisplayName("心跳更新失败不阻断 poll (容错)")
        void pollTask_HeartbeatFailureDoesNotBlock() {
            when(taskTypeConfigMapper.selectByTypeKey("data_export")).thenReturn(enabledTypeConfig);
            when(workerNodeMapper.upsertWorkerHeartbeat(any()))
                    .thenThrow(new RuntimeException("DB connection lost"));
            when(taskMapper.pollAndLockTask("data_export", "PRIORITY", "10.0.0.1", null))
                    .thenReturn(sampleTask);
            when(stateMachine.dispatch(eq(100001L), eq(0), anyString(), isNull())).thenReturn(1L);
            when(taskMapper.selectByIdWithTypeName(100001L, null)).thenReturn(sampleTask);

            PollTaskResponse resp = workerService.pollTask(pollRequest, "10.0.0.1");

            assertNotNull(resp);
            assertEquals(100001L, resp.getId());
        }

        @Test
        @DisplayName("心跳返回 0 行 (UPSERT 失败): 仍继续 poll")
        void pollTask_HeartbeatZeroRows() {
            when(taskTypeConfigMapper.selectByTypeKey("data_export")).thenReturn(enabledTypeConfig);
            when(workerNodeMapper.upsertWorkerHeartbeat(any())).thenReturn(0);
            when(taskMapper.pollAndLockTask("data_export", "PRIORITY", "10.0.0.1", null))
                    .thenReturn(sampleTask);
            when(stateMachine.dispatch(anyLong(), anyInt(), anyString(), isNull())).thenReturn(1L);
            when(taskMapper.selectByIdWithTypeName(100001L, null)).thenReturn(sampleTask);

            PollTaskResponse resp = workerService.pollTask(pollRequest, "10.0.0.1");

            assertNotNull(resp);
        }

        @Test
        @DisplayName("无可用任务: 返回 null (不抛异常)")
        void pollTask_EmptyQueue() {
            when(taskTypeConfigMapper.selectByTypeKey("data_export")).thenReturn(enabledTypeConfig);
            when(workerNodeMapper.upsertWorkerHeartbeat(any())).thenReturn(1);
            when(taskMapper.pollAndLockTask("data_export", "PRIORITY", "10.0.0.1", null)).thenReturn(null);

            PollTaskResponse resp = workerService.pollTask(pollRequest, "10.0.0.1");

            assertNull(resp);
        }
    }

    // ==================== getTaskStatus ====================

    @Nested
    @DisplayName("getTaskStatus - Worker 查询任务状态")
    class GetTaskStatus {

        @Test
        @DisplayName("任务存在: 字段完整映射")
        void getTaskStatus_Success() {
            sampleTask.setStatus("RUNNING");
            sampleTask.setProgress(42);
            sampleTask.setCurrentStepKey("downloading");
            sampleTask.setErrorMsg(null);
            when(taskMapper.selectByIdWithTypeName(100001L, null)).thenReturn(sampleTask);

            TaskDetailResponse resp = workerService.getTaskStatus(100001L);

            assertNotNull(resp);
            assertEquals(100001L, resp.getId());
            assertEquals("data_export", resp.getType());
            assertEquals("RUNNING", resp.getStatus());
            assertEquals(42, resp.getProgress());
            assertEquals("downloading", resp.getCurrentStep());
        }

        @Test
        @DisplayName("任务不存在: 抛 20404")
        void getTaskStatus_NotFound() {
            when(taskMapper.selectByIdWithTypeName(999L, null)).thenReturn(null);

            ApiException ex = assertThrows(ApiException.class,
                    () -> workerService.getTaskStatus(999L));
            assertEquals(BusinessCode.TASK_NOT_FOUND.getCode(), ex.getCode());
        }
    }

    // ==================== reportProgress ====================

    @Nested
    @DisplayName("reportProgress - Worker 上报进度")
    class ReportProgress {

        private ReportProgressRequest req;

        @BeforeEach
        void initReq() {
            req = new ReportProgressRequest();
            req.setCurrentStepKey("downloading");
            req.setStepProgress(50);
            req.setExecutionToken(1L);
            req.setVersion(0);
        }

        @Test
        @DisplayName("任务不存在: 抛 20404")
        void reportProgress_TaskNotFound() {
            when(taskMapper.selectByIdWithTypeName(eq(999L), isNull())).thenReturn(null);

            ApiException ex = assertThrows(ApiException.class,
                    () -> workerService.reportProgress(999L, req));
            assertEquals(BusinessCode.TASK_NOT_FOUND.getCode(), ex.getCode());
            verify(stateMachine, never()).reportProgress(anyLong(), anyInt(), anyLong(), any(), anyInt(), any(), anyInt(), isNull());
        }

        @Test
        @DisplayName("任务非 RUNNING: 抛 20409")
        void reportProgress_TaskNotRunning() {
            sampleTask.setStatus("PENDING");
            when(taskMapper.selectByIdWithTypeName(eq(100001L), isNull())).thenReturn(sampleTask);

            ApiException ex = assertThrows(ApiException.class,
                    () -> workerService.reportProgress(100001L, req));
            assertEquals(BusinessCode.TASK_STATE_INVALID.getCode(), ex.getCode());
            verify(stateMachine, never()).reportProgress(anyLong(), anyInt(), anyLong(), any(), anyInt(), any(), anyInt(), isNull());
        }

        @Test
        @DisplayName("stateMachine.reportProgress CAS 失败 (rows=0): 抛 20409")
        void reportProgress_UpdateAffectsZeroRows() {
            sampleTask.setStatus("RUNNING");
            sampleTask.setStepsDetail(null);
            when(taskMapper.selectByIdWithTypeName(eq(100001L), isNull())).thenReturn(sampleTask);
            when(taskTypeConfigMapper.selectByTypeKey("data_export")).thenReturn(null); // 无 steps 定义
            // P0: stateMachine 内部 CAS 失败抛 ApiException
            doThrow(new ApiException(BusinessCode.TASK_STATE_INVALID.getCode(),
                    "progress CAS failed"))
                    .when(stateMachine).reportProgress(anyLong(), anyInt(), anyLong(),
                            any(), anyInt(), any(), anyInt(), isNull());

            ApiException ex = assertThrows(ApiException.class,
                    () -> workerService.reportProgress(100001L, req));
            assertEquals(BusinessCode.TASK_STATE_INVALID.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("step 已存在: 更新现有 step 而非新增")
        void reportProgress_ExistingStepUpdated() {
            sampleTask.setStatus("RUNNING");
            Map<String, Object> existingStep = new HashMap<>();
            existingStep.put("key", "downloading");
            existingStep.put("status", "pending");
            existingStep.put("progress", 0);
            sampleTask.setStepsDetail(new ArrayList<>(List.of(existingStep)));

            when(taskMapper.selectByIdWithTypeName(eq(100001L), isNull())).thenReturn(sampleTask);
            when(taskTypeConfigMapper.selectByTypeKey("data_export")).thenReturn(null);

            workerService.reportProgress(100001L, req);

            assertEquals("processing", existingStep.get("status"));
            assertEquals(50, existingStep.get("progress"));
            assertNotNull(existingStep.get("updated_at"));
        }

        @Test
        @DisplayName("step 不存在: 新增 step 到 stepsDetail")
        void reportProgress_NewStepAdded() {
            sampleTask.setStatus("RUNNING");
            sampleTask.setStepsDetail(new ArrayList<>());

            when(taskMapper.selectByIdWithTypeName(eq(100001L), isNull())).thenReturn(sampleTask);
            when(taskTypeConfigMapper.selectByTypeKey("data_export")).thenReturn(null);
            req.setCurrentStepKey("uploading");
            req.setStepProgress(30);

            workerService.reportProgress(100001L, req);

            assertEquals(1, sampleTask.getStepsDetail().size());
            Map<String, Object> added = sampleTask.getStepsDetail().get(0);
            assertEquals("uploading", added.get("key"));
            assertEquals("processing", added.get("status"));
            assertEquals(30, added.get("progress"));
            assertNotNull(added.get("start_time"));
        }

        @Test
        @DisplayName("stepsDetail 为 null: 自动初始化为空列表")
        void reportProgress_NullStepsDetail() {
            sampleTask.setStatus("RUNNING");
            sampleTask.setStepsDetail(null);

            when(taskMapper.selectByIdWithTypeName(eq(100001L), isNull())).thenReturn(sampleTask);
            when(taskTypeConfigMapper.selectByTypeKey("data_export")).thenReturn(null);

            workerService.reportProgress(100001L, req);

            assertNotNull(sampleTask.getStepsDetail());
            assertEquals(1, sampleTask.getStepsDetail().size());
        }

        @Test
        @DisplayName("全局进度按权重计算: 前置步骤已完成 + 当前步骤部分进度")
        void reportProgress_GlobalProgressWeighted() {
            sampleTask.setStatus("RUNNING");
            sampleTask.setStepsDetail(new ArrayList<>());

            AstTaskTypeConfig cfg = new AstTaskTypeConfig();
            List<Map<String, Object>> stepsDef = new ArrayList<>();
            Map<String, Object> s1 = new HashMap<>();
            s1.put("key", "downloading"); s1.put("weight", 40);
            Map<String, Object> s2 = new HashMap<>();
            s2.put("key", "transcoding"); s2.put("weight", 40);
            Map<String, Object> s3 = new HashMap<>();
            s3.put("key", "uploading"); s3.put("weight", 20);
            stepsDef.add(s1); stepsDef.add(s2); stepsDef.add(s3);
            cfg.setStepsConfig(stepsDef);
            when(taskTypeConfigMapper.selectByTypeKey("data_export")).thenReturn(cfg);

            when(taskMapper.selectByIdWithTypeName(eq(100001L), isNull())).thenReturn(sampleTask);
            req.setCurrentStepKey("transcoding");
            req.setStepProgress(50);

            workerService.reportProgress(100001L, req);

            verify(stateMachine).reportProgress(eq(100001L), eq(0), eq(1L),
                    eq("transcoding"), eq(50), any(), eq(60), isNull());
        }

        @Test
        @DisplayName("无 steps 定义: 全局进度 = 步骤进度")
        void reportProgress_NoStepsDefinition() {
            sampleTask.setStatus("RUNNING");
            sampleTask.setStepsDetail(null);
            when(taskMapper.selectByIdWithTypeName(eq(100001L), isNull())).thenReturn(sampleTask);
            when(taskTypeConfigMapper.selectByTypeKey("data_export")).thenReturn(null);

            workerService.reportProgress(100001L, req);

            verify(stateMachine).reportProgress(eq(100001L), eq(0), eq(1L),
                    eq("downloading"), eq(50), any(), eq(50), isNull());
        }
    }

    // ==================== reportResult ====================

    @Nested
    @DisplayName("reportResult - Worker 上报结果")
    class ReportResult {

        private ReportResultRequest req;

        @BeforeEach
        void initReq() {
            req = new ReportResultRequest();
            req.setStatus("SUCCESS");
            req.setExecutionToken(1L);
            req.setVersion(0);
            Map<String, Object> result = new HashMap<>();
            result.put("rows", 100);
            req.setResult(result);
        }

        @Test
        @DisplayName("任务不存在: 抛 20404")
        void reportResult_TaskNotFound() {
            when(taskMapper.selectByIdWithTypeName(eq(999L), isNull())).thenReturn(null);

            ApiException ex = assertThrows(ApiException.class,
                    () -> workerService.reportResult(999L, req));
            assertEquals(BusinessCode.TASK_NOT_FOUND.getCode(), ex.getCode());
            verify(stateMachine, never()).completeAs(anyLong(), anyInt(), anyLong(), any(), any(), any(), any(), any(), isNull());
        }

        @Test
        @DisplayName("任务 PENDING (非 RUNNING/CANCELLING): 抛 20409")
        void reportResult_InvalidStatus() {
            sampleTask.setStatus("PENDING");
            when(taskMapper.selectByIdWithTypeName(eq(100001L), isNull())).thenReturn(sampleTask);

            ApiException ex = assertThrows(ApiException.class,
                    () -> workerService.reportResult(100001L, req));
            assertEquals(BusinessCode.TASK_STATE_INVALID.getCode(), ex.getCode());
            verify(stateMachine, never()).completeAs(anyLong(), anyInt(), anyLong(), any(), any(), any(), any(), any(), isNull());
        }

        @Test
        @DisplayName("SUCCESS + 无 callback_url: 仅调用 stateMachine.completeAs,不触发 webhook")
        void reportResult_SuccessNoCallback() {
            sampleTask.setStatus("RUNNING");
            sampleTask.setCallbackUrl(null);
            when(taskMapper.selectByIdWithTypeName(eq(100001L), isNull())).thenReturn(sampleTask);

            workerService.reportResult(100001L, req);

            verify(stateMachine).completeAs(eq(100001L), eq(0), eq(1L),
                    eq(fun.commons.lotask4j.enums.TaskStatus.SUCCESS), any(), eq(null), any(), any(), isNull());
            verifyNoInteractions(webhookService);
        }

        @Test
        @DisplayName("SUCCESS + 有 callback_url: 触发 webhook")
        void reportResult_SuccessWithCallback() {
            sampleTask.setStatus("RUNNING");
            sampleTask.setCallbackUrl("https://example.com/cb");
            when(taskMapper.selectByIdWithTypeName(eq(100001L), isNull())).thenReturn(sampleTask);
            when(taskMapper.selectByIdWithTypeName(100001L, null)).thenReturn(sampleTask);

            workerService.reportResult(100001L, req);

            verify(webhookService).enqueueFinished(any(AstTask.class));
        }

        @Test
        @DisplayName("FAILED 状态 + errorMsg: 允许从 RUNNING 转失败")
        void reportResult_Failed() {
            sampleTask.setStatus("RUNNING");
            sampleTask.setCallbackUrl(null);
            when(taskMapper.selectByIdWithTypeName(eq(100001L), isNull())).thenReturn(sampleTask);

            req.setStatus("FAILED");
            req.setResult(null);
            req.setErrorMsg("DB timeout");

            workerService.reportResult(100001L, req);

            verify(stateMachine).completeAs(eq(100001L), eq(0), eq(1L),
                    eq(fun.commons.lotask4j.enums.TaskStatus.FAILED),
                    eq(null), eq("DB timeout"), any(), any(), isNull());
        }

        @Test
        @DisplayName("CANCELLED 状态从 CANCELLING 转: 允许 (取消确认路径)")
        void reportResult_Cancelled() {
            sampleTask.setStatus("CANCELLING");
            sampleTask.setCallbackUrl(null);
            when(taskMapper.selectByIdWithTypeName(eq(100001L), isNull())).thenReturn(sampleTask);

            req.setStatus("CANCELLED");
            req.setResult(null);

            workerService.reportResult(100001L, req);

            verify(stateMachine).completeAs(eq(100001L), eq(0), eq(1L),
                    eq(fun.commons.lotask4j.enums.TaskStatus.CANCELLED),
                    eq(null), eq(null), any(), any(), isNull());
        }

        @Test
        @DisplayName("stateMachine.completeAs 抛异常: 不调用 webhookService")
        void reportResult_UpdateAffectsZeroRows() {
            sampleTask.setStatus("RUNNING");
            sampleTask.setCallbackUrl(null);
            when(taskMapper.selectByIdWithTypeName(eq(100001L), isNull())).thenReturn(sampleTask);
            doThrow(new ApiException(BusinessCode.TASK_STATE_INVALID.getCode(),
                    "complete CAS failed"))
                    .when(stateMachine).completeAs(anyLong(), anyInt(), anyLong(), any(), any(), any(), any(), any(), isNull());

            ApiException ex = assertThrows(ApiException.class,
                    () -> workerService.reportResult(100001L, req));
            assertEquals(BusinessCode.TASK_STATE_INVALID.getCode(), ex.getCode());
            verifyNoInteractions(webhookService);
        }

        @Test
        @DisplayName("result 为 null: 不影响 stateMachine.completeAs 路径")
        void reportResult_NullResult() {
            sampleTask.setStatus("RUNNING");
            sampleTask.setCallbackUrl(null);
            when(taskMapper.selectByIdWithTypeName(eq(100001L), isNull())).thenReturn(sampleTask);

            req.setResult(null);
            req.setStatus("SUCCESS");

            workerService.reportResult(100001L, req);

            verify(stateMachine).completeAs(eq(100001L), eq(0), eq(1L),
                    eq(fun.commons.lotask4j.enums.TaskStatus.SUCCESS),
                    eq(null), any(), any(), any(), isNull());
        }

        @Test
        @DisplayName("无效终态字符串 (valueOf 失败) → TASK_STATE_INVALID")
        void reportResult_UnparseableStatusString() {
            sampleTask.setStatus("RUNNING");
            when(taskMapper.selectByIdWithTypeName(eq(100001L), isNull())).thenReturn(sampleTask);

            req.setStatus("NOT_A_STATUS");
            ApiException ex = assertThrows(ApiException.class,
                    () -> workerService.reportResult(100001L, req));
            assertEquals(BusinessCode.TASK_STATE_INVALID.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("非终态 (PENDING) → TASK_STATE_INVALID")
        void reportResult_NonTerminalRejected() {
            sampleTask.setStatus("RUNNING");
            when(taskMapper.selectByIdWithTypeName(eq(100001L), isNull())).thenReturn(sampleTask);

            req.setStatus("PENDING");
            ApiException ex = assertThrows(ApiException.class,
                    () -> workerService.reportResult(100001L, req));
            assertEquals(BusinessCode.TASK_STATE_INVALID.getCode(), ex.getCode());
        }
    }

    // ==================== 心跳 / 新 step 追加 / 权重边界 ====================

    @Nested
    @DisplayName("pollTask 心跳与边界")
    class HeartbeatAndEdgeCases {

        @Test
        @DisplayName("workerId 为 null → 心跳用 ip/type 生成 workerId; upsert 返回 0 只告警")
        void pollTask_NullWorkerId_GeneratesFallback() {
            pollRequest.setWorkerId(null);
            when(taskTypeConfigMapper.selectByTypeKey("data_export")).thenReturn(enabledTypeConfig);
            when(workerNodeMapper.upsertWorkerHeartbeat(any())).thenReturn(1);
            when(taskMapper.pollAndLockTask(anyString(), anyString(), anyString(), isNull())).thenReturn(null);

            assertNull(workerService.pollTask(pollRequest, "10.0.0.1"));

            ArgumentCaptor<AstWorkerNode> captor = ArgumentCaptor.forClass(AstWorkerNode.class);
            verify(workerNodeMapper).upsertWorkerHeartbeat(captor.capture());
            assertEquals("worker-10-0-0-1-data_export", captor.getValue().getWorkerId());
        }

        @Test
        @DisplayName("心跳 upsert 抛异常被吞, poll 继续")
        void pollTask_HeartbeatThrows_Swallows() {
            when(taskTypeConfigMapper.selectByTypeKey("data_export")).thenReturn(enabledTypeConfig);
            when(workerNodeMapper.upsertWorkerHeartbeat(any())).thenThrow(new RuntimeException("redis down"));
            when(taskMapper.pollAndLockTask(anyString(), anyString(), anyString(), isNull())).thenReturn(null);

            assertNull(workerService.pollTask(pollRequest, "10.0.0.1"));
        }
    }

    @Nested
    @DisplayName("reportProgress 步骤边界")
    class ProgressStepEdges {

        private final ReportProgressRequest req = new ReportProgressRequest();

        @BeforeEach
        void initReq() {
            req.setCurrentStepKey("uploading");
            req.setStepProgress(50);
            req.setExecutionToken(1L);
            req.setVersion(0);
        }

        @Test
        @DisplayName("currentStepKey 不在 stepsDetail → 追加新 step")
        void reportProgress_AppendsNewStep() {
            Map<String, Object> existing = new HashMap<>();
            existing.put("key", "downloading");
            existing.put("status", "done");
            sampleTask.setStatus("RUNNING");
            sampleTask.setStepsDetail(new ArrayList<>(List.of(existing)));
            when(taskMapper.selectByIdWithTypeName(eq(100001L), isNull())).thenReturn(sampleTask);
            when(taskTypeConfigMapper.selectByTypeKey("data_export")).thenReturn(null);

            workerService.reportProgress(100001L, req);

            verify(stateMachine).reportProgress(eq(100001L), eq(0), eq(1L),
                    eq("uploading"), eq(50),
                    argThat(steps -> steps.size() == 2
                            && "processing".equals(steps.get(1).get("status"))
                            && "uploading".equals(steps.get(1).get("key"))),
                    eq(50), isNull());
        }

        @Test
        @DisplayName("stepsConfig 为空列表 → 全局进度 = 步骤进度")
        void calculateGlobal_EmptyStepsConfig() {
            AstTaskTypeConfig cfg = new AstTaskTypeConfig();
            cfg.setTypeKey("data_export");
            cfg.setStepsConfig(new ArrayList<>());
            sampleTask.setStatus("RUNNING");
            sampleTask.setStepsDetail(null);
            when(taskMapper.selectByIdWithTypeName(eq(100001L), isNull())).thenReturn(sampleTask);
            when(taskTypeConfigMapper.selectByTypeKey("data_export")).thenReturn(cfg);

            workerService.reportProgress(100001L, req);

            verify(stateMachine).reportProgress(eq(100001L), eq(0), eq(1L),
                    eq("uploading"), eq(50), any(), eq(50), isNull());
        }

        @Test
        @DisplayName("权重全 0/null → totalWeight=0 回落步骤进度")
        void calculateGlobal_TotalWeightZero() {
            AstTaskTypeConfig cfg = new AstTaskTypeConfig();
            cfg.setTypeKey("data_export");
            List<Map<String, Object>> stepsDef = new ArrayList<>();
            Map<String, Object> s1 = new HashMap<>();
            s1.put("key", "uploading"); // weight 缺失 → null → 0
            Map<String, Object> s2 = new HashMap<>();
            s2.put("key", "other"); s2.put("weight", 0);
            stepsDef.add(s1); stepsDef.add(s2);
            cfg.setStepsConfig(stepsDef);
            sampleTask.setStatus("RUNNING");
            sampleTask.setStepsDetail(null);
            when(taskMapper.selectByIdWithTypeName(eq(100001L), isNull())).thenReturn(sampleTask);
            when(taskTypeConfigMapper.selectByTypeKey("data_export")).thenReturn(cfg);

            workerService.reportProgress(100001L, req);

            verify(stateMachine).reportProgress(eq(100001L), eq(0), eq(1L),
                    eq("uploading"), eq(50), any(), eq(50), isNull());
        }
    }
}
