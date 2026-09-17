package fun.commons.lotask4j.service;

import fun.commons.framework4j.id.generator.SnowflakeDistributor;
import fun.commons.framework4j.signature.util.SignatureUtil;
import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.entity.AstsOutbox;
import fun.commons.lotask4j.entity.AstsTenant;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.mapper.AstsOutboxMapper;
import fun.commons.lotask4j.mapper.AstsTenantMapper;
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
 * signCallback (R4): 有租户归属 → 三签名头; 无租户/无密钥 → 不签名降级投递
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Webhook 服务测试 (outbox)")
class WebhookServiceTest {

    @Mock private AstsOutboxMapper outboxMapper;
    @Mock private SnowflakeDistributor snowflakeDistributor;
    @Mock private AstTaskMapper taskMapper;
    @Mock private AstsTenantMapper tenantMapper;
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

    @Nested
    @DisplayName("signCallback (R4 防伪造)")
    class SignCallback {

        private HttpEntity<String> exchange_captor;

        private AstsOutbox event() {
            AstsOutbox e = new AstsOutbox();
            e.setId(777L);
            e.setAggregateId(100001L);
            e.setCallbackUrl("https://example.com/cb");
            e.setPayload("{\"event\":\"TASK_FINISHED\"}");
            return e;
        }

        private HttpEntity<String> deliverAndCapture() {
            ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
            when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), captor.capture(), eq(String.class)))
                    .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));
            webhookService.deliver(event());
            return captor.getValue();
        }

        @Test
        @DisplayName("任务不存在 → 不签名, 仍投递")
        void taskMissing_unsigned() {
            when(taskMapper.selectById(100001L)).thenReturn(null);
            HttpEntity<String> sent = deliverAndCapture();
            assertFalse(sent.getHeaders().containsKey("X-ASTS-Signature"));
        }

        @Test
        @DisplayName("任务无租户归属 (平台/存量) → 不签名")
        void noTenant_unsigned() {
            AstTask t = new AstTask();
            t.setId(100001L);
            when(taskMapper.selectById(100001L)).thenReturn(t);
            HttpEntity<String> sent = deliverAndCapture();
            assertFalse(sent.getHeaders().containsKey("X-ASTS-Signature"));
        }

        @Test
        @DisplayName("租户不存在 → 不签名")
        void tenantMissing_unsigned() {
            AstTask t = new AstTask();
            t.setId(100001L);
            t.setTenantId(9L);
            when(taskMapper.selectById(100001L)).thenReturn(t);
            when(tenantMapper.selectById(9L)).thenReturn(null);
            HttpEntity<String> sent = deliverAndCapture();
            assertFalse(sent.getHeaders().containsKey("X-ASTS-Signature"));
        }

        @Test
        @DisplayName("租户密钥为 null → 不签名")
        void tenantSecretNull_unsigned() {
            AstTask t = new AstTask();
            t.setId(100001L);
            t.setTenantId(9L);
            AstsTenant tenant = new AstsTenant();
            tenant.setTenantSecret(null);
            when(taskMapper.selectById(100001L)).thenReturn(t);
            when(tenantMapper.selectById(9L)).thenReturn(tenant);
            HttpEntity<String> sent = deliverAndCapture();
            assertFalse(sent.getHeaders().containsKey("X-ASTS-Signature"));
        }

        @Test
        @DisplayName("有租户密钥 → 三签名头, 签名可用密钥复算验证")
        void signed_headers() {
            AstTask t = new AstTask();
            t.setId(100001L);
            t.setTenantId(9L);
            AstsTenant tenant = new AstsTenant();
            tenant.setTenantSecret("tenant-plain-secret");
            when(taskMapper.selectById(100001L)).thenReturn(t);
            when(tenantMapper.selectById(9L)).thenReturn(tenant);

            HttpEntity<String> sent = deliverAndCapture();

            String sig = sent.getHeaders().getFirst("X-ASTS-Signature");
            String ts = sent.getHeaders().getFirst("X-ASTS-Timestamp");
            assertEquals("777", sent.getHeaders().getFirst("X-ASTS-Event-Id"));
            assertNotNull(sig);
            assertNotNull(ts);
            assertEquals(sig, SignatureUtil.sign("tenant-plain-secret", ts + "\n" + event().getPayload()));
        }

        @Test
        @DisplayName("签名过程异常 (selectById 抛) → 降级无签名, 投递不阻断")
        void signingThrows_degradesToUnsigned() {
            when(taskMapper.selectById(100001L)).thenThrow(new RuntimeException("decrypt fail"));
            // 不外抛即通过 (签名异常被吞, 投递继续)
            assertDoesNotThrow(() -> deliverAndCapture());
        }
    }
}
