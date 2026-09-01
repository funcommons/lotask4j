package fun.commons.lotask4j.config;

import fun.commons.framework4j.web.cache.CachedBodyRequestWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 签名 body 缓存 Filter
 *
 * framework4j-signature 的 SignatureService.computeBodyMd5 从
 * ContentCachingRequestWrapper 取 body 计算 MD5, 但 wrapper 需要调用方
 * (应用)在拦截器之前完成包装与 cacheBody() — 否则 bodyMd5 恒为
 * md5("")(签名不覆盖 body, 防篡改失效)。
 *
 * 本 Filter 对写请求 (POST/PUT/PATCH/DELETE) 用 framework4j-web 的
 * CachedBodyRequestWrapper 包装并立即 cacheBody, 使签名契约固定为
 * md5(body)(与 frontend/src/utils/signature.ts 一致)。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class SignatureBodyCachingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String method = request.getMethod();
        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            CachedBodyRequestWrapper wrapper = new CachedBodyRequestWrapper(request);
            wrapper.cacheBody();
            filterChain.doFilter(wrapper, response);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
