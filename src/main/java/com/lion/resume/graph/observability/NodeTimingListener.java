package com.lion.resume.graph.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 耗时统计监听器：打印每个节点的执行耗时日志。
 *
 * <p>耗时本身由 {@link GraphNodeLifecycleBridge} 计算：{@code before} 时 {@code markStart()}，
 * {@code after} / {@code onError} 时 {@code finish()}，且发生在分发给监听器<strong>之前</strong>。
 * 因此本监听器（以及后续所有监听器）读到的 {@code ctx.getDurationMs()} 都已是终值，无需重复计时。
 *
 * <p>{@code @Order(0)} 排在最前，仅用于让耗时日志先于落库 / 推送日志打印，便于按时间顺序阅读。
 */
@Slf4j
@Component
@Order(0)
public class NodeTimingListener implements NodeExecutionListener {

    @Override
    public void after(NodeExecutionContext ctx) {
        log.info("[节点耗时] taskId={} userId={} node={} 成功 耗时={}ms",
                ctx.getTaskId(), ctx.getUserId(), ctx.getNodeId(), ctx.getDurationMs());
    }

    @Override
    public void onError(NodeExecutionContext ctx) {
        if (ctx != null) {
            log.warn("[节点耗时] taskId={} userId={} node={} 失败 耗时={}ms 原因={}",
                    ctx.getTaskId(), ctx.getUserId(), ctx.getNodeId(), ctx.getDurationMs(), ctx.getErrorMessage());
        }
    }
}
