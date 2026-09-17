package fun.commons.lotask4j.boot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ASTS 业务 bean 全量装配 (controller/service/config/handler/metrics/schedule + mapper)。
 *
 * <p>独立于 {@link AstsServerAutoConfiguration} 的轻装配层 — token-gateway WorkerAssembly
 * 同款家族范式: 装配测试可单独关闭业务面 ({@code lotask4j.business.enabled=false}),
 * 对 checker/flyway 等 light bean 做 ApplicationContextRunner 级验证, 不必拉起 MyBatis 全栈。
 *
 * <p>业务 bean 显式列子包扫描, boot 包自身不进扫描 — 防宿主组件扫描双注册 (thmp 家族教训)。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "lotask4j.business", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableScheduling
@EnableAsync
@ComponentScan(basePackages = {
        "fun.commons.lotask4j.config",
        "fun.commons.lotask4j.controller",
        "fun.commons.lotask4j.handler",
        "fun.commons.lotask4j.metrics",
        "fun.commons.lotask4j.schedule",
        "fun.commons.lotask4j.service"})
@MapperScan("fun.commons.lotask4j.mapper")
public class AstsBusinessAssembly {
}
