sequenceDiagram
    autonumber
    participant Worker as Worker 节点
    participant API as ASTS API
    participant DB as PostgreSQL

    Note over Worker: 任务执行中

    Worker->>Worker: 步骤1：初始化
    Worker->>API: POST /tasks/progress (progress: 5)
    API->>DB: UPDATE progress=5, current_step='init'
    DB-->>API: 更新成功
    API-->>Worker: 200 OK

    loop 数据处理循环
        Worker->>Worker: 处理数据块 (1/10)

        Note over Worker: 每 5-10 秒检测取消
        Worker->>API: GET /tasks/status
        API->>DB: SELECT status
        DB-->>API: status='RUNNING'
        API-->>Worker: 继续执行

        Worker->>API: POST /tasks/progress (progress: 35)
        API->>DB: UPDATE progress=35
    end

    Worker->>Worker: 步骤2：文件写入
    Worker->>API: POST /tasks/progress (progress: 85)
    API->>DB: UPDATE progress=85

    Worker->>Worker: 步骤3：文件上传
    Worker->>API: POST /tasks/progress (progress: 95)
    API->>DB: UPDATE progress=95

    Note over Worker: 执行完成

    Worker->>API: POST /tasks/result (SUCCESS)
    API->>DB: UPDATE status='SUCCESS', finished_at=NOW()
    DB-->>API: 更新成功
    API-->>Worker: 200 OK

    Note over Worker: 继续 Poll 下一个任务
