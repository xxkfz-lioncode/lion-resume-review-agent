package com.lion.resume.graph.action;

import com.lion.resume.common.constant.GraphNodes;
import com.lion.resume.component.TaskProgressService;
import com.lion.resume.graph.ResumeReviewProps;
import com.lion.resume.service.LlmService;
import org.springframework.stereotype.Component;

/**
 * 并行评分节点：岗位文化契合度分析
 */
@Component
public class CultureScoringAction extends AbstractScoringAction {

    public CultureScoringAction(TaskProgressService progressService, ResumeReviewProps props,
                                LlmService llmService) {
        super(progressService, props, llmService);
    }

    @Override
    public String nodeId() {
        return GraphNodes.CULTURE;
    }

    @Override
    protected String dimensionKey() {
        return "culture_fit";
    }

    @Override
    protected String dimensionLabel() {
        return "文化契合度";
    }

    @Override
    protected String reviewFocus() {
        return "与团队/企业文化的契合度：价值观、协作方式、工作节奏与岗位 JD 所体现的文化是否匹配";
    }
}
