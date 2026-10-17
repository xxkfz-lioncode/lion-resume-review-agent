package com.lion.resume.graph.observability;

import org.springframework.core.Ordered;

/**
 * 节点执行监听器（横切逻辑的扩展点）。
 *
 * <p>每个节点执行前后，{@link GraphNodeLifecycleBridge} 会按顺序调用所有实现类。
 * 新增能力（如执行日志、耗时落库、Metrics、审计）只需新增一个实现类并注册为 Bean，
 * 业务节点完全无感知 —— 这就是与业务解耦的关键。
 *
 * <p>约定：
 * <ul>
 *   <li>用 {@link Ordered} 控制顺序（计时(0) → 落库(100) → 推送(200) → 指标(300)）；</li>
 *   <li>实现内部必须自行 try-catch，单个监听器异常不得影响主流程；</li>
 *   <li>{@code ctx} 可能为 {@code null}（异常发生在 before 之前），需判空。</li>
 * </ul>
 */
public interface NodeExecutionListener extends Ordered {

    /** 节点开始执行前 */
    default void before(NodeExecutionContext ctx) {
    }

    /** 节点正常执行完成后 */
    default void after(NodeExecutionContext ctx) {
    }

    /** 节点执行异常（含重试耗尽） */
    default void onError(NodeExecutionContext ctx) {
    }

    @Override
    default int getOrder() {
        return 0;
    }
}
