package fun.commons.lotask4j.demo.config;

import fun.commons.lotask4j.config.SignatureBodyCachingFilter;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * SignatureBodyCachingFilter 单元测试 — body 可重放缓存 (签名 md5 + @RequestBody 可重复读)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SignatureBodyCachingFilter 单元测试")
class SignatureBodyCachingFilterTest {

    private final SignatureBodyCachingFilter filter = new SignatureBodyCachingFilter();

    @Mock
    private FilterChain chain;

    @Test
    @DisplayName("/api/ POST → body 被缓存, getInputStream/getReader 可重复读")
    void postApiBodyCachedAndReplayable() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/client/tasks/submit");
        req.setContent("{\"x\":1}".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse resp = new MockHttpServletResponse();
        AtomicReference<Object> wrapped = new AtomicReference<>();

        org.mockito.Mockito.doAnswer(inv -> {
            wrapped.set(inv.getArgument(0));
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(req, resp, chain);

        assertThat(wrapped.get())
                .isInstanceOf(org.springframework.web.util.ContentCachingRequestWrapper.class);
        jakarta.servlet.http.HttpServletRequest httpReq = (jakarta.servlet.http.HttpServletRequest) wrapped.get();

        // 第一次读 (ContentCachingRequestWrapper 父类缓存已在构造时填充)
        byte[] first = httpReq.getInputStream().readAllBytes();
        assertThat(new String(first, StandardCharsets.UTF_8)).isEqualTo("{\"x\":1}");
        // 第二次读 (覆写的回放流)
        byte[] second = httpReq.getInputStream().readAllBytes();
        assertThat(new String(second, StandardCharsets.UTF_8)).isEqualTo("{\"x\":1}");
        // ServletInputStream 辅助方法
        jakarta.servlet.ServletInputStream replay = httpReq.getInputStream();
        assertThat(replay.isFinished()).isFalse();
        assertThat(replay.isReady()).isTrue();
        assertDoesNotThrow(() -> replay.setReadListener(null));
        // 读尽后 isFinished 翻真
        replay.readAllBytes();
        assertThat(replay.isFinished()).as("流读尽后 isFinished 应为 true").isTrue();
        // getReader 路径
        try (BufferedReader reader = httpReq.getReader()) {
            assertThat(reader.readLine()).isEqualTo("{\"x\":1}");
        }
        // ContentCachingRequestWrapper 缓存 (签名 md5 消费)
        org.springframework.web.util.ContentCachingRequestWrapper ccw =
                (org.springframework.web.util.ContentCachingRequestWrapper) httpReq;
        assertThat(new String(ccw.getContentAsByteArray(), StandardCharsets.UTF_8)).isEqualTo("{\"x\":1}");
    }

    @Test
    @DisplayName("GET 请求 → 原样透传不包装")
    void getPassesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/client/tasks");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    @Test
    @DisplayName("非 /api 路径 POST → 原样透传")
    void nonApiPostPassesThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/web-embed/task-list");
        req.setContent("a".getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse resp = new MockHttpServletResponse();

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }
}
