package fun.commons.lotask4j.demo.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import fun.commons.lotask4j.entity.AstsOutbox;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.mapper.AstsOutboxMapper;
import fun.commons.lotask4j.schedule.OutboxPublisher;
import fun.commons.lotask4j.service.WebhookService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OutboxPublisher 纯单元测试 — 异常降级分支 (CAS 失败 / callbackStatus 同步失败 / 扫描失败)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("OutboxPublisher 单元测试 (异常分支)")
class OutboxPublisherUnitTest {

    @Mock
    private AstTaskMapper taskMapper;

    @Mock
    private AstsOutboxMapper outboxMapper;

    @Mock
    private WebhookService webhookService;

    @InjectMocks
    private OutboxPublisher publisher;

    @BeforeAll
    static void initLambdaCache() {
        org.apache.ibatis.session.Configuration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(assistant, AstsOutbox.class);
    }

    private static AstsOutbox event(Long id, Integer attemptCount, Integer maxAttempts) {
        AstsOutbox e = new AstsOutbox();
        e.setId(id);
        e.setAggregateType("TASK");
        e.setAggregateId(100L);
        e.setEventType("TASK_FINISHED");
        e.setCallbackUrl("http://example.test/hook");
        e.setPayload("{}");
        e.setStatus(AstsOutbox.STATUS_PENDING);
        e.setAttemptCount(attemptCount);
        e.setMaxAttempts(maxAttempts);
        return e;
    }

    @Test
    @DisplayName("deliver 成功 + callbackStatus 同步失败 → 仍不外抛")
    void deliverSuccess_callbackStatusSyncFails() {
        AstsOutbox e = event(1L, 0, 8);
        when(outboxMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(e));
        when(webhookService.deliver(e)).thenReturn(true);
        when(outboxMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        doThrow(new RuntimeException("task archived")).when(taskMapper)
                .updateCallbackStatus(eq(100L), eq(1), isNull());

        assertDoesNotThrow(publisher::publishPending);
    }

    @Test
    @DisplayName("deliver 成功但 CAS 更新为 0 (另一实例已投) → sent 不计")
    void deliverSuccess_casLost() {
        AstsOutbox e = event(2L, 0, 8);
        when(outboxMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(e));
        when(webhookService.deliver(e)).thenReturn(true);
        when(outboxMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertDoesNotThrow(publisher::publishPending);
        verify(taskMapper, never()).updateCallbackStatus(any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    @DisplayName("超限 FAILED 时 callbackStatus 同步失败被吞 (任务行可能已归档)")
    void maxAttempt_failed_callbackStatusIgnored() {
        AstsOutbox e = event(3L, 7, 8); // 本次失败后 8 ≥ 8 → FAILED
        when(outboxMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(e));
        when(webhookService.deliver(e)).thenReturn(false);
        when(outboxMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        doThrow(new RuntimeException("gone")).when(taskMapper)
                .updateCallbackStatus(eq(100L), eq(2), isNull());

        assertDoesNotThrow(publisher::publishPending);
    }

    @Test
    @DisplayName("attemptCount 为 null 按 0 计")
    void failure_nullAttemptCount() {
        AstsOutbox e = event(4L, null, 8);
        when(outboxMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(e));
        when(webhookService.deliver(e)).thenReturn(false);
        when(outboxMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        assertDoesNotThrow(publisher::publishPending);
    }

    @Test
    @DisplayName("扫描异常 (selectPendingDue 抛) → 不外抛, 下轮重试")
    void scanFails_noPropagate() {
        when(outboxMapper.selectList(any(LambdaQueryWrapper.class))).thenThrow(new RuntimeException("db down"));

        assertDoesNotThrow(publisher::publishPending);
        verify(webhookService, never()).deliver(any());
    }
}
