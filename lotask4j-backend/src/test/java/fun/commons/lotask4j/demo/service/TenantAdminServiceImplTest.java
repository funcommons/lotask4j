package fun.commons.lotask4j.demo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import fun.commons.lotask4j.dto.TenantResponse;
import fun.commons.lotask4j.entity.AstsTenant;
import fun.commons.lotask4j.enums.BusinessCode;
import fun.commons.lotask4j.mapper.AstsTenantMapper;
import fun.commons.lotask4j.service.impl.TenantAdminServiceImpl;
import fun.commons.framework4j.web.ApiException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TenantAdminServiceImpl 纯单元测试 — 租户 CRUD 与状态机映射
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TenantAdminService 单元测试")
class TenantAdminServiceImplTest {

    @Mock
    private AstsTenantMapper tenantMapper;

    @Mock
    private fun.commons.framework4j.tenant.auth.TenantSecretService tenantSecretService;

    @InjectMocks
    private TenantAdminServiceImpl service;

    @BeforeAll
    static void initLambdaCache() {
        org.apache.ibatis.session.Configuration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(assistant, AstsTenant.class);
    }

    private static AstsTenant tenant(long id, String status) {
        AstsTenant t = new AstsTenant();
        t.setId(id);
        t.setName("tenant-" + id);
        t.setStatus(status);
        return t;
    }

    @BeforeEach
    void stubFound() {
        when(tenantMapper.selectById(7L)).thenReturn(tenant(7L, "ACTIVE"));
        when(tenantMapper.selectById(404L)).thenReturn(null);
    }

    // ==================== setStatus ====================

    @Test
    @DisplayName("setStatus: INACTIVE 映射为契约状态 SUSPEND")
    void setStatus_inactiveMappedToSuspend() {
        service.setStatus(7L, "INACTIVE");

        ArgumentCaptor<AstsTenant> captor = ArgumentCaptor.forClass(AstsTenant.class);
        verify(tenantMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("SUSPEND");
    }

    @Test
    @DisplayName("setStatus: ACTIVE / SUSPEND 直通")
    void setStatus_passThrough() {
        service.setStatus(7L, "ACTIVE");
        ArgumentCaptor<AstsTenant> captor = ArgumentCaptor.forClass(AstsTenant.class);
        verify(tenantMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");

        service.setStatus(7L, "SUSPEND");
        verify(tenantMapper, org.mockito.Mockito.times(2)).updateById(any(AstsTenant.class));
    }

    @Test
    @DisplayName("setStatus: 非法状态 → APPLICATION_STATUS_INVALID")
    void setStatus_invalidStatus() {
        ApiException ex = assertThrows(ApiException.class,
                () -> service.setStatus(7L, "FOO"));
        assertEquals(BusinessCode.APPLICATION_STATUS_INVALID.getCode(), ex.getCode());
    }

    // ==================== deleteTenant / getTenant ====================

    @Test
    @DisplayName("deleteTenant: 存在 → 逻辑删 (deleteById); 不存在 → APPLICATION_NOT_FOUND")
    void deleteTenant() {
        service.deleteTenant(7L);
        verify(tenantMapper).deleteById(7L);

        ApiException ex = assertThrows(ApiException.class, () -> service.deleteTenant(404L));
        assertEquals(BusinessCode.APPLICATION_NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("getTenant: 存在 → 响应映射; 不存在 → APPLICATION_NOT_FOUND")
    void getTenant() {
        TenantResponse resp = service.getTenant(7L);
        assertThat(resp.getName()).isEqualTo("tenant-7");

        ApiException ex = assertThrows(ApiException.class, () -> service.getTenant(404L));
        assertEquals(BusinessCode.APPLICATION_NOT_FOUND.getCode(), ex.getCode());
    }

    // ==================== resetSecret (委托框架, key 风格双兼容) ====================

    @Test
    @DisplayName("resetSecret: 框架返回 snake_case key (tenant_secret)")
    void resetSecret_snakeKey() {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("id", fun.commons.framework4j.id.util.IdObfuscator.toOpenId(7L));
        data.put("name", "t1");
        data.put("tenant_secret", "plain-secret");
        when(tenantSecretService.reset(7L)).thenReturn(
                fun.commons.framework4j.web.ApiResponse.success(data));

        fun.commons.lotask4j.dto.TenantSecretResponse resp = service.resetSecret(7L);
        assertThat(resp.getTenantSecret()).isEqualTo("plain-secret");
        assertThat(resp.getName()).isEqualTo("t1");
    }

    @Test
    @DisplayName("resetSecret: 框架返回 camelCase key (tenantSecret) 兼容")
    void resetSecret_camelKey() {
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("id", fun.commons.framework4j.id.util.IdObfuscator.toOpenId(7L));
        data.put("name", "t2");
        data.put("tenantSecret", "camel-secret");
        when(tenantSecretService.reset(7L)).thenReturn(
                fun.commons.framework4j.web.ApiResponse.success(data));

        fun.commons.lotask4j.dto.TenantSecretResponse resp = service.resetSecret(7L);
        assertThat(resp.getTenantSecret()).isEqualTo("camel-secret");
    }

    // ==================== listTenants / countTenants ====================

    @Test
    @DisplayName("listTenants: keyword+status 过滤与无条件查询")
    void listTenants_filters() {
        Page<AstsTenant> page = new Page<>(1, 20);
        page.setRecords(List.of(tenant(1L, "ACTIVE"), tenant(2L, "SUSPEND")));
        when(tenantMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        List<TenantResponse> both = service.listTenants("kw", "ACTIVE", 1, 20);
        assertThat(both).hasSize(2);

        List<TenantResponse> none = service.listTenants(null, null, 1, 20);
        assertThat(none).hasSize(2);
    }

    @Test
    @DisplayName("countTenants: 有/无过滤条件均委托 mapper")
    void countTenants_filters() {
        when(tenantMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

        assertThat(service.countTenants("kw", "ACTIVE")).isEqualTo(5L);
        assertThat(service.countTenants(null, null)).isEqualTo(5L);
    }
}
