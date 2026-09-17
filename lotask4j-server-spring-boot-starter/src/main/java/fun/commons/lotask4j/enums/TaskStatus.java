package fun.commons.lotask4j.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * 异步任务状态枚举 — 严格状态机定义（P0 基础）。
 *
 * 合法迁移：
 *   PENDING   → RUNNING  (poll + dispatch + start)
 *   PENDING   → CANCELLING (用户取消尚未启动的任务)
 *   RUNNING   → SUCCESS
 *   RUNNING   → FAILED
 *   RUNNING   → CANCELLING (用户取消正在执行的任务)
 *   CANCELLING → CANCELLED (Worker 确认取消成功)
 *   CANCELLING → FAILED   (取消过程中发生业务错误，标记为失败)
 *
 * 终态：SUCCESS / FAILED / CANCELLED。
 * Reaper 可把 RUNNING 的 lease 过期任务回退为 PENDING（重试）或 FAILED（超 max_attempts）。
 *
 * 所有状态迁移必须通过 {@code TaskStateMachine}，
 * 数据库侧以乐观锁 (version) + fencing token (execution_token) 做 CAS 校验，
 * 应用侧以 {@link #canTransition(String, String)} 做前置语义校验。
 *
 * @author lotask4j-team
 * @version 1.0.0
 */
public enum TaskStatus {

    /** 初始或重试状态，等待 Worker 拉取 */
    PENDING,

    /** Worker 已 dispatched + started，正在执行 */
    RUNNING,

    /** 业务正常完成 */
    SUCCESS,

    /** 业务异常或 Reaper 判定无法恢复 */
    FAILED,

    /** 用户请求取消，等待 Worker 合作中断 */
    CANCELLING,

    /** Worker 确认已停止（终态） */
    CANCELLED;

    /** 终态集合 — Reaper/scheduler 不应再操作这些任务 */
    public static final Set<TaskStatus> TERMINAL = EnumSet.of(SUCCESS, FAILED, CANCELLED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** 任务处于不可被 cancel 的状态 */
    public boolean isCancellable() {
        return this == PENDING || this == RUNNING;
    }

    /**
     * 判断 from → to 是否为合法状态迁移。
     *
     * 仅表达"语义合法"，数据库侧的 version/token/worker 校验
     * 仍然由 SQL CAS 完成。
     */
    public static boolean canTransition(TaskStatus from, TaskStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return false;
        }
        switch (from) {
            case PENDING:
                return to == RUNNING || to == CANCELLING || to == FAILED;
            case RUNNING:
                return to == SUCCESS || to == FAILED || to == CANCELLING || to == PENDING;
            case CANCELLING:
                return to == CANCELLED || to == FAILED;
            default:
                return false;
        }
    }

    public static boolean canTransition(String from, String to) {
        if (from == null || to == null) {
            return false;
        }
        return canTransition(TaskStatus.valueOf(from), TaskStatus.valueOf(to));
    }

    public String wireValue() {
        return name();
    }
}
