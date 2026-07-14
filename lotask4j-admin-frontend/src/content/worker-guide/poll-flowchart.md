flowchart TD
    Start([Worker 启动]) --> Init[初始化配置]
    Init --> PollLoop{开始 Poll 循环}

    PollLoop --> CheckType[POST /worker/tasks/poll]
    CheckType --> UpdateHeart[自动更新心跳 UPSERT worker_node]

    UpdateHeart --> Query[查询 PENDING 任务 FOR UPDATE SKIP LOCKED]

    Query -->|有可用任务| LockTask[锁定任务]
    Query -->|无任务| Wait[等待 5 秒]

    LockTask --> UpdateStatus[更新任务状态 status=RUNNING]
    UpdateStatus --> Execute[执行业务逻辑]

    Execute --> CheckCancel{检测取消信号?}
    CheckCancel -->|CANCELLING| StopExec[停止执行]
    CheckCancel -->|继续| Progress[上报进度 POST /progress]

    Progress --> MoreWork{任务完成?}
    MoreWork -->|否| CheckCancel
    MoreWork -->|是| ReportResult[上报结果 POST /result]

    StopExec --> ReportCancel[上报取消 status=CANCELLED]

    ReportResult --> PollLoop
    ReportCancel --> PollLoop
    Wait --> PollLoop

    style Start fill:#4CAF50,color:#fff
    style Execute fill:#2196F3,color:#fff
    style StopExec fill:#FF5722,color:#fff
    style ReportResult fill:#52c41a,color:#fff
