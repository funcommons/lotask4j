package fun.commons.lotask4j.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import fun.commons.lotask4j.entity.AstTaskTypeConfig;
import fun.commons.lotask4j.enums.BusinessCode;
import fun.commons.lotask4j.mapper.AstTaskMapper;
import fun.commons.lotask4j.mapper.AstTaskTypeConfigMapper;
import fun.commons.framework4j.web.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * P1-5: 任务提交准入控制。
 *
 * 责任：
 * <ol>
 *   <li>读 {@code AstTaskTypeConfig} 的 max_concurrency / max_queued 配置。</li>
 *   <li>读 {@code AstTaskMapper#countInFlightByType} 当前在途 (PENDING + RUNNING + CANCELLING) 数。</li>
 *   <li>若已超阈值，抛 {@code ApiException(QUEUE_FULL, "...")} — 客户端得到 200 + code=20006。</li>
 *   <li>无配置或未禁用 → 放行。</li>
 * </ol>
 *
 * 该服务由 {@code TaskServiceImpl.submitTask} 在 INSERT 之前调用，
 * 防止任务类型过载。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskSubmitGuard {

    private final AstTaskMapper taskMapper;
    private final AstTaskTypeConfigMapper typeConfigMapper;

    /**
     * 准入校验。若超过 max_queued 或 max_concurrency 则抛 QUEUE_FULL。
     *
     * @param taskType 任务类型 key
     * @param tenantId claim 租户 (类型租户内唯一; 同 typeKey 跨租户各自的背压配置)
     * @throws ApiException(code=20006) 队列已满
     */
    public void checkOrThrow(String taskType, Long tenantId) {
        if (taskType == null || taskType.isEmpty()) {
            return; // 无 type 不在背压范围内 (但 task type 校验由 service 层处理)
        }

        AstTaskTypeConfig config = typeConfigMapper.selectOne(
                new LambdaQueryWrapper<AstTaskTypeConfig>()
                        .eq(AstTaskTypeConfig::getTypeKey, taskType)
                        .eq(tenantId != null, AstTaskTypeConfig::getTenantId, tenantId)
                        .eq(AstTaskTypeConfig::getIsDeleted, 0));

        if (config == null) {
            // 没有 type config 注册, 不做背压限制 — 留给 "未知任务类型" 业务码处理
            return;
        }

        Integer maxQueued = config.getMaxQueued();
        Integer maxConcurrency = config.getConcurrencyLimit();

        // 0 视为无限制 (与 null 等价, 配置可读性更好)
        boolean hasQueuedLimit = maxQueued != null && maxQueued > 0;
        boolean hasConcurrencyLimit = maxConcurrency != null && maxConcurrency > 0;

        if (!hasQueuedLimit && !hasConcurrencyLimit) {
            return; // 无任何限制
        }

        long inFlight = taskMapper.countInFlightByType(taskType);

        // 已 max_queued 时拒绝 (PENDING + RUNNING 总深度)
        if (hasQueuedLimit && inFlight >= maxQueued) {
            log.warn("任务队列已满 (max_queued): type={}, inFlight={}, max={}",
                    taskType, inFlight, maxQueued);
            throw new ApiException(BusinessCode.QUEUE_FULL.getCode(),
                    "任务队列已满 (max_queued=" + maxQueued + "): " + taskType);
        }

        // 已 max_concurrency 时拒绝 (RUNNING + CANCELLING 视为占用)
        // 注：这里用 inFlight 作为粗略近似, 实际更精确是 count(RUNNING, CANCELLING)
        if (hasConcurrencyLimit && inFlight >= maxConcurrency) {
            log.warn("任务并发已满 (max_concurrency): type={}, inFlight={}, max={}",
                    taskType, inFlight, maxConcurrency);
            throw new ApiException(BusinessCode.QUEUE_FULL.getCode(),
                    "任务并发已满 (max_concurrency=" + maxConcurrency + "): " + taskType);
        }
    }
}
