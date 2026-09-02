package fun.commons.lotask4j.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.commons.lotask4j.entity.AstTaskTypeConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 任务类型配置 Mapper
 */
@Mapper
public interface AstTaskTypeConfigMapper extends BaseMapper<AstTaskTypeConfig> {

    /**
     * 根据 typeKey 查询任务类型配置
     */
    /**
     * 按类型标识查询配置。
     *
     * @param typeKey  类型标识
     * @param tenantId 租户过滤 (null=全局语义: 平台管理域用; client/worker 侧必须传 claim 租户)
     */
    AstTaskTypeConfig selectByTypeKey(@Param("typeKey") String typeKey,
                                      @Param("tenantId") Long tenantId);

    /**
     * 检查任务类型是否启用
     */
    Boolean isTypeEnabled(@Param("typeKey") String typeKey);

    /**
     * 获取任务类型的并发限制
     */
    Integer getConcurrencyLimit(@Param("typeKey") String typeKey);
}
