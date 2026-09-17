package fun.commons.lotask4j.boot;

import fun.commons.lotask4j.properties.AstsServerProperties;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ASTS 异步慢任务服务自动装配入口 (issue #4 starter 化)。
 *
 * <p>装配链: 壳应用 (1 主类 + 1 yml) → 本类 (imports 文件注册) → controller/service/
 * config/schedule 全量业务 bean + framework4j SDK 自动装配 + Flyway 启动迁移。
 *
 * <p>家族惯例 (thmp/token-gateway): 业务 bean 显式列子包扫描, boot 包自身不进扫描 —
 * 防止宿主组件扫描与本装配双注册。宿主基包落在 {@code fun.commons.lotask4j} 下时会触发
 * 已知双注册限制 (与 token-gateway 同款), 见 starter README。
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
@EnableScheduling
@EnableAsync
@EnableConfigurationProperties(AstsServerProperties.class)
@ComponentScan(basePackages = {
        "fun.commons.lotask4j.config",
        "fun.commons.lotask4j.controller",
        "fun.commons.lotask4j.handler",
        "fun.commons.lotask4j.metrics",
        "fun.commons.lotask4j.schedule",
        "fun.commons.lotask4j.service"})
@MapperScan("fun.commons.lotask4j.mapper")
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
