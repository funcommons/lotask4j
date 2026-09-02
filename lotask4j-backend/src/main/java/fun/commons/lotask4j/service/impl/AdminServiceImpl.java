package fun.commons.lotask4j.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fun.commons.lotask4j.dto.*;
import fun.commons.lotask4j.dto.*;
import fun.commons.lotask4j.enums.BusinessCode;
import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.entity.AstTaskTypeConfig;
import fun.commons.lotask4j.entity.AstWorkerNode;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.mapper.AstTaskTypeConfigMapper;
import fun.commons.lotask4j.mapper.AstWorkerNodeMapper;
import fun.commons.lotask4j.service.AdminService;
import fun.commons.lotask4j.service.TaskService;
import fun.commons.framework4j.web.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin 管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AstWorkerNodeMapper workerNodeMapper;
    private final AstTaskTypeConfigMapper taskTypeConfigMapper;
    private final AstTaskMapper taskMapper;
    private final TaskService taskService;
    private final Environment environment;

    private static final long START_TIME = ManagementFactory.getRuntimeMXBean().getStartTime();

    @Override
    public List<WorkerNodeResponse> getOnlineWorkers() {
        log.debug("Fetching online workers");

        // 查询 status='ONLINE' 的 Worker (由 WorkerCleaner 维护)
        List<AstWorkerNode> workers = workerNodeMapper.selectOnlineWorkers();

        return workers.stream().map(worker -> {
            WorkerNodeResponse response = new WorkerNodeResponse();
            response.setWorkerKey(worker.getWorkerId() != null ? worker.getWorkerId() : String.valueOf(worker.getId()));
            response.setWorkerIp(worker.getWorkerIp());
            response.setHostname(worker.getHostname());
            response.setLastHeartbeatAt(worker.getLastHeartbeatAt());
            response.setStatus(worker.getStatus());
            return response;
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void saveTaskTypeConfig(TaskTypeConfigRequest request) {
        log.info("Saving task type config: {}", request.getTypeKey());

        // 查询是否已存在
        // 定位: 带 request.tenantId → 租户内 upsert (同 typeKey 跨租户共存);
        // tenantId 缺省 → 全局语义 (update 缺省保留原归属的老路径)
        AstTaskTypeConfig existing = taskTypeConfigMapper.selectByTypeKey(request.getTypeKey(), request.getTenantId());

        if (existing != null) {
            // 归属冲突守卫: typeKey 已被其他租户占用时拒绝 (防跨租户静默劫持更新)
            if (request.getTenantId() != null && !request.getTenantId().equals(existing.getTenantId())) {
                throw new IllegalArgumentException(
                        "typeKey 已被其他租户占用: " + request.getTypeKey());
            }
            // 更新现有配置 (tenantId 缺省保留原归属; 显式传入可变更归属)
            if (request.getTenantId() != null) {
                existing.setTenantId(request.getTenantId());
            }
            existing.setName(request.getName());
            existing.setConcurrencyLimit(request.getConcurrencyLimit());
            existing.setTimeoutSeconds(request.getTimeoutSeconds());
            existing.setMaxRetries(request.getMaxRetries());
            existing.setIsEnabled(Boolean.TRUE.equals(request.getIsEnabled()) ? 1 : 0);
            existing.setStepsConfig(request.getStepsConfig());

            taskTypeConfigMapper.updateById(existing);
            log.info("Task type config updated: {}", request.getTypeKey());
        } else {
            // 创建新配置 — 租户归属必填 (V5 起 tenant_id NOT NULL, 且类型是租户级资源)
            if (request.getTenantId() == null) {
                throw new IllegalArgumentException("tenantId 不能为空");
            }
            AstTaskTypeConfig config = new AstTaskTypeConfig();
            config.setTenantId(request.getTenantId());
            config.setTypeKey(request.getTypeKey());
            config.setName(request.getName());
            config.setConcurrencyLimit(request.getConcurrencyLimit());
            config.setTimeoutSeconds(request.getTimeoutSeconds());
            config.setMaxRetries(request.getMaxRetries());
            config.setIsEnabled(Boolean.TRUE.equals(request.getIsEnabled()) ? 1 : 0);
            config.setStepsConfig(request.getStepsConfig());
            config.setCreatedAt(OffsetDateTime.now());
            config.setUpdatedAt(OffsetDateTime.now());
            config.setIsDeleted(0);

            taskTypeConfigMapper.insert(config);
            log.info("Task type config created: {}", request.getTypeKey());
        }
    }

    @Override
    @Transactional
    public SubmitTaskResponse submitTask(SubmitTaskRequest request) {
        log.info("Admin submitting task: type={}", request.getType());

        // 复用 TaskService 的提交任务逻辑
        Long id = taskService.submitTask(request);

        // 封装为响应对象
        return new SubmitTaskResponse(id);
    }

    @Override
    public StatsOverviewResponse getStatsOverview() {
        log.debug("Fetching stats overview");

        StatsOverviewResponse response = new StatsOverviewResponse();

        // 当前待处理和运行中的任务数
        response.setTotalPending(taskMapper.countPendingTasks(null));
        response.setTotalRunning(taskMapper.countRunningTasks(null));

        // 今日统计(从今天0点开始)
        OffsetDateTime todayStart = LocalDate.now()
                .atStartOfDay()
                .atOffset(ZoneOffset.UTC);

        LambdaQueryWrapper<AstTask> todayWrapper = new LambdaQueryWrapper<>();
        todayWrapper.ge(AstTask::getCreatedAt, todayStart)
                   .eq(AstTask::getIsDeleted, 0);

        List<AstTask> todayTasks = taskMapper.selectList(todayWrapper);

        StatsOverviewResponse.TodayStats todayStats = new StatsOverviewResponse.TodayStats();
        todayStats.setSuccess(todayTasks.stream().filter(t -> "SUCCESS".equals(t.getStatus())).count());
        todayStats.setFailed(todayTasks.stream().filter(t -> "FAILED".equals(t.getStatus())).count());
        todayStats.setCancelled(todayTasks.stream().filter(t -> "CANCELLED".equals(t.getStatus())).count());
        response.setTodayStats(todayStats);

        // Worker 节点概况
        OffsetDateTime workerThreshold = OffsetDateTime.now().minusSeconds(30);
        LambdaQueryWrapper<AstWorkerNode> workerWrapper = new LambdaQueryWrapper<>();
        workerWrapper.eq(AstWorkerNode::getIsDeleted, 0);
        List<AstWorkerNode> allWorkers = workerNodeMapper.selectList(workerWrapper);

        StatsOverviewResponse.WorkerCount workerCount = new StatsOverviewResponse.WorkerCount();
        long onlineCount = allWorkers.stream()
                .filter(w -> w.getLastHeartbeatAt() != null && w.getLastHeartbeatAt().isAfter(workerThreshold))
                .count();
        workerCount.setOnline((int) onlineCount);
        workerCount.setOffline((int) (allWorkers.size() - onlineCount));
        response.setWorkerCount(workerCount);

        return response;
    }

    @Override
    public List<TaskTypeConfigResponse> getAllTaskTypeConfigs() {
        log.debug("Fetching all task type configs");

        LambdaQueryWrapper<AstTaskTypeConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AstTaskTypeConfig::getIsDeleted, 0)
               .orderByDesc(AstTaskTypeConfig::getCreatedAt);

        List<AstTaskTypeConfig> configs = taskTypeConfigMapper.selectList(wrapper);

        return configs.stream().map(this::convertToResponse).collect(Collectors.toList());
    }

    @Override
    public TaskTypeConfigResponse getTaskTypeConfig(String typeKey, Long tenantId) {
        log.debug("Fetching task type config: {}", typeKey);

        // tenantId 缺省 = 全局语义 (typeKey 平台内约定全局唯一; 租户内唯一时可显式传参消歧)
        AstTaskTypeConfig config = taskTypeConfigMapper.selectByTypeKey(typeKey, tenantId);
        if (config == null) {
            throw new ApiException(BusinessCode.TASK_NOT_FOUND.getCode(), "任务类型配置不存在: " + typeKey);
        }

        return convertToResponse(config);
    }

    @Override
    @Transactional
    public void deleteTaskTypeConfig(String typeKey, Long tenantId) {
        log.info("Deleting task type config: {}", typeKey);

        AstTaskTypeConfig existing = taskTypeConfigMapper.selectByTypeKey(typeKey, tenantId);
        if (existing == null) {
            throw new ApiException(BusinessCode.TASK_NOT_FOUND.getCode(), "任务类型配置不存在: " + typeKey);
        }

        // 逻辑删除 — isDeleted 是 @TableLogic 字段, 必须走 deleteById
        // (updateById 不会更新逻辑删标记, setIsDeleted(1) 是静默空操作)
        taskTypeConfigMapper.deleteById(existing.getId());

        log.info("Task type config deleted: {}", typeKey);
    }

    @Override
    public PageResponse<TaskDetailResponse> getTaskList(Long id, String status, String type, Integer page, Integer pageSize) {
        log.debug("获取任务列表: id={}, status={}, type={}, page={}, pageSize={}", id, status, type, page, pageSize);

        // 设置默认分页参数
        int currentPage = (page != null && page > 0) ? page : 1;
        int size = (pageSize != null && pageSize > 0) ? pageSize : 20;

        // 计算偏移量
        long offset = (long) (currentPage - 1) * size;
        long limit = size;

        // 执行 COUNT 查询获取总数（管理端默认只查询当前任务，不包括归档任务）
        long total = taskMapper.countTasks(id, status, type, false, null, null, null);

        // 执行分页查询（数据库层面分页）
        List<AstTask> tasks = taskMapper.selectPageWithTypeName(offset, limit, id, status, type, false, null, null, null);

        // 转换为 DTO
        List<TaskDetailResponse> list = tasks.stream()
                .map(this::convertToTaskDetail)
                .collect(Collectors.toList());

        // 返回分页结果
        return PageResponse.of(list, total, currentPage, size);
    }

    /**
     * 转换为任务类型配置响应对象
     */
    private TaskTypeConfigResponse convertToResponse(AstTaskTypeConfig config) {
        TaskTypeConfigResponse response = new TaskTypeConfigResponse();
        response.setId(config.getId());
        response.setTypeKey(config.getTypeKey());
        response.setName(config.getName());
        response.setConcurrencyLimit(config.getConcurrencyLimit());
        response.setTimeoutSeconds(config.getTimeoutSeconds());
        response.setMaxRetries(config.getMaxRetries());
        response.setIsEnabled(Integer.valueOf(1).equals(config.getIsEnabled()));
        response.setStepsConfig(config.getStepsConfig());
        response.setCreatedAt(config.getCreatedAt());
        response.setUpdatedAt(config.getUpdatedAt());

        return response;
    }

    /**
     * 转换为任务详情响应对象
     */
    private TaskDetailResponse convertToTaskDetail(AstTask task) {
        TaskDetailResponse response = new TaskDetailResponse();
        response.setId(task.getId());
        response.setType(task.getTaskTypeKey());
        response.setTypeName(task.getTypeName()); // 直接使用 JOIN 查询结果
        response.setStatus(task.getStatus());
        response.setProgress(task.getProgress());
        response.setCurrentStep(task.getCurrentStepKey());
        response.setStepsDetail(task.getStepsDetail());
        response.setPayload(task.getPayload());
        response.setResult(task.getResult());
        response.setErrorMsg(task.getErrorMsg());
        response.setPriority(task.getPriority());
        response.setCreatedAt(task.getCreatedAt());
        response.setUpdatedAt(task.getUpdatedAt());
        response.setStartedAt(task.getStartedAt());
        response.setFinishedAt(task.getFinishedAt());

        if (task.getStartedAt() != null && task.getFinishedAt() != null) {
            response.setDurationSeconds(
                    java.time.temporal.ChronoUnit.SECONDS.between(
                            task.getStartedAt(), task.getFinishedAt()
                    )
            );
        }

        return response;
    }

    @Override
    public SystemConfigResponse getSystemConfig() {
        log.debug("Fetching system configuration");

        SystemConfigResponse response = new SystemConfigResponse();

        // 系统基本信息
        response.setSystemInfo(buildSystemInfo());

        // 数据库配置
        response.setDatabaseConfig(buildDatabaseConfig());

        // Redis 配置
        response.setRedisConfig(buildRedisConfig());

        // JVM 信息
        response.setJvmInfo(buildJvmInfo());

        // 任务统计
        response.setTaskStats(buildTaskStats());

        return response;
    }

    private SystemConfigResponse.SystemInfo buildSystemInfo() {
        SystemConfigResponse.SystemInfo info = new SystemConfigResponse.SystemInfo();

        info.setAppName(environment.getProperty("spring.application.name", "lotask4j ASTS"));
        info.setAppVersion("1.0.0-SNAPSHOT");
        info.setSpringBootVersion(SpringBootVersion.getVersion());
        info.setJavaVersion(System.getProperty("java.version"));
        info.setOsName(System.getProperty("os.name"));
        info.setOsArch(System.getProperty("os.arch"));

        // 启动时间
        OffsetDateTime startDateTime = OffsetDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(START_TIME),
            ZoneOffset.systemDefault()
        );
        info.setStartTime(startDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // 运行时长
        long uptimeMillis = System.currentTimeMillis() - START_TIME;
        info.setUptime(formatDuration(uptimeMillis));

        return info;
    }

    private SystemConfigResponse.DatabaseConfig buildDatabaseConfig() {
        SystemConfigResponse.DatabaseConfig config = new SystemConfigResponse.DatabaseConfig();

        config.setType("PostgreSQL");
        String jdbcUrl = environment.getProperty("spring.datasource.druid.url", "");
        // 隐藏敏感信息（密码等）
        config.setUrl(maskSensitiveUrl(jdbcUrl));

        // 从 Druid 配置获取连接池大小
        String maxActive = environment.getProperty("spring.datasource.druid.max-active", "20");
        config.setMaxPoolSize(Integer.parseInt(maxActive));
        config.setActiveConnections(0); // 实际项目中可以从 DruidDataSource 获取

        config.setVersion("14+");

        return config;
    }

    private SystemConfigResponse.RedisConfig buildRedisConfig() {
        SystemConfigResponse.RedisConfig config = new SystemConfigResponse.RedisConfig();

        String redisHost = environment.getProperty("spring.data.redis.host", "localhost");
        String redisPort = environment.getProperty("spring.data.redis.port", "6379");
        config.setHost(redisHost + ":" + redisPort);
        config.setDatabase(Integer.parseInt(environment.getProperty("spring.data.redis.database", "0")));
        config.setMode("Standalone");
        config.setStatus("Connected");

        return config;
    }

    private SystemConfigResponse.JvmInfo buildJvmInfo() {
        SystemConfigResponse.JvmInfo info = new SystemConfigResponse.JvmInfo();

        Runtime runtime = Runtime.getRuntime();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        info.setName(System.getProperty("java.vm.name"));
        info.setVersion(System.getProperty("java.vm.version"));

        // 内存信息 (转换为 MB)
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        info.setMaxMemory(maxMemory);
        info.setTotalMemory(totalMemory);
        info.setUsedMemory(usedMemory);
        info.setFreeMemory(freeMemory);

        // CPU 和线程
        info.setCpuCores(runtime.availableProcessors());
        info.setThreadCount(threadBean.getThreadCount());

        return info;
    }

    private SystemConfigResponse.TaskStats buildTaskStats() {
        SystemConfigResponse.TaskStats stats = new SystemConfigResponse.TaskStats();

        // 任务总数（不包括归档）
        stats.setTotalTasks(taskMapper.countTasks(null, null, null, false, null, null, null));
        stats.setPendingTasks(taskService.getPendingTaskCount());
        stats.setRunningTasks(taskService.getRunningTaskCount());

        // 各状态任务数
        stats.setSuccessTasks(taskMapper.countTasks(null, "SUCCESS", null, false, null, null, null));
        stats.setFailedTasks(taskMapper.countTasks(null, "FAILED", null, false, null, null, null));
        stats.setCancelledTasks(taskMapper.countTasks(null, "CANCELLED", null, false, null, null, null));

        // 任务类型数量
        LambdaQueryWrapper<AstTaskTypeConfig> configWrapper = new LambdaQueryWrapper<>();
        configWrapper.eq(AstTaskTypeConfig::getIsDeleted, 0);
        stats.setTaskTypeCount(taskTypeConfigMapper.selectCount(configWrapper).intValue());

        // 在线 Worker 数
        LambdaQueryWrapper<AstWorkerNode> workerWrapper = new LambdaQueryWrapper<>();
        workerWrapper.eq(AstWorkerNode::getStatus, "ONLINE")
                     .eq(AstWorkerNode::getIsDeleted, 0);
        stats.setOnlineWorkerCount(workerNodeMapper.selectCount(workerWrapper).intValue());

        return stats;
    }

    private String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(millis);
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;

        if (days > 0) {
            return String.format("%d天%d小时%d分", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%d小时%d分", hours, minutes);
        } else {
            return String.format("%d分钟", minutes);
        }
    }

    private String maskSensitiveUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        // 移除用户名密码等敏感信息
        return url.replaceAll("//[^@]*@", "//***:***@");
    }
}
