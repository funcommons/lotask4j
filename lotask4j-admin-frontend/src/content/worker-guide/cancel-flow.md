flowchart LR
    Start([Worker 执行中]) --> Loop{业务逻辑循环}

    Loop --> Process[处理数据块 每 5-10 秒]
    Process --> Check[GET /tasks status 检测取消信号]

    Check --> Decision{status=?}

    Decision -->|RUNNING| Continue[继续执行下一个数据块]
    Decision -->|CANCELLING| Detect[检测到取消信号]

    Continue --> Loop

    Detect --> Stop[立即停止业务逻辑]
    Stop --> Cleanup[清理资源 关闭连接文件]
    Cleanup --> UpdateSteps[更新步骤状态]
    UpdateSteps --> Report[POST /result CANCELLED]
    Report --> End([取消完成])

    style Detect fill:#FF9800,color:#fff
    style Stop fill:#FF5722,color:#fff
    style Cleanup fill:#9C27B0,color:#fff
    style Report fill:#2196F3,color:#fff
