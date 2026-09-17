package fun.commons.lotask4j.service;

import fun.commons.lotask4j.service.impl.CallbackServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CallbackService 单元测试
 *
 * 覆盖 Web Embed 鉴权回调验证的所有路径:
 *   - HTTP 200 + code=0 → 通过
 *   - HTTP 200 + code != 0 → 抛 IllegalArgumentException
 *   - HTTP 200 + body null → 抛
 *   - HTTP 4xx/5xx → 抛
 *   - RestClientException → 抛 IllegalArgumentException (包装)
 *   - URL 已带 ? → 用 & 拼接 action=verify&accessKey=
 *   - URL 无 ? → 用 ? 拼接
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("回调验证服务测试")
class CallbackServiceTest {

    @Mock private RestTemplate restTemplate;

    @InjectMocks private CallbackServiceImpl callbackService;

    @Nested
    @DisplayName("verify")
    class Verify {

        @Test
        @DisplayName("HTTP 200 + code=0: 验证通过, 不抛异常")
        void success() {
            Map<String, Object> body = new HashMap<>();
            body.put("code", 0);
            body.put("msg", "ok");
            when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

            assertDoesNotThrow(() ->
                    callbackService.verify("https://example.com/cb", "ACCESS_KEY_123"));
        }

        @Test
        @DisplayName("HTTP 200 + code=1: 抛 IllegalArgumentException")
        void codeNonZero_Throws() {
            Map<String, Object> body = new HashMap<>();
            body.put("code", 1);
            body.put("msg", "invalid access key");
            when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> callbackService.verify("https://example.com/cb", "BAD_KEY"));
            assertTrue(ex.getMessage().contains("1"), "异常消息应包含实际 code 值");
        }

        @Test
        @DisplayName("code 为字符串 \"0\": 也算通过 (String.valueOf 兼容)")
        void codeStringZero_Passes() {
            Map<String, Object> body = new HashMap<>();
            body.put("code", "0");
            when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

            assertDoesNotThrow(() ->
                    callbackService.verify("https://example.com/cb", "KEY"));
        }

        @Test
        @DisplayName("code 缺失: 抛 IllegalArgumentException")
        void codeMissing_Throws() {
            Map<String, Object> body = new HashMap<>();
            body.put("msg", "ok");
            when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

            assertThrows(IllegalArgumentException.class,
                    () -> callbackService.verify("https://example.com/cb", "KEY"));
        }

        @Test
        @DisplayName("body 为 null: 抛 IllegalArgumentException")
        void bodyNull_Throws() {
            when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(null, HttpStatus.OK));

            assertThrows(IllegalArgumentException.class,
                    () -> callbackService.verify("https://example.com/cb", "KEY"));
        }

        @Test
        @DisplayName("HTTP 401: 抛 IllegalArgumentException (status 检查先于 body)")
        void http4xx_Throws() {
            Map<String, Object> body = new HashMap<>();
            body.put("code", 0);
            when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> callbackService.verify("https://example.com/cb", "KEY"));
            assertTrue(ex.getMessage().contains("401"));
        }

        @Test
        @DisplayName("HTTP 500: 抛 IllegalArgumentException")
        void http5xx_Throws() {
            when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR));

            assertThrows(IllegalArgumentException.class,
                    () -> callbackService.verify("https://example.com/cb", "KEY"));
        }

        @Test
        @DisplayName("RestClientException: 包装为 IllegalArgumentException")
        void restClientException_Wrapped() {
            when(restTemplate.getForEntity(anyString(), eq(Map.class)))
                    .thenThrow(new RestClientException("DNS resolve failed"));

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> callbackService.verify("https://example.com/cb", "KEY"));
            assertTrue(ex.getMessage().contains("DNS resolve failed"),
                    "原异常消息应保留");
        }
    }

    @Nested
    @DisplayName("URL 拼接")
    class UrlConstruction {

        @Test
        @DisplayName("URL 不含 ?: 用 ? 拼接")
        void urlWithoutQuery_UsesQuestionMark() {
            Map<String, Object> body = Map.of("code", 0);
            when(restTemplate.getForEntity(eq("https://example.com/cb?action=verify&accessKey=KEY"), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

            callbackService.verify("https://example.com/cb", "KEY");

            verify(restTemplate).getForEntity("https://example.com/cb?action=verify&accessKey=KEY", Map.class);
        }

        @Test
        @DisplayName("URL 已带 ?: 用 & 拼接")
        void urlWithQuery_UsesAmpersand() {
            Map<String, Object> body = Map.of("code", 0);
            when(restTemplate.getForEntity(eq("https://example.com/cb?tenant=acme&action=verify&accessKey=KEY"), eq(Map.class)))
                    .thenReturn(new ResponseEntity<>(body, HttpStatus.OK));

            callbackService.verify("https://example.com/cb?tenant=acme", "KEY");

            verify(restTemplate).getForEntity("https://example.com/cb?tenant=acme&action=verify&accessKey=KEY", Map.class);
        }
    }
}
