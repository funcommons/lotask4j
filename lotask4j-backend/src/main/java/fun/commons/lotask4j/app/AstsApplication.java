package fun.commons.lotask4j.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * ASTS 独立部署样例壳 (issue #4 starter 化后 lotask4j-backend 的全部角色)。
 *
 * <p>壳内零业务代码: 业务 bean / 定时任务 / Flyway 迁移 / 控制台与 embed 静态资源全部由
 * lotask4j-server-spring-boot-starter 自动装配 (AutoConfiguration.imports 引用即嵌入)。
 * framework4j.* 宿主侧声明与 lotask4j.* 调优见 application.yml。
 *
 * <p>主类刻意落在 {@code fun.commons.lotask4j.app} 包 — @SpringBootApplication 默认组件
 * 扫描仅覆盖本包, 与 starter 的 @ComponentScan 业务子包互不重叠 (双注册防线, thmp 家族教训)。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@SpringBootApplication
public class AstsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AstsApplication.class, args);
    }
}
