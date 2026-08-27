package fun.commons.lotask4j.enums;

/**
 * 业务错误码枚举
 *
 * 按照 framework4j-sdk 用户指南规范定义
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
public enum BusinessCode {

    /**
     * 任务提交失败
     */
    TASK_SUBMIT_FAILED(20001, "任务提交失败"),

    /**
     * P1-5: 队列已满 (max_concurrency / max_queued 触发)
     */
    QUEUE_FULL(20006, "任务队列已满"),

    /**
     * 任务不存在 (兼容历史 20404 magic code,统一归一)
     */
    TASK_NOT_FOUND(20100, "任务不存在"),

    /**
     * 未知的任务类型
     */
    TASK_TYPE_UNKNOWN(20101, "未知的任务类型"),

    /**
     * 该任务类型已被禁用
     */
    TASK_TYPE_DISABLED(20102, "该任务类型已被禁用"),

    /**
     * 任务状态不允许取消
     */
    TASK_CANCEL_NOT_ALLOWED(20401, "任务状态不允许取消"),

    /**
     * 任务状态非法（不允许当前操作）— 例如进度上报时任务非 RUNNING、结果上报时任务已变
     * （兼容历史 20409 magic code,统一归一）
     */
    TASK_STATE_INVALID(20409, "任务状态已变更"),

    /**
     * 认证参数缺失 (grant_type / client_id / client_secret)
     */
    AUTH_PARAM_MISSING(20103, "缺少必要的认证参数"),

    /**
     * 不支持的 grant_type (仅支持 client_credentials)
     */
    AUTH_GRANT_TYPE_UNSUPPORTED(20104, "不支持的 grant_type, 仅支持 client_credentials"),

    /**
     * client_id 或 client_secret 无效
     */
    AUTH_INVALID_CREDENTIALS(20105, "client_id 或 client_secret 无效");

    private final int code;
    private final String message;

    BusinessCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
