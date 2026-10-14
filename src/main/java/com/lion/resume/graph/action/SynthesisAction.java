package com.lion.resume.graph.action;

import com.alibaba.cloud.ai.graph.OverAllState;
import cn.hutool.json.JSONUtil;
import com.lion.resume.common.constant.GraphNodes;
import com.lion.resume.common.constant.GraphStateKeys;
import com.lion.resume.component.TaskProgressService;
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
    protected Map<String, Object> doExecute(OverAllState state) {
        Map<String, Object> data = state.data();

        // 键名必须与 AbstractScoringAction 写入时一致（scoreKey = 节点ID + "_score"），
        // 原来写成 "skill_score" 取不到值，三个并行维度会恒为兜底 3.5
        double skill = scoreValue(data.get(GraphStateKeys.scoreKey(GraphNodes.SKILL)), "skill_match");
        double project = scoreValue(data.get(GraphStateKeys.scoreKey(GraphNodes.PROJECT)), "project_experience");
        double culture = scoreValue(data.get(GraphStateKeys.scoreKey(GraphNodes.CULTURE)), "culture_fit");

        // 其余三个维度 + 诊断摘要（LLM，失败时规则兜底）
        Map<String, Object> extra = extraDimensions(data);

        double education = toDouble(extra.get("education"), 3.5);
        double communication = toDouble(extra.get("communication"), 3.5);
        double growth = toDouble(extra.get("growth_potential"), 3.5);
        String summary = String.valueOf(extra.getOrDefault("summary", "候选人综合表现详见雷达图与报告。"));

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

    /** 补充维度：教育背景 / 沟通表达 / 潜力成长 + 诊断摘要 */
    private Map<String, Object> extraDimensions(Map<String, Object> data) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("education", 3.5);
        fallback.put("communication", 3.5);
        fallback.put("growth_potential", 3.5);
        fallback.put("summary", "候选人综合表现良好，详见详细报告。");

        try {
            String system = "你是资深招聘专家。基于候选人与岗位情况，进行六维雷达综合诊断："
                    + "对教育背景、沟通表达、潜力成长三个维度评分（0-5）并给出诊断摘要。"
                    + "返回 JSON：{education: 数字, communication: 数字, growth_potential: 数字, summary: 文字}。只输出 JSON。";
            String user = "岗位描述：\n" + data.getOrDefault(GraphStateKeys.JOB_DESCRIPTION, "")
                    + "\n\n候选人结构化信息：\n" + JSONUtil.toJsonStr(data.get(GraphStateKeys.RESUME_CONTENT))
                    + "\n\n简历原文：\n" + AbstractScoringAction.truncate(
                    String.valueOf(data.getOrDefault(GraphStateKeys.RESUME_TEXT, "")), 2000);
            String response = llmService.call(system, user);
            Map<String, Object> parsed = JSONUtil.parseObj(LlmService.extractJson(response));
            if (parsed != null && !parsed.isEmpty()) {
                return parsed;
            }
        } catch (Exception e) {
            log.warn("综合诊断 LLM 调用失败，使用规则兜底: {}", e.getMessage());
        }
        // 规则兜底：根据简历文本关键词估算
        String text = String.valueOf(data.getOrDefault(GraphStateKeys.RESUME_TEXT, "")).toLowerCase();
        Map<String, Object> content = AbstractScoringAction.castMap(data.get(GraphStateKeys.RESUME_CONTENT));
        List<?> skills = content.get("skills") instanceof List<?> l ? l : List.of();

        double education = text.contains("博士") ? 4.5
                : text.contains("硕士") || text.contains("研究生") ? 4.2 : 3.5;
        double communication = text.contains("沟通") || text.contains("协作") || text.contains("团队") ? 4.0 : 3.4;
        double growth = 3.4 + Math.min(0.8, skills.size() * 0.15);
        fallback.put("education", round(education));
        fallback.put("communication", round(communication));
        fallback.put("growth_potential", round(growth));
        fallback.put("summary", "候选人具备基本岗位素质，详细评估请查看报告。");
        return fallback;
    }

    private static double scoreValue(Object scoreMap, String dimensionKey) {
        Map<String, Object> map = AbstractScoringAction.castMap(scoreMap);
        Object dim = AbstractScoringAction.dim(map, dimensionKey);
        if (dim != null) {
            return AbstractScoringAction.toScore(dim, 3.5);
        }
        return AbstractScoringAction.toScore(map.get("score"), 3.5);
    }

    private static double toDouble(Object o, double fallback) {
        return AbstractScoringAction.toScore(o, fallback);
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
