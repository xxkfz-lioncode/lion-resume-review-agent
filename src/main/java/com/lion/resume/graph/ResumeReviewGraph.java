package com.lion.resume.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.lion.resume.common.constant.GraphNodes;
import com.lion.resume.config.GraphConfig;
import com.lion.resume.graph.action.CultureScoringAction;
import com.lion.resume.graph.action.GenerateReportAction;
import com.lion.resume.graph.action.HumanReviewAction;
import com.lion.resume.graph.action.ParseResumeAction;
import com.lion.resume.graph.action.ProjectScoringAction;
import com.lion.resume.graph.action.SkillScoringAction;
import com.lion.resume.graph.action.SynthesisAction;
import com.lion.resume.graph.edge.ReviewRouter;
import com.lion.resume.graph.observability.GraphNodeLifecycleBridge;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Map;

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
 *                   └── synthesis ──[条件边 ReviewRouter]──┬─→ human_review → generate_report → END
 *                                  └───────────────────────┴─────────────────↑
 * </pre>
 *
 * 并行语义：parse_resume 的多条出边构成 fan-out（并行评分）；
 * 三个评分节点汇聚到 synthesis 构成 fan-in（屏障，等待全部完成后进入）。
 *
 * <p>条件边：synthesis 完成后由 {@link com.lion.resume.graph.edge.ReviewRouter} 判定
 * ——综合评分达标且无维度短板时跳过 {@code human_review}，直接 {@code generate_report}；
 * 否则走人工复核（{@code human_review} 挂起，HR 确认后从断点续跑到 {@code generate_report}）。
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
    private final ReviewRouter reviewRouter;

    /**
     * 编译主图。
     *
     * <p>关键：注入 {@link BaseCheckpointSaver}，使 Graph 具备「中断 → 持久化 → 恢复」能力。
     * 人工复核采用 {@code InterruptableAction#interruptAfter} 动态中断（方案 B）：
     * 节点执行后按评分结果决定是否返回 {@code InterruptionMetadata}，
     * 因此无需在此处静态配置 interruptBefore（否则所有任务都会无条件中断）。
     *
     * <p>关于 releaseThread：保持默认（不释放线程）。HR 可能数小时后才确认，
     * 释放线程后 resume 需依赖 getActiveThreadId 重新定位，存在兼容风险；
     * 不释放则由 TTL 自动回收，恢复链路更可靠。
     */
    @Bean
    public CompiledGraph resumeReviewCompiledGraph(BaseCheckpointSaver checkpointSaver,
                                                  GraphNodeLifecycleBridge lifecycleBridge) throws Exception {
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
                // 条件边：synthesis 后按评分判定是否需要人工复核
                // 返回值必须命中下面的映射表，映射在编译期校验，写错启动即失败
                .addConditionalEdges(GraphNodes.SYNTHESIS,
                        AsyncEdgeAction.edge_async(reviewRouter), Map.of(
                                ReviewRouter.TO_REVIEW, GraphNodes.HUMAN_REVIEW,
                                ReviewRouter.TO_REPORT, GraphNodes.GENERATE_REPORT))
                // 主链：人工复核确认后（或免复核时直接）进入报告生成
                .addEdge(GraphNodes.HUMAN_REVIEW, GraphNodes.GENERATE_REPORT)
                .addEdge(GraphNodes.GENERATE_REPORT, END);

        return workflow.compile(CompileConfig.builder()
                .saverConfig(SaverConfig.builder().register(checkpointSaver).build())
                .withLifecycleListener(lifecycleBridge)
                .build());
    }
}
