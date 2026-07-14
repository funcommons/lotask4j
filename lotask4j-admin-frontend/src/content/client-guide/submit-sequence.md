sequenceDiagram
    autonumber
    participant Client as 客户端应用
    participant API as ASTS API
    participant DB as PostgreSQL
    participant Worker as Worker 节点

    Note over Client: 用户触发导出操作

    Client->>API: POST /client/tasks<br/>{type, payload, priority}
    API->>DB: INSERT INTO asts_task<br/>status='PENDING'
    DB-->>API: 插入成功, 返回 id
    API-->>Client: 返回 id

    Note over Client: 立即返回，不阻塞

    loop 客户端轮询（指数退避）
        Client->>API: GET /client/tasks/{id}
        API->>DB: SELECT * FROM asts_task
        DB-->>API: 返回任务详情
        API-->>Client: status='RUNNING', progress=65%
    end

    Note over Worker: Worker Poll 抢占任务

    Worker->>API: POST /worker/tasks/poll
    API->>DB: SELECT FOR UPDATE SKIP LOCKED
    DB-->>API: 返回任务
    API-->>Worker: 返回任务详情

    Note over Worker: 执行业务逻辑

    Worker->>API: POST /worker/tasks/{id}/progress<br/>{progress: 80}
    API->>DB: UPDATE progress=80

    Worker->>API: POST /worker/tasks/{id}/result<br/>{status: 'SUCCESS'}
    API->>DB: UPDATE status='SUCCESS'

    alt 配置了 Webhook
        API->>Client: POST {callbackUrl}<br/>{event, id, result}
        Client-->>API: 200 OK
    end

    Client->>API: GET /client/tasks/{id}
    API->>DB: SELECT * FROM asts_task
    DB-->>API: status='SUCCESS'
    API-->>Client: 返回最终结果
