package com.lion.resume.graph.action;

import com.alibaba.cloud.ai.graph.OverAllState;
import cn.hutool.json.JSONUtil;
import com.lion.resume.common.constant.GraphStateKeys;
import com.lion.resume.component.TaskProgressService;
import com.lion.resume.graph.ResumeReviewProps;
import com.lion.resume.service.LlmService;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 并行评分节点基类（技能匹配 / 项目经验 / 文化契合）
 * 三个子类通过并行边同时执行，返回结构一致的评分结果
 */
@Slf4j
public abstract class AbstractScoringAction extends BaseTrackingAction {

    protected final LlmService llmService;

    protected AbstractScoringAction(TaskProgressService progressService, ResumeReviewProps props,
                                    LlmService llmService) {
        super(progressService, props);
        this.llmService = llmService;
    }

    /** 维度名，如 skill_match */
    protected abstract String dimensionKey();

    /** 维度中文名，如 技能匹配度 */
    protected abstract String dimensionLabel();

    /** 评审重点描述 */
    protected abstract String reviewFocus();

    @Override
    protected Map<String, Object> doExecute(OverAllState state) {
        Map<String, Object> data = state.data();
        Map<String, Object> resume = castMap(data.get(GraphStateKeys.RESUME_CONTENT));
        String jobDesc = String.valueOf(data.getOrDefault(GraphStateKeys.JOB_DESCRIPTION, ""));
        String resumeText = String.valueOf(data.getOrDefault(GraphStateKeys.RESUME_TEXT, ""));

        String system = "你是资深招聘专家，负责评估候选人" + reviewFocus()
                + "。请返回 JSON：{score: 0-5 的数字, dimensions: {"
                + dimensionKey() + ": 0-5 的数字}, analysis: 简短分析, reasons: [理由数组]}。只输出 JSON。";
        String user = "岗位描述：\n" + jobDesc
                + "\n\n候选人简历：\n" + JSONUtil.toJsonStr(resume)
                + "\n\n简历原文：\n" + truncate(resumeText, 3000);

        Map<String, Object> parsed = new HashMap<>();
        try {
            String response = llmService.call(system, user);
            parsed = JSONUtil.parseObj(LlmService.extractJson(response));
        } catch (Exception e) {
            log.warn("[{}] LLM 评分失败，使用兜底评分: {}", dimensionLabel(), e.getMessage());
        }
        // 键名统一由 GraphStateKeys.scoreKey 生成，SynthesisAction 读取时必须用同一方法
        return Map.of(GraphStateKeys.scoreKey(nodeId()), normalize(parsed));
    }

    /**
     * 归一化评分结果：保证 score/dimensions/reasons 字段存在且为合法数值
     */
    private Map<String, Object> normalize(Map<String, Object> parsed) {
        Map<String, Object> normalized = new HashMap<>();
        double score = toScore(parsed.get("score"), 3.5);
        double dimension = toScore(dim(parsed, dimensionKey()), score);
        normalized.put("score", round(score));
        normalized.put("dimensions", Map.of(dimensionKey(), round(dimension)));
        normalized.put("analysis", str(parsed.get("analysis"), dimensionLabel() + "评估完成"));
        Object reasons = parsed.get("reasons");
        normalized.put("reasons", reasons instanceof List ? reasons : List.of());
        normalized.put("dimension_key", dimensionKey());
        return normalized;
    }

    /** 从 dimensions 中取值 */
    protected static Object dim(Map<String, Object> map, String key) {
        Object dims = map.get("dimensions");
        if (dims instanceof Map<?, ?> dm) {
            return dm.get(key);
        }
        return null;
    }

    protected static double toScore(Object o, double fallback) {
        if (o instanceof Number n) {
            double v = n.doubleValue();
            return Math.max(0, Math.min(5, v));
        }
        if (o instanceof String s) {
            try {
                double v = Double.parseDouble(s.trim());
                return Math.max(0, Math.min(5, v));
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    protected static String str(Object o, String fallback) {
        return o == null ? fallback : String.valueOf(o);
    }

    protected static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }

    protected static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    protected static Map<String, Object> castMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> result = new HashMap<>();
            m.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return new HashMap<>();
    }
}
