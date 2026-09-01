package fun.commons.lotask4j.demo.service;

import fun.commons.lotask4j.entity.WebEmbedConfig;
import fun.commons.lotask4j.mapper.WebEmbedConfigMapper;
import fun.commons.lotask4j.service.CallbackService;
import fun.commons.lotask4j.service.impl.WebEmbedServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * WebEmbedServiceImpl 纯单元测试（accessKey 鉴权 + 组件默认配置）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WebEmbedService 单元测试")
class WebEmbedServiceImplTest {

    @Mock
    private WebEmbedConfigMapper configMapper;

    @Mock
    private CallbackService callbackService;

    @InjectMocks
    private WebEmbedServiceImpl service;

    private static WebEmbedConfig config(String key, boolean open, String callbackUrl, String componentType) {
        WebEmbedConfig c = new WebEmbedConfig();
        c.setConfigKey(key);
        c.setIsOpen(open ? 1 : 0);
        c.setCallbackUrl(callbackUrl);
        c.setComponentType(componentType);
        return c;
    }

    // ==================== handleAccess ====================

    @Test
    @DisplayName("handleAccess: 无 accessKey → 开放模式返回 null")
    void handleAccess_noKey() {
        assertThat(service.handleAccess(null)).isNull();
        assertThat(service.handleAccess("")).isNull();
        verify(configMapper, never()).selectByConfigKey(anyString());
    }

    @Test
    @DisplayName("handleAccess: 无效 accessKey → IAE")
    void handleAccess_invalidKey() {
        when(configMapper.selectByConfigKey("bad")).thenReturn(null);
        assertThatThrownBy(() -> service.handleAccess("bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无效的 accessKey");
    }

    @Test
    @DisplayName("handleAccess: 开放模式 accessKey 直接通过, 不走回调")
    void handleAccess_openMode() {
        WebEmbedConfig c = config("ek-open", true, null, "task-list");
        when(configMapper.selectByConfigKey("ek-open")).thenReturn(c);
        assertThat(service.handleAccess("ek-open")).isSameAs(c);
        verify(callbackService, never()).verify(anyString(), anyString());
    }

    @Test
    @DisplayName("handleAccess: 鉴权模式带 callbackUrl → 走 verify")
    void handleAccess_authMode_withCallback() {
        WebEmbedConfig c = config("ek-auth", false, "https://biz.example.com/verify", "task-list");
        when(configMapper.selectByConfigKey("ek-auth")).thenReturn(c);
        assertThat(service.handleAccess("ek-auth")).isSameAs(c);
        verify(callbackService).verify("https://biz.example.com/verify", "ek-auth");
    }

    @Test
    @DisplayName("handleAccess: 鉴权模式无 callbackUrl → 直接通过")
    void handleAccess_authMode_noCallback() {
        WebEmbedConfig c = config("ek-auth2", false, null, "task-list");
        WebEmbedConfig c2 = config("ek-auth2", false, "", "task-list");
        when(configMapper.selectByConfigKey("ek-auth2")).thenReturn(c2);
        assertThat(service.handleAccess("ek-auth2")).isSameAs(c2);
        verify(callbackService, never()).verify(anyString(), anyString());
    }

    // ==================== checkComponentAccess ====================

    @Test
    @DisplayName("checkComponentAccess: 空 key 开放无限制")
    void checkComponentAccess_noKey() {
        assertThatCode(() -> service.checkComponentAccess(null, "task-list")).doesNotThrowAnyException();
        assertThatCode(() -> service.checkComponentAccess("", "task-list")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("checkComponentAccess: 无效 key / 组件不匹配 → IAE")
    void checkComponentAccess_mismatch() {
        when(configMapper.selectByConfigKey("bad")).thenReturn(null);
        assertThatThrownBy(() -> service.checkComponentAccess("bad", "task-list"))
                .isInstanceOf(IllegalArgumentException.class);

        WebEmbedConfig c = config("ek-m", false, null, "task-list");
        when(configMapper.selectByConfigKey("ek-m")).thenReturn(c);
        assertThatThrownBy(() -> service.checkComponentAccess("ek-m", "task-card"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不允许用于组件");
    }

    @Test
    @DisplayName("checkComponentAccess: 配置 componentType 空/null → IAE; 匹配 → 通过")
    void checkComponentAccess_componentValidation() {
        WebEmbedConfig nullType = config("ek-n", false, null, null);
        when(configMapper.selectByConfigKey("ek-n")).thenReturn(nullType);
        assertThatThrownBy(() -> service.checkComponentAccess("ek-n", "task-list"))
                .isInstanceOf(IllegalArgumentException.class);

        WebEmbedConfig emptyType = config("ek-e", false, null, "");
        when(configMapper.selectByConfigKey("ek-e")).thenReturn(emptyType);
        assertThatThrownBy(() -> service.checkComponentAccess("ek-e", "task-list"))
                .isInstanceOf(IllegalArgumentException.class);

        WebEmbedConfig matched = config("ek-m2", false, null, "task-detail");
        when(configMapper.selectByConfigKey("ek-m2")).thenReturn(matched);
        assertThatCode(() -> service.checkComponentAccess("ek-m2", "task-detail")).doesNotThrowAnyException();
    }

    // ==================== isValidComponentType ====================

    @Test
    @DisplayName("isValidComponentType: 三合法值 true, 其他/null false")
    void isValidComponentType() {
        assertThat(service.isValidComponentType("task-list")).isTrue();
        assertThat(service.isValidComponentType("task-detail")).isTrue();
        assertThat(service.isValidComponentType("task-card")).isTrue();
        assertThat(service.isValidComponentType("all")).isFalse();
        assertThat(service.isValidComponentType(null)).isFalse();
    }

    // ==================== getComponentConfig ====================

    @Test
    @DisplayName("getComponentConfig: task-list 默认配置")
    void getComponentConfig_taskList() {
        Map<String, Object> cfg = service.getComponentConfig(null, "task-list");
        assertThat(cfg.get("theme")).isEqualTo("light");
        assertThat(cfg.get("autoRefresh")).isEqualTo(true);
        assertThat(cfg.get("pageSize")).isEqualTo(20);
        assertThat((java.util.List<String>) cfg.get("showColumns"))
                .containsExactly("id", "type", "status", "progress", "createdAt");
        assertThat(cfg.get("enableCancel")).isEqualTo(true);
        assertThat(cfg.get("enableSubmit")).isEqualTo(true);
    }

    @Test
    @DisplayName("getComponentConfig: task-detail / task-card 默认配置")
    void getComponentConfig_otherTypes() {
        Map<String, Object> detail = service.getComponentConfig(null, "task-detail");
        assertThat(detail.get("showSteps")).isEqualTo(true);
        assertThat(detail.get("showResult")).isEqualTo(true);
        assertThat(detail.get("showTimeline")).isEqualTo(true);

        Map<String, Object> card = service.getComponentConfig(null, "task-card");
        assertThat(card.get("showProgress")).isEqualTo(true);
        assertThat(card.get("showProgress")).isEqualTo(true);
        assertThat(card.get("compact")).isEqualTo(true);
    }

    @Test
    @DisplayName("getComponentConfig: 未知组件类型只有基础默认")
    void getComponentConfig_unknownType() {
        Map<String, Object> cfg = service.getComponentConfig(null, "no-such");
        assertThat(cfg).containsOnlyKeys("theme", "autoRefresh");
    }

    @Test
    @DisplayName("getComponentConfig: 鉴权模式下 DB 配置覆盖默认 (Map 才合并)")
    void getComponentConfig_mergesDbOverride() {
        WebEmbedConfig c = config("ek-ov", true, null, "task-list");
        Map<String, Object> inner = new HashMap<>();
        inner.put("pageSize", 50);
        inner.put("extra", "x");
        Map<String, Object> outer = new HashMap<>();
        outer.put("task-list", inner);
        c.setConfig(outer);
        when(configMapper.selectByConfigKey("ek-ov")).thenReturn(c);

        Map<String, Object> merged = service.getComponentConfig("ek-ov", "task-list");
        assertThat(merged.get("pageSize")).isEqualTo(50);
        assertThat(merged.get("extra")).isEqualTo("x");
        assertThat(merged.get("theme")).isEqualTo("light");
    }

    @Test
    @DisplayName("getComponentConfig: 配置为 null 或覆盖值非 Map → 用默认")
    void getComponentConfig_nonMapOverrideIgnored() {
        WebEmbedConfig nullConfig = config("ek-nc", true, null, "task-list");
        nullConfig.setConfig(null);
        when(configMapper.selectByConfigKey("ek-nc")).thenReturn(nullConfig);
        assertThat(service.getComponentConfig("ek-nc", "task-list").get("pageSize")).isEqualTo(20);

        WebEmbedConfig badType = config("ek-bt", true, null, "task-list");
        Map<String, Object> outer = new HashMap<>();
        outer.put("task-list", "not-a-map");
        badType.setConfig(outer);
        when(configMapper.selectByConfigKey("ek-bt")).thenReturn(badType);
        assertThat(service.getComponentConfig("ek-bt", "task-list").get("pageSize")).isEqualTo(20);
    }

    // ==================== getConfig ====================

    @Test
    @DisplayName("getConfig: 空 key → null; 有 key → 查 DB")
    void getConfig() {
        assertThat(service.getConfig(null)).isNull();
        assertThat(service.getConfig("")).isNull();
        WebEmbedConfig c = config("ek-g", true, null, "task-list");
        when(configMapper.selectByConfigKey("ek-g")).thenReturn(c);
        assertThat(service.getConfig("ek-g")).isSameAs(c);
    }
}
