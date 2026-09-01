package fun.commons.lotask4j.tenant;

import fun.commons.framework4j.tenant.tck.TenantComplianceSuite;
import fun.commons.lotask4j.AstsApplication;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

/**
 * lotask4j 租户合规断言 (tenant-tck; 中间件中台租户设计 §10 的机器可执行版)。
 * <p>
 * 结构断言 T1-T3 (租户表契约列 / 业务表 tenant_id 索引打头 / email 部分唯一索引) 直接跑;
 * 行为断言 T4-T8 (双面 403/防爆破/reset 撤销/X-User-Id) 由
 * ClientWorkerAuthGuardTest / AdminTenantControllerTest / AdminAuthGuardTest 覆盖, 此处不重放。
 * <p>
 * 前置: 本地 PG (:5432, schema-postgres.sql 重建)。
 */
@SpringBootTest(classes = AstsApplication.class)
@ActiveProfiles("test")
@DisplayName("租户合规结构断言 (tenant-tck T1-T3)")
public class TenantComplianceTest extends TenantComplianceSuite {

    @Override
    public TenantComplianceContext complianceContext() {
        return new TenantComplianceContext() {
            @Override
            public String tenantTable() {
                return "asts_tenant";
            }

            @Override
            public List<String> businessTables() {
                return List.of("asts_task", "asts_task_execution_event", "asts_task_type_config",
                        "asts_web_embed_config", "asts_worker_node");
            }
        };
    }
}
