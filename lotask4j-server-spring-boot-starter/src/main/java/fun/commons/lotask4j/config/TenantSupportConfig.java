package fun.commons.lotask4j.config;

import fun.commons.framework4j.tenant.schema.TenantSchema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * framework4j-tenant 接入配置: 实体子类 SPI (契约层冻结字段, 表名 = {table-prefix}tenant = asts_tenant)。
 * <p>
 * TenantStore 由此找到 {@link fun.commons.lotask4j.entity.AstsTenant} 与
 * {@link fun.commons.lotask4j.mapper.AstsTenantMapper}, 内置认证端点
 * (POST /api/v1/auth/token) 与 TenantSecretService 的租户读写均经此通道。
 */
@Configuration
public class TenantSupportConfig {

    @Bean
    @ConditionalOnMissingBean
    public TenantSchema tenantSchema() {
        return () -> fun.commons.lotask4j.entity.AstsTenant.class;
    }
}
