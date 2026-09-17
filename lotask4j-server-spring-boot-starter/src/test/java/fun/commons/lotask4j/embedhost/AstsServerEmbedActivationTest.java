package fun.commons.lotask4j.embedhost;

import fun.commons.lotask4j.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「引用即嵌入」端到端证明 (issue #4 验收 1, token-gateway StarterEmbedActivationTest 家族范式):
 * 嵌入壳只带一个空 {@code @SpringBootApplication} 主类, 业务 bean 全部经
 * AutoConfiguration.imports → AstsServerAutoConfiguration → AstsBusinessAssembly 到位。
 *
 * <p>host 主类落在独立 embedhost 包 — 默认组件扫描仅覆盖本包 (空), 与 starter 业务包
 * 零重叠, 与真实宿主壳同构。需真 PG/Redis (profile test, CI service container 同款)。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@SpringBootTest(classes = AstsServerEmbedActivationTest.EmbedHostApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AstsServerEmbedActivationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ApplicationContext applicationContext;

    /** 最小嵌入壳 — 壳内零业务代码, 一行注解 */
    @SpringBootApplication
    static class EmbedHostApp {
    }

    @Test
    void healthIsUpThroughImportsFileAssembly() {
        ResponseEntity<String> health = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("UP");
    }

    @Test
    void businessBeansAssembledWithoutExplicitImport() {
        // 业务服务链在位 (host 未 import 任何 lotask4j 类)
        assertThat(applicationContext.getBeanNamesForType(TaskService.class)).isNotEmpty();
        // bean 名契约 (迁移前后不可变)
        assertThat(applicationContext.containsBean("webhookRestTemplate")).isTrue();
        assertThat(applicationContext.containsBean("callbackRestTemplate")).isTrue();
        assertThat(applicationContext.containsBean("asyncExecutor")).isTrue();
        // 定时任务在位 (装配层 @EnableScheduling)
        assertThat(applicationContext.getBeanNamesForType(
                fun.commons.lotask4j.schedule.TaskReaper.class)).isNotEmpty();
    }
}
