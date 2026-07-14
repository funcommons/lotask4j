package fun.commons.lotask4j.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * lotask4j 演示应用
 *
 * 本应用演示如何集成和使用 lotask4j 异步慢任务服务 (ASTS)
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"fun.commons.lotask4j.demo"})
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
