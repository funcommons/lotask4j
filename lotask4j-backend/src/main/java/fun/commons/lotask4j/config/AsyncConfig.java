package fun.commons.lotask4j.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务配置
 * 配置异步方法执行的线程池
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "app.async")
public class AsyncConfig {

    /**
     * 核心线程数
     */
    private int corePoolSize = 10;

    /**
     * 最大线程数
     */
    private int maxPoolSize = 50;

    /**
     * 队列容量
     */
    private int queueCapacity = 1000;

    /**
     * 线程名称前缀
     */
    private String threadNamePrefix = "async-executor-";

    /**
     * 等待任务完成后再关闭
     */
    private boolean waitForTasksToCompleteOnShutdown = true;

    /**
     * 等待时间（秒）
     */
    private int awaitTerminationSeconds = 60;

    /**
     * 异步执行器配置
     * 用于 @Async("asyncExecutor") 注解的方法
     */
    @Bean("asyncExecutor")
    public Executor asyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数
        executor.setCorePoolSize(corePoolSize);

        // 最大线程数
        executor.setMaxPoolSize(maxPoolSize);

        // 队列容量
        executor.setQueueCapacity(queueCapacity);

        // 线程名称前缀
        executor.setThreadNamePrefix(threadNamePrefix);

        // 拒绝策略：由调用线程处理
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(waitForTasksToCompleteOnShutdown);

        // 等待时间（秒）
        executor.setAwaitTerminationSeconds(awaitTerminationSeconds);

        executor.initialize();
        return executor;
    }
}
