package com.lion.resume.graph;

import com.lion.resume.common.constant.GraphNodes;

import java.util.Map;

/**
 * 各节点对应的进度区间（用于 SSE 推送 progress）
 */
public final class GraphProgress {

    private static final Map<String, int[]> RANGE = Map.of(
            GraphNodes.PARSE, new int[]{5, 20},
            GraphNodes.SKILL, new int[]{20, 55},
            GraphNodes.PROJECT, new int[]{20, 55},
            GraphNodes.CULTURE, new int[]{20, 55},
            GraphNodes.SYNTHESIS, new int[]{60, 78},
            GraphNodes.HUMAN_REVIEW, new int[]{80, 88},
            GraphNodes.GENERATE_REPORT, new int[]{90, 100}
    );

    public static int startProgress(String node) {
        int[] range = RANGE.getOrDefault(node, new int[]{0, 100});
        return range[0];
    }

    public static int endProgress(String node) {
        int[] range = RANGE.getOrDefault(node, new int[]{0, 100});
        return range[1];
    }

    private GraphProgress() {
    }
}
