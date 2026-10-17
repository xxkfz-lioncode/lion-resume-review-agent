package com.lion.resume.graph.observability;

import com.lion.resume.component.TaskProgressService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 进度推送监听器：节点开始前推送 {@code running} 事件（SSE）。
 *
 * <p>把原先写在 {@code BaseTrackingAction} 里的「开始前推送」前移到监听器，
 * 业务节点不再关心进度广播。节点「完成」事件因需携带节点返回值（LLM 评分等），
 * 仍由 {@code BaseTrackingAction} 在拿到 result 后推送，以保留 SSE 数据字段；
 * 节点「异常」事件在此处统一推送。
 */
@Component
@Order(200)
@ConditionalOnProperty(name = "resume.node-listeners.progress-push",
        havingValue = "true", matchIfMissing = true)
public class ProgressPushListener implements NodeExecutionListener {

    private final TaskProgressService progressService;

    public ProgressPushListener(TaskProgressService progressService) {
        this.progressService = progressService;
    }

    @Override
    public void before(NodeExecutionContext ctx) {
        progressService.onNodeStart(ctx.getTaskId(), ctx.getNodeId());
    }

    @Override
    public void onError(NodeExecutionContext ctx) {
        if (ctx != null) {
            progressService.onNodeError(ctx.getTaskId(), ctx.getNodeId(),
                    ctx.getErrorMessage(), ctx.getAttempt());
        }
    }
}
