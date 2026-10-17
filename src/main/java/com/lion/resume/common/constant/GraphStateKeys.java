package com.lion.resume.common.constant;

/**
 * Graph 全局状态（{@code OverAllState}）键名常量。
 *
 * <p>约定：
 * <ol>
 *   <li>必须与 {@link com.lion.resume.config.GraphConfig} 注册的键一致 —— 未注册的键会在
 *       Checkpoint 中断/恢复（HIL）往返中被丢弃；</li>
 *   <li>键名即 Redis Checkpoint 的持久化标识，只增不改；</li>
 *   <li>{@code *_score} 为并行评分动态键，统一由 {@link #scoreKey(String)} 生成。</li>
 * </ol>
 */
public final class GraphStateKeys {

    private GraphStateKeys() {
    }

    // ==================== 输入：发起任务时写入 ====================

    /** 任务ID（同时作为 threadId 定位 Checkpoint） */
    public static final String TASK_ID = "taskId";

    /** 简历ID */
    public static final String RESUME_ID = "resumeId";

    /** 操作用户ID（发起审查的用户，落库 t_graph_node_log 便于按用户统计） */
    public static final String USER_ID = "userId";

    /** 岗位描述 JD */
    public static final String JOB_DESCRIPTION = "job_description";

    /** 简历结构化 JSON（ParseResumeAction 写入） */
    public static final String RESUME_CONTENT = "resume_content";

    /** 简历纯文本（ParseResumeAction 写入） */
    public static final String RESUME_TEXT = "resume_text";

    // ==================== 评分与诊断：Synthesis 写入，HR 可覆盖 ====================

    /** 六维雷达数据 List<Map{name, value}> */
    public static final String RADAR_DATA = "radar_data";

    /** 综合评分（六维均值） */
    public static final String OVERALL_SCORE = "overall_score";

    /** 诊断摘要 */
    public static final String DIAGNOSIS_SUMMARY = "diagnosis_summary";

    // ==================== 人工复核（HIL） ====================

    /** 是否需要人工复核 */
    public static final String NEEDS_HUMAN_REVIEW = "needs_human_review";

    /** HR 是否已确认（报告据此判断基于哪版评分生成） */
    public static final String APPROVED = "approved";

    /** HR 复核意见 */
    public static final String HR_COMMENT = "hr_comment";

    /** HR 调整后评分 Map<维度名, 分数> */
    public static final String HR_ADJUSTED_SCORES = "hr_adjusted_scores";

    /** 复核原因文案（前端展示） */
    public static final String REVIEW_REASON = "review_reason";

    // ==================== 报告 ====================

    /** 报告已生成 */
    public static final String REPORT_READY = "report_ready";

    /** 报告ID */
    public static final String REPORT_ID = "report_id";

    // ==================== 策略专用 ====================

    /** 多节点累积告警（AppendStrategy） */
    public static final String WARNINGS = "warnings";

    /** 自定义策略示例键（CustomStrategy） */
    public static final String CUSTOM = "custom";

    /**
     * 并行评分节点得分键：节点ID + "_score"。
     * 写入方 {@code AbstractScoringAction}，读取方 {@code SynthesisAction}，两端必须同用。
     */
    public static String scoreKey(String nodeId) {
        return nodeId + "_score";
    }
}
