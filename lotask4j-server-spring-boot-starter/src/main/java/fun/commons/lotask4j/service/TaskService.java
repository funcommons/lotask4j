package fun.commons.lotask4j.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.commons.lotask4j.dto.PageResponse;
import fun.commons.lotask4j.dto.SubmitTaskRequest;
import fun.commons.lotask4j.dto.TaskDetailResponse;
import fun.commons.lotask4j.entity.AstTask;

import java.time.OffsetDateTime;

/**
 * 异步任务业务服务接口
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
public interface TaskService extends IService<AstTask> {

    /**
     * 当前租户可用的任务类型列表 (typeKey + name, 仅启用) — client 域类型下拉用。
     *
     * @param tenantId claim 租户 (null → 空列表)
     */
    java.util.List<java.util.Map<String, Object>> listEnabledTypes(Long tenantId);


    /**
     * 提交异步任务
     *
     * @param request 提交任务请求
     * @return 任务 ID (雪花ID)
     */
    Long submitTask(SubmitTaskRequest request);

    /**
     * 获取任务详情
     *
     * @param taskId 任务 ID (雪花ID)
     * @return 任务详情响应
     */
    TaskDetailResponse getTaskDetail(Long taskId);

    /**
     * 取消任务
     *
     * @param taskId 任务 ID (雪花ID)
     * @return 是否成功取消
     */
    boolean cancelTask(Long taskId);

    /**
     * 获取待处理任务数
     *
     * @return 待处理任务数量
     */
    long getPendingTaskCount();

    /**
     * 获取运行中的任务数
     *
     * @return 运行中的任务数量
     */
    long getRunningTaskCount();

    /**
     * 清理超时任务 (Reaper 机制)
     *
     * @param timeoutSeconds 超时秒数
     * @return 被清理的任务数
     */
    int cleanupTimeoutTasks(int timeoutSeconds);

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
    PageResponse<TaskDetailResponse> getTaskList(Long id, String status, String taskType, Boolean isArchived, OffsetDateTime createdAtStart, OffsetDateTime createdAtEnd, Integer page, Integer pageSize);
}
