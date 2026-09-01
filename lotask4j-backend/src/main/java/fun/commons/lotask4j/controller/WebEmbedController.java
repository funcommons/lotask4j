package fun.commons.lotask4j.controller;

import fun.commons.framework4j.accesstoken.core.AccessTokenGenerator;
import fun.commons.lotask4j.entity.WebEmbedConfig;
import fun.commons.lotask4j.service.WebEmbedService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;

/**
 * Web Embed Controller
 *
 * 业务方通过 URL 访问 /web-embed/{componentType}?accessKey=xxx
 * 后端鉴权/开放模式处理后，写入 Cookie 并重定向到前端 index.html
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@Controller
@RequestMapping("/web-embed")
public class WebEmbedController {

    private static final String COOKIE_NAME = "ASTS_USER_ID";
    /** embed 会话 token cookie (前端 axios 读取后以 Bearer 调 client GET; 非 httpOnly — iframe 场景) */
    private static final String TOKEN_COOKIE_NAME = "ASTS_EMBED_TOKEN";

    @Autowired
    private WebEmbedService webEmbedService;

    @Autowired
    private AccessTokenGenerator accessTokenGenerator;

    @Value("${app.web-embed.front-base-url:/web-embed/index.html}")
    private String frontBaseUrl;

    @Value("${app.web-embed.cookie-expire-seconds:7200}")
    private int cookieExpireSeconds;

    /**
     * 处理 Web Embed 访问请求
     *
     * 开放模式：无需 accessKey，使用默认 userId
     * 鉴权模式：需要 accessKey + 业务系统回调验证
     *
     * @param type 组件类型（task-list / task-detail / task-card）
     * @param accessKey 访问密钥（鉴权模式必填）
     * @param taskId 任务 ID（task-detail / task-card 必填）
     * @param response HTTP 响应（用于写 Cookie）
     * @return 重定向到前端 Vue 应用的 index.html
     */
    @GetMapping("/{type:[a-z-]+}")
    public RedirectView handleComponent(
            @PathVariable("type") String componentType,
            @RequestParam(name = "accessKey", required = false) String accessKey,
            @RequestParam(name = "taskId", required = false) String taskId,
            HttpServletResponse response) {

        log.info("[Web Embed] 访问: componentType={}, accessKey={}, taskId={}",
                componentType, accessKey, taskId);

        // 1. 验证组件类型
        if (!webEmbedService.isValidComponentType(componentType)) {
            log.warn("[Web Embed] 非法的 componentType: {}, 返回 null", componentType);
            return null;
        }

        // 2. 校验 accessKey 与组件的匹配
        webEmbedService.checkComponentAccess(accessKey, componentType);

        // 3. 鉴权 / 开放模式处理 (返回配置; null = 无 accessKey 开放模式)
        WebEmbedConfig config = webEmbedService.handleAccess(accessKey);
        String userId = config != null ? config.getUserId() : openDefaultUserId;

        // 4. 写入 Cookie — 租户短期 token (F 阶段): accessKey 验证通过后按配置归属租户
        //    签发 TENANT 型 token, embed 前端 axios 读 cookie 加 Bearer 调 client GET;
        //    userId cookie 保留 (审计/展示)
        writeUserIdCookie(response, userId);
        if (config != null && config.getTenantId() != null) {
            writeEmbedTokenCookie(response, config.getTenantId());
        }

        // 5. 重定向到前端 Vue 应用（Vite + Vue3 history 模式）
        String redirectUrl = buildRedirectUrl(componentType, taskId);
        log.debug("[Web Embed] 重定向: {}", redirectUrl);

        return new RedirectView(redirectUrl);
    }

    /** 开放模式默认 userId (与 WebEmbedService open-default-user-id 同源) */
    @Value("${app.web-embed.open-default-user-id:guest}")
    private String openDefaultUserId;

    private void writeEmbedTokenCookie(HttpServletResponse response, Long tenantId) {
        String token = accessTokenGenerator.generateToken("TENANT",
                java.util.Map.of("tenant_id", tenantId));
        ResponseCookie cookie = ResponseCookie.from(TOKEN_COOKIE_NAME, token)
                .httpOnly(false)          // embed 前端 axios 需读取
                .secure(true)
                .path("/")
                .maxAge(Duration.ofSeconds(cookieExpireSeconds))
                .sameSite("Strict")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        log.debug("[Web Embed] 已签发租户短期 token: tenantId={}", tenantId);
    }

    /**
     * 写入用户 ID Cookie
     */
    private void writeUserIdCookie(HttpServletResponse response, String userId) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, userId)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ofSeconds(cookieExpireSeconds))
                .sameSite("Strict")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * 构建前端重定向 URL
     *
     * Vite + Vue3 history 模式：重定向到 /web-embed/index.html，参数通过 query 传递
     * 前端 Vue Router 解析 query.component 和 query.taskId
     */
    private String buildRedirectUrl(String componentType, String taskId) {
        StringBuilder url = new StringBuilder("/web-embed/index.html");
        url.append("?component=").append(componentType);

        if (taskId != null && !taskId.isEmpty()) {
            url.append("&taskId=").append(taskId);
        }

        return url.toString();
    }
}
