package fun.commons.lotask4j.boot;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code AstsFlywayConfig} (嵌套于 {@link AstsServerAutoConfiguration}) 装配:
 * H2 内存库端到端迁移 / enabled 开关 / 宿主已有 Flyway bean 时让位。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
class AstsFlywayConfigTest {

    private WebApplicationContextRunner h2Runner(String... extra) {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(AstsServerAutoConfiguration.class))
                .withPropertyValues(
                        "spring.application.name=lotask4j-flyway-test",
                        "framework4j.datasource.datasources.business.url=jdbc:postgresql://localhost:5432/probe",
                        "framework4j.redis.datasources.default.host=localhost",
                        "framework4j.access-token.secretKey=0123456789abcdef0123456789abcdef",
                        "framework4j.tenant.enabled=false",
                        "framework4j.sensitive.enabled=false",
                        "lotask4j.business.enabled=false",
                        "lotask4j.flyway.url=jdbc:h2:mem:flywayprobe;DB_CLOSE_DELAY=-1",
                        "lotask4j.flyway.user=sa",
                        "lotask4j.flyway.locations=classpath:db/flywaytest")
                .withPropertyValues(extra);
    }

    @Test
    void migratesInMemoryDatabaseOnStartup() {
        h2Runner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("lotask4jFlyway");
            assertThat(context).hasBean("lotask4jFlywayMigrationInitializer");
        });
    }

    @Test
    void flywayDisabledSkipsMigrationBeans() {
        h2Runner("lotask4j.flyway.enabled=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(Flyway.class);
            assertThat(context).doesNotHaveBean(FlywayMigrationInitializer.class);
            assertThat(context).hasBean(AstsServerAutoConfiguration.CHECKER_BEAN_NAME);
        });
    }

    @Test
    void backsOffWhenHostProvidesOwnFlyway() {
        // 宿主 Flyway 必须自带 locations (默认 classpath:db/migration 会加载 starter 的 PG 脚本)
        h2Runner().withBean("hostFlyway", Flyway.class,
                        () -> Flyway.configure()
                                .dataSource("jdbc:h2:mem:hostdb;DB_CLOSE_DELAY=-1", "sa", "")
                                .locations("classpath:db/__none__")
                                .load())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(Flyway.class);
                    assertThat(context).doesNotHaveBean("lotask4jFlyway");
                });
    }
}
