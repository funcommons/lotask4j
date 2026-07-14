package fun.commons.lotask4j.service;

import fun.commons.lotask4j.entity.WebEmbedConfig;

/**
 * Web Embed 服务接口
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
public interface WebEmbedService {

    /**
     * 处理访问请求，返回用户 ID（用于写入 Cookie）
     *
     * 开放模式：返回配置的默认 userId
     * 鉴权模式：验证 accessKey + 回调，返回 userId
     *
     * @param accessKey 访问密钥（可为 null，表示开放模式）
     * @return 用户 ID
     * @throws RuntimeException 验证失败时抛出
     */
    String handleAccess(String accessKey);

    /**
     * 验证组件类型是否合法
     *
     * @param componentType 组件类型
     * @return true 合法
     */
    boolean isValidComponentType(String componentType);

    /**
     * 校验 accessKey 是否可用于指定组件
     */
    void checkComponentAccess(String accessKey, String componentType);

    /**
     * 获取组件类型对应的默认配置（JSONB 中对应组件的 config）
     *
     * @param accessKey 访问密钥（开放模式为 null）
     * @param componentType 组件类型
     * @return 组件配置 Map
     */
    java.util.Map<String, Object> getComponentConfig(String accessKey, String componentType);

    /**
     * 根据 accessKey 获取配置
     */
    WebEmbedConfig getConfig(String accessKey);
}
