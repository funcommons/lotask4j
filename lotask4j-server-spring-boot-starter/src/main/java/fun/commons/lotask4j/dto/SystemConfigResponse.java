package fun.commons.lotask4j.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 系统配置信息响应 DTO
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Getter
@Setter
@Schema(description = "系统配置信息")
public class SystemConfigResponse {

    @Schema(description = "系统基本信息")
    private SystemInfo systemInfo;

    @Schema(description = "数据库配置")
    private DatabaseConfig databaseConfig;

    @Schema(description = "Redis 配置")
    private RedisConfig redisConfig;

    @Schema(description = "JVM 信息")
    private JvmInfo jvmInfo;

    @Schema(description = "任务统计")
    private TaskStats taskStats;

    @Getter
    @Setter
    @Schema(description = "系统基本信息")
    public static class SystemInfo {
        @Schema(description = "应用名称", example = "lotask4j ASTS")
        private String appName;

        @Schema(description = "应用版本", example = "1.0.0-SNAPSHOT")
        private String appVersion;

        @Schema(description = "Spring Boot 版本", example = "3.2.0")
        private String springBootVersion;

        @Schema(description = "Java 版本", example = "17.0.8")
        private String javaVersion;

        @Schema(description = "操作系统", example = "Windows 10")
        private String osName;

        @Schema(description = "系统架构", example = "amd64")
        private String osArch;

        @Schema(description = "启动时间", example = "2024-01-01 10:00:00")
        private String startTime;

        @Schema(description = "运行时长", example = "2小时30分")
        private String uptime;
    }

    @Getter
    @Setter
    @Schema(description = "数据库配置")
    public static class DatabaseConfig {
        @Schema(description = "数据库类型", example = "PostgreSQL")
        private String type;

        @Schema(description = "数据库版本", example = "14.5")
        private String version;

        @Schema(description = "JDBC URL（隐藏敏感信息）", example = "jdbc:postgresql://localhost:5432/asts")
        private String url;

        @Schema(description = "最大连接数", example = "20")
        private Integer maxPoolSize;

        @Schema(description = "当前活跃连接", example = "5")
        private Integer activeConnections;
    }

    @Getter
    @Setter
    @Schema(description = "Redis 配置")
    public static class RedisConfig {
        @Schema(description = "Redis 模式", example = "Standalone")
        private String mode;

        @Schema(description = "Redis 主机", example = "localhost:6379")
        private String host;

        @Schema(description = "数据库索引", example = "0")
        private Integer database;

        @Schema(description = "连接状态", example = "Connected")
        private String status;
    }

    @Getter
    @Setter
    @Schema(description = "JVM 信息")
    public static class JvmInfo {
        @Schema(description = "JVM 名称", example = "OpenJDK 64-Bit Server VM")
        private String name;

        @Schema(description = "JVM 版本", example = "17.0.8")
        private String version;

        @Schema(description = "最大内存 (MB)", example = "2048")
        private Long maxMemory;

        @Schema(description = "总内存 (MB)", example = "1024")
        private Long totalMemory;

        @Schema(description = "已用内存 (MB)", example = "512")
        private Long usedMemory;

        @Schema(description = "空闲内存 (MB)", example = "512")
        private Long freeMemory;

        @Schema(description = "CPU 核心数", example = "8")
        private Integer cpuCores;

        @Schema(description = "线程总数", example = "45")
        private Integer threadCount;
    }

    @Getter
    @Setter
    @Schema(description = "任务统计")
    public static class TaskStats {
        @Schema(description = "任务总数", example = "1000")
        private Long totalTasks;

        @Schema(description = "待处理任务", example = "10")
        private Long pendingTasks;

        @Schema(description = "运行中任务", example = "5")
        private Long runningTasks;

        @Schema(description = "成功任务", example = "950")
        private Long successTasks;

        @Schema(description = "失败任务", example = "30")
        private Long failedTasks;

        @Schema(description = "已取消任务", example = "5")
        private Long cancelledTasks;

        @Schema(description = "任务类型数量", example = "3")
        private Integer taskTypeCount;

        @Schema(description = "在线 Worker 数", example = "2")
        private Integer onlineWorkerCount;
    }
}
