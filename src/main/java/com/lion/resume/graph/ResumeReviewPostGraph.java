package com.lion.resume.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.lion.resume.common.constant.GraphNodes;
import com.lion.resume.graph.action.GenerateReportAction;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

/**
 * 复核后置流程子图（Post-Review Workflow）
 *
 * <p>解决「HR 复核确认后仍有后续流程」的可扩展性问题：
 * 主图执行到 human_review 挂起后，HR 确认时不再由 Service 硬编码调用报告生成，
 * 而是驱动本子图从确认后的状态快照继续执行。后续新增流程
 * （如背景调查、面试安排、Offer 建议等）只需在本子图中追加节点/分支，
 * 无需改动 {@link com.lion.resume.service.ReviewTaskService#approve}。
 *
 * <pre>
 * START
 *   └── generate_report（复用主图节点：approved=true 时直接生成报告）
 *         └── (未来: background_check / interview / offer ...)
 *               └── END
 * </pre>
 */
@Component
@RequiredArgsConstructor
public class ResumeReviewPostGraph {

    private final GenerateReportAction generateReportAction;

    @Bean
    public CompiledGraph postReviewCompiledGraph() throws Exception {
        StateGraph workflow = new StateGraph()
                .addNode(GraphNodes.GENERATE_REPORT, generateReportAction)
                // 入口：HR 确认后的状态快照（approved=true）直接作为输入
                .addEdge(START, GraphNodes.GENERATE_REPORT)
                .addEdge(GraphNodes.GENERATE_REPORT, END);
        return workflow.compile();
    }
}
