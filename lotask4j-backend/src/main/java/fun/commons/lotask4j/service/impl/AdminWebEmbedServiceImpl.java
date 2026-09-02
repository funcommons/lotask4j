package fun.commons.lotask4j.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import fun.commons.lotask4j.dto.WebEmbedConfigRequest;
import fun.commons.lotask4j.dto.WebEmbedConfigResponse;
import fun.commons.lotask4j.entity.WebEmbedConfig;
import fun.commons.lotask4j.mapper.WebEmbedConfigMapper;
import fun.commons.lotask4j.service.AdminWebEmbedService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin Web Embed 服务实现
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@Service
public class AdminWebEmbedServiceImpl implements AdminWebEmbedService {

    @Autowired
    private WebEmbedConfigMapper configMapper;

    @Value("${app.web-embed.embed-base-url:http://localhost:9080}")
    private String embedBaseUrl;

    @Override
    public List<WebEmbedConfigResponse> listConfigs(String keyword, Integer isEnabled, Long page, Long pageSize) {
        if (page == null || page < 1) page = 1L;
        if (pageSize == null || pageSize < 1) pageSize = 20L;
        Long offset = (page - 1) * pageSize;

        List<WebEmbedConfig> list = configMapper.selectPageList(offset, pageSize, keyword, isEnabled);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public long countConfigs(String keyword, Integer isEnabled) {
        return configMapper.countList(keyword, isEnabled);
    }

    @Override
    public WebEmbedConfigResponse getConfig(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        WebEmbedConfig config = configMapper.selectById(id);
        // 逻辑删除的配置视为不存在 (selectById 不过滤 is_deleted, 与列表/嵌入流行为对齐)
        if (config == null || Integer.valueOf(1).equals(config.getIsDeleted())) {
            throw new IllegalArgumentException("配置不存在");
        }
        return toResponse(config);
    }

    @Override
    public Long createConfig(WebEmbedConfigRequest request) {
        // 0. 租户归属必填 (embed 短期 token 的租户 claim 来源, 平台替租户建配置)
        if (request.getTenantId() == null) {
            throw new IllegalArgumentException("tenantId 不能为空");
        }

        // 1. 校验 configKey 唯一
        int count = configMapper.countByConfigKeyExcludeId(request.getConfigKey(), null);
        if (count > 0) {
            throw new IllegalArgumentException("configKey 已存在: " + request.getConfigKey());
        }

        // 2. 构建实体
        WebEmbedConfig config = new WebEmbedConfig();
        BeanUtils.copyProperties(request, config);

        // 3. 设置默认值
        if (config.getIsOpen() == null) config.setIsOpen(0);
        if (config.getConfig() == null) config.setConfig(new java.util.HashMap<>());
        if (config.getComponentType() == null || config.getComponentType().isEmpty()) {
            config.setComponentType("all");
        }

        // 4. 插入（雪花算法自动生成 id; config 已在默认值步骤保证非 null）
        String configJson = JSON.toJSONString(config.getConfig());
        configMapper.insertConfig(config, configJson);
        log.info("[Admin] 创建 Web Embed 配置: id={}, key={}", config.getId(), config.getConfigKey());
        return config.getId();
    }

    @Override
    public void updateConfig(WebEmbedConfigRequest request) {
        if (request.getId() == null) {
            throw new IllegalArgumentException("id 不能为空");
        }

        // 1. 校验存在
        WebEmbedConfig exist = configMapper.selectById(request.getId());
        if (exist == null) {
            throw new IllegalArgumentException("配置不存在");
        }

        // 2. 校验 configKey 唯一
        int count = configMapper.countByConfigKeyExcludeId(request.getConfigKey(), request.getId());
        if (count > 0) {
            throw new IllegalArgumentException("configKey 已存在: " + request.getConfigKey());
        }

        // 3. 更新 (tenantId 可选: 传入即变更归属, 缺省保留原归属 — XML 条件更新)
        WebEmbedConfig update = new WebEmbedConfig();
        BeanUtils.copyProperties(request, update);
        update.setId(request.getId());

        String configJson = update.getConfig() != null ? JSON.toJSONString(update.getConfig()) : "{}";
        configMapper.updateConfig(update, configJson);
        log.info("[Admin] 更新 Web Embed 配置: id={}", request.getId());
    }

    @Override
    public void deleteConfig(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        // 逻辑删除
        LambdaUpdateWrapper<WebEmbedConfig> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(WebEmbedConfig::getIsDeleted, 1)
                .set(WebEmbedConfig::getUpdatedAt, OffsetDateTime.now())
                .eq(WebEmbedConfig::getId, id);
        configMapper.update(null, wrapper);
        log.info("[Admin] 删除 Web Embed 配置: id={}", id);
    }

    @Override
    public void toggleEnabled(Long id, Integer isEnabled) {
        if (id == null) {
            throw new IllegalArgumentException("id 不能为空");
        }
        LambdaUpdateWrapper<WebEmbedConfig> wrapper = new LambdaUpdateWrapper<>();
        wrapper.set(WebEmbedConfig::getIsEnabled, isEnabled)
                .set(WebEmbedConfig::getUpdatedAt, OffsetDateTime.now())
                .eq(WebEmbedConfig::getId, id);
        configMapper.update(null, wrapper);
        log.info("[Admin] 切换 Web Embed 状态: id={}, isEnabled={}", id, isEnabled);
    }

    @Override
    public String generateEmbedUrl(String configKey, String componentType, String taskId) {
        StringBuilder url = new StringBuilder()
                .append("/web-embed/").append(componentType)
                .append("?accessKey=").append(configKey);
        if (taskId != null && !taskId.isEmpty()) {
            url.append("&taskId=").append(taskId);
        }
        return url.toString();
    }

    /**
     * 生成绝对嵌入 URL（业务方嵌入用）
     */
    public String generateAbsoluteEmbedUrl(String configKey, String componentType, String taskId) {
        return embedBaseUrl + generateEmbedUrl(configKey, componentType, taskId);
    }

    // ==================== 辅助方法 ====================

    private WebEmbedConfigResponse toResponse(WebEmbedConfig config) {
        WebEmbedConfigResponse resp = new WebEmbedConfigResponse();
        BeanUtils.copyProperties(config, resp);
        // 自动生成嵌入 URL（指向 task-list）
        if (config.getConfigKey() != null) {
            resp.setEmbedUrl(generateEmbedUrl(config.getConfigKey(), "task-list", null));
        }
        return resp;
    }
}
