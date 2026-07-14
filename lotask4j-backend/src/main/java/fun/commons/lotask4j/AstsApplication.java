package fun.commons.lotask4j;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 异步慢任务服务 (ASTS) 主应用启动类
 *
 * @author lotask4j-team
 * @version 1.0.0
 * @since 2024-01-01
 */
@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class
})
@EnableScheduling
@EnableAsync
@ComponentScan(basePackages = {
    "fun.commons.lotask4j"
})
@MapperScan(basePackages = {
    "fun.commons.lotask4j.mapper"
})
public class AstsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AstsApplication.class, args);
    }
}
