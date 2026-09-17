package fun.commons.lotask4j.boot;

import fun.commons.lotask4j.properties.AstsServerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AstsServerAutoConfiguration} 装配冒烟 (light 层): 开关/环境条件/校验器 bean 名契约。
 * 业务面 (AstsBusinessAssembly) 全栈断言见 AstsServerEmbedActivationTest (真 PG/Redis 环境)。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
class AstsServerAutoConfigurationTest {

    /** 满足校验器的最小属性集 (business/flyway/tenant/sensitive 关闭以最小化) */
    private static final String[] MINIMAL = {
            "spring.application.name=lotask4j-runner-test",
            "framework4j.datasource.datasources.business.url=jdbc:postgresql://localhost:5432/probe",
            "framework4j.redis.datasources.default.host=localhost",
            "framework4j.access-token.secretKey=0123456789abcdef0123456789abcdef",
            "framework4j.tenant.enabled=false",
            "framework4j.sensitive.enabled=false",
            "lotask4j.flyway.enabled=false",
            "lotask4j.business.enabled=false"};

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AstsServerAutoConfiguration.class))
            .withPropertyValues(MINIMAL);

    @Test
    void assemblesLightBeansWhenEnabled() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AstsServerProperties.class);
            assertThat(context).hasBean(AstsServerAutoConfiguration.CHECKER_BEAN_NAME);
            assertThat(context).hasSingleBean(AstsRequiredPropertiesChecker.class);
        });
    }

    @Test
    void lotask4jEnabledFalseSwitchesEverythingOff() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AstsServerAutoConfiguration.class))
                .withPropertyValues("lotask4j.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(AstsServerProperties.class);
                    assertThat(context).doesNotHaveBean(AstsRequiredPropertiesChecker.class);
                });
    }

    @Test
    void skipsAssemblyInNonWebEnvironment() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AstsServerAutoConfiguration.class))
                .withPropertyValues(MINIMAL)
                .run(context -> assertThat(context)
                        .doesNotHaveBean(AstsRequiredPropertiesChecker.class));
    }
}
