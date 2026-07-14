package fun.commons.lotask4j.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Worker 抢占任务请求 DTO
 */
@Getter
@Setter
public class PollTaskRequest {

    /**
     * 任务类型
     */
    @NotBlank(message = "任务类型不能为空")
    private String taskType;

    /**
     * 调度策略: PRIORITY(优先级优先), FIFO(先入先出)
     */
    private String strategy = "PRIORITY";
}
