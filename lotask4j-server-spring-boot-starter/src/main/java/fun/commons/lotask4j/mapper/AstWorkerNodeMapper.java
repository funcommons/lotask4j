package fun.commons.lotask4j.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import fun.commons.lotask4j.entity.AstWorkerNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Worker 节点 Mapper
 */
@Mapper
public interface AstWorkerNodeMapper extends BaseMapper<AstWorkerNode> {

    /**
     * 根据 Worker ID 查询 Worker 节点
     */
    AstWorkerNode selectByWorkerId(@Param("workerId") String workerId);

    /**
     * 查询在线 Worker 列表(基于 status='ONLINE')
     * status 字段由 WorkerCleaner 定时任务维护
     */
    List<AstWorkerNode> selectOnlineWorkers();

    /**
     * 更新 Worker 心跳时间
     */
    int updateHeartbeat(
            @Param("id") Long id,
            @Param("workerId") String workerId,
            @Param("workerIp") String workerIp,
            @Param("workerPort") Integer workerPort,
            @Param("hostname") String hostname,
            @Param("heartbeatTime") OffsetDateTime heartbeatTime
    );

    /**
     * 根据 Worker IP 和任务类型查询 Worker 节点
     *
     * @param workerIp Worker IP 地址
     * @param taskTypeKey 任务类型标识
     * @return Worker 节点
     */
    AstWorkerNode selectByIpAndType(@Param("workerIp") String workerIp,
                                     @Param("taskTypeKey") String taskTypeKey);

    /**
     * UPSERT Worker 心跳记录 (ON CONFLICT DO UPDATE)
     * 如果 (worker_ip, task_type_key) 已存在则更新心跳时间和状态，否则插入新记录
     *
     * @param worker Worker 节点实体
     * @return 影响行数
     */
    int upsertWorkerHeartbeat(@Param("worker") AstWorkerNode worker);

    /**
     * 标记超时 Worker 为 OFFLINE
     * 将心跳时间早于 threshold 的 Worker 状态设为 OFFLINE
     *
     * @param taskTypeKey 任务类型标识
     * @param threshold 超时阈值时间 (早于此时间的 Worker 将被标记为 OFFLINE)
     * @return 被标记为 OFFLINE 的 Worker 数量
     */
    int markOfflineWorkers(@Param("taskTypeKey") String taskTypeKey,
                           @Param("threshold") OffsetDateTime threshold);

    /**
     * 物理删除严重超时的 Worker
     * 永久删除心跳时间早于 threshold 的 Worker 记录
     *
     * @param taskTypeKey 任务类型标识
     * @param threshold 删除阈值时间 (早于此时间的 Worker 将被物理删除)
     * @return 被删除的 Worker 数量
     */
    int deleteExpiredWorkers(@Param("taskTypeKey") String taskTypeKey,
                             @Param("threshold") OffsetDateTime threshold);
}
