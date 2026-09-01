package fun.commons.lotask4j.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * API 写请求 body 可重放缓存 Filter
 *
 * 背景 (framework4j 实测行为, 2026-09):
 * 1. framework4j-signature 的 SignatureService 从 ContentCachingRequestWrapper 的
 *    getContentAsByteArray() 取 body 计算 MD5 — 需要 filter 提前填充 CCRW 缓存,
 *    否则签名契约退化为 md5("")(不覆盖 body, 防篡改失效)。
 * 2. framework4j-web 自带的 CachedBodyRequestWrapper#getInputStream() 回放的
 *    cachedBody 字段从未被赋值 (恒为 null → 空流), 消息转换器的 @RequestBody
 *    反序列化因此拿到空 body。
 *
 * 本 Filter 的 ReusableBodyRequestWrapper 两者兼修:
 * - 构造时读一遍原始流 (填充父类 CCRW 缓存 → 签名 md5(body) 可用);
 * - 覆写 getInputStream()/getReader() 回放 body 副本 (converter 可重复读)。
 *
 * 圈定 /api/ 下非 GET 请求 (统一修复, 与签名 path-patterns 解耦)。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class SignatureBodyCachingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!"GET".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().startsWith("/api/")) {
            filterChain.doFilter(new ReusableBodyRequestWrapper(request), response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /**
     * body 可重放 wrapper: 继承 ContentCachingRequestWrapper (签名模块按该类型识别),
     * 构造时读原始流填父类缓存, 再覆写流读取为本地副本回放。
     */
    static class ReusableBodyRequestWrapper extends ContentCachingRequestWrapper {

        private final byte[] body;

        ReusableBodyRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            // 读一遍父类代理流: 内容进 CCRW 缓存 (SignatureService#getContentAsByteArray 消费)
            this.body = super.getInputStream().readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int available() throws IOException {
                    return in.available();
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    // 同步读, 无需异步监听
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
