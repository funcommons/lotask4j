package fun.commons.lotask4j.properties;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AstsServerProperties} 全字段绑定 + 默认值断言 (JaCoCo 100% LINE 门禁配套)。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
class AstsServerPropertiesTest {

    @Test
    void defaultsMatchSampleBaseline() {
        AstsServerProperties props = new AstsServerProperties();

        assertThat(props.getAsync().getCorePoolSize()).isEqualTo(10);
        assertThat(props.getAsync().getMaxPoolSize()).isEqualTo(50);
        assertThat(props.getAsync().getQueueCapacity()).isEqualTo(1000);
        assertThat(props.getAsync().getThreadNamePrefix()).isEqualTo("async-executor-");
        assertThat(props.getAsync().isWaitForTasksToCompleteOnShutdown()).isTrue();
        assertThat(props.getAsync().getAwaitTerminationSeconds()).isEqualTo(60);

        assertThat(props.getAsts().getHeartbeatInterval()).isEqualTo(30);
        assertThat(props.getAsts().getReaperInterval()).isEqualTo(60000);
        assertThat(props.getAsts().getDefaultTimeout()).isEqualTo(600);
        assertThat(props.getAsts().getMaxRetries()).isEqualTo(3);
        assertThat(props.getAsts().getDefaultLeaseSeconds()).isEqualTo(120);
        assertThat(props.getAsts().getLeaseGraceSeconds()).isEqualTo(30);
        assertThat(props.getAsts().getLeaseBufferSeconds()).isEqualTo(20);
        assertThat(props.getAsts().getWorkerConcurrency()).isEqualTo(5);
        assertThat(props.getAsts().getPollInterval()).isEqualTo(1);
        assertThat(props.getAsts().getOutboxInterval()).isEqualTo(5000);

        assertThat(props.getWebhook().getMaxRetries()).isEqualTo(3);
        assertThat(props.getWebhook().getRetryInterval()).isEqualTo(5);
        assertThat(props.getWebhook().getTimeoutSeconds()).isEqualTo(30);

        assertThat(props.getSteps().getMaxSteps()).isEqualTo(100);
        assertThat(props.getSteps().getUpdateInterval()).isEqualTo(1000);

        assertThat(props.getWebEmbed().getFrontBaseUrl()).isEqualTo("/web-embed/index.html");
        assertThat(props.getWebEmbed().getOpenDefaultUserId()).isEqualTo("guest");
        assertThat(props.getWebEmbed().getCallbackTimeoutSeconds()).isEqualTo(3);
        assertThat(props.getWebEmbed().getCookieExpireSeconds()).isEqualTo(7200);
        assertThat(props.getWebEmbed().getEmbedBaseUrl()).isEqualTo("http://localhost:9080");

        assertThat(props.getFlyway().isEnabled()).isTrue();
        assertThat(props.getFlyway().getUrl()).isNull();
        assertThat(props.getFlyway().getUser()).isNull();
        assertThat(props.getFlyway().getPassword()).isEmpty();
        assertThat(props.getFlyway().getLocations()).containsExactly("classpath:db/migration");
        assertThat(props.getFlyway().isBaselineOnMigrate()).isTrue();
        assertThat(props.getFlyway().getBaselineVersion()).isEqualTo("1");
        assertThat(props.getFlyway().isValidateOnMigrate()).isTrue();

        assertThat(props.getSecurity().isStrict()).isFalse();
    }

    @Test
    void bindsFullKebabCaseSample() {
        Map<String, Object> source = Map.ofEntries(
                Map.entry("lotask4j.async.core-pool-size", 7),
                Map.entry("lotask4j.async.max-pool-size", 70),
                Map.entry("lotask4j.async.queue-capacity", 77),
                Map.entry("lotask4j.async.thread-name-prefix", "astspool-"),
                Map.entry("lotask4j.async.wait-for-tasks-to-complete-on-shutdown", false),
                Map.entry("lotask4j.async.await-termination-seconds", 61),
                Map.entry("lotask4j.asts.heartbeat-interval", 31),
                Map.entry("lotask4j.asts.reaper-interval", 61000),
                Map.entry("lotask4j.asts.default-timeout", 601),
                Map.entry("lotask4j.asts.max-retries", 4),
                Map.entry("lotask4j.asts.default-lease-seconds", 121),
                Map.entry("lotask4j.asts.lease-grace-seconds", 31),
                Map.entry("lotask4j.asts.lease-buffer-seconds", 21),
                Map.entry("lotask4j.asts.worker-concurrency", 6),
                Map.entry("lotask4j.asts.poll-interval", 2),
                Map.entry("lotask4j.asts.outbox-interval", 5100),
                Map.entry("lotask4j.webhook.max-retries", 4),
                Map.entry("lotask4j.webhook.retry-interval", 6),
                Map.entry("lotask4j.webhook.timeout-seconds", 31),
                Map.entry("lotask4j.steps.max-steps", 101),
                Map.entry("lotask4j.steps.update-interval", 1001),
                Map.entry("lotask4j.web-embed.front-base-url", "/embed/index.html"),
                Map.entry("lotask4j.web-embed.open-default-user-id", "user1"),
                Map.entry("lotask4j.web-embed.callback-timeout-seconds", 4),
                Map.entry("lotask4j.web-embed.cookie-expire-seconds", 7201),
                Map.entry("lotask4j.web-embed.embed-base-url", "http://embed.example"),
                Map.entry("lotask4j.flyway.enabled", false),
                Map.entry("lotask4j.flyway.url", "jdbc:postgresql://db:5432/x"),
                Map.entry("lotask4j.flyway.user", "migrator"),
                Map.entry("lotask4j.flyway.password", "secret"),
                Map.entry("lotask4j.flyway.locations", "classpath:a,classpath:b"),
                Map.entry("lotask4j.flyway.baseline-on-migrate", false),
                Map.entry("lotask4j.flyway.baseline-version", "2"),
                Map.entry("lotask4j.flyway.validate-on-migrate", false),
                Map.entry("lotask4j.security.strict", true));

        AstsServerProperties props = new Binder(new MapConfigurationPropertySource(source))
                .bind("lotask4j", Bindable.of(AstsServerProperties.class)).get();

        assertThat(props.getAsync().getCorePoolSize()).isEqualTo(7);
        assertThat(props.getAsync().getMaxPoolSize()).isEqualTo(70);
        assertThat(props.getAsync().getQueueCapacity()).isEqualTo(77);
        assertThat(props.getAsync().getThreadNamePrefix()).isEqualTo("astspool-");
        assertThat(props.getAsync().isWaitForTasksToCompleteOnShutdown()).isFalse();
        assertThat(props.getAsync().getAwaitTerminationSeconds()).isEqualTo(61);

        assertThat(props.getAsts().getHeartbeatInterval()).isEqualTo(31);
        assertThat(props.getAsts().getReaperInterval()).isEqualTo(61000);
        assertThat(props.getAsts().getDefaultTimeout()).isEqualTo(601);
        assertThat(props.getAsts().getMaxRetries()).isEqualTo(4);
        assertThat(props.getAsts().getDefaultLeaseSeconds()).isEqualTo(121);
        assertThat(props.getAsts().getLeaseGraceSeconds()).isEqualTo(31);
        assertThat(props.getAsts().getLeaseBufferSeconds()).isEqualTo(21);
        assertThat(props.getAsts().getWorkerConcurrency()).isEqualTo(6);
        assertThat(props.getAsts().getPollInterval()).isEqualTo(2);
        assertThat(props.getAsts().getOutboxInterval()).isEqualTo(5100);

        assertThat(props.getWebhook().getMaxRetries()).isEqualTo(4);
        assertThat(props.getWebhook().getRetryInterval()).isEqualTo(6);
        assertThat(props.getWebhook().getTimeoutSeconds()).isEqualTo(31);

        assertThat(props.getSteps().getMaxSteps()).isEqualTo(101);
        assertThat(props.getSteps().getUpdateInterval()).isEqualTo(1001);

        assertThat(props.getWebEmbed().getFrontBaseUrl()).isEqualTo("/embed/index.html");
        assertThat(props.getWebEmbed().getOpenDefaultUserId()).isEqualTo("user1");
        assertThat(props.getWebEmbed().getCallbackTimeoutSeconds()).isEqualTo(4);
        assertThat(props.getWebEmbed().getCookieExpireSeconds()).isEqualTo(7201);
        assertThat(props.getWebEmbed().getEmbedBaseUrl()).isEqualTo("http://embed.example");

        assertThat(props.getFlyway().isEnabled()).isFalse();
        assertThat(props.getFlyway().getUrl()).isEqualTo("jdbc:postgresql://db:5432/x");
        assertThat(props.getFlyway().getUser()).isEqualTo("migrator");
        assertThat(props.getFlyway().getPassword()).isEqualTo("secret");
        assertThat(props.getFlyway().getLocations()).isEqualTo(List.of("classpath:a", "classpath:b"));
        assertThat(props.getFlyway().isBaselineOnMigrate()).isFalse();
        assertThat(props.getFlyway().getBaselineVersion()).isEqualTo("2");
        assertThat(props.getFlyway().isValidateOnMigrate()).isFalse();

        assertThat(props.getSecurity().isStrict()).isTrue();
    }
}
