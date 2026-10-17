package com.lion.resume.graph.action;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import cn.hutool.json.JSONUtil;
import com.lion.resume.common.constant.GraphNodes;
import com.lion.resume.common.constant.GraphStateKeys;
import com.lion.resume.common.exception.BizException;
import com.lion.resume.component.TaskProgressService;
import com.lion.resume.dto.SynthesisResult;
import com.lion.resume.graph.ResumeReviewProps;
import com.lion.resume.service.LlmService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Synthesis Node（综合诊断节点）：
 * 汇总三个并行评分结果，补齐其余维度（教育背景/沟通表达/潜力成长），
 * 生成六维雷达图数据与文字诊断摘要
 */
@Slf4j
@Component
public class SynthesisAction extends BaseTrackingAction {

    private final LlmService llmService;

    public SynthesisAction(TaskProgressService progressService, ResumeReviewProps props,
                           LlmService llmService) {
        super(progressService, props);
        this.llmService = llmService;
    }

    @Override
    public String nodeId() {
        return GraphNodes.SYNTHESIS;
    }

    @Override
    protected Map<String, Object> doExecute(OverAllState state, RunnableConfig config) {
        Map<String, Object> data = state.data();

        // 键名必须与 AbstractScoringAction 写入时一致（scoreKey = 节点ID + "_score"），
        // 原来写成 "skill_score" 取不到值，三个并行维度会恒为兜底 3.5
        double skill = scoreValue(data.get(GraphStateKeys.scoreKey(GraphNodes.SKILL)), "skill_match");
        double project = scoreValue(data.get(GraphStateKeys.scoreKey(GraphNodes.PROJECT)), "project_experience");
        double culture = scoreValue(data.get(GraphStateKeys.scoreKey(GraphNodes.CULTURE)), "culture_fit");

        // 其余三个维度 + 诊断摘要（LLM，按 JSON Schema 输出；失败即节点失败，无兜底）
        SynthesisResult extra = extraDimensions(data);

        double education = requireScore(extra.getEducation(), "education");
        double communication = requireScore(extra.getCommunication(), "communication");
        double growth = requireScore(extra.getGrowthPotential(), "growth_potential");
        String summary = extra.getSummary() == null || extra.getSummary().isBlank()
                ? null : extra.getSummary().trim();
        if (summary == null) {
            throw new BizException(500, "综合诊断失败：模型未返回诊断摘要");
        }

        List<Map<String, Object>> radar = List.of(
                radar("技能匹配", skill),
                radar("项目经验", project),
                radar("文化契合", culture),
                radar("教育背景", education),
                radar("沟通表达", communication),
                radar("潜力成长", growth));

        double overall = round((skill + project + culture + education + communication + growth) / 6.0);

        Map<String, Object> result = new HashMap<>();
        result.put(GraphStateKeys.RADAR_DATA, radar);
        result.put(GraphStateKeys.OVERALL_SCORE, overall);
        result.put(GraphStateKeys.DIAGNOSIS_SUMMARY, summary);
        return result;
    }

    /** 补充维度：教育背景 / 沟通表达 / 潜力成长 + 诊断摘要（JSON Schema 约束输出） */
    private SynthesisResult extraDimensions(Map<String, Object> data) {
        String system = "你是资深招聘专家。基于候选人与岗位情况，进行六维雷达综合诊断："
                + "对教育背景、沟通表达、潜力成长三个维度评分（0-5）并给出综合诊断摘要。"
                + "严格按给定的 JSON Schema 输出，分数必须是 0-5 的数字。";
        String user = "岗位描述：\n" + data.getOrDefault(GraphStateKeys.JOB_DESCRIPTION, "")
                + "\n\n候选人结构化信息：\n" + JSONUtil.toJsonStr(data.get(GraphStateKeys.RESUME_CONTENT))
                + "\n\n简历原文：\n" + AbstractScoringAction.truncate(
                String.valueOf(data.getOrDefault(GraphStateKeys.RESUME_TEXT, "")), 2000);

        SynthesisResult result = llmService.callForObject(system, user, SynthesisResult.class);
        if (result == null) {
            throw new BizException(500, "综合诊断失败：模型未返回有效内容");
        }
        return result;
    }

    private static double scoreValue(Object scoreMap, String dimensionKey) {
        Map<String, Object> map = AbstractScoringAction.castMap(scoreMap);
        Object dim = AbstractScoringAction.dim(map, dimensionKey);
        Object value = dim != null ? dim : map.get("score");
        if (value == null) {
            // 评分节点已保证写入该键，缺失说明上游异常，直接失败而不是塞一个假分数
            throw new BizException(500, "缺失评分结果: " + dimensionKey);
        }
        return AbstractScoringAction.toScore(value, 0);
    }

    /** 评分缺失即失败，不再用默认分兜底 */
    private static double requireScore(Double value, String dimension) {
        if (value == null) {
            throw new BizException(500, "综合诊断失败：缺失维度评分 " + dimension);
        }
        return AbstractScoringAction.toScore(value, 0);
    }

    private static double round(double v) {
        return AbstractScoringAction.round(v);
    }

    private static Map<String, Object> radar(String name, double value) {
        Map<String, Object> item = new HashMap<>();
        item.put("name", name);
        item.put("value", round(value));
        return item;
    }
}
