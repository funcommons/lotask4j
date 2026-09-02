package fun.commons.lotask4j.dto;

import fun.commons.framework4j.openid.annotation.OpenId;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务详情响应 DTO
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "任务详情信息")
public class TaskDetailResponse {

    /**
     * 任务 ID (对外使用 OpenID 混淆)
     */
    @OpenId
    @Schema(description = "任务唯一标识", example = "YeirYkxHuQ")
    private Long id;

    /**
     * 租户归属 (仅管理端列表回显; 客户端视角恒为本租户, 无需感知)
     */
    @Schema(description = "租户 ID (管理端)", example = "9101")
    private Long tenantId;

    /**
     * 任务类型 Key
     */
    @Schema(description = "任务类型标识", example = "data_export")
    private String type;

    /**
     * 任务类型名称
     */
    @Schema(description = "任务类型名称", example = "数据导出任务")
    private String typeName;

    /**
     * 任务状态
     * PENDING: 待处理
     * RUNNING: 处理中
     * SUCCESS: 成功
     * FAILED: 失败
     * CANCELLING: 取消中
     * CANCELLED: 已取消
     */
    @Schema(description = "任务状态", example = "RUNNING")
    private String status;

    /**
     * 全局进度百分比
     */
    @Schema(description = "全局进度百分比 (0-100)", example = "65")
    private Integer progress;

    /**
     * 当前步骤信息
     */
    @Schema(description = "当前正在执行的步骤 Key", example = "querying")
    private String currentStep;

    /**
     * 步骤详情列表
     * 每个步骤包含: key, name, status, detail, start_time, end_time, cost_ms
     */
    @Schema(description = "步骤执行详情")
    private List<Map<String, Object>> stepsDetail;

    /**
     * 任务输入参数
     */
    @Schema(description = "任务输入参数 (JSON)")
    private Map<String, Object> payload;

    /**
     * 执行结果
     */
    @Schema(description = "任务执行结果 (JSON)")
    private Map<String, Object> result;

    /**
     * 错误信息
     */
    @Schema(description = "错误信息")
    private String errorMsg;

    /**
     * 优先级
     */
    @Schema(description = "优先级", example = "10")
    private Integer priority;

    /**
     * 创建时间
     */
    @Schema(description = "任务创建时间")
    private OffsetDateTime createdAt;

    /**
     * 更新时间
     */
    @Schema(description = "任务更新时间")
    private OffsetDateTime updatedAt;

    /**
     * 开始执行时间
     */
    @Schema(description = "任务开始执行时间")
    private OffsetDateTime startedAt;

    /**
     * 完成时间
     */
    @Schema(description = "任务完成时间")
    private OffsetDateTime finishedAt;

    /**
     * 执行耗时 (秒)
     */
    @Schema(description = "执行耗时 (秒)")
    private Long durationSeconds;

    /**
     * 任务超时时间配置 (秒)
     * 从任务类型配置中获取，前端可以根据 createdAt + timeoutSeconds 计算过期时间
     */
    @Schema(description = "任务超时时间配置 (秒)", example = "3600")
    private Integer timeoutSeconds;

    /**
     * 任务过期时间
     * 超过此时间未完成的任务可以被清理或标记为过期
     */
    @Schema(description = "任务过期时间")
    private OffsetDateTime expiredAt;

    /**
     * P0: 当前 attempt (重试轮次)
     */
    @Schema(description = "当前重试轮次 (attempt)")
    private Integer attempt;

    /**
     * P0: 最大尝试次数
     */
    @Schema(description = "最大尝试次数 (maxAttempts)")
    private Integer maxAttempts;

    /**
     * P0: 乐观锁版本号
     */
    @Schema(description = "乐观锁版本号")
    private Integer version;

    /**
     * P0: 当前执行 ID
     */
    @Schema(description = "当前 execution ID")
    private Long executionId;

    /**
     * P0: fencing token
     */
    @Schema(description = "fencing token (Worker 上报时回传)")
    private Long executionToken;

    /**
     * P0: 当前 Worker 实例 ID
     */
    @Schema(description = "当前 Worker 实例 ID")
    private String workerId;

    /**
     * P0: 租约到期时间
     */
    @Schema(description = "租约到期时间")
    private OffsetDateTime leaseExpireAt;

    /**
     * P0: 用户请求取消时间（只读）
     */
    @Schema(description = "用户请求取消的时间")
    private OffsetDateTime requestedCancelAt;

    /**
     * P0: 上次错误码
     */
    @Schema(description = "上次错误码 (BusinessCode)")
    private String lastErrorCode;

    /**
     * P0: 上次错误描述
     */
    @Schema(description = "上次错误描述")
    private String lastErrorMessage;

    /**
     * P0: 幂等键 (回传给提交方)
     */
    @Schema(description = "幂等键 (回传给提交方)")
    private String idempotencyKey;
}
