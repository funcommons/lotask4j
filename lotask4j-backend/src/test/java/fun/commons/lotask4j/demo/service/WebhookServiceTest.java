package fun.commons.lotask4j.service;

import fun.commons.framework4j.id.generator.SnowflakeDistributor;
import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.entity.AstsOutbox;
import fun.commons.lotask4j.mapper.AstsOutboxMapper;
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
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WebhookService 单元测试 (outbox 模式)
 *
 * enqueueFinished: 无 callbackUrl 跳过; 有则插 PENDING 行 (payload 快照含关键字段)
 * deliver: 2xx → true; 4xx/5xx → false; 异常 → false 不外抛
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Webhook 服务测试 (outbox)")
class WebhookServiceTest {

    @Mock private AstsOutboxMapper outboxMapper;
    @Mock private SnowflakeDistributor snowflakeDistributor;
    @Mock private RestTemplate restTemplate;

    @InjectMocks private WebhookServiceImpl webhookService;

    private AstTask task;

    @BeforeEach
    void setUp() {
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
    @DisplayName("enqueueFinished")
    class Enqueue {

        @Test
        @DisplayName("callbackUrl 为 null: 跳过, 不写 outbox")
        void skipWhenCallbackUrlNull() {
            task.setCallbackUrl(null);
            webhookService.enqueueFinished(task);
            verifyNoInteractions(outboxMapper);
        }

        @Test
        @DisplayName("callbackUrl 为空字符串: 跳过")
        void skipWhenCallbackUrlEmpty() {
            task.setCallbackUrl("");
            webhookService.enqueueFinished(task);
            verifyNoInteractions(outboxMapper);
        }

        @Test
        @DisplayName("有 callbackUrl: 插 PENDING 行, payload 快照含关键字段")
        void enqueuesPendingWithSnapshot() {
            when(snowflakeDistributor.nextId()).thenReturn(777L);

            webhookService.enqueueFinished(task);

            ArgumentCaptor<AstsOutbox> captor = ArgumentCaptor.forClass(AstsOutbox.class);
            verify(outboxMapper).insert(captor.capture());
            AstsOutbox event = captor.getValue();

            assertEquals(777L, event.getId());
            assertEquals("TASK", event.getAggregateType());
            assertEquals(100001L, event.getAggregateId());
            assertEquals("TASK_FINISHED", event.getEventType());
            assertEquals("https://example.com/cb", event.getCallbackUrl());
            assertEquals(AstsOutbox.STATUS_PENDING, event.getStatus());
            assertEquals(0, event.getAttemptCount());
            assertEquals(AstsOutbox.MAX_ATTEMPTS, event.getMaxAttempts());

            String payload = event.getPayload();
            assertTrue(payload.contains("\"event\":\"TASK_FINISHED\""), "缺少 event 字段");
            assertTrue(payload.contains("\"type\":\"data_export\""), "缺少 type 字段");
            assertTrue(payload.contains("\"status\":\"SUCCESS\""), "缺少 status 字段");
            assertTrue(payload.contains("\"task_id\":"), "缺少 task_id 字段");
            assertTrue(payload.contains("\"timestamp\":"), "缺少 timestamp 字段");
            assertTrue(payload.contains("\"rows\":100"), "result 应原样序列化");
        }
    }

    @Nested
    @DisplayName("deliver")
    class Deliver {

        private AstsOutbox event() {
            AstsOutbox e = new AstsOutbox();
            e.setId(777L);
            e.setAggregateId(100001L);
            e.setCallbackUrl("https://example.com/cb");
            e.setPayload("{\"event\":\"TASK_FINISHED\"}");
            return e;
        }

        @Test
        @DisplayName("HTTP 2xx → true")
        void http2xx_true() {
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));
            assertTrue(webhookService.deliver(event()));
        }

        @Test
        @DisplayName("HTTP 5xx → false")
        void http5xx_false() {
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("err", HttpStatus.BAD_GATEWAY));
            assertFalse(webhookService.deliver(event()));
        }

        @Test
        @DisplayName("异常 → false, 不向外抛")
        void exception_falseNoThrow() {
            when(restTemplate.exchange(anyString(), any(), any(), eq(String.class)))
                    .thenThrow(new RuntimeException("Connection refused"));
            assertDoesNotThrow(() -> assertFalse(webhookService.deliver(event())));
        }
    }
}
