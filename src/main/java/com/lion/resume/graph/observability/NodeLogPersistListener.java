package com.lion.resume.graph.observability;

import com.lion.resume.entity.NodeLog;
import com.lion.resume.mapper.NodeLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 节点执行日志落库监听器（后续扩展点）。
 *
 * <p>节点执行完成 / 异常时，将耗时、状态、错误信息写入 {@code t_graph_node_log}。
 * 写入走独立线程池异步化，且实现内部已 try-catch 隔离，写库失败只告警、绝不阻断审查主流程。
 *
 * <p>开关：{@code resume.node-listeners.node-log=true} 开启（默认开启）；关闭则不落库。
 */
@Slf4j
@Component
@Order(100)
@ConditionalOnProperty(name = "resume.node-listeners.node-log",
        havingValue = "true", matchIfMissing = true)
public class NodeLogPersistListener implements NodeExecutionListener {

    private final NodeLogMapper nodeLogMapper;
    private final ThreadPoolTaskExecutor nodeLogExecutor;

    public NodeLogPersistListener(NodeLogMapper nodeLogMapper,
                                  @Qualifier("nodeLogExecutor") ThreadPoolTaskExecutor nodeLogExecutor) {
        this.nodeLogMapper = nodeLogMapper;
        this.nodeLogExecutor = nodeLogExecutor;
    }

    @Override
    public void after(NodeExecutionContext ctx) {
        persist(ctx);
    }

    @Override
    public void onError(NodeExecutionContext ctx) {
        persist(ctx);
    }

    private void persist(NodeExecutionContext ctx) {
        if (ctx == null) {
            return;
        }
        nodeLogExecutor.execute(() -> {
            try {
                NodeLog logEntity = new NodeLog();
                logEntity.setId(UUID.randomUUID().toString().replace("-", ""));
                logEntity.setTaskId(ctx.getTaskId());
                logEntity.setUserId(ctx.getUserId());
                logEntity.setThreadId(ctx.getThreadId());
                logEntity.setNodeId(ctx.getNodeId());
                logEntity.setAttempt(ctx.getAttempt());
                logEntity.setStatus(ctx.getStatus() == null ? null : ctx.getStatus().getValue());
                logEntity.setDurationMs(ctx.getDurationMs());
                logEntity.setErrorMsg(ctx.getErrorMessage());
                logEntity.setStartTime(LocalDateTime.now().minusNanos(
                        ctx.getDurationMs() == null ? 0 : ctx.getDurationMs() * 1_000_000L));
                logEntity.setEndTime(LocalDateTime.now());
                logEntity.setCreateTime(LocalDateTime.now());
                nodeLogMapper.insert(logEntity);
            } catch (Exception e) {
                log.warn("节点执行日志落库失败 taskId={} node={}", ctx.getTaskId(), ctx.getNodeId(), e);
            }
        });
    }
}
