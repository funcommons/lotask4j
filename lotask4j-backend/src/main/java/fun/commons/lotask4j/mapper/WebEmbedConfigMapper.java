package fun.commons.lotask4j.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.commons.lotask4j.entity.WebEmbedConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Web Embed 配置 Mapper
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Mapper
public interface WebEmbedConfigMapper extends BaseMapper<WebEmbedConfig> {

    /**
     * 根据 configKey 查询配置
     */
    WebEmbedConfig selectByConfigKey(@Param("configKey") String configKey);

    /**
     * 分页查询配置列表
     */
    List<WebEmbedConfig> selectPageList(@Param("offset") Long offset,
                                        @Param("limit") Long limit,
                                        @Param("keyword") String keyword,
                                        @Param("isEnabled") Integer isEnabled);

    /**
     * 统计总数
     */
    long countList(@Param("keyword") String keyword,
                   @Param("isEnabled") Integer isEnabled);

    /**
     * 检查 configKey 是否存在（排除自己）
     */
    int countByConfigKeyExcludeId(@Param("configKey") String configKey,
                                  @Param("excludeId") Long excludeId);

    /**
     * 插入配置（处理 JSONB 字段）
     */
    int insertConfig(@Param("config") WebEmbedConfig config,
                     @Param("configJson") String configJson);

    /**
     * 更新配置（处理 JSONB 字段）
     */
    int updateConfig(@Param("config") WebEmbedConfig config,
                     @Param("configJson") String configJson);
}
