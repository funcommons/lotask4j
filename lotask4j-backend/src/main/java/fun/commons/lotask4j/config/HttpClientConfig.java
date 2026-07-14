package fun.commons.lotask4j.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * HTTP 客户端配置
 *
 * 集中管理 RestTemplate，避免各处 new RestTemplate()
 * 用于 Web Embed 回调验证、Webhook 等出站 HTTP 调用
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Configuration
public class HttpClientConfig {

    @Value("${app.web-embed.callback-timeout-seconds:3}")
    private int callbackTimeoutSeconds;

    @Value("${app.webhook.timeout-seconds:30}")
    private int webhookTimeoutSeconds;

    /**
     * Webhook 发送专用 RestTemplate (长超时)
     * 任务完成后异步回调业务系统
     */
    @Bean("webhookRestTemplate")
    public RestTemplate webhookRestTemplate() {
        return createRestTemplate(webhookTimeoutSeconds);
    }

    /**
     * 回调验证专用 RestTemplate（短超时）
     */
    @Bean("callbackRestTemplate")
    public RestTemplate callbackRestTemplate() {
        return createRestTemplate(callbackTimeoutSeconds);
    }

    private RestTemplate createRestTemplate(int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());

        RestTemplate restTemplate = new RestTemplate(factory);
        // 关闭默认的 4xx/5xx 错误处理，由调用方自行判断
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) {
                return false;
            }
        });
        return restTemplate;
    }
}
