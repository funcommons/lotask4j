package fun.commons.lotask4j.boot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AstsRequiredPropertiesChecker} fail-fast 矩阵: 双命名空间必填项/范围项/strict 密钥。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
class AstsRequiredPropertiesCheckerTest {

    private static final String DEFAULT_SECRET = "0123456789abcdef0123456789abcdef";

    /** 关闭可关闭域 (flyway/tenant/sensitive) 的最小属性基线 */
    private WebApplicationContextRunner baseRunner(String... extra) {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AstsServerAutoConfiguration.class))
                .withPropertyValues(
                        "spring.application.name=lotask4j-runner-test",
                        "framework4j.datasource.datasources.business.url=jdbc:postgresql://localhost:5432/probe",
                        "framework4j.redis.datasources.default.host=localhost",
                        "framework4j.access-token.secretKey=" + DEFAULT_SECRET,
                        "framework4j.tenant.enabled=false",
                        "framework4j.sensitive.enabled=false",
                        "lotask4j.flyway.enabled=false")
                .withPropertyValues(extra);
    }

    private String failureMessage(WebApplicationContextRunner runner) {
        StringBuilder message = new StringBuilder();
        runner.run(context -> {
            assertThat(context).hasFailed();
            message.append(NestedExceptionUtils.getMostSpecificCause(context.getStartupFailure()).getMessage());
        });
        return message.toString();
    }

    @Test
    void missingApplicationNameFailsFast() {
        assertThat(failureMessage(baseRunner("spring.application.name=")))
                .contains("spring.application.name");
    }

    @Test
    void missingDatasourceUrlFailsFast() {
        assertThat(failureMessage(baseRunner("framework4j.datasource.datasources.business.url=")))
                .contains("framework4j.datasource.datasources.business.url");
    }

    @Test
    void redisDisabledSkipsHostRequirement() {
        baseRunner("framework4j.redis.enabled=false").run(context ->
                assertThat(context).hasNotFailed());
    }

    @Test
    void missingRedisHostFailsFast() {
        assertThat(failureMessage(baseRunner("framework4j.redis.datasources.default.host=")))
                .contains("framework4j.redis.datasources.default.host");
    }

    @Test
    void disabledDomainsSkipTheirChecks() {
        baseRunner(
                "framework4j.datasource.enabled=false",
                "framework4j.access-token.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void missingSensitiveKeyFailsFast() {
        assertThat(failureMessage(baseRunner("framework4j.sensitive.enabled=true")))
                .contains("framework4j.sensitive.encryption-key");
    }

    @Test
    void missingAccessTokenSecretFailsFast() {
        assertThat(failureMessage(baseRunner("framework4j.access-token.secretKey=")))
                .contains("framework4j.access-token.secretKey");
    }

    @Test
    void tenantPlatformSecretRequiredWhenTenantAuthEnabled() {
        assertThat(failureMessage(baseRunner("framework4j.tenant.enabled=true")))
                .contains("framework4j.tenant.platform.client-id")
                .contains("framework4j.tenant.platform.client-secret");
    }

    @Test
    void defaultSecretWarnOnlyByDefault() {
        // framework4j 默认 dev 密钥占位 (与 main 侧常量一致的样例值) — 非严格模式放行 (仅 WARN)
        baseRunner(
                "framework4j.access-token.secretKey=default-secret-key-change-in-production",
                "framework4j.tenant.enabled=true",
                "framework4j.tenant.platform.client-id=PLATFORM",
                "framework4j.tenant.platform.client-secret=lotask4j-platform-dev-secret",
                "framework4j.sensitive.enabled=true",
                "framework4j.sensitive.encryption-key=12345678901234567890123456789012")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void strictModeRejectsDefaultSecrets() {
        assertThat(failureMessage(baseRunner(
                "framework4j.access-token.secretKey=default-secret-key-change-in-production",
                "lotask4j.security.strict=true")))
                .contains("lotask4j.security.strict=true");
    }

    @Test
    void flywayUrlRequiredWhenEnabled() {
        assertThat(failureMessage(baseRunner("lotask4j.flyway.enabled=true")))
                .contains("lotask4j.flyway.url")
                .contains("lotask4j.flyway.user");
    }

    @Test
    void reaperIntervalBelowOneSecondRejected() {
        assertThat(failureMessage(baseRunner("lotask4j.asts.reaper-interval=60")))
                .contains("lotask4j.asts.reaper-interval");
    }

    @Test
    void asyncPoolMismatchRejected() {
        assertThat(failureMessage(baseRunner("lotask4j.async.core-pool-size=99")))
                .contains("lotask4j.async.core-pool-size");
    }

    @Test
    void rangeViolationsAggregatedInOneFailure() {
        assertThat(failureMessage(baseRunner(
                "lotask4j.asts.outbox-interval=60",
                "lotask4j.async.core-pool-size=0",
                "lotask4j.webhook.timeout-seconds=0",
                "lotask4j.steps.max-steps=0",
                "lotask4j.web-embed.cookie-expire-seconds=0",
                "lotask4j.asts.default-lease-seconds=0")))
                .contains("lotask4j.asts.outbox-interval")
                .contains("lotask4j.async.core-pool-size")
                .contains("lotask4j.webhook.timeout-seconds")
                .contains("lotask4j.steps.max-steps")
                .contains("lotask4j.web-embed.cookie-expire-seconds")
                .contains("lotask4j.asts.default-lease-seconds");
    }

    @Test
    void violationsAreAggregatedInOneFailure() {
        assertThat(failureMessage(baseRunner(
                "spring.application.name=",
                "framework4j.datasource.datasources.business.url=",
                "framework4j.redis.datasources.default.host=",
                "framework4j.access-token.secretKey=")))
                .contains("配置校验失败 (4 项)")
                .contains("spring.application.name")
                .contains("framework4j.access-token.secretKey");
    }
}
