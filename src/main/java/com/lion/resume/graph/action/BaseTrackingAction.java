package com.lion.resume.graph.action;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig;
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
 *
 * <p>实现 {@link AsyncNodeActionWithConfig} 而非 {@code AsyncNodeAction}：
 * 这样每个节点都能直接拿到 {@link RunnableConfig}，从 {@code config.threadId()}
 * 取任务 ID（发起执行时 threadId 即 taskId），身份类字段不再依赖 OverAllState 往返，
 * 也就不会受「键未注册 → Checkpoint 往返后丢失」的影响。
 */
@Slf4j
public abstract class BaseTrackingAction implements AsyncNodeActionWithConfig {

    protected final TaskProgressService progressService;
    protected final ResumeReviewProps props;

    protected BaseTrackingAction(TaskProgressService progressService, ResumeReviewProps props) {
        this.progressService = progressService;
        this.props = props;
    }

    /** 节点ID */
    public abstract String nodeId();

    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state, RunnableConfig config) {
        String taskId = resolveTaskId(state, config);
        // 仅用于日志/进度推送的兜底值，避免 null 打进日志
        String safeTaskId = (taskId == null || taskId.isBlank()) ? "unknown" : taskId;
        // 节点「开始前推送」已下放到 ProgressPushListener（GraphLifecycleListener 桥接），
        // 本类只负责业务执行、重试、以及「完成」事件（完成需携带节点返回值，仍在此处推送）。

        int attempt = 0;
        while (true) {
            try {
                Map<String, Object> result = doExecute(state, config);
                if (emitCompleteEvent(result)) {
                    // 推送完成标志（含节点返回值，供前端展示评分）
                    progressService.onNodeComplete(safeTaskId, nodeId(), result);
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
                progressService.onNodeRetry(safeTaskId, nodeId(), attempt);
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
     * 解析当前任务ID（取不到返回 {@code null}，由调用方决定兜底）。
     *
     * <p>取值优先级：
     * <ol>
     *   <li>{@code config.threadId()} —— 发起执行时显式指定 {@code threadId = taskId}，
     *       由框架沿调用链传递，与 OverAllState 无关，最可靠；</li>
     *   <li>{@code OverAllState} 的 {@code task_id} —— 兼容手工调用 / 单元测试。</li>
     * </ol>
     * 不用 {@code String.valueOf(...)}：null 会变成字符串 "null"，导致按 id 查询全部落空。
     */
    protected String resolveTaskId(OverAllState state, RunnableConfig config) {
        if (config != null && config.threadId() != null) {
            String tid = config.threadId().orElse(null);
            if (tid != null && !tid.isBlank()) {
                return tid;
            }
        }
        if (state != null && state.data() != null) {
            Object v = state.data().get(GraphStateKeys.TASK_ID);
            if (v != null) {
                // 正常情况下不会走到这里：config.threadId() 应有值。
                // 若日志里频繁出现，说明框架未把 RunnableConfig 透传给节点，需检查 addNode 的注册方式。
                log.debug("节点[{}] 未从 RunnableConfig 取到 threadId，回退使用 State 中的 task_id", nodeId());
                return String.valueOf(v);
            }
        }
        return null;
    }

    /**
     * 是否发送 completed 事件（默认发送；子类可覆盖，如 human_review 挂起时不发送）
     */
    protected boolean emitCompleteEvent(Map<String, Object> result) {
        return true;
    }

    /**
     * 节点业务逻辑
     *
     * @param state  全局状态
     * @param config 本次执行的运行配置，可从中取 threadId / 中断标记等
     */
    protected abstract Map<String, Object> doExecute(OverAllState state, RunnableConfig config) throws Exception;
}
