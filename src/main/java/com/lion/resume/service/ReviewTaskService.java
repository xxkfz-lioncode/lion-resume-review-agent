package com.lion.resume.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lion.resume.cache.GraphStateCache;
import com.lion.resume.common.constant.GraphNodes;
import com.lion.resume.common.enums.NodeStatus;
import com.lion.resume.common.exception.BizException;
import com.lion.resume.dto.GraphStatusEvent;
import com.lion.resume.entity.Resume;
import com.lion.resume.entity.ReviewReport;
import com.lion.resume.entity.ReviewTask;
import com.lion.resume.graph.ResumeReviewGraph;
import com.lion.resume.graph.ResumeReviewPostGraph;
import com.lion.resume.mapper.ReviewReportMapper;
import com.lion.resume.mapper.ReviewTaskMapper;
import com.lion.resume.sse.GraphEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 审查任务编排服务：
 * 1. 创建任务 → 异步执行 Graph（避免 HTTP 长连接超时）
 * 2. 通过 Redis/SSE 广播节点状态
 * 3. 人机协同：HR 复核确认后驱动后置子图恢复执行
 */
@Slf4j
@Service
public class ReviewTaskService {

    private final ReviewTaskMapper taskMapper;
    private final ReviewReportMapper reportMapper;
    private final ResumeService resumeService;
    private final ResumeReviewGraph resumeReviewGraph;
    private final ResumeReviewPostGraph postReviewGraph;
    private final GraphEventPublisher eventPublisher;
    private final GraphStateCache stateCache;
    private final Executor graphExecutor;

    public ReviewTaskService(ReviewTaskMapper taskMapper,
                             ReviewReportMapper reportMapper,
                             ResumeService resumeService,
                             ResumeReviewGraph resumeReviewGraph,
                             ResumeReviewPostGraph postReviewGraph,
                             GraphEventPublisher eventPublisher,
                             GraphStateCache stateCache,
                             @Qualifier("graphExecutor") Executor graphExecutor) {
        this.taskMapper = taskMapper;
        this.reportMapper = reportMapper;
        this.resumeService = resumeService;
        this.resumeReviewGraph = resumeReviewGraph;
        this.postReviewGraph = postReviewGraph;
        this.eventPublisher = eventPublisher;
        this.stateCache = stateCache;
        this.graphExecutor = graphExecutor;
    }

    /**
     * 发起审查（异步执行 Graph），立即返回 taskId 供前端订阅 SSE
     */
    public String startReview(String resumeIdStr, String jobDescription) {
        Long resumeId = Long.valueOf(resumeIdStr);
        Resume resume = resumeService.getById(resumeId);

        String taskId = UUID.randomUUID().toString().replace("-", "");
        ReviewTask task = new ReviewTask();
        task.setId(taskId);
        task.setResumeId(resumeId);
        task.setJobDescription(jobDescription == null ? "" : jobDescription);
        task.setStatus("RUNNING");
        task.setProgress(0);
        task.setCurrentNode("START");
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.insert(task);

        eventPublisher.publish(GraphStatusEvent.builder()
                .type("node")
                .taskId(taskId)
                .node("__START__")
                .status(NodeStatus.COMPLETED.getValue())
                .progress(0)
                .message("审查任务已创建，简历: " + resume.getFileName())
                .timestamp(System.currentTimeMillis())
                .build());

        Map<String, Object> initialState = new HashMap<>();
        initialState.put("taskId", taskId);
        initialState.put("resumeId", resumeId);
        initialState.put("job_description", jobDescription == null ? "" : jobDescription);
        initialState.put("needs_human_review", false);
        initialState.put("approved", false);

        graphExecutor.execute(() -> runGraph(taskId, initialState));
        return taskId;
    }

    /**
     * 执行 Graph 工作流
     */
    private void runGraph(String taskId, Map<String, Object> initialState) {
        try {
            CompiledGraph compiledGraph = resumeReviewGraph.resumeReviewCompiledGraph();
            compiledGraph.stream(initialState)
                    .doOnError(e -> {
                        log.error("Graph 执行异常, taskId={}: {}", taskId, e.getMessage());
                        markFailed(taskId, e.getMessage());
                    })
                    .doOnComplete(() -> {
                        Map<String, Object> finalState = stateCache.loadState(taskId);
                        boolean needsReview = Boolean.TRUE.equals(finalState.get("needs_human_review"));
                        boolean approved = Boolean.TRUE.equals(finalState.get("approved"));
                        if (needsReview && !approved) {
                            markNeedsReview(taskId);
                        } else {
                            markCompleted(taskId);
                        }
                    })
                    .blockLast();
        } catch (Exception e) {
            log.error("Graph 执行异常, taskId={}", taskId, e);
            markFailed(taskId, e.getMessage());
        }
    }

    /**
     * HR 复核确认（人机协同恢复点）
     * <p>职责单一：校验状态 → 应用 HR 调整 → 标记已复核 → 推进后置流程。
     * 复核后的所有节点（报告生成、未来可能的背调/面试/Offer 等）统一由
     * {@link ResumeReviewPostGraph} 编排，本方法不关心具体下游动作，避免硬编码。
     */
    public void approve(String taskId, Map<String, Double> adjustedScores, String reviewComment) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "任务不存在: " + taskId);
        }
        if (!"NEEDS_REVIEW".equals(task.getStatus())) {
            throw new BizException("任务当前不在待复核状态: " + task.getStatus());
        }

        Map<String, Object> state = stateCache.loadState(taskId);

        // 应用 HR 调整后的评分（按雷达图维度名匹配）
        List<Map<String, Object>> radar = castRadar(state.get("radar_data"));
        if (adjustedScores != null && !adjustedScores.isEmpty()) {
            for (Map<String, Object> item : radar) {
                Double v = adjustedScores.get(String.valueOf(item.get("name")));
                if (v != null) {
                    item.put("value", Math.max(0, Math.min(5, v)));
                }
            }
            double sum = 0;
            for (Map<String, Object> item : radar) {
                sum += ((Number) item.get("value")).doubleValue();
            }
            state.put("overall_score", Math.round(sum / radar.size() * 10) / 10.0);
            state.put("hr_adjusted_scores", adjustedScores);
        }
        state.put("approved", true);
        state.put("hr_comment", reviewComment == null ? "" : reviewComment);
        stateCache.saveState(taskId, state);
        stateCache.deletePendingReview(taskId);

        // SSE：通知前端复核完成，恢复执行
        eventPublisher.publish(GraphStatusEvent.builder()
                .type("node")
                .taskId(taskId)
                .node(GraphNodes.HUMAN_REVIEW)
                .status(NodeStatus.RESUMED.getValue())
                .progress(88)
                .message("HR 已确认评分，继续执行后续流程")
                .data(state)
                .timestamp(System.currentTimeMillis())
                .build());

        // 恢复：驱动后置子图继续执行（报告生成等后续节点由 Graph 编排，异步）
        graphExecutor.execute(() -> runPostReview(taskId, state));
    }

    /**
     * 执行复核后置流程子图：HR 确认后的状态快照作为输入，
     * 依次执行 generate_report（及未来新增节点），完成后标记任务结束。
     */
    private void runPostReview(String taskId, Map<String, Object> state) {
        try {
            CompiledGraph postGraph = postReviewGraph.postReviewCompiledGraph();
            postGraph.stream(state)
                    .doOnError(e -> {
                        log.error("后置流程执行异常, taskId={}: {}", taskId, e.getMessage());
                        markFailed(taskId, e.getMessage());
                    })
                    .doOnComplete(() -> markCompleted(taskId))
                    .blockLast();
        } catch (Exception e) {
            log.error("后置流程执行异常, taskId={}", taskId, e);
            markFailed(taskId, e.getMessage());
        }
    }

    /**
     * 查询任务详情（任务 + 报告 + 拓扑）
     */
    public Map<String, Object> getTaskDetail(String taskId) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "任务不存在: " + taskId);
        }
        LambdaQueryWrapper<ReviewReport> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ReviewReport::getTaskId, taskId);
        ReviewReport report = reportMapper.selectOne(queryWrapper);
        Resume resume = resumeService.getById(task.getResumeId());

        Map<String, Object> result = new HashMap<>();
        result.put("task", task);
        result.put("resume", Map.of("id", resume.getId(), "fileName", resume.getFileName()));
        result.put("report", report);
        result.put("topology", topology());
        return result;
    }

    /**
     * 报告列表（分页 + 关键词过滤 + 全量统计）
     * 关联简历文件名与任务状态，供历史报告页面展示
     */
    public Map<String, Object> listReports(int page, int size, String keyword) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        String kw = keyword == null ? "" : keyword.trim();

        LambdaQueryWrapper<ReviewReport> qw = new LambdaQueryWrapper<>();
        if (!kw.isEmpty()) {
            // 关键词匹配：报告摘要 或 简历文件名
            List<Long> matchedResumeIds = resumeService.listIdsByFileNameLike(kw);
            qw.and(w -> {
                w.like(ReviewReport::getSummary, kw);
                if (!matchedResumeIds.isEmpty()) {
                    w.or().in(ReviewReport::getResumeId, matchedResumeIds);
                }
            });
        }
        qw.orderByDesc(ReviewReport::getUpdateTime);
        List<ReviewReport> all = reportMapper.selectList(qw);
        int total = all.size();
        int from = Math.min((safePage - 1) * safeSize, total);
        int to = Math.min(from + safeSize, total);
        List<ReviewReport> pageList = all.subList(from, to);

        // 批量关联查询简历与任务
        Set<Long> resumeIds = pageList.stream().map(ReviewReport::getResumeId).collect(Collectors.toSet());
        Set<String> taskIds = pageList.stream().map(ReviewReport::getTaskId).collect(Collectors.toSet());
        Map<Long, Resume> resumeMap = resumeIds.isEmpty() ? Map.of()
                : resumeService.listByIds(resumeIds).stream().collect(Collectors.toMap(Resume::getId, r -> r));
        Map<String, ReviewTask> taskMap = taskIds.isEmpty() ? Map.of()
                : taskMapper.selectBatchIds(taskIds).stream().collect(Collectors.toMap(ReviewTask::getId, t -> t));

        List<Map<String, Object>> list = new ArrayList<>();
        for (ReviewReport r : pageList) {
            Map<String, Object> m = new HashMap<>();
            m.put("reportId", String.valueOf(r.getId()));
            m.put("taskId", r.getTaskId());
            m.put("resumeId", String.valueOf(r.getResumeId()));
            Resume res = resumeMap.get(r.getResumeId());
            m.put("fileName", res == null ? "（简历已删除）" : res.getFileName());
            m.put("overallScore", r.getOverallScore() == null ? 0 : r.getOverallScore().doubleValue());
            m.put("summary", r.getSummary());
            m.put("needsHumanReview", Boolean.TRUE.equals(r.getNeedsHumanReview()));
            ReviewTask t = taskMap.get(r.getTaskId());
            m.put("taskStatus", t == null ? "" : t.getStatus());
            m.put("createTime", String.valueOf(r.getCreateTime()));
            m.put("updateTime", String.valueOf(r.getUpdateTime()));
            // 生成耗时（秒）：从任务创建到报告完成
            LocalDateTime start = t != null ? t.getCreateTime() : r.getCreateTime();
            LocalDateTime end = r.getUpdateTime();
            if (start != null && end != null) {
                long seconds = Duration.between(start, end).getSeconds();
                m.put("duration", seconds);
            } else {
                m.put("duration", 0);
            }
            list.add(m);
        }

        // 全量统计（基于当前过滤条件）
        int needsReviewCount = (int) all.stream()
                .filter(r -> Boolean.TRUE.equals(r.getNeedsHumanReview())).count();
        double avg = all.stream().map(ReviewReport::getOverallScore)
                .filter(Objects::nonNull).mapToDouble(BigDecimal::doubleValue).average().orElse(0);
        double max = all.stream().map(ReviewReport::getOverallScore)
                .filter(Objects::nonNull).mapToDouble(BigDecimal::doubleValue).max().orElse(0);

        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("list", list);
        result.put("stats", Map.of(
                "total", total,
                "avgScore", Math.round(avg * 10) / 10.0,
                "maxScore", Math.round(max * 10) / 10.0,
                "needsReviewCount", needsReviewCount));
        return result;
    }

    /**
     * Graph 拓扑（供前端渲染流程追踪面板）
     */
    public Map<String, Object> topology() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(Map.of("id", "__START__", "label", "开始", "description", "接收简历与岗位描述", "stage", -1));
        nodes.addAll(GraphNodes.NODE_META.values().stream()
                .map(m -> new HashMap<>(m)).toList());
        nodes.add(Map.of("id", "__END__", "label", "结束", "description", "报告持久化并推送完成信号", "stage", 5));
        return Map.of("nodes", nodes, "edges", GraphNodes.edges());
    }

    private void markCompleted(String taskId) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setCurrentNode("__END__");
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
        }
        // 通知前端任务整体完成（触发报告拉取）
        eventPublisher.publish(GraphStatusEvent.builder()
                .type("task")
                .taskId(taskId)
                .node("__END__")
                .status(NodeStatus.COMPLETED.getValue())
                .progress(100)
                .message("简历审查完成")
                .timestamp(System.currentTimeMillis())
                .build());
        // 同步更新 __END__ 节点面板状态为已完成
        eventPublisher.publish(GraphStatusEvent.builder()
                .type("node")
                .taskId(taskId)
                .node("__END__")
                .status(NodeStatus.COMPLETED.getValue())
                .progress(100)
                .message("结束")
                .timestamp(System.currentTimeMillis())
                .build());
    }

    private void markNeedsReview(String taskId) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus("NEEDS_REVIEW");
            task.setProgress(88);
            task.setCurrentNode(GraphNodes.HUMAN_REVIEW);
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }

    private void markFailed(String taskId, String error) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task != null) {
            task.setStatus("FAILED");
            task.setErrorMessage(error);
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
        }
        // 通知前端任务整体失败
        eventPublisher.publish(GraphStatusEvent.builder()
                .type("task")
                .taskId(taskId)
                .node("__END__")
                .status(NodeStatus.ERROR.getValue())
                .message("审查失败: " + error)
                .timestamp(System.currentTimeMillis())
                .build());
        // 同步更新 __END__ 节点面板状态为失败
        eventPublisher.publish(GraphStatusEvent.builder()
                .type("node")
                .taskId(taskId)
                .node("__END__")
                .status(NodeStatus.ERROR.getValue())
                .message(error)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castRadar(Object o) {
        if (o instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> m) {
                    Map<String, Object> copy = new HashMap<>();
                    m.forEach((k, v) -> copy.put(String.valueOf(k), v));
                    result.add(copy);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }

    /**
     * 删除报告及关联任务（级联删除）
     */
    public void deleteReport(Long reportId) {
        ReviewReport report = reportMapper.selectById(reportId);
        if (report == null) {
            throw new BizException(404, "报告不存在");
        }
        // 删除任务（级联删除日志、状态等）
        if (report.getTaskId() != null) {
            taskMapper.deleteById(report.getTaskId());
        }
        // 删除报告
        reportMapper.deleteById(reportId);
    }
}
