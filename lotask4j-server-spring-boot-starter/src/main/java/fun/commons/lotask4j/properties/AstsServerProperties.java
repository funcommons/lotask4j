package fun.commons.lotask4j.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * ASTS 服务配置 (lotask4j.* 命名空间, issue #4 starter 化).
 *
 * <p>仅承载本服务自有配置; framework4j.* 键由 SDK 自行消费 (宿主侧声明, 保持原样不改名),
 * 必填项校验见 {@code AstsRequiredPropertiesChecker}。
 *
 * <p>注意: {@code @Scheduled} 的 fixedRateString/fixedDelayString 占位符无法读取本 bean,
 * {@code lotask4j.asts.reaper-interval} / {@code lotask4j.asts.outbox-interval} 在
 * schedule 包内以字面量占位符引用 (默认值与此处字段保持一致, 改动需双侧同步)。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "lotask4j")
public class AstsServerProperties {

    /** 异步线程池 (原 app.async.*) */
    private Async async = new Async();

    /** ASTS 任务域调优 (原 app.asts.*) */
    private Asts asts = new Asts();

    /** Webhook 回调 (原 app.webhook.*) */
    private Webhook webhook = new Webhook();

    /** 步骤上报 (原 app.steps.*) */
    private Steps steps = new Steps();

    /** Web Embed 组件 (原 app.web-embed.*) */
    private WebEmbed webEmbed = new WebEmbed();

    /** Flyway 迁移 (原 spring.flyway.* — starter 自管, 独立直连绕过 Druid Wall) */
    private Flyway flyway = new Flyway();

    /** 安全管控 (严格模式下检测到默认密钥直接拒绝启动) */
    private Security security = new Security();

    /** 异步线程池配置 */
    @Getter
    @Setter
    public static class Async {
        /** 核心线程数 */
        private int corePoolSize = 10;
        /** 最大线程数 */
        private int maxPoolSize = 50;
        /** 队列容量 */
        private int queueCapacity = 1000;
        /** 线程名称前缀 */
        private String threadNamePrefix = "async-executor-";
        /** 等待任务完成后再关闭 */
        private boolean waitForTasksToCompleteOnShutdown = true;
        /** 关闭等待时间（秒） */
        private int awaitTerminationSeconds = 60;
    }

    /** ASTS 任务域调优配置 */
    @Getter
    @Setter
    public static class Asts {
        /** Worker 心跳间隔（秒）— worker 侧契约参考值 */
        private int heartbeatInterval = 30;
        /** Reaper 清理间隔（毫秒）— @Scheduled fixedRateString 字面量占位符同源 */
        private int reaperInterval = 60000;
        /** 任务执行超时时间（秒）— 前端/worker 展示参考值 */
        private int defaultTimeout = 600;
        /** 最大重试次数 — 展示参考值 */
        private int maxRetries = 3;
        /** 默认 lease 秒数（worker poll 之后持锁时长，到期不续约视为失联）— TaskStateMachine 读取 */
        private int defaultLeaseSeconds = 120;
        /** Reaper 宽限秒数（lease 过期后再等这么久才回收，防边界误杀）— TaskReaper 读取 */
        private int leaseGraceSeconds = 30;
        /** 续约允许时间余量（lease_buffer_sec）：worker 必须早于 lease_expire_at 这么久发起续约 — worker 侧契约 */
        private int leaseBufferSeconds = 20;
        /** 并发执行任务数 — worker 侧契约参考值 */
        private int workerConcurrency = 5;
        /** 轮询任务的间隔（秒）— worker 侧契约参考值 */
        private int pollInterval = 1;
        /** Outbox 扫描间隔（毫秒）— @Scheduled fixedDelayString 字面量占位符同源 */
        private int outboxInterval = 5000;
    }

    /** Webhook 回调配置 */
    @Getter
    @Setter
    public static class Webhook {
        /** 最大重试次数 — OutboxPublisher 退避上限参考值 */
        private int maxRetries = 3;
        /** 重试间隔（秒） */
        private int retryInterval = 5;
        /** 请求超时（秒）— HttpClientConfig#webhookRestTemplate 读取 */
        private int timeoutSeconds = 30;
    }

    /** 步骤上报配置 */
    @Getter
    @Setter
    public static class Steps {
        /** 最大步骤数 */
        private int maxSteps = 100;
        /** 步骤更新间隔（毫秒） */
        private int updateInterval = 1000;
    }

    /** Web Embed 组件配置 */
    @Getter
    @Setter
    public static class WebEmbed {
        /** 前端入口 URL（部署在 /web-embed/ 路径下）— WebEmbedController 302 目标 */
        private String frontBaseUrl = "/web-embed/index.html";
        /** 开放模式默认 userId — WebEmbedController / WebEmbedServiceImpl 读取 */
        private String openDefaultUserId = "guest";
        /** 回调验证超时（秒）— HttpClientConfig#callbackRestTemplate 读取 */
        private int callbackTimeoutSeconds = 3;
        /** Cookie 有效期（秒）— WebEmbedController 读取 */
        private int cookieExpireSeconds = 7200;
        /** 嵌入 URL 基础地址（后台生成预览 URL）— AdminWebEmbedServiceImpl 读取 */
        private String embedBaseUrl = "http://localhost:9080";
    }

    /** Flyway 迁移配置（独立直连, 绕过 Druid Wall 对 PG 语法 的误拦） */
    @Getter
    @Setter
    public static class Flyway {
        /** 是否启用启动期迁移 (家族 DDF 宿主投放 DDL 时关闭) */
        private boolean enabled = true;
        /** 迁移目标库 URL (必填-when-enabled) */
        private String url;
        /** 迁移目标库用户名 (必填-when-enabled) */
        private String user;
        /** 迁移目标库密码 (可为空) */
        private String password = "";
        /** 迁移脚本位置 */
        private List<String> locations = new ArrayList<>(List.of("classpath:db/migration"));
        /** 存量库 baseline (已有表只打标记不执行 V1) */
        private boolean baselineOnMigrate = true;
        /** baseline 版本 */
        private String baselineVersion = "1";
        /** 迁移后校验 */
        private boolean validateOnMigrate = true;
    }

    /** 安全管控配置 */
    @Getter
    @Setter
    public static class Security {
        /**
         * 严格模式: 检测到默认密钥 (access-token.secretKey / sensitive.encryption-key /
         * tenant.platform.client-secret) 直接拒绝启动; 默认 false 仅告警 (保 dev 开箱可跑)。
         */
        private boolean strict = false;
    }
}
