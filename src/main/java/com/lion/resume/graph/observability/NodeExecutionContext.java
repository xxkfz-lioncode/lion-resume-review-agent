package com.lion.resume.graph.observability;

import com.lion.resume.common.enums.NodeStatus;

import java.util.Map;

/**
 * 单次节点执行的上下文：在 Graph 生命周期回调（before/after/onError）之间传递。
 *
 * <p>由 {@link GraphNodeLifecycleBridge} 在 {@code before} 时创建、记录起始时间戳，
 * 在 {@code after}/{@code onError} 时计算耗时，再分发给所有 {@link NodeExecutionListener}。
 * 监听器中只读取本对象，不依赖框架具体回调签名，后续框架升级也不影响业务监听器。
 */
public class NodeExecutionContext {

    /** 业务任务ID */
    private final String taskId;
    /** 操作用户ID */
    private final String userId;
    /** Graph threadId（Checkpoint 隔离维度） */
    private final String threadId;
    /** 节点ID */
    private final String nodeId;
    /** 当前节点完整状态快照（只读，禁止落库大字段） */
    private final Map<String, Object> state;
    /** 是否处于中断（人工复核挂起） */
    private final boolean interrupted;

    private int attempt = 1;
    private long startNanos;
    private Long durationMs;
    private NodeStatus status = NodeStatus.RUNNING;
    private String errorMessage;

    public NodeExecutionContext(String taskId, String userId, String threadId, String nodeId,
                                Map<String, Object> state, boolean interrupted) {
        this.taskId = taskId;
        this.userId = userId;
        this.threadId = threadId;
        this.nodeId = nodeId;
        this.state = state;
        this.interrupted = interrupted;
    }

    public void markStart(long startNanos) {
        this.startNanos = startNanos;
    }

    public void finish(NodeStatus status, long endNanos) {
        this.status = status;
        if (startNanos > 0) {
            this.durationMs = (endNanos - startNanos) / 1_000_000;
        }
    }

    public String getTaskId() {
        return taskId;
    }

    public String getUserId() {
        return userId;
    }

    public String getThreadId() {
        return threadId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public Map<String, Object> getState() {
        return state;
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    public int getAttempt() {
        return attempt;
    }

    public void setAttempt(int attempt) {
        this.attempt = attempt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
