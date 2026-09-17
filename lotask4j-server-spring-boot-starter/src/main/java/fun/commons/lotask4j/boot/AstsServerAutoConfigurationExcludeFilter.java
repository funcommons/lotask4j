package fun.commons.lotask4j.boot;

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.beans.factory.Aware;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ASTS starter 自动装配排除过滤器 (issue #4)。
 *
 * <p>Spring Boot 3.5 无库级 {@code META-INF/spring/...autoconfigure.exclude} 文件机制,
 * 库构件声明「本构件不适配的自动装配」的正统路径是 {@link AutoConfigurationImportFilter}
 * (spring.factories 注册)。原 lotask4j-backend application.yml 的 4 项
 * {@code spring.autoconfigure.exclude} 由此接管, 壳应用零配置:
 *
 * <ul>
 *   <li>DataSourceAutoConfiguration — 数据面走 framework4j 多数据源</li>
 *   <li>MybatisPlusAutoConfiguration — SqlSessionFactory 由 framework4j-datasource 装配</li>
 *   <li>DruidDataSourceAutoConfigure — 同上 (不排除时启动即失败, v1.5.1 切 3-starter 后暴露)</li>
 *   <li>RedissonAutoConfigurationV2 — Redis 面走 framework4j-redis (本服务并发控制走 PG)</li>
 * </ul>
 *
 * <p>filter 由 SpringFactoriesLoader 实例化 (非 Spring bean), selector 只回调
 * {@link Aware} 家族的 Environment/BeanFactory/ResourceLoader 三种 — 这里用
 * {@link EnvironmentAware} (ApplicationContextAware 不会被调, environment 将为 null)。
 *
 * <p>filter 语义是 skip 而非 exclude: 被过滤类不在 classpath 时安全空转 (无
 * {@code checkExcludedClasses} 硬失败)。{@code lotask4j.enabled=false} 时全部放行,
 * 宿主恢复自己的原生装配。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
public class AstsServerAutoConfigurationExcludeFilter
        implements AutoConfigurationImportFilter, EnvironmentAware {

    /** lotask4j.enabled=false 时放行全部候选 (宿主自管数据面/缓存面) */
    static final String ENABLED_KEY = "lotask4j.enabled";

    /** 原应用 yml spring.autoconfigure.exclude 的 4 项 (HashSet: 候选数组可能含 null, Set.of 会 NPE) */
    private static final Set<String> VETOED = new HashSet<>(List.of(
            "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure",
            "org.redisson.spring.starter.RedissonAutoConfigurationV2"));

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean[] match(String[] classNames, AutoConfigurationMetadata metadata) {
        boolean[] match = new boolean[classNames.length];
        boolean starterEnabled = environment.getProperty(ENABLED_KEY, Boolean.class, true);
        for (int i = 0; i < classNames.length; i++) {
            String candidate = classNames[i];
            match[i] = !(starterEnabled && candidate != null && VETOED.contains(candidate));
        }
        return match;
    }
}
