package fun.commons.lotask4j.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.commons.lotask4j.entity.AstsTenant;
import org.apache.ibatis.annotations.Mapper;

/**
 * 租户表 Mapper (framework4j-tenant TenantStore SPI 侧车)。
 * <p>
 * MyBatisTenantStore 通过 TenantSchema 找到本 Mapper 完成租户查找/更新
 * (内置认证端点换 token、TenantSecretService reset 均经此)。
 */
@Mapper
public interface AstsTenantMapper extends BaseMapper<AstsTenant> {
}
