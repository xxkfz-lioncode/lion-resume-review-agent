package com.lion.resume.graph;

import cn.hutool.core.map.MapUtil;
import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.lion.resume.common.constant.GraphNodes;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GraphProgress 单元测试
 * <p>
 * 验证各图节点对应的 SSE 进度区间（start/end）以及未知节点兜底行为
 */
class GraphProgressTest {

    @Test
    void knownNode_parse_returns5to20() throws GraphStateException {


        // 1. 定义全局状态 (State) 的更新策略
        KeyStrategyFactory stateFactory = () -> Map.of(
                "score", new ReplaceStrategy(),   // 分数直接覆盖
                "testName", new AppendStrategy(),   // 分数直接覆盖
                "result", new ReplaceStrategy()   // 结果直接覆盖
        );

        // 2. 构建状态图
        StateGraph graph = new StateGraph("OrderRoutingWorkflow", stateFactory);

        // 3. 添加节点 (Nodes)
        // 评分检查节点
        graph.addNode("checkScore", AsyncNodeAction.node_async(state -> {
            // 从状态中获取分数，如果没有则默认给个 75 分
            Integer score = (Integer) state.value("score").orElse(75);
            System.out.println("[Node] 检查分数: " + score);
            return Map.of("score", score, "testName", "张三");
        }));

        // 三个业务处理节点
        graph.addNode("pass", AsyncNodeAction.node_async(state -> {
            System.out.println("[Node] 执行自动通过流程");
            return Map.of("result", "ORDER_PASSED","testName", "李四");
        }));

        graph.addNode("review", AsyncNodeAction.node_async(state -> {
            System.out.println("[Node] 触发人工复核流程");
            return Map.of("result", "NEEDS_REVIEW");
        }));

        graph.addNode("reject", AsyncNodeAction.node_async(state -> {
            System.out.println("[Node] 执行拒绝流程");
            return Map.of("result", "ORDER_REJECTED");
        }));

        // 4. 定义边 (Edges)
        // 入口边：从 START 到 checkScore
        graph.addEdge(StateGraph.START, "checkScore");

        // 核心：条件边 (Conditional Edge)
        graph.addConditionalEdges(
                "checkScore", // 源节点 ID
                AsyncEdgeAction.edge_async(state -> {
                    // 1. 读取状态中的分数
                    int score = (Integer) state.value("score").orElse(0);
                    // 2. 根据业务逻辑返回一个“路由标签”
                    if (score >= 80) {
                        return "PASS";
                    } else if (score >= 60) {
                        return "REVIEW";
                    } else {
                        return "REJECT";
                    }
                }),
                // 3. 定义路由标签与目标节点的映射关系
                Map.of(
                        "PASS", "pass",
                        "REVIEW", "review",
                        "REJECT", "reject"
                )
        );

        // 出口边：所有分支最终都指向 END
        graph.addEdge("pass", StateGraph.END);
        graph.addEdge("review", StateGraph.END);
        graph.addEdge("reject", StateGraph.END);

        // 5. 编译并执行图
        CompiledGraph compiledGraph = graph.compile();

        // 模拟传入初始状态（你可以修改这里的分数来测试不同的分支）
        OverAllState finalState = compiledGraph.invoke(Map.of("score", 85)).get();

        // 6. 获取最终结果
        System.out.println("=== 最终执行结果 ===");
        System.out.println(finalState.value("result").orElse("无结果"));
        System.out.println(finalState.value("testName").orElse("无结果"));


        GraphRepresentation mermaidGraph = graph.getGraph(GraphRepresentation.Type.PLANTUML, "OrderRoutingWorkflow");
        System.out.println("=== Mermaid 流程图 ===");
        System.out.println(mermaidGraph.content());
        System.out.println("======================\n");
    }


}
