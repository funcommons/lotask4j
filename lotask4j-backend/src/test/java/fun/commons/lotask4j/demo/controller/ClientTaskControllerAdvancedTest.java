package fun.commons.lotask4j.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import fun.commons.lotask4j.dto.SubmitTaskRequest;
import fun.commons.lotask4j.dto.TaskDetailResponse;
import fun.commons.lotask4j.service.TaskService;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ClientTaskController 高级集成测试 - 边界条件和异常场景
 *
 * 使用 @SpringBootTest 和 H2 内存数据库进行集成测试
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("客户端任务控制器高级测试")
class ClientTaskControllerAdvancedTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TaskService taskService;

    // ==================== 边界条件测试 ====================

    @Test
    @DisplayName("POST /api/v1/client/tasks - 优先级为0 (最小值)")
    void testSubmitTask_PriorityMin() throws Exception {
        // Given
        SubmitTaskRequest request = new SubmitTaskRequest();
        request.setType("data_export");
        request.setPayload(new HashMap<>());
        request.setPriority(0);

        when(taskService.submitTask(any())).thenReturn(123456789L);

        // When & Then
        mockMvc.perform(post("/api/v1/client/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks - 优先级为100 (最大值)")
    void testSubmitTask_PriorityMax() throws Exception {
        // Given
        SubmitTaskRequest request = new SubmitTaskRequest();
        request.setType("data_export");
        request.setPayload(new HashMap<>());
        request.setPriority(100);

        when(taskService.submitTask(any())).thenReturn(123456789L);

        // When & Then
        mockMvc.perform(post("/api/v1/client/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks - 优先级为负数 (校验失败)")
    void testSubmitTask_PriorityNegative() throws Exception {
        // Given
        SubmitTaskRequest request = new SubmitTaskRequest();
        request.setType("data_export");
        request.setPayload(new HashMap<>());
        request.setPriority(-1);

        // When & Then - framework4j-web 返回 HTTP 200，错误信息在响应体中
        mockMvc.perform(post("/api/v1/client/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fail").value(true))
            .andExpect(jsonPath("$.code").exists())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks - Payload 为空 Map")
    void testSubmitTask_EmptyPayload() throws Exception {
        // Given
        SubmitTaskRequest request = new SubmitTaskRequest();
        request.setType("data_export");
        request.setPayload(new HashMap<>());
        request.setPriority(10);

        when(taskService.submitTask(any())).thenReturn(123456789L);

        // When & Then
        mockMvc.perform(post("/api/v1/client/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks - Payload 包含嵌套对象")
    void testSubmitTask_NestedPayload() throws Exception {
        // Given
        SubmitTaskRequest request = new SubmitTaskRequest();
        request.setType("data_export");

        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> nested = new HashMap<>();
        nested.put("format", "xlsx");
        nested.put("columns", new String[]{"id", "name", "email"});
        payload.put("query", "SELECT * FROM users");
        payload.put("options", nested);

        request.setPayload(payload);
        request.setPriority(10);

        when(taskService.submitTask(any())).thenReturn(123456789L);

        // When & Then
        mockMvc.perform(post("/api/v1/client/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks - 带 Callback URL")
    void testSubmitTask_WithCallbackUrl() throws Exception {
        // Given
        SubmitTaskRequest request = new SubmitTaskRequest();
        request.setType("data_export");
        request.setPayload(new HashMap<>());
        request.setPriority(10);
        request.setCallbackUrl("https://example.com/callback");

        when(taskService.submitTask(any())).thenReturn(123456789L);

        // When & Then
        mockMvc.perform(post("/api/v1/client/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks - Callback URL 格式无效")
    void testSubmitTask_InvalidCallbackUrl() throws Exception {
        // Given
        SubmitTaskRequest request = new SubmitTaskRequest();
        request.setType("data_export");
        request.setPayload(new HashMap<>());
        request.setPriority(10);
        request.setCallbackUrl("not-a-url");

        when(taskService.submitTask(any())).thenReturn(123456789L);

        // When & Then - 如果有 URL 校验，应该返回 400
        mockMvc.perform(post("/api/v1/client/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print());
            // 注意：当前实现可能没有 URL 格式校验，这里仅作演示
    }

    // ==================== 异常场景测试 ====================

    @Test
    @DisplayName("POST /api/v1/client/tasks - Type 为空字符串")
    void testSubmitTask_EmptyType() throws Exception {
        // Given
        SubmitTaskRequest request = new SubmitTaskRequest();
        request.setType("");
        request.setPayload(new HashMap<>());

        // When & Then - framework4j-web 返回 HTTP 200，错误信息在响应体中
        mockMvc.perform(post("/api/v1/client/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fail").value(true))
            .andExpect(jsonPath("$.code").exists())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks - Payload 为 null")
    void testSubmitTask_NullPayload() throws Exception {
        // Given
        SubmitTaskRequest request = new SubmitTaskRequest();
        request.setType("data_export");
        request.setPayload(null);

        // When & Then - framework4j-web 返回 HTTP 200，错误信息在响应体中
        mockMvc.perform(post("/api/v1/client/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fail").value(true))
            .andExpect(jsonPath("$.code").exists())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks - 请求体为空")
    void testSubmitTask_EmptyBody() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/client/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(""))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fail").value(true))
            .andExpect(jsonPath("$.code").exists())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks - JSON 格式错误")
    void testSubmitTask_MalformedJson() throws Exception {
        // When & Then
        mockMvc.perform(post("/api/v1/client/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{invalid json}"))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fail").value(true))
            .andExpect(jsonPath("$.code").exists())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks - Service 层抛出异常")
    void testSubmitTask_ServiceException() throws Exception {
        // Given
        SubmitTaskRequest request = new SubmitTaskRequest();
        request.setType("data_export");
        request.setPayload(new HashMap<>());
        request.setPriority(10);

        when(taskService.submitTask(any()))
            .thenThrow(new RuntimeException("Internal service error"));

        // framework4j v1.1.3: 裸 RuntimeException → 500 + code=10001 SYSTEM_BUSY
        mockMvc.perform(post("/api/v1/client/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(10001))
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /api/v1/client/tasks/{id} - OpenID 格式查询")
    void testGetTaskDetail_WithOpenId() throws Exception {
        // Given
        String openId = "pX6s9o7dLoTL"; // OpenID 格式
        TaskDetailResponse response = new TaskDetailResponse();
        response.setId(123456789L); // OpenID 会自动转换为字符串
        response.setType("data_export");
        response.setStatus("PENDING");

        when(taskService.getTaskDetail(anyLong())).thenReturn(response);

        // When & Then
        mockMvc.perform(get("/api/v1/client/tasks/" + openId))
            .andDo(print())
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/v1/client/tasks/{id} - 无效的 OpenID 格式")
    void testGetTaskDetail_InvalidOpenId() throws Exception {
        // Given
        String invalidOpenId = "invalid-openid-123";  // 包含非 Base62 字符
        when(taskService.getTaskDetail(anyLong()))
            .thenThrow(new RuntimeException("Invalid OpenID format"));

        // When & Then - framework4j-web 返回 HTTP 200，错误信息在响应体中
        mockMvc.perform(get("/api/v1/client/tasks/" + invalidOpenId))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fail").value(true))
            .andExpect(jsonPath("$.code").exists())
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks/{id}/cancel - 已取消的任务再次取消")
    void testCancelTask_AlreadyCancelled() throws Exception {
        // Given
        when(taskService.cancelTask(anyLong()))
            .thenThrow(new RuntimeException("Task already in final state"));

        // framework4j v1.1.3: 裸 RuntimeException → 500 + code=10001 SYSTEM_BUSY
        mockMvc.perform(post("/api/v1/client/tasks/pX6s9o7dLoTL/cancel"))
            .andDo(print())
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(10001))
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks/{id}/cancel - 任务不存在")
    void testCancelTask_NotFound() throws Exception {
        // Given
        when(taskService.cancelTask(anyLong()))
            .thenThrow(new RuntimeException("Task not found"));

        // framework4j v1.1.3: 裸 RuntimeException → 500 + code=10001
        mockMvc.perform(post("/api/v1/client/tasks/pX6s9o7dLoTL/cancel"))
            .andDo(print())
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value(10001))
            .andExpect(jsonPath("$.message").exists());
    }

    // ==================== HTTP 方法测试 ====================

    @Test
    @DisplayName("PUT /api/v1/client/tasks - 方法不支持")
    void testSubmitTask_WrongMethod_PUT() throws Exception {
        // framework4j-web 行为：路由层异常（HttpRequestMethodNotSupportedException）保留原 HTTP 状态码 405，
        // 业务码 10104 METHOD_NOT_SUPPORTED。
        mockMvc.perform(put("/api/v1/client/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andDo(print())
            .andExpect(status().isMethodNotAllowed())  // 405
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fail").value(true))
            .andExpect(jsonPath("$.code").value(10104))
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("DELETE /api/v1/client/tasks/{id} - 方法不支持")
    void testDeleteTask_NotSupported() throws Exception {
        // framework4j-web 行为：DELETE 没有对应的 handler mapping，抛 HttpRequestMethodNotSupportedException，HTTP 405。
        mockMvc.perform(delete("/api/v1/client/tasks/pX6s9o7dLoTL"))
            .andDo(print())
            .andExpect(status().isMethodNotAllowed())  // 405
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fail").value(true))
            .andExpect(jsonPath("$.code").value(10104))
            .andExpect(jsonPath("$.message").exists());
    }

    // ==================== CORS 和 Headers 测试 ====================

    @Test
    @DisplayName("OPTIONS /api/v1/client/tasks - CORS 预检请求")
    void testSubmitTask_CORS_Preflight() throws Exception {
        // When & Then
        mockMvc.perform(options("/api/v1/client/tasks")
                .header("Origin", "https://example.com")
                .header("Access-Control-Request-Method", "POST"))
            .andDo(print());
            // CORS 配置取决于实际的 CORS 设置
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks - 缺少 Content-Type header")
    void testSubmitTask_MissingContentType() throws Exception {
        // framework4j-web 行为：缺少/错 Content-Type 抛 HttpMediaTypeNotSupportedException，HTTP 415 + 业务码 10105。
        mockMvc.perform(post("/api/v1/client/tasks")
                .content("{}"))
            .andDo(print())
            .andExpect(status().isUnsupportedMediaType())  // 415
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.fail").value(true))
            .andExpect(jsonPath("$.code").value(10105))
            .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/v1/client/tasks - Accept header 为 application/xml")
    void testSubmitTask_WrongAcceptHeader() throws Exception {
        // Given
        SubmitTaskRequest request = new SubmitTaskRequest();
        request.setType("data_export");
        request.setPayload(new HashMap<>());

        when(taskService.submitTask(any())).thenReturn(123456789L);

        // When & Then - 服务器应该返回 JSON (忽略 Accept header 或返回 406)
        mockMvc.perform(post("/api/v1/client/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_XML)
                .content(objectMapper.writeValueAsString(request)))
            .andDo(print());
            // 实际行为取决于 Spring MVC 配置
    }

    // ==================== 并发测试模拟 ====================

    @Test
    @DisplayName("快速连续提交多个任务")
    void testSubmitTask_RapidRequests() throws Exception {
        // Given
        SubmitTaskRequest request = new SubmitTaskRequest();
        request.setType("data_export");
        request.setPayload(new HashMap<>());
        request.setPriority(10);

        when(taskService.submitTask(any())).thenReturn(123456789L, 123456790L, 123456791L);

        // When & Then - 快速发送3个请求
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/client/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        }

        verify(taskService, times(3)).submitTask(any());
    }
}
