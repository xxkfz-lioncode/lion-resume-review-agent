package com.lion.resume.config;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import java.util.HashMap;
import java.util.Map;

/**
 * Graph 状态键策略配置
 *
 * <p>定义全局状态 {@code OverAllState} 中各状态键的合并策略：
 * 节点 {@code apply()} 返回的 Map 会按此处的策略合并进全局状态。
 * 未在此注册的键默认走 {@link ReplaceStrategy}（后写覆盖先写）。
 *
 * <pre>
 * 键名         策略                说明
 * warnings     AppendStrategy      多个节点累积写入，追加到 List（去重合并）
 * overall_score ReplaceStrategy    单值字段，后写覆盖先写
 * </pre>
 */
public class GraphConfig {

    /**
     * 状态键合并策略工厂（{@link KeyStrategyFactory}）
     * <p>供 {@code ResumeReviewGraph} / {@code ResumeReviewPostGraph} 构造
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
        // 多个节点累积写入的警告/提示列表（追加合并）
        map.put("warnings", new AppendStrategy());
        // 综合评分：单值覆盖（SynthesisAction 与 GenerateReportAction 串行写入，值一致）
        map.put("overall_score", new ReplaceStrategy());
        return map;
    };
}
