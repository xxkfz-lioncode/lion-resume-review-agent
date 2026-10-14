package com.lion.resume.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Graph 流程图导出服务
 * <p>
 * 基于框架 {@link GraphRepresentation}（Mermaid / PlantUML）生成「完整执行图」文本，
 * 供前端静态展示或文档导出。与 {@link ReviewTaskService#topology()} 的区别：
 * 前者是框架自动生成的标准图文本（含 START/END、条件边等完整拓扑），
 * 后者是自定义节点/边 JSON，用于前端实时状态高亮的流程面板。
 *
 * <p>说明：人工复核已改为官方中断机制，后置子图已移除，故仅导出主图。
 */
@Service
@RequiredArgsConstructor
public class GraphExportService {

    /** 主图（审查主流程，含 human_review 中断点） */
    private final CompiledGraph resumeReviewCompiledGraph;

    /**
     * 导出主图图文本
     *
     * @param format 图格式：mermaid（默认）| plantuml
     * @return {@code {main: 主图文本}}
     */
    public Map<String, String> graphText(String format) {
        GraphRepresentation.Type type = "plantuml".equalsIgnoreCase(format)
                ? GraphRepresentation.Type.PLANTUML
                : GraphRepresentation.Type.MERMAID;
        return Map.of("main", resumeReviewCompiledGraph.getGraph(type, "ResumeReviewWorkflow").content());
    }
}
