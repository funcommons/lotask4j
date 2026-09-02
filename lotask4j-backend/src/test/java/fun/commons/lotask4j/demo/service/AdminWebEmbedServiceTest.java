package fun.commons.lotask4j.demo.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import fun.commons.lotask4j.dto.WebEmbedConfigRequest;
import fun.commons.lotask4j.dto.WebEmbedConfigResponse;
import fun.commons.lotask4j.entity.WebEmbedConfig;
import fun.commons.lotask4j.mapper.WebEmbedConfigMapper;
import fun.commons.lotask4j.service.impl.AdminWebEmbedServiceImpl;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminWebEmbedServiceImpl 纯单元测试（embed 配置 CRUD 校验分支 + 默认值逻辑）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdminWebEmbedService 单元测试")
class AdminWebEmbedServiceTest {

    @Mock
    private WebEmbedConfigMapper configMapper;

    @InjectMocks
    private AdminWebEmbedServiceImpl service;

    @BeforeAll
    static void initLambdaCache() {
        // LambdaUpdateWrapper 需要实体表信息缓存（同 TaskArchiverTest）
        org.apache.ibatis.session.Configuration cfg = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        org.apache.ibatis.builder.MapperBuilderAssistant assistant =
                new org.apache.ibatis.builder.MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(assistant, WebEmbedConfig.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "embedBaseUrl", "http://embed.example.com");
    }

    private static WebEmbedConfig config(long id, String key, String componentType) {
        WebEmbedConfig c = new WebEmbedConfig();
        c.setId(id);
        c.setConfigKey(key);
        c.setConfigName("n-" + key);
        c.setUserId("u-1");
        c.setComponentType(componentType);
        c.setIsOpen(1);
        c.setIsEnabled(1);
        return c;
    }

    // ==================== listConfigs / countConfigs ====================

    @Test
    @DisplayName("listConfigs: 空结果返回空列表")
    void listConfigs_empty() {
        when(configMapper.selectPageList(any(), any(), any(), any())).thenReturn(List.of());
        assertThat(service.listConfigs(null, null, 1L, 20L)).isEmpty();
    }

    @Test
    @DisplayName("listConfigs: mapper 返回 null 也返回空列表")
    void listConfigs_nullList() {
        when(configMapper.selectPageList(any(), any(), any(), any())).thenReturn(null);
        assertThat(service.listConfigs("kw", 1, 2L, 10L)).isEmpty();
    }

    @Test
    @DisplayName("listConfigs: page/pageSize 为 null 或 <1 时回落默认值; 条目带相对 embedUrl")
    void listConfigs_mapsResponses_withDefaults() {
        when(configMapper.selectPageList(0L, 20L, "kw", 1))
                .thenReturn(List.of(config(7L, "ek-1", "task-list")));

        List<WebEmbedConfigResponse> items = service.listConfigs("kw", 1, null, 0L);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getId()).isEqualTo(7L);
        assertThat(items.get(0).getEmbedUrl())
                .isEqualTo("/web-embed/task-list?accessKey=ek-1");
    }

    @Test
    @DisplayName("listConfigs: page<1/pageSize<1 各自独立回落")
    void listConfigs_clampsInvalidPageAndSize() {
        when(configMapper.selectPageList(any(), any(), any(), any())).thenReturn(List.of());
        service.listConfigs(null, null, 0L, -5L);
        verify(configMapper).selectPageList(0L, 20L, null, null);
    }

    @Test
    @DisplayName("countConfigs 委托 mapper")
    void countConfigs() {
        when(configMapper.countList("kw", 0)).thenReturn(3L);
        assertThat(service.countConfigs("kw", 0)).isEqualTo(3L);
    }

    // ==================== getConfig ====================

    @Test
    @DisplayName("getConfig: id 为空 / 配置不存在 → IAE")
    void getConfig_validation() {
        assertThatThrownBy(() -> service.getConfig(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id 不能为空");
        when(configMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getConfig(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("配置不存在");
    }

    @Test
    @DisplayName("getConfig: configKey 为 null 时不生成 embedUrl")
    void getConfig_nullConfigKey_noEmbedUrl() {
        WebEmbedConfig c = config(8L, null, "task-card");
        when(configMapper.selectById(8L)).thenReturn(c);
        WebEmbedConfigResponse resp = service.getConfig(8L);
        assertThat(resp.getEmbedUrl()).isNull();
        assertThat(resp.getConfigKey()).isNull();
    }

    // ==================== createConfig ====================

    @Test
    @DisplayName("createConfig: tenantId 缺失 → IAE (embed token 的租户 claim 来源)")
    void createConfig_tenantRequired() {
        WebEmbedConfigRequest req = new WebEmbedConfigRequest();
        req.setConfigKey("ek-noTenant");
        req.setConfigName("n");
        req.setUserId("u");
        req.setComponentType("task-list");
        assertThatThrownBy(() -> service.createConfig(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId 不能为空");
    }

    @Test
    @DisplayName("createConfig: configKey 重复 → IAE")
    void createConfig_duplicateKey() {
        WebEmbedConfigRequest req = new WebEmbedConfigRequest();
        req.setConfigKey("dup");
        req.setConfigName("n");
        req.setUserId("u");
        req.setTenantId(1L);
        req.setComponentType("task-list");
        when(configMapper.countByConfigKeyExcludeId("dup", null)).thenReturn(1);
        assertThatThrownBy(() -> service.createConfig(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("configKey 已存在");
    }

    @Test
    @DisplayName("createConfig: null 字段补默认值 (isOpen=0 / config={} / componentType=all)")
    void createConfig_appliesDefaults() {
        WebEmbedConfigRequest req = new WebEmbedConfigRequest();
        req.setConfigKey("ek-new");
        req.setConfigName("n");
        req.setUserId("u");
        req.setTenantId(1L);
        // componentType/isOpen/config 全 null
        when(configMapper.countByConfigKeyExcludeId("ek-new", null)).thenReturn(0);

        service.createConfig(req);

        ArgumentCaptor<WebEmbedConfig> captor = ArgumentCaptor.forClass(WebEmbedConfig.class);
        verify(configMapper).insertConfig(captor.capture(), eq("{}"));
        WebEmbedConfig saved = captor.getValue();
        assertThat(saved.getIsOpen()).isEqualTo(0);
        assertThat(saved.getConfig()).isEmpty();
        assertThat(saved.getComponentType()).isEqualTo("all");
    }

    @Test
    @DisplayName("createConfig: 有 config 时序列化为 JSON 传入 insertConfig")
    void createConfig_serializesConfig() {
        WebEmbedConfigRequest req = new WebEmbedConfigRequest();
        req.setConfigKey("ek-json");
        req.setConfigName("n");
        req.setUserId("u");
        req.setTenantId(1L);
        req.setComponentType("task-card");
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("theme", "dark");
        req.setConfig(cfg);
        when(configMapper.countByConfigKeyExcludeId("ek-json", null)).thenReturn(0);

        service.createConfig(req);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(configMapper).insertConfig(any(WebEmbedConfig.class), json.capture());
        assertThat(JSON.parseObject(json.getValue())).containsEntry("theme", "dark");
    }

    // ==================== updateConfig ====================

    @Test
    @DisplayName("updateConfig: id 为空 / 不存在 / key 重复 → IAE")
    void updateConfig_validation() {
        WebEmbedConfigRequest req = new WebEmbedConfigRequest();
        req.setConfigKey("k");
        req.setConfigName("n");
        req.setUserId("u");
        req.setComponentType("task-list");

        req.setId(null);
        assertThatThrownBy(() -> service.updateConfig(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id 不能为空");

        req.setId(404L);
        when(configMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> service.updateConfig(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("配置不存在");

        when(configMapper.selectById(404L)).thenReturn(config(404L, "k", "task-list"));
        when(configMapper.countByConfigKeyExcludeId("k", 404L)).thenReturn(2);
        assertThatThrownBy(() -> service.updateConfig(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("configKey 已存在");
    }

    @Test
    @DisplayName("updateConfig: 成功路径带 config 序列化")
    void updateConfig_success() {
        WebEmbedConfigRequest req = new WebEmbedConfigRequest();
        req.setId(5L);
        req.setConfigKey("ek-up");
        req.setConfigName("n");
        req.setUserId("u");
        req.setComponentType("task-detail");
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("showSteps", false);
        req.setConfig(cfg);

        when(configMapper.selectById(5L)).thenReturn(config(5L, "ek-up", "task-detail"));
        when(configMapper.countByConfigKeyExcludeId("ek-up", 5L)).thenReturn(0);

        service.updateConfig(req);

        ArgumentCaptor<WebEmbedConfig> captor = ArgumentCaptor.forClass(WebEmbedConfig.class);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(configMapper).updateConfig(captor.capture(), json.capture());
        assertThat(captor.getValue().getId()).isEqualTo(5L);
        assertThat(JSON.parseObject(json.getValue())).containsEntry("showSteps", false);
    }

    @Test
    @DisplayName("updateConfig: config 为 null 时传 {}")
    void updateConfig_nullConfig_emptyJson() {
        WebEmbedConfigRequest req = new WebEmbedConfigRequest();
        req.setId(6L);
        req.setConfigKey("ek-up2");
        req.setConfigName("n");
        req.setUserId("u");
        req.setComponentType("task-list");

        when(configMapper.selectById(6L)).thenReturn(config(6L, "ek-up2", "task-list"));
        when(configMapper.countByConfigKeyExcludeId("ek-up2", 6L)).thenReturn(0);

        service.updateConfig(req);
        verify(configMapper).updateConfig(any(WebEmbedConfig.class), eq("{}"));
    }

    // ==================== deleteConfig / toggleEnabled ====================

    @Test
    @DisplayName("deleteConfig: id 为空 → IAE; 成功走逻辑删除 update")
    void deleteConfig() {
        assertThatThrownBy(() -> service.deleteConfig(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id 不能为空");

        when(configMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        service.deleteConfig(9L);
        verify(configMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("toggleEnabled: id 为空 → IAE; 成功走 update")
    void toggleEnabled() {
        assertThatThrownBy(() -> service.toggleEnabled(null, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("id 不能为空");

        when(configMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        service.toggleEnabled(10L, 0);
        verify(configMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    // ==================== URL 生成 ====================

    @Test
    @DisplayName("generateEmbedUrl: 带/不带 taskId")
    void generateEmbedUrl() {
        assertThat(service.generateEmbedUrl("ek-a", "task-list", "123"))
                .isEqualTo("/web-embed/task-list?accessKey=ek-a&taskId=123");
        assertThat(service.generateEmbedUrl("ek-a", "task-list", null))
                .isEqualTo("/web-embed/task-list?accessKey=ek-a");
        assertThat(service.generateEmbedUrl("ek-a", "task-list", ""))
                .isEqualTo("/web-embed/task-list?accessKey=ek-a");
    }

    @Test
    @DisplayName("generateAbsoluteEmbedUrl: 前缀 embedBaseUrl")
    void generateAbsoluteEmbedUrl() {
        assertThat(service.generateAbsoluteEmbedUrl("ek-b", "task-card", null))
                .isEqualTo("http://embed.example.com/web-embed/task-card?accessKey=ek-b");
    }
}
