flowchart TB
    Start([客户端发起取消]) --> CheckStatus{检查任务状态}

    CheckStatus -->|SUCCESS/FAILED/CANCELLED| End1[返回不允许取消]
    CheckStatus -->|PENDING| UpdatePending[更新为 CANCELLING 任务未开始]
    CheckStatus -->|RUNNING| UpdateRunning[更新为 CANCELLING 任务执行中]

    UpdatePending --> NotifyClient[立即返回客户端]
    UpdateRunning --> NotifyClient

    NotifyClient --> WorkerCheck{Worker 定期检测}

    WorkerCheck -->|GET /status 每 5-10 秒| DetectSignal{status=CANCELLING?}

    DetectSignal -->|否| Continue[继续执行业务逻辑]
    DetectSignal -->|是| StopLogic[停止业务逻辑]

    Continue --> WorkerCheck

    StopLogic --> Cleanup[清理资源]
    Cleanup --> ReportCancelled[POST /result]
    ReportCancelled --> FinalUpdate[更新为 CANCELLED]
    FinalUpdate --> End2([取消成功])

    End1 --> EndNode([流程结束])
    End2 --> EndNode

    style Start fill:#4CAF50,color:#fff
    style StopLogic fill:#FF5722,color:#fff
    style FinalUpdate fill:#2196F3,color:#fff
    style EndNode fill:#607D8B,color:#fff
