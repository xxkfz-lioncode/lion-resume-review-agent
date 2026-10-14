package com.lion.resume.graph.action;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.lion.resume.common.constant.GraphNodes;
import com.lion.resume.common.constant.GraphStateKeys;
import com.lion.resume.component.TaskProgressService;
import com.lion.resume.entity.ReviewReport;
import com.lion.resume.graph.ResumeReviewProps;
import com.lion.resume.service.ReviewReportService;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * End Node 前的报告生成节点：调用 LLM 流式生成诊断报告，并通过 SSE chunk 推送实现打字机效果。
 *
 * <p>说明：人工复核已改由 {@code HumanReviewAction#interruptAfter} 真正中断，
 * 本节点不会再在"未批准"状态下被空跑，因此无需保留等待分支。
 * 若 approved=true，说明报告基于 HR 调整后的评分生成。
 */
@Component
public class GenerateReportAction extends BaseTrackingAction {

    private final ReviewReportService reportService;

    public GenerateReportAction(TaskProgressService progressService, ResumeReviewProps props,
                                ReviewReportService reportService) {
        super(progressService, props);
        this.reportService = reportService;
    }

    @Override
    public String nodeId() {
        return GraphNodes.GENERATE_REPORT;
    }

    @Override
    protected Map<String, Object> doExecute(OverAllState state) {
        Map<String, Object> data = state.data();
        // 不用 String.valueOf（null 会变成字符串 "null"，导致后续按 id 查询全部落空）
        Object rawTaskId = data.get(GraphStateKeys.TASK_ID);
        String taskId = (rawTaskId == null) ? null : String.valueOf(rawTaskId);
        boolean approved = Boolean.TRUE.equals(data.get(GraphStateKeys.APPROVED));

        ReviewReport report = reportService.generate(taskId, data,
                approved, approved ? String.valueOf(data.getOrDefault(GraphStateKeys.HR_COMMENT, "")) : "");

        Map<String, Object> result = new HashMap<>();
        result.put(GraphStateKeys.REPORT_READY, true);
        result.put(GraphStateKeys.REPORT_ID, report.getId());
        result.put(GraphStateKeys.OVERALL_SCORE, report.getOverallScore());
        return result;
    }
}
