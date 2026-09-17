package fun.commons.lotask4j.boot;

import fun.commons.lotask4j.properties.AstsServerProperties;
import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * ASTS 异步慢任务服务自动装配入口 (issue #4 starter 化)。
 *
 * <p>装配链: 壳应用 (1 主类 + 1 yml) → 本类 (imports 文件注册) →
 * {@link AstsBusinessAssembly} (controller/service/config/schedule 全量业务 bean + mapper)
 * + framework4j SDK 自动装配 + Flyway 启动迁移。
 *
 * <p>两层装配 (token-gateway WorkerAssembly 家族范式): 本类只携带 light bean
 * (配置/校验器/Flyway), 业务面由 {@link AstsBusinessAssembly} 独立开关
 * ({@code lotask4j.business.enabled}) — 装配测试可单独验证 light 层。
 *
 * <p>总开关 {@code lotask4j.enabled=false} 时整体退出 (含 AutoConfigurationImportFilter
 * 放行原生 DataSource/MyBatisPlus/Druid/Redisson 装配), 宿主自管。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "lotask4j", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AstsServerProperties.class)
@Import(AstsBusinessAssembly.class)
public class AstsServerAutoConfiguration {

    /** 校验器 bean 名 — Flyway initializer @DependsOn 锚点 */
    public static final String CHECKER_BEAN_NAME = "astsRequiredPropertiesChecker";

    /**
     * 必填配置 fail-fast 校验器 — 先于所有副作用 bean (含 Flyway 迁移) 执行,
     * 缺配在 context refresh 阶段即失败, 不等运行期 NPE/拒连。
     */
    @Bean
    public AstsRequiredPropertiesChecker astsRequiredPropertiesChecker(
            AstsServerProperties properties, Environment environment) {
        return new AstsRequiredPropertiesChecker(properties, environment);
    }

    /**
     * Flyway 启动迁移 (原 spring.flyway.* 接管): 独立直连 (不走 Druid — Wall 会误拦部分
     * PG 语法), 键空间 lotask4j.flyway.*; 家族 DDF 宿主投放 DDL 时设
     * {@code lotask4j.flyway.enabled=false} 跳过。
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "lotask4j.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class AstsFlywayConfig {

        @Bean
        @DependsOn(AstsServerAutoConfiguration.CHECKER_BEAN_NAME)
        @ConditionalOnMissingBean(Flyway.class)
        public Flyway lotask4jFlyway(AstsServerProperties properties) {
            AstsServerProperties.Flyway flyway = properties.getFlyway();
            return Flyway.configure()
                    .dataSource(flyway.getUrl(), flyway.getUser(), flyway.getPassword())
                    .locations(flyway.getLocations().toArray(String[]::new))
                    .baselineOnMigrate(flyway.isBaselineOnMigrate())
                    .baselineVersion(flyway.getBaselineVersion())
                    .validateOnMigrate(flyway.isValidateOnMigrate())
                    .load();
        }

        /**
         * 必须配对 FlywayMigrationInitializer 而非裸 Flyway bean — Boot 的
         * 「迁移先于任何数据库使用」排序 (FlywayMigrationInitializerDatabaseInitializerDetector)
         * 以该 bean 类型为锚点。
         * dependsOn 校验器: 缺配先于迁移报错, 不让 Flyway 先碰库。
         */
        @Bean
        @DependsOn(AstsServerAutoConfiguration.CHECKER_BEAN_NAME)
        public FlywayMigrationInitializer lotask4jFlywayMigrationInitializer(Flyway flyway) {
            return new FlywayMigrationInitializer(flyway);
        }
    }
}
