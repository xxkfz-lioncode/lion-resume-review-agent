package com.lion.resume.component;

import com.lion.resume.cache.GraphStateCache;
import com.lion.resume.common.constant.GraphNodes;
import com.lion.resume.common.enums.NodeStatus;
import com.lion.resume.dto.GraphStatusEvent;
import com.lion.resume.entity.ReviewTask;
import com.lion.resume.graph.GraphProgress;
import com.lion.resume.mapper.ReviewTaskMapper;
import com.lion.resume.sse.GraphEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务进度上报组件（被 Graph 各节点调用）：
 * 1. 节点开始/完成时更新数据库中的 current_node、progress
 * 2. 将 Graph 状态快照写入 Redis（缓存执行状态）
 * 3. 通过 SSE 推送节点状态事件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskProgressService {

    private final ReviewTaskMapper taskMapper;
    private final GraphEventPublisher eventPublisher;
    private final GraphStateCache stateCache;

    public void onNodeStart(String taskId, String node, Map<String, Object> stateData) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setCurrentNode(node);
            task.setProgress(GraphProgress.startProgress(node));
            task.setStatus("RUNNING");
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
        }
        stateCache.saveState(taskId, stateData);
        eventPublisher.publish(GraphStatusEvent.builder()
                .type("node")
                .taskId(taskId)
                .node(node)
                .status(NodeStatus.RUNNING.getValue())
                .progress(GraphProgress.startProgress(node))
                .message("开始执行: " + label(node))
                .timestamp(System.currentTimeMillis())
                .build());
    }

    public void onNodeComplete(String taskId, String node, Map<String, Object> result) {
        // 合并节点结果到 Redis 状态快照
        Map<String, Object> snapshot = stateCache.loadState(taskId);
        if (result != null) {
            snapshot.putAll(result);
        }
        stateCache.saveState(taskId, snapshot);

        ReviewTask task = taskMapper.selectById(taskId);
        if (task != null && (task.getProgress() == null || task.getProgress() < GraphProgress.endProgress(node))) {
            task.setProgress(GraphProgress.endProgress(node));
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
        }
        eventPublisher.publish(GraphStatusEvent.builder()
                .type("node")
                .taskId(taskId)
                .node(node)
                .status(NodeStatus.COMPLETED.getValue())
                .progress(GraphProgress.endProgress(node))
                .message("完成: " + label(node))
                .data(result)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    public void onNodeRetry(String taskId, String node, int attempt) {
        eventPublisher.publish(GraphStatusEvent.builder()
                .type("node")
                .taskId(taskId)
                .node(node)
                .status(NodeStatus.RETRYING.getValue())
                .progress(GraphProgress.startProgress(node))
                .message("第 " + attempt + " 次重试: " + label(node))
                .timestamp(System.currentTimeMillis())
                .build());
    }

    public void onNodeError(String taskId, String node, String error, int attempt) {
        eventPublisher.publish(GraphStatusEvent.builder()
                .type("node")
                .taskId(taskId)
                .node(node)
                .status(NodeStatus.ERROR.getValue())
                .progress(GraphProgress.startProgress(node))
                .message("节点异常(" + attempt + "): " + error)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    public void updateTaskStatus(String taskId, String status) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus(status);
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }

    private String label(String node) {
        Map<String, Object> meta = GraphNodes.NODE_META.get(node);
        return meta == null ? node : String.valueOf(meta.get("label"));
    }
}
