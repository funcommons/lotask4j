package fun.commons.lotask4j.mapper;

import fun.commons.lotask4j.AstsApplication;
import fun.commons.lotask4j.schedule.TaskPartitionMaintainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TaskPartitionMapper / TaskPartitionMaintainer 测试 — 月分区运维在真 PG (分区表) 上的行为
 *
 * 覆盖:
 * 1. 无 default 滞留 → 直接 PARTITION OF (maintainer 编排)
 * 2. default 有滞留行 → 承接表 copy+delete+ATTACH, 行不丢
 * 3. 幂等 (已存在月份重复 ensure 不报错)
 */
@SpringBootTest(classes = AstsApplication.class)
@ActiveProfiles("test")
@DisplayName("任务月分区运维测试")
class TaskPartitionMapperTest {

    @Autowired
    private TaskPartitionMapper partitionMapper;

    @Autowired
    private TaskPartitionMaintainer maintainer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 远期月份避免与 TaskArchiver 滚动预建的当月/下月冲突 */
    private static YearMonth futureMonth(int plus) {
        return YearMonth.now().plusMonths(plus);
    }

    private static String name(YearMonth m) {
        return "asts_task_" + m.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
    }

    private static String start(YearMonth m) {
        return m.atDay(1).toString();
    }

    private static String end(YearMonth m) {
        return m.plusMonths(1).atDay(1).toString();
    }

    @AfterEach
    void cleanup() {
        for (int plus = 2; plus <= 4; plus++) {
            // 分区子表可直接 DROP (测试自建分区与测试行, 随之清理)
            jdbcTemplate.execute("DROP TABLE IF EXISTS " + name(futureMonth(plus)));
        }
        jdbcTemplate.update("DELETE FROM asts_task WHERE id IN (9001, 9002)");
    }

    @Test
    @DisplayName("无 default 滞留时直接建分区 (maintainer 编排)")
    void createPartitionDirectly() {
        YearMonth m = futureMonth(2);
        String table = name(m);

        assertFalse(partitionMapper.partitionExists(table));
        assertEquals(0, partitionMapper.countDefaultRowsInRange(start(m), end(m)));

        maintainer.ensurePartitionFor(m);

        assertTrue(partitionMapper.partitionExists(table));
    }

    @Test
    @DisplayName("default 有滞留行时走承接表 copy+delete+ATTACH, 行不丢")
    void moveStuckRowsThenAttach() {
        YearMonth m = futureMonth(3);
        String table = name(m);

        // 往 default 塞一行该范围的滞留数据
        jdbcTemplate.update(
                "INSERT INTO asts_task (id, task_type_key, created_at) VALUES (?, ?, CAST(? AS timestamptz))",
                9001L, "data_export", start(m) + " 12:00:00+08");

        assertEquals(1, partitionMapper.countDefaultRowsInRange(start(m), end(m)));

        maintainer.ensurePartitionFor(m);

        assertEquals(0, partitionMapper.countDefaultRowsInRange(start(m), end(m)), "default 应清空该范围");
        assertTrue(partitionMapper.partitionExists(table));

        // 行仍可从父表查到, 且已路由到新分区
        String tableOid = jdbcTemplate.queryForObject(
                "SELECT tableoid::regclass::text FROM asts_task WHERE id = 9001", String.class);
        assertEquals(table, tableOid);
    }

    @Test
    @DisplayName("建分区幂等 (已存在月份重复 ensure 不报错)")
    void createPartitionIdempotent() {
        YearMonth m = futureMonth(4);

        maintainer.ensurePartitionFor(m);
        assertDoesNotThrow(() -> maintainer.ensurePartitionFor(m));
        assertTrue(partitionMapper.partitionExists(name(m)));
    }
}
