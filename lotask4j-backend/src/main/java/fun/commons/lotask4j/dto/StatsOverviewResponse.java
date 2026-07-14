package fun.commons.lotask4j.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 统计概览响应 DTO
 */
@Getter
@Setter
public class StatsOverviewResponse {

    /**
     * 当前待处理任务总数
     */
    private Long totalPending;

    /**
     * 当前正在运行任务总数
     */
    private Long totalRunning;

    /**
     * 今日累计��据
     */
    private TodayStats todayStats;

    /**
     * Worker 节点概况
     */
    private WorkerCount workerCount;

    @Getter
    @Setter
    public static class TodayStats {
        /**
         * 今日成功任务数
         */
        private Long success;

        /**
         * 今日失败任务数
         */
        private Long failed;

        /**
         * 今日取消任务数
         */
        private Long cancelled;
    }

    @Getter
    @Setter
    public static class WorkerCount {
        /**
         * 在线 Worker 数
         */
        private Integer online;

        /**
         * 离线 Worker 数
         */
        private Integer offline;
    }
}
