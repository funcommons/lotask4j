package fun.commons.lotask4j.service;

import fun.commons.lotask4j.dto.*;
import fun.commons.lotask4j.dto.*;

import java.util.List;

/**
 * Admin 管理服务接口
 */
public interface AdminService {

    /**
     * 获取在线 Worker 列表(最近30秒内有心跳)
     */
    List<WorkerNodeResponse> getOnlineWorkers();

    /**
     * 获取所有任务类型配置列表
     */
    List<TaskTypeConfigResponse> getAllTaskTypeConfigs();

    /**
     * 根据类型标识获取任务类型配置
     */
    TaskTypeConfigResponse getTaskTypeConfig(String typeKey);

    /**
     * 新增或更新任务类型配置
     */
    void saveTaskTypeConfig(TaskTypeConfigRequest request);

    /**
     * 删除任务类型配置
     */
    void deleteTaskTypeConfig(String typeKey);

    /**
     * 手动提交任务(管理员)
     */
    SubmitTaskResponse submitTask(SubmitTaskRequest request);

    /**
     * 获取统计概览
     */
    StatsOverviewResponse getStatsOverview();

    /**
     * 获取任务列表(支持筛选和分页)
     */
    PageResponse<TaskDetailResponse> getTaskList(Long id, String status, String type, Integer page, Integer pageSize);

    /**
     * 获取系统配置信息
     */
    SystemConfigResponse getSystemConfig();
}
