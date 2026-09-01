package fun.commons.lotask4j.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import fun.commons.framework4j.tenant.entity.TenantEntity;

/**
 * 租户表实体 (实体子类 SPI, framework4j-tenant 接入; benefit4j UbmaTenant 同款)。
 * <p>
 * 字段全部继承 {@link TenantEntity} (契约层冻结: id=租户 id 雪花/四类配置 JSONB/
 * 密钥 AES-GCM 双版本宽限期/生命周期状态机); 表名守项目简码规范 asts_tenant
 * (由 asts_application 演进而来, 见 Flyway V4)。
 * {@code autoResultMap = true} 必须 —— 密钥列 typeHandler select 解密依赖它。
 */
@TableName(value = "asts_tenant", autoResultMap = true)
public class AstsTenant extends TenantEntity {
}
