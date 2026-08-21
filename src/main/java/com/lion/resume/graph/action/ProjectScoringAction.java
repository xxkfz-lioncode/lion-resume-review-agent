package com.lion.resume.graph.action;

import com.lion.resume.common.constant.GraphNodes;
import com.lion.resume.component.TaskProgressService;
import com.lion.resume.graph.ResumeReviewProps;
import com.lion.resume.service.LlmService;
import org.springframework.stereotype.Component;

/**
 * 并行评分节点：项目经验含金量评估
 */
@Component
public class ProjectScoringAction extends AbstractScoringAction {

    public ProjectScoringAction(TaskProgressService progressService, ResumeReviewProps props,
                                LlmService llmService) {
        super(progressService, props, llmService);
    }

    @Override
    public String nodeId() {
        return GraphNodes.PROJECT;
    }

    @Override
    protected String dimensionKey() {
        return "project_experience";
    }

    @Override
    protected String dimensionLabel() {
        return "项目经验含金量";
    }

    @Override
    protected String reviewFocus() {
        return "项目经验含金量：项目规模、技术复杂度、候选人在其中的角色与贡献、业务成果是否量化";
    }
}
