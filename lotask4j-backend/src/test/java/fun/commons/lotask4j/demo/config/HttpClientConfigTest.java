package fun.commons.lotask4j.demo.config;

import fun.commons.lotask4j.config.HttpClientConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HttpClientConfig 单元测试 — RestTemplate 错误处理契约 (4xx/5xx 不抛, 调用方自行判断)
 */
@DisplayName("HttpClientConfig 单元测试")
class HttpClientConfigTest {

    @Test
    @DisplayName("两个 RestTemplate bean 构造成功; 500 响应不视为错误")
    void beans_andErrorHandler() throws IOException {
        HttpClientConfig config = new HttpClientConfig();
        ReflectionTestUtils.setField(config, "callbackTimeoutSeconds", 3);
        ReflectionTestUtils.setField(config, "webhookTimeoutSeconds", 30);

        RestTemplate webhook = config.webhookRestTemplate();
        RestTemplate callback = config.callbackRestTemplate();

        assertThat(webhook).isNotNull();
        assertThat(callback).isNotNull();

        ClientHttpResponse resp500 = new MockClientHttpResponse("err".getBytes(), 500);
        assertThat(webhook.getErrorHandler().hasError(resp500))
                .as("4xx/5xx 不视为错误 (webhook 自行判断状态码)").isFalse();
        assertThat(callback.getErrorHandler().hasError(resp500)).isFalse();
    }
}
