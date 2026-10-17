package com.lion.resume.graph.action;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.lion.resume.common.constant.GraphStateKeys;
import com.lion.resume.component.TaskProgressService;
import com.lion.resume.graph.ResumeReviewProps;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 节点追踪基类（即"自定义切面"）：
 * 每个节点执行前后，向 OverAllState / Redis / SSE 注入当前进度与节点状态
 * - onStart: 推送 running 事件、更新任务进度
 * - onComplete: 推送 completed 事件（状态持久化由框架 Checkpoint 负责）
 * - onError: 推送 error / retrying 事件，并按配置自动重试（LLM 限流、网络抖动容错）
 */
@Slf4j
public abstract class BaseTrackingAction implements AsyncNodeAction {

    protected final TaskProgressService progressService;
    protected final ResumeReviewProps props;

    protected BaseTrackingAction(TaskProgressService progressService, ResumeReviewProps props) {
        this.progressService = progressService;
        this.props = props;
    }

    /** 节点ID */
    public abstract String nodeId();

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        Map<String, Object> data = state.data();
        String taskId = String.valueOf(data.getOrDefault(GraphStateKeys.TASK_ID, "unknown"));
        // 节点「开始前推送」已下放到 ProgressPushListener（GraphLifecycleListener 桥接），
        // 本类只负责业务执行、重试、以及「完成」事件（完成需携带节点返回值，仍在此处推送）。

        int attempt = 0;
        while (true) {
            try {
                Map<String, Object> result = doExecute(state);
                if (emitCompleteEvent(result)) {
                    // 推送完成标志（含节点返回值，供前端展示评分）
                    progressService.onNodeComplete(taskId, nodeId(), result);
                }
                return CompletableFuture.completedFuture(result);
            } catch (Exception e) {
                attempt++;
                log.warn("节点[{}]执行失败(第{}次): {}", nodeId(), attempt, e.getMessage());
                // 最终失败：抛出异常交由框架 onError → ProgressPushListener 推送 error 事件
                if (attempt >= props.getLlmRetryTimes()) {
                    return CompletableFuture.failedFuture(
                            new RuntimeException("节点[" + nodeId() + "]执行失败: " + e.getMessage(), e));
                }
                // 重试前推送 retrying 事件（每轮重试一次，监听器无法感知尝试次数，故保留此处）
                progressService.onNodeRetry(taskId, nodeId(), attempt);
                try {
                    Thread.sleep(props.getLlmRetryDelayMs() * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return CompletableFuture.failedFuture(new RuntimeException("节点执行被中断"));
    }

    /**
     * 是否发送 completed 事件（默认发送；子类可覆盖，如 human_review 挂起时不发送）
     */
    protected boolean emitCompleteEvent(Map<String, Object> result) {
        return true;
    }

    /**
     * 节点业务逻辑
     */
    protected abstract Map<String, Object> doExecute(OverAllState state) throws Exception;
}
