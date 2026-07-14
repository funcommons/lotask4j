package fun.commons.lotask4j.service;

import fun.commons.lotask4j.dto.*;
import fun.commons.lotask4j.dto.*;

/**
 * Worker 服务接口
 */
public interface WorkerService {

    /**
     * Worker 抢占任务
     * @param request 抢占任务请求
     * @param workerIp Worker IP 地址
     * @return 任务信息,如果没有可用任务则返回 null
     */
    PollTaskResponse pollTask(PollTaskRequest request, String workerIp);

    /**
     * Worker 查询任务状态(用于检测取消信号)
     * @param id 任务 ID
     * @return 任务详情
     */
    TaskDetailResponse getTaskStatus(Long id);

    /**
     * Worker 上报任务进度
     * @param id 任务 ID
     * @param request 进度信息
     */
    void reportProgress(Long id, ReportProgressRequest request);

    /**
     * Worker 上报任务最终结果
     * @param id 任务 ID
     * @param request 结果信息
     */
    void reportResult(Long id, ReportResultRequest request);
}
