package com.lion.resume.graph.edge;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.lion.resume.common.constant.GraphNodes;
import com.lion.resume.common.constant.GraphStateKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 复核路由（条件边）：synthesis 之后按评分结果决定流向。
 *
 * <p>判定依据只有状态里的 {@code needs_human_review}（由 {@code SynthesisAction} 按阈值算出），
 * 边本身不读分数、不设阈值 —— 阈值归业务配置，边只做「翻译」，职责单一。
 *
 * <p>实现同步 {@link EdgeAction}，在 {@code ResumeReviewGraph} 里通过
 * {@code AsyncEdgeAction.edge_async(reviewRouter)} 包装成异步条件边，再注册给
 * {@link com.alibaba.cloud.ai.graph.StateGraph#addConditionalEdges}。
 *
 * <p>返回值必须是 {@code ResumeReviewGraph#addConditionalEdges} 映射表里的 key，
 * 框架在编译期就会校验，写错会在启动时直接报
 * {@code cannot find edge mapping for id}，不会拖到运行时。
 */
@Slf4j
@Component
public class ReviewRouter implements EdgeAction {

    /** 需要人工复核 → human_review */
    public static final String TO_REVIEW = "review";

    /** 免复核 → generate_report */
    public static final String TO_REPORT = "auto";

    @Override
    public String apply(OverAllState state) {
        boolean needsReview = Boolean.TRUE.equals(state.data().get(GraphStateKeys.NEEDS_HUMAN_REVIEW));
        String next = needsReview ? TO_REVIEW : TO_REPORT;
        log.info("复核路由: needsHumanReview={} → {} ({})",
                needsReview, next, needsReview ? GraphNodes.HUMAN_REVIEW : GraphNodes.GENERATE_REPORT);
        return next;
    }
}
