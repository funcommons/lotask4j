package fun.commons.lotask4j.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;


/**
 * Worker 节点响应 DTO (用于 Admin API)
 */
@Getter
@Setter
public class WorkerNodeResponse {

    /**
     * Worker 唯一标识
     */
    private String workerKey;

    /**
     * Worker IP 地址
     */
    private String workerIp;

    /**
     * 主机名
     */
    private String hostname;

    /**
     * 最后心跳时间
     */
    private OffsetDateTime lastHeartbeatAt;

    /**
     * 状态: ONLINE/OFFLINE/BUSY
     */
    private String status;
}
