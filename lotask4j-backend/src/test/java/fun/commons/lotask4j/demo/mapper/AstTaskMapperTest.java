package fun.commons.lotask4j.mapper;

import fun.commons.lotask4j.AstsApplication;
import fun.commons.lotask4j.entity.AstTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AstTaskMapper 测试
 *
 * 测试 MyBatis Mapper 接口
 */
@SpringBootTest(classes = AstsApplication.class)
@ActiveProfiles("test")
@DisplayName("任务Mapper测试")
class AstTaskMapperTest {

    @Autowired
    private AstTaskMapper astTaskMapper;

    @Test
    @DisplayName("插入任务 - 成功")
    void testInsertTask_Success() {
        // Given
        AstTask task = new AstTask();
        task.setId(100001L);
        task.setTaskTypeKey("data_export");
        task.setStatus("PENDING");
        task.setPriority(10);
        // retryCount 已迁移到 attempt/maxAttempts（P0）
        task.setProgress(0);
        task.setCurrentStepProgress(0);

        Map<String, Object> payload = new HashMap<>();
        payload.put("query", "SELECT * FROM test");
        task.setPayload(payload);

        task.setResult(new HashMap<>());
        task.setStepsDetail(new java.util.ArrayList<>());

        task.setCreatedAt(OffsetDateTime.now());
        task.setUpdatedAt(OffsetDateTime.now());
        task.setIsDeleted(0);

        // When
        int count = astTaskMapper.insert(task);

        // Then
        assertEquals(1, count, "插入应该影响1行");
        assertNotNull(task.getId(), "ID不应为空");
    }

    @Test
    @DisplayName("根据 ID 查询任务")
    void testSelectById() {
        // Given: 先插入一条任务
        AstTask task = new AstTask();
        task.setId(100002L);
        task.setTaskTypeKey("video_transcode");
        task.setStatus("RUNNING");
        task.setPriority(20);
        task.setProgress(50);
        task.setPayload(new HashMap<>());
        task.setResult(new HashMap<>());
        task.setStepsDetail(new java.util.ArrayList<>());
        task.setCreatedAt(OffsetDateTime.now());
        task.setUpdatedAt(OffsetDateTime.now());
        task.setIsDeleted(0);

        astTaskMapper.insert(task);

        // When
        AstTask found = astTaskMapper.selectById(100002L);

        // Then
        assertNotNull(found, "应该能找到任务");
        assertEquals(100002L, found.getId());
        assertEquals("video_transcode", found.getTaskTypeKey());
        assertEquals("RUNNING", found.getStatus());
        assertEquals(50, found.getProgress());
    }

    @Test
    @DisplayName("根据 ID 查询任务 - 不存在")
    void testSelectById_NotFound() {
        // When
        AstTask found = astTaskMapper.selectById(999999L);

        // Then
        assertNull(found, "不存在的ID应该返回null");
    }

    @Test
    @DisplayName("统计待处理任务数")
    void testCountPendingTasks() {
        // Given: 插入 3 条 PENDING 任务
        for (int i = 0; i < 3; i++) {
            AstTask task = new AstTask();
            task.setId(100010L + i);
            task.setTaskTypeKey("test");
            task.setStatus("PENDING");
            task.setPriority(0);
            task.setPayload(new HashMap<>());
            task.setResult(new HashMap<>());
            task.setStepsDetail(new java.util.ArrayList<>());
            task.setCreatedAt(OffsetDateTime.now());
            task.setUpdatedAt(OffsetDateTime.now());
            task.setIsDeleted(0);
            astTaskMapper.insert(task);
        }

        // When
        long count = astTaskMapper.countPendingTasks();

        // Then
        assertTrue(count >= 3, "待处理任务数应该 >= 3");
    }

    @Test
    @DisplayName("统计运行中任务数")
    void testCountRunningTasks() {
        // Given: 插入 2 条 RUNNING 任务
        for (int i = 0; i < 2; i++) {
            AstTask task = new AstTask();
            task.setId(100020L + i);
            task.setTaskTypeKey("test");
            task.setStatus("RUNNING");
            task.setPriority(0);
            task.setPayload(new HashMap<>());
            task.setResult(new HashMap<>());
            task.setStepsDetail(new java.util.ArrayList<>());
            task.setCreatedAt(OffsetDateTime.now());
            task.setUpdatedAt(OffsetDateTime.now());
            task.setIsDeleted(0);
            astTaskMapper.insert(task);
        }

        // When
        long count = astTaskMapper.countRunningTasks();

        // Then
        assertTrue(count >= 2, "运行中任务数应该 >= 2");
    }

    @Test
    @DisplayName("更新任务状态")
    void testUpdateTaskStatus() {
        // Given: 先插入一条任务
        AstTask task = new AstTask();
        task.setId(100030L);
        task.setTaskTypeKey("test");
        task.setStatus("PENDING");
        task.setPriority(0);
        task.setProgress(0);
        task.setPayload(new HashMap<>());
        task.setResult(new HashMap<>());
        task.setStepsDetail(new java.util.ArrayList<>());
        task.setCreatedAt(OffsetDateTime.now());
        task.setUpdatedAt(OffsetDateTime.now());
        task.setIsDeleted(0);

        astTaskMapper.insert(task);

        // When: 更新状态为 RUNNING，进度为 50
        task.setStatus("RUNNING");
        task.setProgress(50);
        int count = astTaskMapper.updateById(task);

        // Then
        assertEquals(1, count, "更新应该影响1行");

        // 验证更新结果
        AstTask updated = astTaskMapper.selectById(100030L);
        assertNotNull(updated);
        assertEquals("RUNNING", updated.getStatus());
        assertEquals(50, updated.getProgress());
    }

    @Test
    @DisplayName("软删除任务")
    void testSoftDeleteTask() {
        // Given: 插入任务
        AstTask task = new AstTask();
        task.setId(100040L);
        task.setTaskTypeKey("test");
        task.setStatus("SUCCESS");
        task.setPriority(0);
        task.setPayload(new HashMap<>());
        task.setResult(new HashMap<>());
        task.setStepsDetail(new java.util.ArrayList<>());
        task.setCreatedAt(OffsetDateTime.now());
        task.setUpdatedAt(OffsetDateTime.now());
        task.setIsDeleted(0);

        astTaskMapper.insert(task);

        // When: 软删除
        task.setIsDeleted(1);
        astTaskMapper.updateById(task);

        // Then: 验证 isDeleted 字段
        AstTask found = astTaskMapper.selectById(100040L);
        if (found != null) {
            assertEquals(1, found.getIsDeleted());
        }
    }
}
