# Worker 实现示例（Java）

## 1. Maven 依赖

\`\`\`xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
</dependencies>
\`\`\`

---

## 2. ASTS Worker 客户端封装

\`\`\`java
package com.example.worker.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.util.Map;
import java.util.HashMap;

@Component
public class AstsWorkerClient {

    private final RestTemplate restTemplate;
    private final String astsBaseUrl = "http://localhost:8080/api/v1/worker";

    public AstsWorkerClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Poll 任务
     */
    public Map<String, Object> pollTask(String taskType, String strategy, String workerIp) {
        String url = astsBaseUrl + "/tasks/poll";

        Map<String, Object> request = new HashMap<>();
        request.put("taskType", taskType);
        request.put("strategy", strategy);
        request.put("workerIp", workerIp);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
        Map<String, Object> body = response.getBody();

        if (body != null && (Integer) body.get("code") == 0) {
            return (Map<String, Object>) body.get("data");
        }

        return null;
    }

    /**
     * 查询任务状态（检测取消信号）
     */
    public String getTaskStatus(String id) {
        String url = astsBaseUrl + "/tasks/" + id + "/status";

        ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
        Map<String, Object> body = response.getBody();

        if (body != null && (Integer) body.get("code") == 0) {
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            return (String) data.get("status");
        }

        return null;
    }

    /**
     * 上报任务进度
     */
    public void reportProgress(String id, int progress, String currentStep,
                                String currentStepKey, Object stepsDetail) {
        String url = astsBaseUrl + "/tasks/" + id + "/progress";

        Map<String, Object> request = new HashMap<>();
        request.put("progress", progress);
        request.put("currentStep", currentStep);
        request.put("currentStepKey", currentStepKey);
        request.put("stepsDetail", stepsDetail);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        restTemplate.postForEntity(url, entity, Map.class);
    }

    /**
     * 上报任务结果
     */
    public void reportResult(String id, String status, Map<String, Object> result,
                             String errorMsg) {
        String url = astsBaseUrl + "/tasks/" + id + "/result";

        Map<String, Object> request = new HashMap<>();
        request.put("status", status);
        request.put("result", result);
        request.put("errorMsg", errorMsg);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

        restTemplate.postForEntity(url, entity, Map.class);
    }
}
\`\`\`

---

## 3. 任务执行器基类

\`\`\`java
package com.example.worker.executor;

import com.example.worker.client.AstsWorkerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

public abstract class TaskExecutor {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutor.class);

    protected final AstsWorkerClient astsClient;

    public TaskExecutor(AstsWorkerClient astsClient) {
        this.astsClient = astsClient;
    }

    /**
     * 执行任务
     */
    public void execute(Map<String, Object> task) {
        String id = (String) task.get("id");
        Map<String, Object> payload = (Map<String, Object>) task.get("payload");

        log.info("开始执行任务: id={}", id);

        try {
            // 执行业务逻辑
            Map<String, Object> result = doExecute(id, payload);

            // 上报成功结果
            astsClient.reportResult(id, "SUCCESS", result, null);
            log.info("任务执行成功: id={}", id);

        } catch (CancelledException e) {
            // 任务被取消
            astsClient.reportResult(id, "CANCELLED", null, "用户取消任务");
            log.warn("任务已取消: id={}", id);

        } catch (Exception e) {
            // 任务执行失败
            astsClient.reportResult(id, "FAILED", null, e.getMessage());
            log.error("任务执行失败: id={}, error={}", id, e.getMessage(), e);
        }
    }

    /**
     * 子类实现具体的业务逻辑
     */
    protected abstract Map<String, Object> doExecute(String id,
                                                      Map<String, Object> payload)
            throws Exception;

    /**
     * 检测取消信号
     */
    protected void checkCancellation(String id) throws CancelledException {
        String status = astsClient.getTaskStatus(id);
        if ("CANCELLING".equals(status)) {
            throw new CancelledException("任务已被取消");
        }
    }

    /**
     * 上报进度
     */
    protected void reportProgress(String id, int progress, String currentStep,
                                   String currentStepKey) {
        astsClient.reportProgress(id, progress, currentStep, currentStepKey, null);
    }

    /**
     * 取消异常
     */
    public static class CancelledException extends Exception {
        public CancelledException(String message) {
            super(message);
        }
    }
}
\`\`\`

---

## 4. 数据导出任务执行器实现

\`\`\`java
package com.example.worker.executor;

import com.example.worker.client.AstsWorkerClient;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.HashMap;

@Component
public class DataExportExecutor extends TaskExecutor {

    public DataExportExecutor(AstsWorkerClient astsClient) {
        super(astsClient);
    }

    @Override
    protected Map<String, Object> doExecute(String id, Map<String, Object> payload)
            throws Exception {

        String query = (String) payload.get("query");
        String format = (String) payload.get("format");

        // 步骤 1: 初始化
        reportProgress(id, 5, "初始化", "init");
        checkCancellation(id);  // 检测取消信号
        Thread.sleep(500);

        // 步骤 2: 数据查询
        reportProgress(id, 20, "数据查询", "querying");
        checkCancellation(id);

        // 模拟查询大量数据
        int totalRows = 100000;
        for (int i = 0; i < 10; i++) {
            Thread.sleep(2000);  // 模拟查询延迟
            checkCancellation(id);  // 定期检测取消

            int currentRows = (i + 1) * 10000;
            int stepProgress = (currentRows * 100) / totalRows;
            int globalProgress = 20 + (stepProgress * 60 / 100);  // 20% ~ 80%

            reportProgress(id, globalProgress, "数据查询", "querying");
        }

        // 步骤 3: 文件写入
        reportProgress(id, 85, "文件写入", "writing");
        checkCancellation(id);
        Thread.sleep(3000);  // 模拟写入

        // 步骤 4: 文件上传
        reportProgress(id, 95, "文件上传", "uploading");
        checkCancellation(id);
        Thread.sleep(2000);  // 模拟上传

        // 构造结果
        Map<String, Object> result = new HashMap<>();
        result.put("fileUrl", "https://oss.example.com/export_" + id + ".xlsx");
        result.put("fileSize", 5242880);
        result.put("rows", totalRows);
        result.put("format", format);

        return result;
    }
}
\`\`\`

---

## 5. Worker 主循环

\`\`\`java
package com.example.worker;

import com.example.worker.client.AstsWorkerClient;
import com.example.worker.executor.DataExportExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Map;
import java.util.HashMap;

@Component
public class WorkerRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkerRunner.class);

    private final AstsWorkerClient astsClient;
    private final DataExportExecutor dataExportExecutor;

    public WorkerRunner(AstsWorkerClient astsClient, DataExportExecutor dataExportExecutor) {
        this.astsClient = astsClient;
        this.dataExportExecutor = dataExportExecutor;
    }

    @Override
    public void run(String... args) throws Exception {
        String workerIp = InetAddress.getLocalHost().getHostAddress();
        log.info("Worker 启动成功, IP: {}", workerIp);

        // 配置支持的任务类型和执行器
        Map<String, TaskExecutor> executors = new HashMap<>();
        executors.put("data_export", dataExportExecutor);

        // 主循环
        while (true) {
            try {
                // Poll 任务（自动心跳）
                Map<String, Object> task = astsClient.pollTask(
                    "data_export",  // 任务类型
                    "PRIORITY",     // 策略
                    workerIp        // Worker IP
                );

                if (task != null) {
                    // 找到任务，执行
                    String taskType = (String) task.get("type");
                    TaskExecutor executor = executors.get(taskType);

                    if (executor != null) {
                        executor.execute(task);
                    } else {
                        log.warn("不支持的任务类型: {}", taskType);
                    }

                } else {
                    // 无任务，等待 5 秒
                    Thread.sleep(5000);
                }

            } catch (Exception e) {
                log.error("Worker 执行异常", e);
                Thread.sleep(5000);  // 出错后等待 5 秒
            }
        }
    }
}
\`\`\`

---

## 6. Spring Boot 主类

\`\`\`java
package com.example.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class WorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
\`\`\`

---

## 7. application.yml 配置

\`\`\`yaml
server:
  port: 9090

asts:
  api:
    base-url: http://localhost:8080/api/v1/worker
  worker:
    ip: 192.168.1.100
    poll-interval: 5000  # Poll 间隔（毫秒）
    supported-task-types:
      - data_export
      - video_transcode

logging:
  level:
    com.example.worker: INFO
\`\`\`
