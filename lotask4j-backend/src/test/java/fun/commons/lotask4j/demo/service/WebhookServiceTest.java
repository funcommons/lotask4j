package fun.commons.lotask4j.service;

import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.service.impl.WebhookServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WebhookService 单元测试
 *
 * 覆盖任务完成后回调客户端的关键路径:
 *   - 无 callbackUrl 跳过
 *   - HTTP 2xx → callbackStatus=1
 *   - HTTP 4xx/5xx → callbackStatus=2
 *   - RestTemplate 抛异常 → callbackStatus=2, 不向外传播
 *   - updateCallbackStatus 失败 (0 行 / 抛异常) 容错
 *   - Webhook 请求体内容正确 (event/type/status/result/timestamp)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Webhook 服务测试")
class WebhookServiceTest {

    @Mock private AstTaskMapper taskMapper;
    @Mock private RestTemplate restTemplate;

    @InjectMocks private WebhookServiceImpl webhookService;

    private AstTask task;

    @BeforeEach
    void setUp() {
        // WebhookServiceImpl 内部 new RestTemplate(), 用反射替换成 mock
        ReflectionTestUtils.setField(webhookService, "restTemplate", restTemplate);

        task = new AstTask();
        task.setId(100001L);
        task.setTaskTypeKey("data_export");
        task.setStatus("SUCCESS");
        task.setCallbackUrl("https://example.com/cb");
        Map<String, Object> result = Map.of("rows", 100);
        task.setResult(result);
    }

    @Nested
    @DisplayName("sendWebhookAsync")
    class SendWebhookAsync {

        @Test
        @DisplayName("callbackUrl 为 null: 直接跳过, 不调 mapper")
        void skipWhenCallbackUrlNull() {
            task.setCallbackUrl(null);
            webhookService.sendWebhookAsync(task);
            verifyNoInteractions(restTemplate, taskMapper);
        }

        @Test
        @DisplayName("callbackUrl 为空字符串: 直接跳过")
        void skipWhenCallbackUrlEmpty() {
            task.setCallbackUrl("");
            webhookService.sendWebhookAsync(task);
            verifyNoInteractions(restTemplate, taskMapper);
        }

        @Test
        @DisplayName("HTTP 200: 标记 callbackStatus=1 (成功)")
        void http2xx_MarksSuccess() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

            webhookService.sendWebhookAsync(task);

            verify(taskMapper).updateCallbackStatus(100001L, 1);
        }

        @Test
        @DisplayName("HTTP 4xx: 标记 callbackStatus=2 (失败)")
        void http4xx_MarksFailure() {
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("err", HttpStatus.NOT_FOUND));

            webhookService.sendWebhookAsync(task);

            verify(taskMapper).updateCallbackStatus(100001L, 2);
        }

        @Test
        @DisplayName("HTTP 5xx: 标记 callbackStatus=2")
        void http5xx_MarksFailure() {
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("err", HttpStatus.BAD_GATEWAY));

            webhookService.sendWebhookAsync(task);

            verify(taskMapper).updateCallbackStatus(100001L, 2);
        }

        @Test
        @DisplayName("RestClientException: 标记 callbackStatus=2, 不向外抛")
        void restClientException_MarksFailureNoPropagate() {
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenThrow(new RestClientException("Connection refused"));

            // 不应抛异常出去 (sendWebhookAsync 是 @Async, 抛了也没人接)
            assertDoesNotThrow(() -> webhookService.sendWebhookAsync(task));

            verify(taskMapper).updateCallbackStatus(100001L, 2);
        }

        @Test
        @DisplayName("任意 RuntimeException: 同样标记 callbackStatus=2, 不向外抛")
        void runtimeException_MarksFailureNoPropagate() {
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenThrow(new RuntimeException("SSL handshake failed"));

            assertDoesNotThrow(() -> webhookService.sendWebhookAsync(task));

            verify(taskMapper).updateCallbackStatus(100001L, 2);
        }

        @Test
        @DisplayName("updateCallbackStatus 影响 0 行 (任务已删): 仅警告, 不抛")
        void updateAffectsZeroRows_NoPropagate() {
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));
            when(taskMapper.updateCallbackStatus(100001L, 1)).thenReturn(0);

            assertDoesNotThrow(() -> webhookService.sendWebhookAsync(task));

            verify(taskMapper).updateCallbackStatus(100001L, 1);
        }

        @Test
        @DisplayName("updateCallbackStatus 抛异常: 捕获, 不向外抛")
        void updateThrows_NoPropagate() {
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));
            when(taskMapper.updateCallbackStatus(anyLong(), anyInt()))
                    .thenThrow(new RuntimeException("DB connection lost"));

            assertDoesNotThrow(() -> webhookService.sendWebhookAsync(task));
        }

        @Test
        @DisplayName("Webhook 请求体: 含 event=TASK_FINISHED + type + status + result + timestamp")
        void webhookBodyContainsRequiredFields() {
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

            webhookService.sendWebhookAsync(task);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).exchange(eq("https://example.com/cb"), eq(HttpMethod.POST), captor.capture(), eq(String.class));

            String body = captor.getValue().getBody();
            assertNotNull(body);
            // 关键字段都在
            assertTrue(body.contains("\"event\":\"TASK_FINISHED\""), "缺少 event 字段");
            assertTrue(body.contains("\"type\":\"data_export\""), "缺少 type 字段");
            assertTrue(body.contains("\"status\":\"SUCCESS\""), "缺少 status 字段");
            assertTrue(body.contains("\"task_id\":"), "缺少 task_id 字段");
            assertTrue(body.contains("\"timestamp\":"), "缺少 timestamp 字段");
            assertTrue(body.contains("\"rows\":100"), "result 应原样序列化");
        }
    }
}
