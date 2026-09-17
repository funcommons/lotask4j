package fun.commons.lotask4j.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.commons.lotask4j.entity.AstTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 异步任务 Mapper 接口
 *
 * MyBatis Plus 自动生成 CRUD 方法和通用查询方法
 * 支持:
 * - selectById(id)
 * - selectList(LambdaQueryWrapper)
 * - updateById(entity)
 * - deleteById(id)
 * 等通用方法
 *
 * P0 增强：所有状态/进度/结果变更方法接受 expected_version 与 fencing token，
 * 数据库侧做 CAS（UPDATE 条件包含 version 与 execution_token 匹配）。
 * 受影响行数为 0 表示乐观锁竞争失败，调用方应回退并重试/抛错。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Mapper
public interface AstTaskMapper extends BaseMapper<AstTask> {

    /**
     * 根据任务 ID 查询任务（包含类型名称）
     * 使用 LEFT JOIN 关联 asts_task_type_config 表获取类型名称
     *
     * @param id 任务唯一标识
     * @return 任务实体（包含 typeName）
     */
    AstTask selectByIdWithTypeName(@Param("id") Long id, @Param("tenantId") Long tenantId);

    /**
     * 获取待处理任务数
     */
    long countPendingTasks(@Param("tenantId") Long tenantId);

    /**
     * 获取运行中的任务数
     */
    long countRunningTasks(@Param("tenantId") Long tenantId);

    /**
     * 重置超时的任务为 PENDING（仅 updated_at 维度，旧协议；保留兼容）
     */
    int resetTimeoutTasks(@Param("timeoutSeconds") int timeoutSeconds);

    /**
     * Worker 抢占任务(使用 SKIP LOCKED 乐观锁)
     */
    AstTask pollAndLockTask(@Param("taskType") String taskType,
                             @Param("strategy") String strategy,
                             @Param("workerIp") String workerIp,
                             @Param("tenantId") Long tenantId);

    /**
     * 更新任务进度（保留兼容 — 旧路径,逐步改用 progressWithVersion）
     */
    int updateTaskProgress(@Param("id") Long id,
                           @Param("currentStepKey") String currentStepKey,
                           @Param("stepProgress") Integer stepProgress,
                           @Param("stepsDetail") String stepsDetail,
                           @Param("globalProgress") Integer globalProgress,
                           @Param("tenantId") Long tenantId);

    /**
     * 更新任务最终结果（保留兼容 — 旧路径,逐步改用 completeWithToken）
     */
    int updateTaskResult(@Param("id") Long id,
                         @Param("status") String status,
                         @Param("result") String result,
                         @Param("errorMsg") String errorMsg,
                         @Param("tenantId") Long tenantId);

    /**
     * 更新任务回调状态
     */
    int updateCallbackStatus(@Param("id") Long id,
                             @Param("callbackStatus") Integer callbackStatus,
                             @Param("tenantId") Long tenantId);

    /**
     * 插入任务 (处理 JSONB 字段)
     */
    int insertTask(@Param("task") AstTask task,
                   @Param("payloadJson") String payloadJson,
                   @Param("resultJson") String resultJson);

    /**
     * 查询任务列表（包含类型名称）
     */
    List<AstTask> selectListWithTypeName(@Param("status") String status,
                                          @Param("taskType") String taskType,
       @Param("tenantId") Long tenantId);

    /**
     * 分页查询任务列表（包含类型名称）
     */
    List<AstTask> selectPageWithTypeName(@Param("offset") Long offset,
                                          @Param("limit") Long limit,
                                          @Param("id") Long id,
                                          @Param("status") String status,
                                          @Param("taskType") String taskType,
                                          @Param("isArchived") Boolean isArchived,
                                          @Param("createdAtStart") OffsetDateTime createdAtStart,
                                          @Param("createdAtEnd") OffsetDateTime createdAtEnd,
       @Param("tenantId") Long tenantId);

    /**
     * 统计符合条件的任务总数
     */
    long countTasks(@Param("id") Long id,
                    @Param("status") String status,
                    @Param("taskType") String taskType,
                    @Param("isArchived") Boolean isArchived,
                    @Param("createdAtStart") OffsetDateTime createdAtStart,
                    @Param("createdAtEnd") OffsetDateTime createdAtEnd,
       @Param("tenantId") Long tenantId);

    /**
     * 统计已过期的待处理任务数量
     */
    int countExpiredPendingTasks();

    /**
     * 将已过期的待处理任务标记为失败状态
     */
    int markExpiredTasksAsFailed();

    // ===================================================================================
    // P0 增强：乐观锁 + fencing token 的 CAS 方法
    // 这些方法由 TaskStateMachine 调用，避免在 service 层直接拼 SQL
    // ===================================================================================

    /**
     * 根据幂等键查已存在任务（P0-5）。
     * 命中返回任务，未命中返回 null。
     */
    AstTask findByIdempotencyKey(@Param("taskTypeKey") String taskTypeKey,
                                  @Param("idempotencyKey") String idempotencyKey,
       @Param("tenantId") Long tenantId);

    /**
     * PENDING/CANCELLING → DISPATCHED 状态迁移（CAS by version）。
     * 同一个 task 在 lease 超时被 reap 之前仅能被 dispatch 一次。
     *
     * @return 影响行数 — 0 表示乐观锁失败
     */
    int dispatchTask(@Param("id") Long id,
                      @Param("expectedVersion") Integer expectedVersion,
                      @Param("workerId") String workerId,
                      @Param("executionId") Long executionId,
                      @Param("executionToken") Long executionToken,
                      @Param("leaseSeconds") Integer leaseSeconds,
                      @Param("now") OffsetDateTime now,
       @Param("tenantId") Long tenantId);

    /**
     * DISPATCHED → RUNNING 状态迁移（CAS by version + token）。
     *
     * @return 影响行数 — 0 表示乐观锁或 fencing 失败
     */
    int startExecution(@Param("id") Long id,
                        @Param("expectedVersion") Integer expectedVersion,
                        @Param("executionToken") Long executionToken,
                        @Param("startedAt") OffsetDateTime startedAt,
       @Param("tenantId") Long tenantId);

    /**
     * 续约 lease（CAS by version + token）。
     * Worker 心跳时调用。
     *
     * @return 影响行数 — 0 表示乐观锁或 fencing 失败
     */
    int extendLease(@Param("id") Long id,
                     @Param("expectedVersion") Integer expectedVersion,
                     @Param("executionToken") Long executionToken,
                     @Param("leaseSeconds") Integer leaseSeconds,
                     @Param("now") OffsetDateTime now,
       @Param("tenantId") Long tenantId);

    /**
     * 进度上报（CAS by version + token）。
     *
     * @return 影响行数 — 0 表示乐观锁或 fencing 失败
     */
    int progressWithVersion(@Param("id") Long id,
                             @Param("expectedVersion") Integer expectedVersion,
                             @Param("executionToken") Long executionToken,
                             @Param("currentStepKey") String currentStepKey,
                             @Param("stepProgress") Integer stepProgress,
                             @Param("stepsDetail") String stepsDetail,
                             @Param("globalProgress") Integer globalProgress,
                             @Param("now") OffsetDateTime now,
       @Param("tenantId") Long tenantId);

    /**
     * 终态 CAS（CAS by version + token）。
     * 终态包括 SUCCESS / FAILED / CANCELLED。
     *
     * @param finalStatus 目标状态名（SUCCESS / FAILED / CANCELLED）
     * @return 影响行数 — 0 表示乐观锁或 fencing 失败
     */
    int completeWithToken(@Param("id") Long id,
                           @Param("expectedVersion") Integer expectedVersion,
                           @Param("executionToken") Long executionToken,
                           @Param("finalStatus") String finalStatus,
                           @Param("resultJson") String resultJson,
                           @Param("errorMsg") String errorMsg,
                           @Param("lastErrorCode") String lastErrorCode,
                           @Param("lastErrorMessage") String lastErrorMessage,
                           @Param("now") OffsetDateTime now,
       @Param("tenantId") Long tenantId);

    /**
     * 用户请求取消（PENDING/RUNNING → CANCELLING，CAS by version）。
     * 不要求 fencing token — 用户级操作与 Worker 状态独立。
     *
     * @return 影响行数 — 0 表示乐观锁失败或任务已非可取消状态
     */
    int markCancelRequested(@Param("id") Long id,
                             @Param("expectedVersion") Integer expectedVersion,
                             @Param("requestedCancelAt") OffsetDateTime requestedCancelAt,
                             @Param("now") OffsetDateTime now,
       @Param("tenantId") Long tenantId);

    /**
     * Worker 确认取消完成（CANCELLING → CANCELLED，CAS by version + token）。
     */
    int confirmCancel(@Param("id") Long id,
                       @Param("expectedVersion") Integer expectedVersion,
                       @Param("executionToken") Long executionToken,
                       @Param("now") OffsetDateTime now,
       @Param("tenantId") Long tenantId);

    /**
     * Reaper：把 lease 过期但未确认的任务回退。
     * <ul>
     *   <li>attempt &lt; maxAttempts → 回到 PENDING，重试 (attempt + 1)</li>
     *   <li>attempt &gt;= maxAttempts → 直接 FAILED</li>
     * </ul>
     *
     * @param leaseCutoff 早于这个时间视为 lease 过期
     * @return 影响行数
     */
    int resetExpiredLeases(@Param("leaseCutoff") OffsetDateTime leaseCutoff,
                            @Param("now") OffsetDateTime now);

    /**
     * 查询所有"当前 dispatch 中但 lease 已过期"的 RUNNING 任务（供 Reaper 监控）。
     */
    List<AstTask> selectExpiredRunning(@Param("leaseCutoff") OffsetDateTime leaseCutoff);

    /**
     * P1-5: 统计某任务类型未完成任务数 (PENDING + RUNNING, 但排除过期/已终态/已 archive)。
     */
    long countInFlightByType(@Param("taskType") String taskType);
}
