package fun.commons.lotask4j.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.commons.lotask4j.entity.AstTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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
    AstTask selectByIdWithTypeName(@Param("id") Long id);

    /**
     * 获取待处理任务数
     *
     * @return 待处理任务数量
     */
    long countPendingTasks();

    /**
     * 获取运行中的任务数
     *
     * @return 运行中的任务数量
     */
    long countRunningTasks();

    /**
     * 重置超时的任务为 PENDING
     *
     * @param timeoutSeconds 超时秒数
     * @return 被重置的任务数
     */
    int resetTimeoutTasks(int timeoutSeconds);

    /**
     * Worker 抢占任务(使用 SKIP LOCKED 乐观锁)
     *
     * @param taskType 任务类型
     * @param strategy 调度策略: PRIORITY(优先级优先), FIFO(先入先出)
     * @param workerIp Worker IP 地址
     * @return 被抢占的任务,如果没有可用任务则返回 null
     */
    AstTask pollAndLockTask(@Param("taskType") String taskType,
                             @Param("strategy") String strategy,
                             @Param("workerIp") String workerIp);

    /**
     * 更新任务进度
     */
    int updateTaskProgress(@Param("id") Long id,
                           @Param("currentStepKey") String currentStepKey,
                           @Param("stepProgress") Integer stepProgress,
                           @Param("stepsDetail") String stepsDetail,
                           @Param("globalProgress") Integer globalProgress);

    /**
     * 更新任务最终结果
     */
    int updateTaskResult(@Param("id") Long id,
                         @Param("status") String status,
                         @Param("result") String result,
                         @Param("errorMsg") String errorMsg);

    /**
     * 更新任务回调状态
     *
     * @param id 任务ID
     * @param callbackStatus 回调状态 (1=成功, 2=失败)
     * @return 影响行数
     */
    int updateCallbackStatus(@Param("id") Long id,
                             @Param("callbackStatus") Integer callbackStatus);

    /**
     * 插入任务 (处理 JSONB 字段)
     */
    int insertTask(@Param("task") AstTask task,
                   @Param("payloadJson") String payloadJson,
                   @Param("resultJson") String resultJson);

    /**
     * 查询任务列表（包含类型名称）
     * 使用 LEFT JOIN 关联 asts_task_type_config 表获取类型名称
     *
     * @param status 任务状态筛选（可选）
     * @param taskType 任务类型筛选（可选）
     * @return 任务列表（包含 typeName）
     */
    List<AstTask> selectListWithTypeName(@Param("status") String status,
                                          @Param("taskType") String taskType);

    /**
     * 分页查询任务列表（包含类型名称）
     * 使用 LEFT JOIN 关联 asts_task_type_config 表获取类型名称
     *
     * @param offset 偏移量
     * @param limit 每页数量
     * @param id 任务ID筛选（可选，精确匹配）
     * @param status 任务状态筛选（可选）
     * @param taskType 任务类型筛选（可选）
     * @param isArchived 是否查询归档任务（true: 归档, false: 当前, null: 全部）
     * @param createdAtStart 创建时间起始
     * @param createdAtEnd 创建时间结束
     * @return 任务列表（包含 typeName）
     */
    List<AstTask> selectPageWithTypeName(@Param("offset") Long offset,
                                          @Param("limit") Long limit,
                                          @Param("id") Long id,
                                          @Param("status") String status,
                                          @Param("taskType") String taskType,
                                          @Param("isArchived") Boolean isArchived,
                                          @Param("createdAtStart") java.time.OffsetDateTime createdAtStart,
                                          @Param("createdAtEnd") java.time.OffsetDateTime createdAtEnd);

    /**
     * 统计符合条件的任务总数
     *
     * @param id 任务ID筛选（可选，精确匹配）
     * @param status 任务状态筛选（可选）
     * @param taskType 任务类型筛选（可选）
     * @param isArchived 是否查询归档任务（true: 归档, false: 当前, null: 全部）
     * @param createdAtStart 创建时间起始
     * @param createdAtEnd 创建时间结束
     * @return 任务总数
     */
    long countTasks(@Param("id") Long id,
                    @Param("status") String status,
                    @Param("taskType") String taskType,
                    @Param("isArchived") Boolean isArchived,
                    @Param("createdAtStart") java.time.OffsetDateTime createdAtStart,
                    @Param("createdAtEnd") java.time.OffsetDateTime createdAtEnd);

    /**
     * 统计已过期的待处理任务数量
     *
     * @return 过期的 PENDING 任务数量
     */
    int countExpiredPendingTasks();

    /**
     * 将已过期的待处理任务标记为失败状态
     *
     * @return 更新的任务数量
     */
    int markExpiredTasksAsFailed();
}
