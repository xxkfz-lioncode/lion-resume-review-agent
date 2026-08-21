package com.lion.resume.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.lion.resume.common.constant.GraphNodes;
import com.lion.resume.config.GraphConfig;
import com.lion.resume.graph.action.CultureScoringAction;
import com.lion.resume.graph.action.GenerateReportAction;
import com.lion.resume.graph.action.HumanReviewAction;
import com.lion.resume.graph.action.ParseResumeAction;
import com.lion.resume.graph.action.ProjectScoringAction;
import com.lion.resume.graph.action.SkillScoringAction;
import com.lion.resume.graph.action.SynthesisAction;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

/**
 * 简历审查 Graph 工作流装配
 *
 * <pre>
 * START
 *   └── parse_resume（解析节点）
 *         ├──→ skill_scoring   ──┐
 *         ├──→ project_scoring ──┤→ synthesis（并行 fan-in 汇合）
 *         └──→ culture_scoring ──┘
 *                   └── synthesis → human_review → generate_report → END
 * </pre>
 *
 * 并行语义：parse_resume 的多条出边构成 fan-out（并行评分）；
 * 三个评分节点汇聚到 synthesis 构成 fan-in（屏障，等待全部完成后进入）。
 */
@Component
@RequiredArgsConstructor
public class ResumeReviewGraph {

    private final ParseResumeAction parseResumeAction;
    private final SkillScoringAction skillScoringAction;
    private final ProjectScoringAction projectScoringAction;
    private final CultureScoringAction cultureScoringAction;
    private final SynthesisAction synthesisAction;
    private final HumanReviewAction humanReviewAction;
    private final GenerateReportAction generateReportAction;

    @Bean
    public CompiledGraph resumeReviewCompiledGraph() throws Exception {
        StateGraph workflow = new StateGraph(GraphConfig.keyStrategyFactory)
                .addNode(GraphNodes.PARSE, parseResumeAction)
                .addNode(GraphNodes.SKILL, skillScoringAction)
                .addNode(GraphNodes.PROJECT, projectScoringAction)
                .addNode(GraphNodes.CULTURE, cultureScoringAction)
                .addNode(GraphNodes.SYNTHESIS, synthesisAction)
                .addNode(GraphNodes.HUMAN_REVIEW, humanReviewAction)
                .addNode(GraphNodes.GENERATE_REPORT, generateReportAction)
                // 入口
                .addEdge(START, GraphNodes.PARSE)
                // 并行 fan-out：解析完成后同时启动三个评分节点
                .addEdge(GraphNodes.PARSE, GraphNodes.SKILL)
                .addEdge(GraphNodes.PARSE, GraphNodes.PROJECT)
                .addEdge(GraphNodes.PARSE, GraphNodes.CULTURE)
                // 并行 fan-in：三个评分全部完成后进入综合诊断
                .addEdge(GraphNodes.SKILL, GraphNodes.SYNTHESIS)
                .addEdge(GraphNodes.PROJECT, GraphNodes.SYNTHESIS)
                .addEdge(GraphNodes.CULTURE, GraphNodes.SYNTHESIS)
                // 主链
                .addEdge(GraphNodes.SYNTHESIS, GraphNodes.HUMAN_REVIEW)
                .addEdge(GraphNodes.HUMAN_REVIEW, GraphNodes.GENERATE_REPORT)
                .addEdge(GraphNodes.GENERATE_REPORT, END);

        return workflow.compile();
    }
}
