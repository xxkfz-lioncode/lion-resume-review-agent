package com.lion.resume.config;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.lion.resume.common.constant.GraphStateKeys;
import com.lion.resume.graph.strategy.CustomStrategy;

import java.util.HashMap;
import java.util.Map;

/**
 * Graph 状态键策略配置
 *
 * <p>定义全局状态 {@code OverAllState} 中各状态键的合并策略：
 * 节点 {@code apply()} 返回的 Map 会按此处的策略合并进全局状态。
 * 键名统一取自 {@link GraphStateKeys}，策略只有三类：
 * {@link AppendStrategy}（追加合并）、{@link ReplaceStrategy}（单值覆盖）、
 * {@link CustomStrategy}（自定义规则）。
 */
public class GraphConfig {

    /**
     * 状态键合并策略工厂（{@link KeyStrategyFactory}）
     * <p>供 {@code ResumeReviewGraph} 构造
     * {@code StateGraph} 时传入：{@code new StateGraph(keyStrategyFactory)}。
     *
     * <ul>
     *   <li>{@link AppendStrategy}：追加到 List，适合"多个节点往同一键累积结果"的场景
     *       （如并行评分节点收集 warnings），读取时对应类型为 {@code List<T>}；</li>
     *   <li>{@link ReplaceStrategy}：后写覆盖先写，适合"最终结果唯一"的字段
     *       （如综合评分、复核意见）。</li>
     * </ul>
     */
    public static KeyStrategyFactory keyStrategyFactory = () -> {
        Map<String, KeyStrategy> map = new HashMap<>();

        // 追加合并：多个节点累积写入的告警/提示列表
        map.put(GraphStateKeys.WARNINGS, new AppendStrategy());
        // 自定义合并规则
        map.put(GraphStateKeys.CUSTOM, new CustomStrategy());

        // 重要：业务字段必须在此注册！Checkpoint.updateState（官方 HIL 的 updateState/resume 路径）
        // 只保留注册过的键，未注册的键会在中断/恢复往返中被丢弃，
        // 恢复后 generate_report 就会取不到 taskId/resumeId 而失败。
        replace(map,
                GraphStateKeys.TASK_ID,
                GraphStateKeys.RESUME_ID,
                GraphStateKeys.JOB_DESCRIPTION,
                GraphStateKeys.RESUME_CONTENT,
                GraphStateKeys.RESUME_TEXT,
                GraphStateKeys.RADAR_DATA,
                GraphStateKeys.OVERALL_SCORE,
                GraphStateKeys.DIAGNOSIS_SUMMARY,
                GraphStateKeys.NEEDS_HUMAN_REVIEW,
                GraphStateKeys.APPROVED,
                GraphStateKeys.HR_COMMENT,
                GraphStateKeys.HR_ADJUSTED_SCORES,
                GraphStateKeys.REVIEW_REASON,
                GraphStateKeys.REPORT_READY,
                GraphStateKeys.REPORT_ID);
        return map;
    };

    /** 单值覆盖类字段统一注册（等价于逐个 {@code new ReplaceStrategy()}，只是省掉重复代码） */
    private static void replace(Map<String, KeyStrategy> map, String... keys) {
        for (String key : keys) {
            map.put(key, new ReplaceStrategy());
        }
    }
}
