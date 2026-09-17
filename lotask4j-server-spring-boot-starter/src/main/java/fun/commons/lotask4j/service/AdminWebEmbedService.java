package fun.commons.lotask4j.service;

import fun.commons.lotask4j.dto.WebEmbedConfigRequest;
import fun.commons.lotask4j.dto.WebEmbedConfigResponse;

import java.util.List;

/**
 * Admin Web Embed 服务
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
public interface AdminWebEmbedService {

    /**
     * 分页查询配置列表
     */
    List<WebEmbedConfigResponse> listConfigs(String keyword, Integer isEnabled, Long page, Long pageSize);

    /**
     * 统计总数
     */
    long countConfigs(String keyword, Integer isEnabled);

    /**
     * 获取单个配置
     */
    WebEmbedConfigResponse getConfig(Long id);

    /**
     * 创建配置
     */
    Long createConfig(WebEmbedConfigRequest request);

    /**
     * 更新配置
     */
    void updateConfig(WebEmbedConfigRequest request);

    /**
     * 删除配置（逻辑删除）
     */
    void deleteConfig(Long id);

    /**
     * 启用/禁用
     */
    void toggleEnabled(Long id, Integer isEnabled);

    /**
     * 生成嵌入 URL（供预览用，相对路径）
     */
    String generateEmbedUrl(String configKey, String componentType, String taskId);

    /**
     * 生成绝对嵌入 URL（业务方嵌入用）
     */
    String generateAbsoluteEmbedUrl(String configKey, String componentType, String taskId);
}
