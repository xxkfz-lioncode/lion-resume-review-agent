package com.lion.resume.graph.observability;

import com.alibaba.cloud.ai.graph.GraphLifecycleListener;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.lion.resume.common.constant.GraphStateKeys;
import com.lion.resume.common.enums.NodeStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 框架生命周期 → 自研监听器链的桥接器（全局唯一的框架对接点）。
 *
 * <p>实现框架 {@link GraphLifecycleListener}，把回调统一转换为
 * {@link NodeExecutionContext} 并分发给所有 {@link NodeExecutionListener}。
 * 业务侧只需关注监听器，框架 API 变动只影响本类。
 *
 * <p>线程安全：三个评分节点并行执行，用 {@code threadId + nodeId} 作为 key 隔离上下文，
 * 每次 after/onError 后必须 {@code remove}，避免内存泄漏。
 */
@Slf4j
@Component
@Order(0)
public class GraphNodeLifecycleBridge implements GraphLifecycleListener {

    private final List<NodeExecutionListener> listeners;

    private final ConcurrentHashMap<String, NodeExecutionContext> running = new ConcurrentHashMap<>();

    public GraphNodeLifecycleBridge(List<NodeExecutionListener> listeners) {
        this.listeners = listeners;
    }

    @Override
    public void onStart(String nodeId, Map<String, Object> state, RunnableConfig config) {
        // 整图开始，暂无单次节点上下文，留空
    }

    @Override
    public void before(String nodeId, Map<String, Object> state, RunnableConfig config, Long time) {
        String taskId = String.valueOf(state.getOrDefault(GraphStateKeys.TASK_ID, "unknown"));
        String userId = String.valueOf(state.getOrDefault(GraphStateKeys.USER_ID, "system"));
        boolean interrupted = config.isInterrupted(nodeId);
        NodeExecutionContext ctx = new NodeExecutionContext(
                taskId, userId, config.threadId().orElse("unknown"), nodeId, state, interrupted);
        ctx.markStart(System.nanoTime());
        running.put(key(config, nodeId), ctx);
        dispatch(l -> l.before(ctx));
    }

    @Override
    public void after(String nodeId, Map<String, Object> state, RunnableConfig config, Long time) {
        NodeExecutionContext ctx = running.remove(key(config, nodeId));
        if (ctx == null) {
            return;
        }
        ctx.finish(NodeStatus.COMPLETED, System.nanoTime());
        dispatch(l -> l.after(ctx));
    }

    @Override
    public void onError(String nodeId, Map<String, Object> state, Throwable ex, RunnableConfig config) {
        NodeExecutionContext ctx = running.remove(key(config, nodeId));
        if (ctx != null) {
            ctx.finish(NodeStatus.ERROR, System.nanoTime());
            ctx.setErrorMessage(ex.getMessage());
        }
        dispatch(l -> l.onError(ctx));
    }

    @Override
    public void onComplete(String nodeId, Map<String, Object> state, RunnableConfig config) {
        // 整图完成，暂无单次节点上下文，留空
    }

    private void dispatch(Consumer<NodeExecutionListener> invoker) {
        for (NodeExecutionListener listener : listeners) {
            try {
                invoker.accept(listener);
            } catch (Exception e) {
                log.warn("节点监听器[{}]执行异常，已隔离", listener.getClass().getSimpleName(), e);
            }
        }
    }

    private String key(RunnableConfig config, String nodeId) {
        return config.threadId().orElse("unknown") + ":" + nodeId;
    }
}
