package com.lion.resume.graph;

import com.alibaba.cloud.ai.graph.*;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

/**
 * 子图（SubGraph）简单示例
 *
 * <pre>
 * 子图：START → risk_check_llm → END
 * 主图：START → execute_risk_subgraph ──[条件边]──┬─→ human_review → END
 *                                                 └─→ auto_pass     → END
 * </pre>
 *
 * 要点：子图单独编译（不用配 Saver），主图用一个节点包住它，做「入参映射 + 出参映射」。
 */
class SubGraphTest {

    /**
     * 高风险：子图判 HIGH → 条件边走人工复核
     *
     * <p>这里只做日志输出、不做断言，方便直接观察子图结果是否正确映射回主状态。
     */
    @Test
    @SneakyThrows
    void highRisk_routesToHumanReview() {
        CompiledGraph mainGraph = buildMainGraph();
        OverAllState state = run(mainGraph, "张三，5 年 Java，疑似简历造假。");

        // 观察点：risk_level 来自子图，decision 由主图条件边根据 risk_level 路由得出
        System.out.println("[期望] risk_level=HIGH, decision=NEEDS_HUMAN_REVIEW");
        System.out.println("[实际] risk_level=" + state.value("risk_level").orElse("无")
                + ", decision=" + state.value("decision").orElse("无"));
    }

    /**
     * 低风险：子图判 LOW → 条件边走自动通过
     */
    @Test
    @SneakyThrows
    void lowRisk_autoPass() {
        CompiledGraph mainGraph = buildMainGraph();
        OverAllState state = run(mainGraph, "李四，3 年后端，熟悉 Spring Boot。");

        // 观察点：子图返回 LOW，主图应跳过人工复核直接通过
        System.out.println("[期望] risk_level=LOW, decision=AUTO_PASSED");
        System.out.println("[实际] risk_level=" + state.value("risk_level").orElse("无")
                + ", decision=" + state.value("decision").orElse("无"));
    }

    /**
     * 打印两张图的 Mermaid 流程图
     *
     * <p>输出内容可直接粘到支持 Mermaid 的地方渲染（Markdown 代码块、语雀、
     * <a href="https://mermaid.live">mermaid.live</a>、GitHub / GitLab 的 Markdown 均可）。
     *
     * <p>想导出 PlantUML 把 {@code Type.MERMAID} 换成 {@code Type.PLANTUML} 即可。
     */
    @Test
    @SneakyThrows
    void printMermaidGraph() {
        // 子图结构
        GraphRepresentation subGraph = buildRiskSubGraph()
                .getGraph(GraphRepresentation.Type.MERMAID, "风险审查子图");
        System.out.println("\n========== 子图 Mermaid ==========");
        System.out.println(subGraph.content());

        // 主图结构（子图在主图里就是一个普通节点 execute_risk_subgraph）
        GraphRepresentation mainGraph = buildMainGraph()
                .getGraph(GraphRepresentation.Type.MERMAID, "简历审查主图");
        System.out.println("\n========== 主图 Mermaid ==========");
        System.out.println(mainGraph.content());
        System.out.println("==================================\n");
    }

    // ==================== 构建 ====================

    /**
     * 子图：独立定义、独立编译，无需配置 Saver
     *
     * <p>说明：
     * <ul>
     *   <li>子图有自己的 {@link KeyStrategyFactory}，key 与主图完全独立（这里叫 text / output）；</li>
     *   <li>子图<b>不需要</b>配 CheckpointSaver —— 是否可中断恢复由主图决定，
     *       子图作为主图的一个节点被一起持久化；</li>
     *   <li>内部节点可以随意复杂（多节点、条件边、循环），对主图而言都被这一层封装屏蔽掉。</li>
     * </ul>
     */
    private CompiledGraph buildRiskSubGraph() throws GraphStateException {
        // 子图状态：text 为入参，output 为出参
        KeyStrategyFactory factory = () -> Map.of(
                "text", new ReplaceStrategy(),
                "output", new ReplaceStrategy()
        );

        return new StateGraph("RiskCheckSubWorkflow", factory)
                // 唯一节点：模拟 LLM 风险判断（真实场景替换为 ChatClient 调用）
                .addNode("risk_check_llm", AsyncNodeAction.node_async(state -> {
                    // 读取子图自己的入参
                    String text = String.valueOf(state.value("text").orElse(""));
                    // 用关键词规则模拟 LLM 判断，避免单测依赖真实模型
                    boolean hit = text.contains("造假") || text.contains("泄密");
                    System.out.println("[子图][risk_check_llm] 命中风险词 = " + hit);
                    return Map.of("output", hit ? "HIGH" : "LOW");
                }))
                .addEdge(StateGraph.START, "risk_check_llm")
                .addEdge("risk_check_llm", StateGraph.END)
                .compile();
    }

    /**
     * 主图：用一个自定义节点把子图包起来
     *
     * <p>关键就是这个包裹节点，它做三件事：
     * <ol>
     *   <li>把主图状态映射成子图入参；</li>
     *   <li>调用子图 {@code invoke}；</li>
     *   <li>把子图出参映射回主图状态的字段名（这里是 risk_level）。</li>
     * </ol>
     *
     * <p>注意：子图状态与主图状态是<b>隔离</b>的，子图里的 key 不会自动合并回主状态，
     * 必须像下面这样显式映射，否则主图后续节点读不到。
     */
    private CompiledGraph buildMainGraph() throws GraphStateException {
        // 先把子图编译好，作为局部变量传给包裹节点
        CompiledGraph riskSubGraph = buildRiskSubGraph();

        // 主图状态：input 入参，risk_level 由子图回写，decision 为最终决策
        KeyStrategyFactory factory = () -> Map.of(
                "input", new ReplaceStrategy(),
                "risk_level", new ReplaceStrategy(),
                "decision", new ReplaceStrategy()
        );

        return new StateGraph("ResumeReviewMainWorkflow", factory)
                // ---- 包裹子图的节点 ----
                .addNode("execute_risk_subgraph", AsyncNodeAction.node_async(state -> {
                    // ① 主图数据 → 子图入参（key 名从 input 换成子图认识的 text）
                    Map<String, Object> subInput = Map.of("text", state.value("input").orElse(""));
                    // ② 执行子图，拿到子图自己的 OverAllState
                    Optional<OverAllState> subResult = riskSubGraph.invoke(subInput);
                    // ③ 子图出参 → 主图状态（key 名从 output 换回主图的 risk_level）
                    Object riskLevel = subResult.orElseThrow().value("output").orElse("LOW");
                    System.out.println("[主图] 子图返回 = " + riskLevel);
                    return Map.of("risk_level", riskLevel);
                }))
                // ---- 两个分支节点 ----
                .addNode("human_review", AsyncNodeAction.node_async(
                        state -> Map.of("decision", "NEEDS_HUMAN_REVIEW")))
                .addNode("auto_pass", AsyncNodeAction.node_async(
                        state -> Map.of("decision", "AUTO_PASSED")))

                // ---- 边 ----
                .addEdge(StateGraph.START, "execute_risk_subgraph")
                // 条件边：按子图回写的 risk_level 决定走人工复核还是直接通过
                // 返回的 "REVIEW" / "PASS" 必须能在下面的映射表里找到
                .addConditionalEdges("execute_risk_subgraph",
                        AsyncEdgeAction.edge_async(state ->
                                "HIGH".equals(state.value("risk_level").orElse("LOW")) ? "REVIEW" : "PASS"),
                        Map.of("REVIEW", "human_review", "PASS", "auto_pass"))
                .addEdge("human_review", StateGraph.END)
                .addEdge("auto_pass", StateGraph.END)
                .compile();
    }

    /** 执行主图并打印最终状态 */
    private OverAllState run(CompiledGraph graph, String input) {
        OverAllState state = graph.invoke(Map.of("input", input)).orElseThrow();
        System.out.println("[结果] risk_level = " + state.value("risk_level").orElse("无")
                + " , decision = " + state.value("decision").orElse("无"));
        return state;
    }
}
