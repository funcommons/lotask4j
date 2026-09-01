package fun.commons.lotask4j.service.impl;

import fun.commons.lotask4j.entity.WebEmbedConfig;
import fun.commons.lotask4j.mapper.WebEmbedConfigMapper;
import fun.commons.lotask4j.service.CallbackService;
import fun.commons.lotask4j.service.WebEmbedService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Web Embed 服务实现
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@Service
public class WebEmbedServiceImpl implements WebEmbedService {

    private static final List<String> VALID_COMPONENTS = Arrays.asList(
            "task-list", "task-detail", "task-card"
    );

    @Autowired
    private WebEmbedConfigMapper configMapper;

    @Autowired
    private CallbackService callbackService;

    @Value("${app.web-embed.open-default-user-id:guest}")
    private String openDefaultUserId;

    @Override
    public WebEmbedConfig handleAccess(String accessKey) {
        // 开放模式：无 accessKey (无租户归属 — 多租户模式下不签发 embed token)
        if (accessKey == null || accessKey.isEmpty()) {
            log.debug("[Web Embed] 开放模式访问 (无 accessKey)");
            return null;
        }

        // 鉴权模式：查询配置
        WebEmbedConfig config = configMapper.selectByConfigKey(accessKey);
        if (config == null) {
            throw new IllegalArgumentException("无效的 accessKey");
        }

        // 开放标记的 accessKey：直接通过
        if (config.isOpenMode()) {
            log.debug("[Web Embed] 开放 accessKey 验证通过: accessKey={}", accessKey);
            return config;
        }

        // 鉴权模式：回调验证
        if (config.getCallbackUrl() != null && !config.getCallbackUrl().isEmpty()) {
            callbackService.verify(config.getCallbackUrl(), accessKey);
        }

        log.info("[Web Embed] 鉴权模式验证通过: accessKey={}", accessKey);
        return config;
    }

    /**
     * 校验 accessKey 是否可用于指定组件
     */
    public void checkComponentAccess(String accessKey, String componentType) {
        if (accessKey == null || accessKey.isEmpty()) {
            return;  // 开放模式无限制
        }
        WebEmbedConfig config = configMapper.selectByConfigKey(accessKey);
        if (config == null) {
            throw new IllegalArgumentException("无效的 accessKey");
        }
        // componentType 必填，配置必须与请求的组件类型匹配
        String configComponentType = config.getComponentType();
        if (configComponentType == null || configComponentType.isEmpty()
                || !configComponentType.equals(componentType)) {
            throw new IllegalArgumentException(
                "accessKey " + accessKey + " 不允许用于组件 " + componentType);
        }
    }

    @Override
    public boolean isValidComponentType(String componentType) {
        return componentType != null && VALID_COMPONENTS.contains(componentType);
    }

    @Override
    public Map<String, Object> getComponentConfig(String accessKey, String componentType) {
        Map<String, Object> result = new HashMap<>(getDefaultConfig(componentType));

        // 鉴权模式：从 DB 读取用户配置
        if (accessKey != null && !accessKey.isEmpty()) {
            WebEmbedConfig config = configMapper.selectByConfigKey(accessKey);
            if (config != null && config.getConfig() != null) {
                Object componentConfig = config.getConfig().get(componentType);
                if (componentConfig instanceof Map) {
                    result.putAll((Map<String, Object>) componentConfig);
                }
            }
        }

        return result;
    }

    @Override
    public WebEmbedConfig getConfig(String accessKey) {
        if (accessKey == null || accessKey.isEmpty()) {
            return null;
        }
        return configMapper.selectByConfigKey(accessKey);
    }

    /**
     * 获取内置默认配置
     */
    private Map<String, Object> getDefaultConfig(String componentType) {
        Map<String, Object> config = new HashMap<>();
        config.put("theme", "light");
        config.put("autoRefresh", true);

        switch (componentType) {
            case "task-list":
                config.put("pageSize", 20);
                config.put("showColumns", Arrays.asList("id", "type", "status", "progress", "createdAt"));
                config.put("enableCancel", true);
                config.put("enableSubmit", true);
                break;
            case "task-detail":
                config.put("showSteps", true);
                config.put("showResult", true);
                config.put("showTimeline", true);
                break;
            case "task-card":
                config.put("showProgress", true);
                config.put("compact", true);
                break;
            default:
                break;
        }

        return config;
    }
}
