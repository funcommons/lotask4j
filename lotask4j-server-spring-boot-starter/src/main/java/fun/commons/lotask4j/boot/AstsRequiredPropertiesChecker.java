package fun.commons.lotask4j.boot;

import fun.commons.framework4j.api.ApiCode;
import fun.commons.framework4j.web.ApiException;
import fun.commons.lotask4j.properties.AstsServerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * ASTS starter 必填配置 fail-fast 校验器 (issue #4)。
 *
 * <p>防「配置键改名/漏配后静默失效」事故类 (MMagiX2 2026-09-17 联调复盘): 启动期聚合校验
 * 双命名空间必填项, 缺配直接拒绝启动, 不等运行期 NPE/拒连。
 *
 * <p>校验范围:
 * <ul>
 *   <li>framework4j.* — SDK 契约键 (宿主侧声明, 键名保持原样), 按 enabled 开关条件必填</li>
 *   <li>lotask4j.flyway.* — starter 自管迁移配置</li>
 *   <li>范围合法性 — reaper/outbox 间隔 ≥1000ms 等 (防 60ms 级单位事故复发)</li>
 * </ul>
 *
 * <p>默认密钥值 (TOKEN_SECRET/AES_KEY/PLATFORM_SECRET 占位默认) 默认仅 WARN —
 * 保证 dev 开箱可跑; {@code lotask4j.security.strict=true} 时升级为启动失败 (生产口径)。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
public class AstsRequiredPropertiesChecker implements InitializingBean {

    /** framework4j access-token secretKey 的 dev 占位默认值 */
    static final String DEFAULT_TOKEN_SECRET = "default-secret-key-change-in-production";
    /** framework4j.sensitive encryption-key 的 dev 占位默认值 */
    static final String DEFAULT_AES_KEY = "12345678901234567890123456789012";
    /** framework4j.tenant.platform client-secret 的 dev 占位默认值 */
    static final String DEFAULT_PLATFORM_SECRET = "lotask4j-platform-dev-secret";

    private final AstsServerProperties properties;
    private final Environment environment;

    public AstsRequiredPropertiesChecker(AstsServerProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> violations = new ArrayList<>();

        checkSpringApplicationName(violations);
        checkDatasource(violations);
        checkRedis(violations);
        checkAccessToken(violations);
        checkTenantPlatform(violations);
        checkSensitive(violations);
        checkFlyway(violations);
        checkRanges(violations);

        if (!violations.isEmpty()) {
            throw new ApiException(ApiCode.MIDDLEWARE_ERROR,
                    "lotask4j starter 配置校验失败 (" + violations.size() + " 项): "
                            + String.join("; ", violations));
        }
    }

    private void checkSpringApplicationName(List<String> violations) {
        // spring.application.name 是 framework4j ID 生成与 Token Key 的种子, 缺失即 ID 冲突
        String name = environment.getProperty("spring.application.name");
        if (!StringUtils.hasText(name)) {
            violations.add("spring.application.name 不能为空 (framework4j ID 生成/Token Key 依赖)");
        }
    }

    private void checkDatasource(List<String> violations) {
        if (!enabled("framework4j.datasource.enabled", true)) {
            return;
        }
        String url = environment.getProperty("framework4j.datasource.datasources.business.url");
        if (!StringUtils.hasText(url)) {
            violations.add("framework4j.datasource.datasources.business.url 不能为空 (业务库连接)");
        }
    }

    private void checkRedis(List<String> violations) {
        if (!enabled("framework4j.redis.enabled", true)) {
            return;
        }
        String host = environment.getProperty("framework4j.redis.datasources.default.host");
        if (!StringUtils.hasText(host)) {
            violations.add("framework4j.redis.datasources.default.host 不能为空 (限流/签名 nonce/access-token 依赖)");
        }
    }

    private void checkAccessToken(List<String> violations) {
        if (!enabled("framework4j.access-token.enabled", true)) {
            return;
        }
        String secretKey = environment.getProperty("framework4j.access-token.secretKey");
        if (!StringUtils.hasText(secretKey)) {
            violations.add("framework4j.access-token.secretKey 不能为空 (TOKEN_SECRET, ≥32 字符)");
        } else {
            warnOrViolation(violations, secretKey, DEFAULT_TOKEN_SECRET,
                    "framework4j.access-token.secretKey 仍在使用默认开发密钥, 生产环境必须通过 TOKEN_SECRET 注入");
        }
    }

    private void checkTenantPlatform(List<String> violations) {
        if (!enabled("framework4j.tenant.enabled", true) || !enabled("framework4j.tenant.auth.enabled", true)) {
            return;
        }
        String clientId = environment.getProperty("framework4j.tenant.platform.client-id");
        if (!StringUtils.hasText(clientId)) {
            violations.add("framework4j.tenant.platform.client-id 不能为空 (平台域 client_credentials)");
        }
        String clientSecret = environment.getProperty("framework4j.tenant.platform.client-secret");
        if (!StringUtils.hasText(clientSecret)) {
            violations.add("framework4j.tenant.platform.client-secret 不能为空 (PLATFORM_CLIENT_SECRET)");
        } else {
            warnOrViolation(violations, clientSecret, DEFAULT_PLATFORM_SECRET,
                    "framework4j.tenant.platform.client-secret 仍在使用默认开发密钥, 生产环境必须通过 PLATFORM_CLIENT_SECRET 注入");
        }
    }

    private void checkSensitive(List<String> violations) {
        if (!enabled("framework4j.sensitive.enabled", true)) {
            return;
        }
        String encryptionKey = environment.getProperty("framework4j.sensitive.encryption-key");
        if (!StringUtils.hasText(encryptionKey)) {
            violations.add("framework4j.sensitive.encryption-key 不能为空 (AES_KEY, 租户密钥 AES-256-GCM 落库)");
        } else {
            warnOrViolation(violations, encryptionKey, DEFAULT_AES_KEY,
                    "framework4j.sensitive.encryption-key 仍在使用默认开发密钥, 生产环境必须通过 AES_KEY 注入");
        }
    }

    private void checkFlyway(List<String> violations) {
        AstsServerProperties.Flyway flyway = properties.getFlyway();
        if (!flyway.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(flyway.getUrl())) {
            violations.add("lotask4j.flyway.url 不能为空 (lotask4j.flyway.enabled=true 时必填)");
        }
        if (!StringUtils.hasText(flyway.getUser())) {
            violations.add("lotask4j.flyway.user 不能为空 (lotask4j.flyway.enabled=true 时必填)");
        }
    }

    private void checkRanges(List<String> violations) {
        AstsServerProperties.Asts asts = properties.getAsts();
        if (asts.getReaperInterval() < 1000) {
            violations.add("lotask4j.asts.reaper-interval 单位为毫秒且必须 ≥1000 (历史教训: 曾按秒配 60 → 60ms/轮误触发)");
        }
        if (asts.getOutboxInterval() < 1000) {
            violations.add("lotask4j.asts.outbox-interval 单位为毫秒且必须 ≥1000");
        }
        AstsServerProperties.Async async = properties.getAsync();
        if (async.getCorePoolSize() < 1 || async.getCorePoolSize() > async.getMaxPoolSize()) {
            violations.add("lotask4j.async.core-pool-size 必须 ≥1 且 ≤ max-pool-size");
        }
        if (properties.getWebhook().getTimeoutSeconds() <= 0) {
            violations.add("lotask4j.webhook.timeout-seconds 必须 > 0");
        }
        if (properties.getSteps().getMaxSteps() <= 0) {
            violations.add("lotask4j.steps.max-steps 必须 > 0");
        }
        if (properties.getWebEmbed().getCookieExpireSeconds() <= 0) {
            violations.add("lotask4j.web-embed.cookie-expire-seconds 必须 > 0");
        }
        if (asts.getDefaultLeaseSeconds() <= 0) {
            violations.add("lotask4j.asts.default-lease-seconds 必须 > 0");
        }
    }

    /** 默认密钥处置: strict=true 记为启动失败项, 否则仅 WARN (dev 开箱可跑) */
    private void warnOrViolation(List<String> violations, String actual, String defaultValue, String message) {
        if (!defaultValue.equals(actual)) {
            return;
        }
        if (properties.getSecurity().isStrict()) {
            violations.add(message + " [lotask4j.security.strict=true]");
        } else {
            log.warn("[ lotask4j 安全告警 ] {} — 设 lotask4j.security.strict=true 可升级为拒绝启动", message);
        }
    }

    private boolean enabled(String key, boolean defaultValue) {
        return environment.getProperty(key, Boolean.class, defaultValue);
    }
}
