package fun.commons.lotask4j.demo.service;

import fun.commons.lotask4j.dto.SubmitTaskRequest;
import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.entity.AstTaskTypeConfig;
import fun.commons.lotask4j.enums.BusinessCode;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.mapper.AstTaskTypeConfigMapper;
import fun.commons.lotask4j.service.TaskService;
import fun.commons.lotask4j.service.impl.TaskServiceImpl;
import fun.commons.lotask4j.service.TaskStateMachine;
import fun.commons.lotask4j.service.TaskSubmitGuard;
import fun.commons.framework4j.tenant.context.TenantIdentity;
import fun.commons.framework4j.web.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.OffsetDateTime;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TaskService 租户 claim 注入路径测试 — TenantIdentity.currentTenantId 返回非 null 时:
 *   幂等命中提前返回 / 类型超时配置生效 / cancelTask 走租户过滤读 / 提交异常统一 TASK_SUBMIT_FAILED
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TaskService 租户 claim 路径测试")
class TaskServiceTenantClaimTest {

    @Mock
    private AstTaskMapper astTaskMapper;

    @Mock
    private AstTaskTypeConfigMapper taskTypeConfigMapper;

    @Mock
    private TaskStateMachine stateMachine;

    @Mock
    private TaskSubmitGuard submitGuard;

    @InjectMocks
    private TaskServiceImpl taskService;

    private MockedStatic<TenantIdentity> tenantIdentity;

    @BeforeEach
    void setUp() {
        // 使用反射设置 baseMapper (ServiceImpl 的父类字段, @InjectMocks 不覆盖继承字段)
        org.springframework.test.util.ReflectionTestUtils.setField(taskService, "baseMapper", astTaskMapper);
        tenantIdentity = org.mockito.Mockito.mockStatic(TenantIdentity.class);
        tenantIdentity.when(() -> TenantIdentity.currentTenantId(any())).thenReturn(42L);
        when(astTaskMapper.insertTask(any(AstTask.class), anyString(), anyString())).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        tenantIdentity.close();
    }

    private SubmitTaskRequest request(String type, String idemKey) {
        SubmitTaskRequest req = new SubmitTaskRequest();
        req.setType(type);
        req.setPayload(new HashMap<>());
        req.setIdempotencyKey(idemKey);
        return req;
    }

    @Test
    @DisplayName("幂等命中 (同租户同 key) → 直接返回已有 id, 不再走背压/插入")
    void submitTask_idempotencyHit() {
        AstTask existing = new AstTask();
        existing.setId(555L);
        when(stateMachine.findByIdempotencyKey("data_export", "idem-1", 42L)).thenReturn(existing);

        Long id = taskService.submitTask(request("data_export", "idem-1"));

        assertEquals(555L, id);
        verify(submitGuard, never()).checkOrThrow(anyString());
        verify(astTaskMapper, never()).insertTask(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("类型配置有 timeoutSeconds → expiredAt 取配置超时 (非默认 7 天)")
    void submitTask_typeConfigTimeout() {
        AstTaskTypeConfig cfg = new AstTaskTypeConfig();
        cfg.setTypeKey("data_export");
        cfg.setTimeoutSeconds(600);
        when(taskTypeConfigMapper.selectOne(any())).thenReturn(cfg);

        OffsetDateTime before = OffsetDateTime.now();
        taskService.submitTask(request("data_export", null));
        OffsetDateTime after = OffsetDateTime.now();

        ArgumentCaptor<AstTask> captor = ArgumentCaptor.forClass(AstTask.class);
        verify(astTaskMapper).insertTask(captor.capture(), anyString(), anyString());
        AstTask saved = captor.getValue();
        assertThat(saved.getExpiredAt())
                .as("expiredAt 应为 now + 600s (配置超时)")
                .isAfter(before.plusSeconds(595))
                .isBefore(after.plusSeconds(605));
        assertThat(saved.getTenantId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("guard 抛 ApiException → 原样上抛 (不包装成 TASK_SUBMIT_FAILED)")
    void submitTask_apiExceptionRethrown() {
        org.mockito.Mockito.doThrow(new ApiException(
                BusinessCode.TASK_STATE_INVALID.getCode(), "并发已满"))
                .when(submitGuard).checkOrThrow("data_export");

        ApiException ex = assertThrows(ApiException.class,
                () -> taskService.submitTask(request("data_export", null)));
        assertEquals(BusinessCode.TASK_STATE_INVALID.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("提交过程异常 → 统一 TASK_SUBMIT_FAILED")
    void submitTask_genericExceptionWrapped() {
        when(taskTypeConfigMapper.selectOne(any())).thenThrow(new RuntimeException("db boom"));

        ApiException ex = assertThrows(ApiException.class,
                () -> taskService.submitTask(request("data_export", null)));
        assertEquals(BusinessCode.TASK_SUBMIT_FAILED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("cancelTask 有租户 claim → 走租户过滤读, 传租户 CAS 取消")
    void cancelTask_withTenantClaim() {
        AstTask running = new AstTask();
        running.setId(100L);
        running.setStatus("RUNNING");
        running.setVersion(3);
        when(astTaskMapper.selectByIdWithTypeName(100L, 42L)).thenReturn(running);

        assertTrue(taskService.cancelTask(100L));
        verify(stateMachine).requestCancel(100L, 3, 42L);
        verify(astTaskMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("cancelTask 终态 → TASK_CANCEL_NOT_ALLOWED")
    void cancelTask_terminalRejected() {
        AstTask done = new AstTask();
        done.setId(101L);
        done.setStatus("SUCCESS");
        when(astTaskMapper.selectByIdWithTypeName(101L, 42L)).thenReturn(done);

        ApiException ex = assertThrows(ApiException.class, () -> taskService.cancelTask(101L));
        assertEquals(BusinessCode.TASK_CANCEL_NOT_ALLOWED.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("getTaskDetail 不存在 → TASK_NOT_FOUND (envelope)")
    void getTaskDetail_notFound() {
        when(astTaskMapper.selectByIdWithTypeName(eq(404L), eq(42L))).thenReturn(null);

        ApiException ex = assertThrows(ApiException.class, () -> taskService.getTaskDetail(404L));
        assertEquals(BusinessCode.TASK_NOT_FOUND.getCode(), ex.getCode());
    }
}
