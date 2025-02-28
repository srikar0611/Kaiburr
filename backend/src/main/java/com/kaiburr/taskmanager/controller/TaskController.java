package com.kaiburr.taskmanager.controller;

import com.kaiburr.taskmanager.model.Task;
import com.kaiburr.taskmanager.model.TaskExecution;
import com.kaiburr.taskmanager.repository.TaskRepository;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/tasks")
@CrossOrigin(origins = "http://localhost:3000")
public class TaskController {
    private static final Logger logger = LoggerFactory.getLogger(TaskController.class);
    private final TaskRepository taskRepository;

    public TaskController(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    // ✅ Get all tasks or a specific task by ID
    @GetMapping
    public ResponseEntity<?> getTasks(@RequestParam(required = false) String id) {
        if (id != null) {
            return taskRepository.findById(id)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }
        return ResponseEntity.ok(taskRepository.findAll());
    }

    // ✅ Get task by ID
    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskById(@PathVariable String id) {
        return taskRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ✅ Create a new task
    @PostMapping
    public ResponseEntity<Task> createTask(@RequestBody Task task) {
        if (task.getTaskExecutions() == null) {
            task.setTaskExecutions(new ArrayList<>());
        }
        return ResponseEntity.ok(taskRepository.save(task));
    }

    // ✅ Delete task
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable String id) {
        if (!taskRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        taskRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    // ✅ Search tasks by name
    @GetMapping("/search")
    public ResponseEntity<List<Task>> findByName(@RequestParam String name) {
        return ResponseEntity.ok(taskRepository.findByNameContainingIgnoreCase(name));
    }

    // ✅ Get Execution Stats (For Dashboard)
    @GetMapping("/execution-stats")
    public ResponseEntity<Map<String, Integer>> getExecutionStats() {
        List<Task> tasks = taskRepository.findAll();
        Map<String, Integer> executionStats = new HashMap<>();

        for (Task task : tasks) {
            int executionCount = (task.getTaskExecutions() != null) ? task.getTaskExecutions().size() : 0;
            executionStats.put(task.getName(), executionCount);
        }

        return ResponseEntity.ok(executionStats);
    }

    // ✅ Initialize Kubernetes Client
    private CoreV1Api initializeKubernetesClient() throws IOException {
        try {
            String kubeConfigPath = System.getProperty("user.home") + "/.kube/config";
            ApiClient client = Config.fromConfig(new FileReader(kubeConfigPath));
            client.setVerifyingSsl(false);
            Configuration.setDefaultApiClient(client);
            return new CoreV1Api(client);
        } catch (IOException e) {
            logger.warn("⚠️ Could not load kube config, falling back to in-cluster config");
            ApiClient client = Config.fromCluster();
            Configuration.setDefaultApiClient(client);
            return new CoreV1Api(client);
        }
    }

    // ✅ Execute Task in Kubernetes
    @PutMapping("/{id}/execute")
    public ResponseEntity<String> executeTask(@PathVariable String id) {
        try {
            Optional<Task> taskOpt = taskRepository.findById(id);
            if (taskOpt.isEmpty()) return ResponseEntity.notFound().build();

            Task task = taskOpt.get();
            String podName = "task-execution-" + UUID.randomUUID().toString().substring(0, 8);
            String command = task.getCommand();

            if (command == null || command.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("❌ Task command cannot be empty");
            }

            logger.info("🔄 Executing task {} with command: {}", id, command);

            CoreV1Api api = initializeKubernetesClient();

            // Capture Start Time
            Instant startTime = Instant.now();

            // Create Pod
            V1Pod pod = new V1Pod()
                    .apiVersion("v1")
                    .kind("Pod")
                    .metadata(new V1ObjectMeta().name(podName).namespace("default"))
                    .spec(new V1PodSpec()
                            .addContainersItem(new V1Container()
                                    .name("executor")
                                    .image("busybox:latest")
                                    .command(List.of("sh", "-c", command))
                                    .imagePullPolicy("IfNotPresent"))
                            .restartPolicy("Never"));

            logger.info("🚀 Creating pod: {}", podName);
            api.createNamespacedPod("default", pod, null, null, null, null);
            logger.info("✅ Pod {} created successfully", podName);

            // Wait for Pod Completion
            String finalLogs = "Pod execution timed out or failed.";
            for (int i = 0; i < 15; i++) {
                TimeUnit.SECONDS.sleep(2);
                V1Pod podStatus = api.readNamespacedPodStatus(podName, "default", null);
                String phase = podStatus.getStatus().getPhase();
                logger.info("🔄 Pod {} status: {}", podName, phase);

                if ("Succeeded".equals(phase) || "Failed".equals(phase)) {
                    finalLogs = api.readNamespacedPodLog(podName, "default", null, false, false,
                            null, null, false, null, null, false);
                    break;
                }
            }

            // Capture End Time
            Instant endTime = Instant.now();

            // Store Execution History
            task.getTaskExecutions().add(new TaskExecution(startTime, endTime, finalLogs));
            taskRepository.save(task);

            // Cleanup Pod After Execution
            api.deleteNamespacedPod(podName, "default", null, null, null, null, null, null);
            logger.info("🗑 Pod {} deleted", podName);

            return ResponseEntity.ok(finalLogs);

        } catch (IOException e) {
            logger.error("❌ Failed to initialize Kubernetes client", e);
            return ResponseEntity.internalServerError().body("❌ Failed to initialize Kubernetes client.");
        } catch (ApiException e) {
            logger.error("❌ Kubernetes API error: {}", e.getResponseBody());
            return ResponseEntity.internalServerError().body("❌ Kubernetes API error.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.internalServerError().body("❌ Pod execution interrupted.");
        }
    }
}
