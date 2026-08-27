package fun.commons.lotask4j.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.lotask4j.dto.StatsOverviewResponse;
import fun.commons.lotask4j.dto.WorkerNodeResponse;
import fun.commons.lotask4j.entity.AstTaskExecutionEvent;
import fun.commons.lotask4j.enums.TaskEventType;
import fun.commons.lotask4j.service.AdminService;
import fun.commons.lotask4j.service.TaskEventRecorder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminTaskController 关键端点集成测试 (P1-D)。
 *
 * 主要覆盖低覆盖方法：workers / stats / events（P1-3）。
 * @MockBean 替换 AdminService 与 TaskEventRecorder，避免启动真实依赖。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("AdminTaskController 集成 (P1-D)")
class AdminTaskControllerEventsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean private AdminService adminService;
    @MockBean private TaskEventRecorder taskEventRecorder;

    private AstTaskExecutionEvent sampleEvent;

    @BeforeEach
    void setUp() {
        sampleEvent = new AstTaskExecutionEvent();
        sampleEvent.setId(1L);
        sampleEvent.setTaskId(123456789L); // 与 WorkerTaskControllerTest 同样的"pX6s9o7dLoTL"对应
        sampleEvent.setEventType(TaskEventType.TASK_DISPATCHED.name());
        sampleEvent.setOldStatus("PENDING");
        sampleEvent.setNewStatus("RUNNING");
        sampleEvent.setWorkerId("wkr-test-001");
        sampleEvent.setOperator("wkr-test-001");
        sampleEvent.setTraceId("abc123");
        sampleEvent.setAttempt(1);
        Map<String, Object> detail = new HashMap<>();
        detail.put("execution_id", 999L);
        sampleEvent.setDetail(detail);
        sampleEvent.setCreatedAt(OffsetDateTime.now());
    }

    // ==================== P1-3 events ====================

    @Test
    @DisplayName("GET /admin/tasks/{id}/events - 列出事件历史")
    void testGetTaskEvents_Success() throws Exception {
        when(taskEventRecorder.historyOf(eq(123456789L), eq(100)))
                .thenReturn(List.of(sampleEvent));

        mockMvc.perform(get("/api/v1/admin/tasks/pX6s9o7dLoTL/events"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // FastJSON2 默认序列化使用 snake_case (framework4j 行为)
                .andExpect(jsonPath("$.data[0].event_type").value("TASK_DISPATCHED"))
                .andExpect(jsonPath("$.data[0].worker_id").value("wkr-test-001"));

        verify(taskEventRecorder).historyOf(eq(123456789L), eq(100));
    }

    @Test
    @DisplayName("GET /admin/tasks/{id}/events - 自定义 limit")
    void testGetTaskEvents_Limit() throws Exception {
        when(taskEventRecorder.historyOf(anyLong(), eq(50))).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/tasks/pX6s9o7dLoTL/events").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(taskEventRecorder).historyOf(anyLong(), eq(50));
    }

    @Test
    @DisplayName("GET /admin/tasks/{id}/events - recorder 抛 ApiException (404)")
    void testGetTaskEvents_NotFound() throws Exception {
        doThrow(new fun.commons.framework4j.web.ApiException(
                fun.commons.lotask4j.enums.BusinessCode.TASK_NOT_FOUND.getCode(),
                "任务不存在"))
                .when(taskEventRecorder).historyOf(anyLong(), anyInt());

        mockMvc.perform(get("/api/v1/admin/tasks/pX6s9o7dLoTL/events"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(20100));
    }

    // ==================== workers / stats ====================

    @Test
    @DisplayName("GET /admin/workers - 列出在线 Worker")
    void testGetOnlineWorkers() throws Exception {
        WorkerNodeResponse node = new WorkerNodeResponse();
        node.setWorkerKey("wkr-test-001");
        node.setWorkerIp("10.0.0.1");
        node.setHostname("worker-01");
        node.setStatus("ONLINE");
        when(adminService.getOnlineWorkers()).thenReturn(List.of(node));

        mockMvc.perform(get("/api/v1/admin/workers"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // FastJSON2 默认使用 snake_case (framework4j 行为)
                .andExpect(jsonPath("$.data[0].worker_key").value("wkr-test-001"))
                .andExpect(jsonPath("$.data[0].status").value("ONLINE"));
    }

    @Test
    @DisplayName("GET /admin/stats/overview - 统计概览")
    void testGetStatsOverview() throws Exception {
        StatsOverviewResponse stats = new StatsOverviewResponse();
        stats.setTotalPending(10L);
        stats.setTotalRunning(5L);
        StatsOverviewResponse.TodayStats today = new StatsOverviewResponse.TodayStats();
        today.setSuccess(100L);
        today.setFailed(3L);
        today.setCancelled(2L);
        stats.setTodayStats(today);
        StatsOverviewResponse.WorkerCount wc = new StatsOverviewResponse.WorkerCount();
        wc.setOnline(2);
        wc.setOffline(1);
        stats.setWorkerCount(wc);
        when(adminService.getStatsOverview()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/admin/stats/overview"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total_pending").value(10))
                .andExpect(jsonPath("$.data.total_running").value(5))
                .andExpect(jsonPath("$.data.today_stats.success").value(100))
                .andExpect(jsonPath("$.data.worker_count.online").value(2));
    }
}
