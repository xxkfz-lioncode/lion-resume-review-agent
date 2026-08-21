package com.lion.resume.graph.action;

import com.lion.resume.common.constant.GraphNodes;
import com.lion.resume.component.TaskProgressService;
import com.lion.resume.graph.ResumeReviewProps;
import com.lion.resume.service.LlmService;
import org.springframework.stereotype.Component;

/**
 * 并行评分节点：技能匹配度分析
 */
@Component
public class SkillScoringAction extends AbstractScoringAction {

    public SkillScoringAction(TaskProgressService progressService, ResumeReviewProps props,
                              LlmService llmService) {
        super(progressService, props, llmService);
    }

    @Override
    public String nodeId() {
        return GraphNodes.SKILL;
    }

    @Override
    protected String dimensionKey() {
        return "skill_match";
    }

    @Override
    protected String dimensionLabel() {
        return "技能匹配度";
    }

    @Override
    protected String reviewFocus() {
        return "技能与岗位的匹配程度：技术栈、工具链、行业知识是否与岗位要求对齐";
    }
}
