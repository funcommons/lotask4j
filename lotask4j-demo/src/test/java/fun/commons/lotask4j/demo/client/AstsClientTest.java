package fun.commons.lotask4j.demo.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AstsClient 单元测试 — 接入示例的契约回归 (防示例腐化)
 *
 * 服务端真实契约 (由 scripts/smoke.sh 对真库验证):
 *   submit  → data.id (OpenID 串)
 *   detail  → data.id/status/currentStep..., GET 需 Bearer
 *   token   → data.access_token, 同客户端缓存复用
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AstsClient 接入示例测试")
class AstsClientTest {

    @Mock
    private ExchangeFunction exchange;

    private AstsClient client;
    private final List<ClientRequest> requests = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        requests.clear();
        WebClient.Builder builder = WebClient.builder();
        when(exchange.exchange(any())).thenAnswer(inv -> {
            ClientRequest req = inv.getArgument(0);
            requests.add(req);
            return Mono.just(responseFor(req));
        });
        // WebClient.Builder 换入 mock exchange
        org.springframework.test.util.ReflectionTestUtils.setField(
                builder, "exchangeFunction", exchange);
        client = new AstsClient(builder);
        ReflectionTestUtils.setField(client, "serverUrl", "http://asts.test");
        ReflectionTestUtils.setField(client, "accessKey", "tenant-a");
        ReflectionTestUtils.setField(client, "secret", "tenant-secret");
    }

    /** 按 path 返回不同 envelope */
    private ClientResponse responseFor(ClientRequest req) {
        String path = req.url().getPath();
        String body;
        if (path.endsWith("/auth/token")) {
            body = "{\"code\":0,\"data\":{\"access_token\":\"jwt-token\",\"expires_in\":28800}}";
        } else if (path.endsWith("/submit")) {
            body = "{\"code\":0,\"data\":{\"id\":\"OpenId123\"}}";
        } else if (path.contains("/cancel")) {
            body = "{\"code\":0,\"data\":null}";
        } else {
            body = "{\"code\":0,\"data\":{\"id\":\"OpenId123\",\"type\":\"data_export\","
                    + "\"status\":\"SUCCESS\",\"progress\":100,\"currentStep\":\"upload\","
                    + "\"stepsDetail\":[],\"result\":{\"fileUrl\":\"oss://x\"}}}";
        }
        return ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body(body)
                .build();
    }

    private ClientRequest requestTo(String pathSuffix) {
        return requests.stream()
                .filter(r -> r.url().getPath().endsWith(pathSuffix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未发出到 " + pathSuffix + " 的请求"));
    }

    @Test
    @DisplayName("submitTask: 先换 Token, 签名四头 + Bearer, 解析 data.id")
    void submitTask_contract() {
        AstsClient.TaskResponse resp = client.submitTask("data_export", Map.of("q", 1), 10).block();

        assertThat(resp).isNotNull();
        assertThat(resp.taskId()).as("服务端字段为 data.id").isEqualTo("OpenId123");

        ClientRequest submit = requestTo("/submit");
        assertThat(submit.headers().getFirst("X-Access-Key")).isEqualTo("tenant-a");
        assertThat(submit.headers().getFirst("X-Timestamp")).isNotBlank();
        assertThat(submit.headers().getFirst("X-Nonce")).isNotBlank();
        assertThat(submit.headers().getFirst("X-Signature")).isNotBlank();
        assertThat(submit.headers().getFirst("Authorization")).isEqualTo("Bearer jwt-token");
    }

    @Test
    @DisplayName("Token 缓存: 多次调用只换一次 Token")
    void tokenCached() {
        client.submitTask("t", Map.of(), 0).block();
        client.getTaskDetail("OpenId123").block();

        long tokenCalls = requests.stream()
                .filter(r -> r.url().getPath().endsWith("/auth/token")).count();
        assertThat(tokenCalls).as("Bearer 应缓存复用").isEqualTo(1);
    }

    @Test
    @DisplayName("getTaskDetail: 带 Bearer, 解析 data.id/status/currentStep")
    void getTaskDetail_contract() {
        AstsClient.TaskDetail detail = client.getTaskDetail("OpenId123").block();

        assertThat(detail).isNotNull();
        assertThat(detail.taskId()).isEqualTo("OpenId123");
        assertThat(detail.status()).isEqualTo("SUCCESS");
        assertThat(detail.progress()).isEqualTo(100);
        assertThat(detail.currentStep()).isEqualTo("upload");

        ClientRequest req = requestTo("/tasks/OpenId123");
        assertThat(req.headers().getFirst("Authorization")).isEqualTo("Bearer jwt-token");
    }

    @Test
    @DisplayName("cancelTask: 对含任务 ID 的 path 签名")
    void cancelTask_signaturePath() {
        client.cancelTask("OpenId123").block();

        ClientRequest cancel = requestTo("/cancel");
        assertThat(cancel.url().getPath()).isEqualTo("/api/v1/client/tasks/OpenId123/cancel");
        assertThat(cancel.headers().getFirst("X-Signature")).isNotBlank();
        assertThat(cancel.headers().getFirst("Authorization")).isEqualTo("Bearer jwt-token");
    }
}
