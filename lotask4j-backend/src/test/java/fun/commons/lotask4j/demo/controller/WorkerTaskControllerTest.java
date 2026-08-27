package fun.commons.lotask4j.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.lotask4j.dto.*;
import fun.commons.lotask4j.dto.*;
import fun.commons.lotask4j.service.WorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * WorkerTaskController 集成测试
 *
 * 使用 @SpringBootTest + H2 + MockBean(WorkerService) 模式，
 * 与 ClientTaskControllerTest 保持一致。
 *
 * 覆盖 4 个 Worker 端点：
 *   - POST /api/v1/worker/tasks/poll
 *   - GET  /api/v1/worker/tasks/{id}/status
 *   - POST /api/v1/worker/tasks/{id}/progress
 *   - POST /api/v1/worker/tasks/{id}/result
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Worker 任务控制器测试")
class WorkerTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WorkerService workerService;

    private PollTaskRequest pollRequest;
    private PollTaskResponse pollResponse;
    private TaskDetailResponse taskDetailResponse;
    private ReportProgressRequest progressRequest;
    private ReportResultRequest resultRequest;

    @BeforeEach
    void setUp() {
        // Poll 请求
        pollRequest = new PollTaskRequest();
        pollRequest.setTaskType("data_export");
        pollRequest.setStrategy("PRIORITY");
        pollRequest.setWorkerId("wkr-test-001");  // P0

        // Poll 响应：返回抢占到的任务
        pollResponse = new PollTaskResponse();
        pollResponse.setId(123456789L); // @OpenId 编码为 "pX6s9o7dLoTL"
        pollResponse.setType("data_export");
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", "SELECT * FROM users");
        pollResponse.setPayload(payload);
        pollResponse.setPriority(100);
        pollResponse.setExecutionToken(1L);     // P0
        pollResponse.setVersion(0);             // P0
        pollResponse.setAttempt(1);

        // 任务详情
        taskDetailResponse = new TaskDetailResponse();
        taskDetailResponse.setId(123456789L);
        taskDetailResponse.setType("data_export");
        taskDetailResponse.setStatus("RUNNING");
        taskDetailResponse.setProgress(35);
        taskDetailResponse.setPriority(100);

        // 进度上报 — P0: 必须带 executionToken + version
        progressRequest = new ReportProgressRequest();
        progressRequest.setCurrentStepKey("querying");
        progressRequest.setStepProgress(50);
        progressRequest.setExecutionToken(1L);
        progressRequest.setVersion(0);

        // 结果上报 (SUCCESS) — P0: 必须带 executionToken + version
        resultRequest = new ReportResultRequest();
        resultRequest.setStatus("SUCCESS");
        Map<String, Object> result = new HashMap<>();
        result.put("rows", 100);
        resultRequest.setResult(result);
        resultRequest.setExecutionToken(1L);
        resultRequest.setVersion(0);
    }

    // ==================== POST /poll ====================

    @Test
    @DisplayName("POST /api/v1/worker/tasks/poll - 抢占任务成功")
    void testPollTask_Success() throws Exception {
        when(workerService.pollTask(any(PollTaskRequest.class), any(String.class)))
            .thenReturn(pollResponse);

        mockMvc.perform(post("/api/v1/worker/tasks/poll")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pollRequest)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.id").exists()) // @OpenId 转字符串
            .andExpect(jsonPath("$.data.type").value("data_export"))
            .andExpect(jsonPath("$.data.priority").value(100))
            .andExpect(jsonPath("$.data.payload.query").value("SELECT * FROM users"));
    }

    @Test
    @DisplayName("POST /api/v1/worker/tasks/poll - 队列为空，返回 null data")
    void testPollTask_EmptyQueue() throws Exception {
        when(workerService.pollTask(any(PollTaskRequest.class), any(String.class)))
            .thenReturn(null);

        mockMvc.perform(post("/api/v1/worker/tasks/poll")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pollRequest)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
        // data 为 null 是合法的（无任务可执行）
    }

    @Test
    @DisplayName("POST /api/v1/worker/tasks/poll - taskType 缺失，校验失败")
    void testPollTask_MissingTaskType() throws Exception {
        PollTaskRequest invalid = new PollTaskRequest();
        invalid.setTaskType(null);
        invalid.setWorkerId("wkr-test-001");

        mockMvc.perform(post("/api/v1/worker/tasks/poll")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fail").value(true))
            .andExpect(jsonPath("$.code").exists())
            .andExpect(jsonPath("$.message").exists());

        // 校验失败不应进入 service
        verify(workerService, never()).pollTask(any(), any());
    }

    @Test
    @DisplayName("POST /api/v1/worker/tasks/poll - Worker IP 从 X-Forwarded-For 提取")
    void testPollTask_WorkerIpFromXForwardedFor() throws Exception {
        when(workerService.pollTask(any(PollTaskRequest.class), eq("203.0.113.10")))
            .thenReturn(pollResponse);

        mockMvc.perform(post("/api/v1/worker/tasks/poll")
                .header("X-Forwarded-For", "203.0.113.10, 10.0.0.1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pollRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        verify(workerService).pollTask(any(PollTaskRequest.class), eq("203.0.113.10"));
    }

    // ==================== GET /status ====================

    @Test
    @DisplayName("GET /api/v1/worker/tasks/{id}/status - 查询成功")
    void testGetTaskStatus_Success() throws Exception {
        when(workerService.getTaskStatus(anyLong())).thenReturn(taskDetailResponse);

        mockMvc.perform(get("/api/v1/worker/tasks/pX6s9o7dLoTL/status"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.id").exists())
            .andExpect(jsonPath("$.data.type").value("data_export"))
            .andExpect(jsonPath("$.data.status").value("RUNNING"))
            .andExpect(jsonPath("$.data.progress").value(35));
    }

    @Test
    @DisplayName("GET /api/v1/worker/tasks/{id}/status - 检测到 CANCELLING 信号")
    void testGetTaskStatus_DetectCancellation() throws Exception {
        taskDetailResponse.setStatus("CANCELLING");
        when(workerService.getTaskStatus(anyLong())).thenReturn(taskDetailResponse);

        mockMvc.perform(get("/api/v1/worker/tasks/pX6s9o7dLoTL/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLING"));
    }

    @Test
    @DisplayName("GET /api/v1/worker/tasks/{id}/status - 任务不存在")
    void testGetTaskStatus_NotFound() throws Exception {
        when(workerService.getTaskStatus(anyLong()))
            .thenThrow(new RuntimeException("Task not found"));

        // framework4j v1.1.3: 裸 RuntimeException → 500 + code=10001
        mockMvc.perform(get("/api/v1/worker/tasks/pX6s9o7dLoTL/status"))
            .andDo(print())
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(10001));
    }

    // ==================== POST /progress ====================

    @Test
    @DisplayName("POST /api/v1/worker/tasks/{id}/progress - 上报进度成功")
    void testReportProgress_Success() throws Exception {
        // void 方法，不需要 when...thenReturn
        mockMvc.perform(post("/api/v1/worker/tasks/pX6s9o7dLoTL/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(progressRequest)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        verify(workerService).reportProgress(eq(123456789L), any(ReportProgressRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/worker/tasks/{id}/progress - currentStepKey 为空，校验失败")
    void testReportProgress_MissingStepKey() throws Exception {
        ReportProgressRequest invalid = new ReportProgressRequest();
        invalid.setCurrentStepKey(null);
        invalid.setStepProgress(50);

        mockMvc.perform(post("/api/v1/worker/tasks/pX6s9o7dLoTL/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fail").value(true));

        verify(workerService, never()).reportProgress(anyLong(), any());
    }

    @Test
    @DisplayName("POST /api/v1/worker/tasks/{id}/progress - stepProgress 超过 100，校验失败")
    void testReportProgress_StepProgressOverMax() throws Exception {
        ReportProgressRequest invalid = new ReportProgressRequest();
        invalid.setCurrentStepKey("querying");
        invalid.setStepProgress(150);

        mockMvc.perform(post("/api/v1/worker/tasks/pX6s9o7dLoTL/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fail").value(true));

        verify(workerService, never()).reportProgress(anyLong(), any());
    }

    @Test
    @DisplayName("POST /api/v1/worker/tasks/{id}/progress - stepProgress 为负数，校验失败")
    void testReportProgress_StepProgressNegative() throws Exception {
        ReportProgressRequest invalid = new ReportProgressRequest();
        invalid.setCurrentStepKey("querying");
        invalid.setStepProgress(-1);

        mockMvc.perform(post("/api/v1/worker/tasks/pX6s9o7dLoTL/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fail").value(true));

        verify(workerService, never()).reportProgress(anyLong(), any());
    }

    @Test
    @DisplayName("POST /api/v1/worker/tasks/{id}/progress - 任务非 RUNNING 状态被拒绝")
    void testReportProgress_TaskNotRunning() throws Exception {
        doThrow(new IllegalStateException("Task is not RUNNING"))
            .when(workerService).reportProgress(eq(123456789L), any(ReportProgressRequest.class));

        // framework4j v1.1.3: IllegalStateException 不是 IllegalArgumentException 子类，
        // 走 handleException(Exception) 兜底 → 500 + code=10001 SYSTEM_BUSY
        // (业务上应让 service 抛 ApiException(BusinessCode.TASK_CANCEL_NOT_ALLOWED))
        mockMvc.perform(post("/api/v1/worker/tasks/pX6s9o7dLoTL/progress")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(progressRequest)))
            .andDo(print())
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(10001))
            .andExpect(jsonPath("$.message").exists());
    }

    // ==================== POST /result ====================

    @Test
    @DisplayName("POST /api/v1/worker/tasks/{id}/result - SUCCESS 结果上报成功")
    void testReportResult_Success() throws Exception {
        mockMvc.perform(post("/api/v1/worker/tasks/pX6s9o7dLoTL/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resultRequest)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        verify(workerService).reportResult(eq(123456789L), any(ReportResultRequest.class));
    }

    @Test
    @DisplayName("POST /api/v1/worker/tasks/{id}/result - FAILED 状态上报")
    void testReportResult_Failed() throws Exception {
        resultRequest.setStatus("FAILED");
        resultRequest.setResult(null);
        resultRequest.setErrorMsg("DB connection timeout");

        mockMvc.perform(post("/api/v1/worker/tasks/pX6s9o7dLoTL/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resultRequest)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        verify(workerService).reportResult(eq(123456789L), argThat(r -> "FAILED".equals(r.getStatus())));
    }

    @Test
    @DisplayName("POST /api/v1/worker/tasks/{id}/result - CANCELLED 状态上报 (确认取消)")
    void testReportResult_Cancelled() throws Exception {
        resultRequest.setStatus("CANCELLED");
        resultRequest.setResult(null);

        mockMvc.perform(post("/api/v1/worker/tasks/pX6s9o7dLoTL/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resultRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));

        verify(workerService).reportResult(eq(123456789L), argThat(r -> "CANCELLED".equals(r.getStatus())));
    }

    @Test
    @DisplayName("POST /api/v1/worker/tasks/{id}/result - status 非法 (RUNNING)，校验失败")
    void testReportResult_InvalidStatus() throws Exception {
        resultRequest.setStatus("RUNNING");

        mockMvc.perform(post("/api/v1/worker/tasks/pX6s9o7dLoTL/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resultRequest)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fail").value(true));

        verify(workerService, never()).reportResult(anyLong(), any());
    }

    @Test
    @DisplayName("POST /api/v1/worker/tasks/{id}/result - status 缺失，校验失败")
    void testReportResult_MissingStatus() throws Exception {
        resultRequest.setStatus(null);

        mockMvc.perform(post("/api/v1/worker/tasks/pX6s9o7dLoTL/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resultRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false));

        verify(workerService, never()).reportResult(anyLong(), any());
    }
}
