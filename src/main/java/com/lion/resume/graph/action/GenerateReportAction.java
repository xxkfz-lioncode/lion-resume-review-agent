package com.lion.resume.graph.action;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.lion.resume.common.constant.GraphNodes;
import com.lion.resume.common.enums.NodeStatus;
import com.lion.resume.component.TaskProgressService;
import com.lion.resume.dto.GraphStatusEvent;
import com.lion.resume.entity.ReviewReport;
import com.lion.resume.graph.ResumeReviewProps;
import com.lion.resume.service.ReviewReportService;
import com.lion.resume.sse.GraphEventPublisher;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * End Node 前的报告生成节点：
 * - 若处于人工复核挂起状态（needs_human_review && !approved），则等待 HR 确认
 * - 否则调用 LLM 流式生成诊断报告，并通过 SSE chunk 推送实现打字机效果
 */
@Component
public class GenerateReportAction extends BaseTrackingAction {

    private final ReviewReportService reportService;
    private final GraphEventPublisher eventPublisher;

    public GenerateReportAction(TaskProgressService progressService, ResumeReviewProps props,
                                ReviewReportService reportService, GraphEventPublisher eventPublisher) {
        super(progressService, props);
        this.reportService = reportService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String nodeId() {
        return GraphNodes.GENERATE_REPORT;
    }

    @Override
    protected Map<String, Object> doExecute(OverAllState state) {
        Map<String, Object> data = state.data();
        String taskId = String.valueOf(data.get("taskId"));
        boolean needsReview = Boolean.TRUE.equals(data.get("needs_human_review"));
        boolean approved = Boolean.TRUE.equals(data.get("approved"));

        if (needsReview && !approved) {
            // 挂起：等待 HR 复核后再生成报告
            eventPublisher.publish(GraphStatusEvent.builder()
                    .type("node")
                    .taskId(taskId)
                    .node(nodeId())
                    .status(NodeStatus.PENDING.getValue())
                    .progress(88)
                    .message("等待 HR 确认评分后生成报告")
                    .timestamp(System.currentTimeMillis())
                    .build());
            return Map.of("report_ready", false);
        }

        ReviewReport report = reportService.generate(taskId, data,
                approved, approved ? String.valueOf(data.getOrDefault("hr_comment", "")) : "");

        Map<String, Object> result = new HashMap<>();
        result.put("report_ready", true);
        result.put("report_id", report.getId());
        result.put("overall_score", report.getOverallScore());
        return result;
    }
}
