package fun.commons.lotask4j.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * backend 薄壳冒烟 (issue #4 验收 1/5): starter 装配 + 样例 yml + 壳主类三者锁步证明。
 * 完整功能回归在 starter 测试树; Flyway 迁移路径由 scripts/smoke.sh (compose) 覆盖。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BackendShellSmokeTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shellBootsAndHealthIsUp() {
        ResponseEntity<String> health = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getBody()).contains("UP");
    }
}
