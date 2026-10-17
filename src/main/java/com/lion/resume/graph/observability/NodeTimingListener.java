package com.lion.resume.graph.observability;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 耗时统计监听器：节点执行前后用 {@code System.nanoTime()} 计算耗时并记录日志。
 *
 * <p>不使用回调的 {@code Long} 时间戳参数（其语义随版本可能变化），统一自管计时，最稳。
 * 这是所有监听器中顺序最靠前（order=0）的，先拿到真实耗时，后续监听器读 {@code ctx.getDurationMs()} 即可。
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
