package fun.commons.lotask4j.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.lotask4j.dto.SubmitTaskRequest;
import fun.commons.lotask4j.dto.TaskDetailResponse;
import fun.commons.lotask4j.service.TaskService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ClientTaskController 集成测试
 *
 * 使用 @SpringBootTest 和 H2 内存数据库进行集成测试
 * 使用 MockBean 模拟 TaskService 以便控制测试行为
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("客户端任务控制器测试")
class ClientTaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskService taskService;

    private SubmitTaskRequest validRequest;
    private TaskDetailResponse taskDetailResponse;

    @BeforeEach
    void setUp() {
        // 准备请求数据
        validRequest = new SubmitTaskRequest();
        validRequest.setType("data_export");

        Map<String, Object> payload = new HashMap<>();
        payload.put("query", "SELECT * FROM users");
        payload.put("format", "xlsx");
        validRequest.setPayload(payload);
        validRequest.setPriority(10);

        // 准备响应数据
        taskDetailResponse = new TaskDetailResponse();
        taskDetailResponse.setId(123456789L); // @OpenId 编码为 "pX6s9o7dLoTL"
        taskDetailResponse.setType("data_export");
        taskDetailResponse.setStatus("PENDING");
        taskDetailResponse.setProgress(0);
        taskDetailResponse.setPriority(10);
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks - 提交任务成功")
    void testSubmitTask_Success() throws Exception {
        // Given
        when(taskService.submitTask(any(SubmitTaskRequest.class)))
            .thenReturn(123456789L); // 返回 Long ID，@OpenId 会自动转换为字符串

        // When & Then
        mockMvc.perform(post("/api/v1/client/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.id").exists()); // 验证 id 字段存在 (OpenID 字符串)
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks - 参数校验失败")
    void testSubmitTask_ValidationFailed() throws Exception {
        // Given: type 为空
        SubmitTaskRequest invalidRequest = new SubmitTaskRequest();
        invalidRequest.setType(null);
        invalidRequest.setPayload(new HashMap<>());

        // When & Then - framework4j-web 返回 HTTP 200，错误信息在响应体中
        mockMvc.perform(post("/api/v1/client/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fail").value(true))
            .andExpect(jsonPath("$.code").exists())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /api/v1/client/tasks/{id} - 查询任务详情成功")
    void testGetTaskDetail_Success() throws Exception {
        // Given
        when(taskService.getTaskDetail(anyLong())).thenReturn(taskDetailResponse);

        // When & Then: 传入 OpenID 字符串，@OpenId 会自动转换为 Long
        mockMvc.perform(get("/api/v1/client/tasks/pX6s9o7dLoTL"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.id").exists()) // 验证 id 字段存在
            .andExpect(jsonPath("$.data.type").value("data_export"))
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.progress").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/client/tasks/{id} - 任务不存在")
    void testGetTaskDetail_NotFound() throws Exception {
        // Given
        when(taskService.getTaskDetail(anyLong()))
            .thenThrow(new RuntimeException("Task not found"));

        // framework4j v1.1.3 行为：service 抛裸 RuntimeException 视为代码 bug，
        // 走 framework4j-web GlobalExceptionHandler.handleException → HTTP 500 + code=10001 SYSTEM_BUSY。
        mockMvc.perform(get("/api/v1/client/tasks/pX6s9o7dLoTL"))
            .andDo(print())
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(10001))
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks/{id}/cancel - 取消任务成功")
    void testCancelTask_Success() throws Exception {
        // Given: Service 层不抛出异常表示成功
        // (void 方法不需要 when...thenReturn)

        // When & Then: 传入 OpenID 字符串
        mockMvc.perform(post("/api/v1/client/tasks/pX6s9o7dLoTL/cancel"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks/{id}/cancel - 任务已完成无法取消")
    void testCancelTask_AlreadyFinished() throws Exception {
        // Given
        when(taskService.cancelTask(anyLong()))
            .thenThrow(new RuntimeException("Task already finished"));

        // framework4j v1.1.3: 裸 RuntimeException → 500 + code=10001 SYSTEM_BUSY
        mockMvc.perform(post("/api/v1/client/tasks/pX6s9o7dLoTL/cancel"))
            .andDo(print())
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(10001))
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks - 优先级超出范围")
    void testSubmitTask_PriorityOutOfRange() throws Exception {
        // Given: 优先级为 101 (超出范围)
        validRequest.setPriority(101);

        // When & Then - framework4j-web 返回 HTTP 200，错误信息在响应体中
        mockMvc.perform(post("/api/v1/client/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fail").value(true))
            .andExpect(jsonPath("$.code").exists())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks - Content-Type 错误")
    void testSubmitTask_WrongContentType() throws Exception {
        // framework4j-web 行为：路由层异常（HttpMediaTypeNotSupportedException 等）保留原 HTTP 状态码，
        // 业务码在响应体 code 字段里（10105 MEDIA_TYPE_NOT_SUPPORTED）。
        mockMvc.perform(post("/api/v1/client/tasks")
                .contentType(MediaType.TEXT_PLAIN)
                .content("invalid content"))
            .andDo(print())
            .andExpect(status().isUnsupportedMediaType())  // 415
            .andExpect(jsonPath("$.code").value(10105))
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fail").value(true))
            .andExpect(jsonPath("$.message").exists());
    }
}
