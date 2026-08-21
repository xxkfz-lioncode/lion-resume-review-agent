package com.lion.resume.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lion.resume.common.constant.GraphNodes;
import com.lion.resume.common.enums.NodeStatus;
import com.lion.resume.dto.GraphStatusEvent;
import com.lion.resume.entity.ReviewReport;
import com.lion.resume.mapper.ReviewReportMapper;
import com.lion.resume.sse.GraphEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审查报告服务：
 * 1. LLM 流式生成 Markdown 诊断报告（SSE chunk 打字机效果）
 * 2. 报告持久化
 * 3. 可选：简历向量化入库 Milvus + 历史相似人才检索
 */
@Slf4j
@Service
public class ReviewReportService {

    private final ReviewReportMapper reportMapper;
    private final LlmService llmService;
    private final GraphEventPublisher eventPublisher;

    @Autowired(required = false)
    private VectorStore vectorStore;

    @Value("${spring.ai.vectorstore.milvus.client.host:}")
    private String milvusHost;

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    public ReviewReportService(ReviewReportMapper reportMapper, LlmService llmService,
                               GraphEventPublisher eventPublisher) {
        this.reportMapper = reportMapper;
        this.llmService = llmService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 生成并持久化审查报告（流式推送）
     *
     * @param approved 是否为 HR 复核后的二次生成
     */
    public ReviewReport generate(String taskId, Map<String, Object> stateData,
                                 boolean approved, String hrComment) {
        Long resumeId = Long.valueOf(String.valueOf(stateData.get("resumeId")));
        String jobDesc = String.valueOf(stateData.getOrDefault("job_description", ""));
        Map<String, Object> resumeContent = castMap(stateData.get("resume_content"));
        String resumeText = String.valueOf(stateData.getOrDefault("resume_text", ""));
        List<?> radarData = stateData.get("radar_data") instanceof List<?> l ? l : List.of();
        double overall = toDouble(stateData.get("overall_score"), 0);
        boolean needsReview = Boolean.TRUE.equals(stateData.get("needs_human_review"));

        String system = "你是资深 HR 招聘专家与职业规划顾问。基于六维雷达评分与候选人简历，"
                + "输出一份结构化 Markdown 简历审查报告，包含：综合评分、六维分析、核心优势、风险提示、面试建议。"
                + "语言专业客观。";
        String user = buildPrompt(jobDesc, resumeContent, resumeText, radarData, overall,
                needsReview, approved, hrComment);

        // 流式生成 + SSE 推送（打字机效果）
        StringBuilder sb = new StringBuilder();
        llmService.stream(system, user)
                .doOnNext(chunk -> {
                    sb.append(chunk);
                    eventPublisher.publish(GraphStatusEvent.builder()
                            .type("stream")
                            .taskId(taskId)
                            .node(GraphNodes.GENERATE_REPORT)
                            .status(NodeStatus.STREAM.getValue())
                            .progress(93)
                            .data(chunk)
                            .timestamp(System.currentTimeMillis())
                            .build());
                })
                .doOnError(e -> log.warn("报告流式生成中断: {}", e.getMessage()))
                .blockLast();

        String detailReport = sb.toString();
        if (detailReport.isBlank()) {
            detailReport = "# 简历审查报告\n\n（报告生成失败，请检查 LLM 配置）";
        }

        // 持久化（按 taskId 幂等 upsert）
        LambdaQueryWrapper<ReviewReport> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ReviewReport::getTaskId, taskId);
        ReviewReport report = reportMapper.selectOne(queryWrapper);
        boolean create = report == null;
        if (create) {
            report = new ReviewReport();
            report.setTaskId(taskId);
            report.setResumeId(resumeId);
            report.setCreateTime(LocalDateTime.now());
        }
        report.setRadarData(JSON.toJSONString(radarData));
        report.setOverallScore(BigDecimal.valueOf(Math.round(overall * 10) / 10.0));
        report.setSummary(extractSummary(detailReport));
        report.setDetailReport(detailReport);
        report.setNeedsHumanReview(needsReview);
        if (approved) {
            report.setHrReviewComment(hrComment);
            Object adj = stateData.get("hr_adjusted_scores");
            report.setHrAdjustedScores(adj == null ? null : JSON.toJSONString(adj));
        }
        report.setUpdateTime(LocalDateTime.now());
        if (create) {
            reportMapper.insert(report);
        } else {
            reportMapper.updateById(report);
        }

        // 可选：简历向量化入库 Milvus + 相似人才检索
        embedIfEnabled(resumeId, resumeText, taskId);

        return report;
    }

    /** 向量化入库 + 相似人才检索（可选，全部失败不阻塞主流程） */
    private void embedIfEnabled(Long resumeId, String resumeText, String taskId) {
        // 未配置 spring.ai.vectorstore.milvus.client.host 即视为关闭
        if (vectorStore == null || milvusHost == null || milvusHost.isBlank()) {
            return;
        }
        if (apiKey == null || apiKey.isBlank() || apiKey.contains("sk-your-dashscope")) {
            log.warn("未配置真实 API Key，跳过向量化入库");
            return;
        }
        String text = resumeText == null ? "" : resumeText;
        try {
            // VectorStore 内部自动调用 EmbeddingModel 生成向量并入库（无需手动 embed）
            vectorStore.add(List.of(new Document(text, Map.of("resume_id", resumeId))));
            List<Document> hits = vectorStore.similaritySearch(
                    SearchRequest.builder().query(text).topK(5).build());
            List<Long> similar = hits.stream()
                    .map(doc -> doc.getMetadata().get("resume_id"))
                    .filter(Number.class::isInstance)
                    .map(n -> ((Number) n).longValue())
                    .filter(id -> !id.equals(resumeId))
                    .distinct()
                    .toList();
            if (!similar.isEmpty()) {
                log.info("发现历史相似人才: {}", similar);
                eventPublisher.publish(GraphStatusEvent.builder()
                        .type("info")
                        .taskId(taskId)
                        .node(GraphNodes.GENERATE_REPORT)
                        .status(NodeStatus.COMPLETED.getValue())
                        .progress(98)
                        .message("已在人才库中检索到 " + similar.size() + " 位历史相似人才")
                        .data(Map.of("similar_resume_ids", similar))
                        .timestamp(System.currentTimeMillis())
                        .build());
            }
        } catch (Exception e) {
            log.warn("Milvus 向量化/检索失败（不影响主流程）: {}", e.getMessage());
        }
    }

    private String buildPrompt(String jobDesc, Map<String, Object> resume, String resumeText,
                               List<?> radarData, double overall,
                               boolean needsReview, boolean approved, String hrComment) {
        StringBuilder sb = new StringBuilder();
        sb.append("岗位描述：\n").append(jobDesc == null ? "" : jobDesc).append("\n\n");
        sb.append("候选人结构化信息：\n").append(JSON.toJSONString(resume)).append("\n\n");
        sb.append("六维雷达评分：\n").append(JSON.toJSONString(radarData)).append("\n\n");
        sb.append("综合评分：").append(overall).append("/5\n\n");
        if (needsReview) {
            sb.append("该候选人经过 HR 人工复核，");
            if (approved) {
                sb.append("HR 已确认/调整评分。HR 意见：").append(hrComment == null ? "" : hrComment).append("\n\n");
            }
        }
        sb.append("简历原文：\n").append(truncate(resumeText, 3000)).append("\n");
        return sb.toString();
    }

    private static String extractSummary(String report) {
        if (report == null || report.isBlank()) {
            return "";
        }
        // 取第二个标题前的内容作为摘要（去掉主标题）
        String[] lines = report.split("\\n");
        StringBuilder sb = new StringBuilder();
        int headingCount = 0;
        for (String line : lines) {
            String t = line.trim();
            if (t.startsWith("#")) {
                headingCount++;
                if (headingCount > 1) {
                    break;
                }
                continue;
            }
            if (headingCount >= 1 && !t.isEmpty()) {
                sb.append(t).append(" ");
            }
            if (sb.length() > 180) {
                break;
            }
        }
        return sb.toString().trim();
    }

    private static Map<String, Object> castMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> result = new java.util.HashMap<>();
            m.forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return new java.util.HashMap<>();
    }

    private static double toDouble(Object o, double fallback) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return fallback;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
