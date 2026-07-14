package fun.commons.lotask4j.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.commons.lotask4j.dto.PageResponse;
import fun.commons.lotask4j.dto.SubmitTaskRequest;
import fun.commons.lotask4j.dto.TaskDetailResponse;
import fun.commons.lotask4j.entity.AstTask;
import fun.commons.lotask4j.entity.AstTaskTypeConfig;
import fun.commons.lotask4j.enums.BusinessCode;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.mapper.AstTaskTypeConfigMapper;
import fun.commons.lotask4j.service.TaskService;
import fun.commons.framework4j.web.ApiException;
import fun.commons.framework4j.id.generator.SnowflakeDistributor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 异步任务业务服务实现
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl extends ServiceImpl<AstTaskMapper, AstTask> implements TaskService {

    private final SnowflakeDistributor snowflakeDistributor;
    private final AstTaskTypeConfigMapper taskTypeConfigMapper;

    /**
     * 提交异步任务
     *
     * @param request 提交任务请求
     * @return 任务 ID (雪花ID)
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitTask(SubmitTaskRequest request) {
        try {
            // 查询任务类型配置获取超时时间
            AstTaskTypeConfig typeConfig = taskTypeConfigMapper.selectOne(
                new LambdaQueryWrapper<AstTaskTypeConfig>()
                    .eq(AstTaskTypeConfig::getTypeKey, request.getType())
                    .eq(AstTaskTypeConfig::getIsDeleted, 0)
            );

            // 计算过期时间
            OffsetDateTime now = OffsetDateTime.now();
            OffsetDateTime expiredAt;

            if (typeConfig != null && typeConfig.getTimeoutSeconds() != null && typeConfig.getTimeoutSeconds() > 0) {
                // 使用配置的超时时间
                expiredAt = now.plusSeconds(typeConfig.getTimeoutSeconds());
                log.debug("任务类型 {} 使用配置超时时间: {} 秒", request.getType(), typeConfig.getTimeoutSeconds());
            } else {
                // 使用默认超时时间: 7 天
                expiredAt = now.plusDays(7);
                log.debug("任务类型 {} 使用默认超时时间: 7 天", request.getType());
            }

            AstTask task = new AstTask();
            task.setId(snowflakeDistributor.nextId());
            task.setTaskTypeKey(request.getType());
            task.setStatus("PENDING");
            task.setPriority(request.getPriority() != null ? request.getPriority() : 0);
            task.setPayload(request.getPayload());
            task.setCallbackUrl(request.getCallbackUrl());
            task.setCreatedAt(now);
            task.setUpdatedAt(now);
            task.setExpiredAt(expiredAt);
            task.setIsDeleted(0);

            // 将 payload 和 result 转换为 JSON 字符串
            String payloadJson = com.alibaba.fastjson2.JSON.toJSONString(
                    request.getPayload() != null ? request.getPayload() : new java.util.HashMap<>()
            );
            String resultJson = "{}";

            // 使用自定义插入方法处理 JSONB 字段
            baseMapper.insertTask(task, payloadJson, resultJson);

            log.info("任务提交成功: id={}, type={}, priority={}, expiredAt={}",
                    task.getId(), task.getTaskTypeKey(), task.getPriority(), task.getExpiredAt());

            return task.getId();
        } catch (Exception e) {
            log.error("任务提交失败", e);
            throw new ApiException(BusinessCode.TASK_SUBMIT_FAILED.getCode(),
                    "任务提交失败: " + e.getMessage());
        }
    }

    /**
     * 获取任务详情
     *
     * @param taskId 任务 ID (雪花ID)
     * @return 任务详情响应
     */
    @Override
    public TaskDetailResponse getTaskDetail(Long taskId) {
        // 使用 JOIN 查询，一次性获取类型名称
        AstTask task = baseMapper.selectByIdWithTypeName(taskId);
        if (task == null) {
            throw new ApiException(BusinessCode.TASK_NOT_FOUND.getCode(),
                    BusinessCode.TASK_NOT_FOUND.getMessage());
        }

        TaskDetailResponse response = new TaskDetailResponse();
        response.setId(task.getId());  // taskId → id
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
        response.setTimeoutSeconds(task.getTimeoutSeconds()); // 从 JOIN 查询获取的超时配置
        response.setExpiredAt(task.getExpiredAt()); // 设置任务过期时间

        if (task.getStartedAt() != null && task.getFinishedAt() != null) {
            response.setDurationSeconds(
                    java.time.temporal.ChronoUnit.SECONDS.between(
                            task.getStartedAt(), task.getFinishedAt()
                    )
            );
        }

        return response;
    }

    /**
     * 取消任务
     *
     * @param taskId 任务 ID (雪花ID)
     * @return 是否成功取消
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelTask(Long taskId) {
        AstTask task = baseMapper.selectById(taskId);
        if (task == null) {
            throw new ApiException(BusinessCode.TASK_NOT_FOUND.getCode(),
                    BusinessCode.TASK_NOT_FOUND.getMessage());
        }

        // 只有待处理和运行中的任务可以取消
        if (!"PENDING".equals(task.getStatus()) && !"RUNNING".equals(task.getStatus())) {
            throw new ApiException(BusinessCode.TASK_CANCEL_NOT_ALLOWED.getCode(),
                    "任务状态不允许取消: " + task.getStatus());
        }

        task.setStatus("CANCELLING");
        task.setUpdatedAt(OffsetDateTime.now());

        boolean success = baseMapper.updateById(task) > 0;

        if (success) {
            log.info("任务取消请求发送: id={}", taskId);
        }

        return success;
    }

    /**
     * 获取待处理任务数
     *
     * @return 待处理任务数量
     */
    @Override
    public long getPendingTaskCount() {
        return baseMapper.countPendingTasks();
    }

    /**
     * 获取运行中的任务数
     *
     * @return 运行中的任务数量
     */
    @Override
    public long getRunningTaskCount() {
        return baseMapper.countRunningTasks();
    }

    /**
     * 清理超时任务 (Reaper 机制)
     *
     * @param timeoutSeconds 超时秒数
     * @return 被清理的任务数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanupTimeoutTasks(int timeoutSeconds) {
        int count = baseMapper.resetTimeoutTasks(timeoutSeconds);
        if (count > 0) {
            log.info("Reaper 清理超时任务: count={}, timeout={}s", count, timeoutSeconds);
        }
        return count;
    }

    /**
     * 获取任务列表 (支持筛选和分页)
     *
     * @param id 任务ID筛选（可选，精确匹配）
     * @param status 任务状态筛选
     * @param taskType 任务类型筛选
     * @param isArchived 是否查询归档任务（true: 归档, false: 当前, null: 全部）
     * @param createdAtStart 创建时间起始
     * @param createdAtEnd 创建时间结束
     * @param page 页码
     * @param pageSize 每页数量
     * @return 分页任务详情列表
     */
    @Override
    public PageResponse<TaskDetailResponse> getTaskList(Long id, String status, String taskType, Boolean isArchived, OffsetDateTime createdAtStart, OffsetDateTime createdAtEnd, Integer page, Integer pageSize) {
        log.debug("获取任务列表: id={}, status={}, taskType={}, isArchived={}, createdAtStart={}, createdAtEnd={}, page={}, pageSize={}",
                  id, status, taskType, isArchived, createdAtStart, createdAtEnd, page, pageSize);

        // 设置默认分页参数
        int currentPage = (page != null && page > 0) ? page : 1;
        int size = (pageSize != null && pageSize > 0) ? pageSize : 20;

        // 计算偏移量
        long offset = (long) (currentPage - 1) * size;
        long limit = size;

        // 执行 COUNT 查询获取总数
        long total = baseMapper.countTasks(id, status, taskType, isArchived, createdAtStart, createdAtEnd);

        // 执行分页查询（数据库层面分页）
        List<AstTask> tasks = baseMapper.selectPageWithTypeName(offset, limit, id, status, taskType, isArchived, createdAtStart, createdAtEnd);

        // 转换为 DTO
        List<TaskDetailResponse> list = tasks.stream()
                .map(this::convertToTaskDetail)
                .collect(Collectors.toList());

        // 返回分页结果
        return PageResponse.of(list, total, currentPage, size);
    }

    /**
     * 转换任务实体为任务详情响应对象
     */
    private TaskDetailResponse convertToTaskDetail(AstTask task) {
        TaskDetailResponse response = new TaskDetailResponse();
        response.setId(task.getId());
        response.setType(task.getTaskTypeKey());
        response.setTypeName(task.getTypeName()); // 直接使用 typeName，无需再查询
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
        response.setTimeoutSeconds(task.getTimeoutSeconds()); // 设置超时配置
        response.setExpiredAt(task.getExpiredAt()); // 设置任务过期时间

        if (task.getStartedAt() != null && task.getFinishedAt() != null) {
            response.setDurationSeconds(
                    java.time.temporal.ChronoUnit.SECONDS.between(
                            task.getStartedAt(), task.getFinishedAt()
                    )
            );
        }

        return response;
    }

    /**
     * 获取任务类型名称
     *
     * @param typeKey 任务类型 Key
     * @return 任务类型名称，如果未找到返回 typeKey 本身
     */
    private String getTaskTypeName(String typeKey) {
        if (typeKey == null || typeKey.isEmpty()) {
            return "";
        }

        try {
            AstTaskTypeConfig config = taskTypeConfigMapper.selectByTypeKey(typeKey);
            return config != null ? config.getName() : typeKey;
        } catch (Exception e) {
            log.warn("获取任务类型名称失败: typeKey={}", typeKey, e);
            return typeKey;
        }
    }
}
