package fun.commons.lotask4j.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.commons.lotask4j.entity.AstsOutbox;
import org.apache.ibatis.annotations.Mapper;

/**
 * Webhook 投递 outbox Mapper
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Mapper
public interface AstsOutboxMapper extends BaseMapper<AstsOutbox> {
}
