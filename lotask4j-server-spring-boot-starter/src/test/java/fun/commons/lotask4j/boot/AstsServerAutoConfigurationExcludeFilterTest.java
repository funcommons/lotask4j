package fun.commons.lotask4j.boot;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AstsServerAutoConfigurationExcludeFilter} vet 矩阵:
 * starter 启用时过滤 4 项不适配自动装配; lotask4j.enabled=false 时全量放行。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
class AstsServerAutoConfigurationExcludeFilterTest {

    private static final String[] CANDIDATES = {
            "com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure",
            "org.redisson.spring.starter.RedissonAutoConfigurationV2",
            "org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration"};

    private final AutoConfigurationMetadata metadata = Mockito.mock(AutoConfigurationMetadata.class);

    @Test
    void vetoesIncompatibleAutoConfigurationsWhenEnabled() {
        assertThat(newFilter(new MockEnvironment()).match(CANDIDATES, metadata))
                .containsExactly(false, false, false, false, true);
    }

    @Test
    void passesEverythingThroughWhenStarterDisabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty(AstsServerAutoConfigurationExcludeFilter.ENABLED_KEY, "false");

        assertThat(newFilter(environment).match(CANDIDATES, metadata))
                .containsExactly(true, true, true, true, true);
    }

    @Test
    void environmentWithoutSwitchDefaultsToEnabled() {
        // 与上一用例等价的默认分支: 开关键缺省 → 视为启用 → 照常过滤
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("unrelated.key", "value");

        assertThat(newFilter(environment).match(CANDIDATES, metadata))
                .containsExactly(false, false, false, false, true);
    }

    @Test
    void emptyCandidateListYieldsEmptyMask() {
        assertThat(newFilter(new MockEnvironment()).match(new String[0], metadata)).isEmpty();
    }

    private AstsServerAutoConfigurationExcludeFilter newFilter(ConfigurableEnvironment environment) {
        AstsServerAutoConfigurationExcludeFilter filter = new AstsServerAutoConfigurationExcludeFilter();
        StaticApplicationContext context = new StaticApplicationContext();
        context.setEnvironment(environment);
        filter.setApplicationContext(context);
        return filter;
    }
}
