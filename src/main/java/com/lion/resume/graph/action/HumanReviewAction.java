package com.lion.resume.graph.action;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.lion.resume.cache.GraphStateCache;
import com.lion.resume.common.constant.GraphNodes;
import com.lion.resume.common.enums.NodeStatus;
import com.lion.resume.component.TaskProgressService;
import com.lion.resume.dto.GraphStatusEvent;
import com.lion.resume.graph.ResumeReviewProps;
import com.lion.resume.sse.GraphEventPublisher;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Human-in-the-Loop（人机协同）节点：
 * 当 AI 六维均分低于"及格线"（borderline-score）时，触发人工复核：
 * 1. 保存待复核快照到 Redis
 * 2. 通过 SSE 通知前端弹出审核框
 * 3. Graph 继续走到 generate_report（该节点感知 pending 后等待 HR 确认）
 *
 * 说明：本实现基于「节点内决策 + 服务端恢复」模式，无需依赖框架 interrupt 挂起机制，
 * 可稳定运行于 spring-ai-alibaba-graph 1.1.x。如需真正挂起 Graph 线程，可改用
 * InterruptableAction 的 interrupt/interruptAfter 钩子（官方 README 有示例）。
 */
@Component
public class HumanReviewAction extends BaseTrackingAction {

    private final GraphStateCache stateCache;
    private final GraphEventPublisher eventPublisher;

    public HumanReviewAction(TaskProgressService progressService, ResumeReviewProps props,
                             GraphStateCache stateCache, GraphEventPublisher eventPublisher) {
        super(progressService, props);
        this.stateCache = stateCache;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String nodeId() {
        return GraphNodes.HUMAN_REVIEW;
    }

    @Override
    protected Map<String, Object> doExecute(OverAllState state) {
        Map<String, Object> data = state.data();
        String taskId = String.valueOf(data.get("taskId"));
        double overall = AbstractScoringAction.toScore(data.get("overall_score"), 3.0);

        boolean needsReview = overall < props.getBorderlineScore();

        Map<String, Object> result = new HashMap<>();
        result.put("needs_human_review", needsReview);
        result.put("review_reason", needsReview
                ? String.format("AI 综合评分 %.1f 低于阈值 %.1f，等待 HR 复核", overall, props.getBorderlineScore())
                : "");

        if (needsReview) {
            progressService.updateTaskStatus(taskId, "NEEDS_REVIEW");
            // 保存待复核快照（供 HR 审核框展示）
            Map<String, Object> pending = new HashMap<>();
            pending.put("radar_data", data.get("radar_data"));
            pending.put("overall_score", overall);
            pending.put("diagnosis_summary", data.get("diagnosis_summary"));
            pending.put("job_description", data.get("job_description"));
            stateCache.savePendingReview(taskId, pending);

            eventPublisher.publish(GraphStatusEvent.builder()
                    .type("node")
                    .taskId(taskId)
                    .node(nodeId())
                    .status(NodeStatus.PENDING.getValue())
                    .progress(80)
                    .message("AI 评分低于阈值，等待 HR 人工复核")
                    .data(Map.of("radar_data", data.get("radar_data"),
                            "overall_score", overall,
                            "diagnosis_summary", data.get("diagnosis_summary"),
                            "borderline", props.getBorderlineScore()))
                    .timestamp(System.currentTimeMillis())
                    .build());
        }
        return result;
    }

    /** 挂起时不发送 completed，前端展示为"待复核"高亮 */
    @Override
    protected boolean emitCompleteEvent(Map<String, Object> result) {
        return !Boolean.TRUE.equals(result.get("needs_human_review"));
    }
}
