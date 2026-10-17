package com.lion.resume.common.constant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Graph 节点定义常量 + 前端可视化拓扑
 *
 * 拓扑结构：
 * <pre>
 * START
 *   └── parse_resume（解析节点）
 *         ├── skill_scoring   （并行评分：技能匹配度）
 *         ├── project_scoring （并行评分：项目经验含金量）
 *         └── culture_scoring （并行评分：岗位文化契合度）
 *               └── synthesis（综合诊断：六维雷达图 + 文字诊断）
 *                     ├──[条件边：需复核] human_review（人机协同：边缘候选人工复核）──┐
 *                     └──[条件边：免复核]────────────────────────────────────────┴→ generate_report（生成报告）→ END
 * </pre>
 */
public final class GraphNodes {

    public static final String PARSE = "parse_resume";
    public static final String SKILL = "skill_scoring";
    public static final String PROJECT = "project_scoring";
    public static final String CULTURE = "culture_scoring";
    public static final String SYNTHESIS = "synthesis";
    public static final String HUMAN_REVIEW = "human_review";
    public static final String GENERATE_REPORT = "generate_report";

    /**
     * 节点元信息（供前端绘制流程追踪面板）
     */
    public static final Map<String, Map<String, Object>> NODE_META = new LinkedHashMap<>();

    static {
        NODE_META.put(PARSE, meta(PARSE, "简历解析", "OCR / 文档解析，提取结构化 JSON", 0));
        NODE_META.put(SKILL, meta(SKILL, "技能匹配度", "LLM 评估技能与岗位匹配", 1));
        NODE_META.put(PROJECT, meta(PROJECT, "项目经验评估", "LLM 评估项目含金量", 1));
        NODE_META.put(CULTURE, meta(CULTURE, "文化契合度", "LLM 评估岗位文化契合", 1));
        NODE_META.put(SYNTHESIS, meta(SYNTHESIS, "综合诊断", "汇总并行结果，生成六维雷达图与诊断", 2));
        NODE_META.put(HUMAN_REVIEW, meta(HUMAN_REVIEW, "人工复核", "边缘候选进入 HR 审核（Human-in-the-Loop）", 3));
        NODE_META.put(GENERATE_REPORT, meta(GENERATE_REPORT, "生成报告", "LLM 生成最终诊断报告", 4));
    }

    private static Map<String, Object> meta(String id, String label, String desc, int stage) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("label", label);
        m.put("description", desc);
        m.put("stage", stage);
        return m;
    }

    /**
     * 拓扑边
     */
    public static List<String[]> edges() {
        List<String[]> edges = new ArrayList<>();
        edges.add(new String[]{"__START__", PARSE});
        edges.add(new String[]{PARSE, SKILL});
        edges.add(new String[]{PARSE, PROJECT});
        edges.add(new String[]{PARSE, CULTURE});
        edges.add(new String[]{SKILL, SYNTHESIS});
        edges.add(new String[]{PROJECT, SYNTHESIS});
        edges.add(new String[]{CULTURE, SYNTHESIS});
        // 条件边的两条分支都画出来，前端才能看到「免复核」这条路径
        edges.add(new String[]{SYNTHESIS, HUMAN_REVIEW});
        edges.add(new String[]{SYNTHESIS, GENERATE_REPORT});
        edges.add(new String[]{HUMAN_REVIEW, GENERATE_REPORT});
        edges.add(new String[]{GENERATE_REPORT, "__END__"});
        return edges;
    }

    private GraphNodes() {
    }
}
