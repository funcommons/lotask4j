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
    AstTaskTypeConfig selectByTypeKey(@Param("typeKey") String typeKey);

    /**
     * 检查任务类型是否启用
     */
    Boolean isTypeEnabled(@Param("typeKey") String typeKey);

    /**
     * 获取任务类型的并发限制
     */
    Integer getConcurrencyLimit(@Param("typeKey") String typeKey);
}
