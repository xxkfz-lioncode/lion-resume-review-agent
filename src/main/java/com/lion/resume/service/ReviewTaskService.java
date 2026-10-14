package com.lion.resume.service;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lion.resume.common.constant.GraphNodes;
import com.lion.resume.common.constant.GraphStateKeys;
import com.lion.resume.common.enums.NodeStatus;
import com.lion.resume.common.exception.BizException;
import com.lion.resume.dto.GraphStatusEvent;
import com.lion.resume.entity.Resume;
import com.lion.resume.entity.ReviewReport;
import com.lion.resume.entity.ReviewTask;
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
 * 3. 人机协同：基于官方 HIL 机制，{@code HumanReviewAction#interruptAfter} 真正中断 Graph，
 *    HR 复核确认后由 {@link #approve} 通过 {@code updateState} + {@code resume} 恢复执行。
 *
 * <p>threadId 约定：以 taskId 作为 Graph 的 threadId，Checkpoint 按线程隔离，
 * 恢复时凭 taskId 即可定位到中断时的完整状态。
 *
 * <p>注意：直接注入编译好的 {@link CompiledGraph}（Spring 单例），
 * 不要每次执行都调用 {@code ResumeReviewGraph#resumeReviewCompiledGraph()} 重新编译。
 */
@Slf4j
@Service
public class ReviewTaskService {

    private final ReviewTaskMapper taskMapper;
    private final ReviewReportMapper reportMapper;
    private final ResumeService resumeService;
    private final CompiledGraph resumeReviewCompiledGraph;
    private final GraphEventPublisher eventPublisher;
    private final Executor graphExecutor;

    public ReviewTaskService(ReviewTaskMapper taskMapper,
                             ReviewReportMapper reportMapper,
                             ResumeService resumeService,
                             CompiledGraph resumeReviewCompiledGraph,
                             GraphEventPublisher eventPublisher,
                             @Qualifier("graphExecutor") Executor graphExecutor) {
        this.taskMapper = taskMapper;
        this.reportMapper = reportMapper;
        this.resumeService = resumeService;
        this.resumeReviewCompiledGraph = resumeReviewCompiledGraph;
        this.eventPublisher = eventPublisher;
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
        initialState.put(GraphStateKeys.TASK_ID, taskId);
        initialState.put(GraphStateKeys.RESUME_ID, resumeId);
        initialState.put(GraphStateKeys.JOB_DESCRIPTION, jobDescription == null ? "" : jobDescription);
        initialState.put(GraphStateKeys.NEEDS_HUMAN_REVIEW, false);
        initialState.put(GraphStateKeys.APPROVED, false);

        graphExecutor.execute(() -> runGraph(taskId, initialState));
        return taskId;
    }

    /**
     * 执行 Graph 工作流
     */
    private void runGraph(String taskId, Map<String, Object> initialState) {
        try {
            // threadId = taskId：Checkpoint 按任务隔离，供中断后恢复定位
            RunnableConfig config = RunnableConfig.builder().threadId(taskId).build();
            resumeReviewCompiledGraph.stream(initialState, config)
                    .doOnError(e -> {
                        log.error("Graph 执行异常, taskId={}: {}", taskId, e.getMessage());
                        markFailed(taskId, e.getMessage());
                    })
                    .doOnComplete(() -> {
                        // 中断时 stream 同样会 complete，故以 DB 状态区分：
                        // 已被 HumanReviewAction 置为 NEEDS_REVIEW 则视为挂起，等待 HR 恢复
                        ReviewTask task = taskMapper.selectById(taskId);
                        if (task != null && "NEEDS_REVIEW".equals(task.getStatus())) {
                            log.info("Graph 已在 human_review 处挂起，等待 HR 复核, taskId={}", taskId);
                            return;
                        }
                        markCompleted(taskId);
                    })
                    .blockLast();
        } catch (Exception e) {
            log.error("Graph 执行异常, taskId={}", taskId, e);
            markFailed(taskId, e.getMessage());
        }
    }

    /**
     * HR 复核确认（人机协同恢复点）
     *
     * <p>主流程四步：CAS 校验 → 构造恢复状态 → updateState 写入 → 恢复执行。
     * 后续节点（报告生成，及未来的背调/面试/Offer）由主图继续编排，此处不硬编码下游动作。
     */
    public void approve(String taskId, Map<String, Double> adjustedScores, String reviewComment) {
        ReviewTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BizException(404, "任务不存在: " + taskId);
        }
        // CAS 幂等：防止重复提交导致两次恢复（双倍 LLM 开销）
        if (tryStartResuming(taskId) != 1) {
            throw new BizException("任务当前不在待复核状态: " + task.getStatus());
        }

        try {
            // threadId 必须与发起时一致（同为 taskId），否则定位不到中断时的 Checkpoint
            // resume() 用于清除中断标记，缺失则 Graph 不会继续往下执行
            RunnableConfig config = RunnableConfig.builder().threadId(taskId).resume().build();

            Map<String, Object> resumeState = buildResumeState(taskId, config, adjustedScores, reviewComment);

            // 写入 HR 调整，返回携带新 checkpointId 的配置
            RunnableConfig resumeConfig = resumeReviewCompiledGraph
                    .updateState(config, resumeState, GraphNodes.HUMAN_REVIEW);

            publishResumed(taskId, resumeState);
            // 恢复后从中断处继续，已执行节点不重跑；resumeState 同时作为 inputs 兜底防字段丢失
            graphExecutor.execute(() -> resumeGraph(taskId, resumeConfig, resumeState));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("恢复 Graph 失败, taskId={}", taskId, e);
            markFailed(taskId, e.getMessage());
            throw new BizException("恢复审查流程失败: " + e.getMessage());
        }
    }

    /**
     * 构造恢复执行所需的完整状态。
     *
     * <p>以 Checkpoint 状态为基底，叠加 HR 调整，再以 DB 为权威来源补齐基础字段。
     * 之所以按 DB 补基础字段：Checkpoint 序列化往返与 updateState 合并不可靠，
     * taskId / resumeId 等可能丢失，导致下游节点取不到值而失败。
     */
    private Map<String, Object> buildResumeState(String taskId, RunnableConfig config,
                                                 Map<String, Double> adjustedScores, String reviewComment) {
        Map<String, Object> state = new HashMap<>(loadCheckpointState(resumeReviewCompiledGraph, config));

        applyScoreAdjustments(state, adjustedScores);
        // 放行标志：GenerateReportAction 据此正常生成报告（不再等待复核）
        state.put(GraphStateKeys.APPROVED, true);
        state.put(GraphStateKeys.HR_COMMENT, reviewComment == null ? "" : reviewComment);
        fillBaseFields(state, taskId);

        return state;
    }

    /**
     * 应用 HR 调整后的评分：按雷达图维度名覆盖（clamp 0-5），并重算综合分为六维均值。
     */
    private void applyScoreAdjustments(Map<String, Object> state, Map<String, Double> adjustedScores) {
        List<Map<String, Object>> radar = castRadar(state.get(GraphStateKeys.RADAR_DATA));
        if (adjustedScores == null || adjustedScores.isEmpty() || radar.isEmpty()) {
            return;
        }
        // 按维度名匹配覆盖，限制在 0-5 分制内
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
        state.put(GraphStateKeys.RADAR_DATA, radar);
        state.put(GraphStateKeys.OVERALL_SCORE, Math.round(sum / radar.size() * 10) / 10.0);
        state.put(GraphStateKeys.HR_ADJUSTED_SCORES, adjustedScores);
    }

    /** SSE 通知前端关闭复核框、刷新雷达图。 */
    private void publishResumed(String taskId, Map<String, Object> state) {
        // 仅推评分数据：完整 state 含简历全文，体积过大
        Map<String, Object> pushData = new HashMap<>();
        pushData.put(GraphStateKeys.RADAR_DATA, state.get(GraphStateKeys.RADAR_DATA));
        pushData.put(GraphStateKeys.OVERALL_SCORE, state.get(GraphStateKeys.OVERALL_SCORE));
        eventPublisher.publish(GraphStatusEvent.builder()
                .type("node")
                .taskId(taskId)
                .node(GraphNodes.HUMAN_REVIEW)
                .status(NodeStatus.RESUMED.getValue())
                .progress(88)
                .message("HR 已确认评分，继续执行后续流程")
                .data(pushData)
                .timestamp(System.currentTimeMillis())
                .build());
    }

    /**
     * CAS 推进：仅当任务处于 NEEDS_REVIEW 时才置为 RUNNING。
     *
     * @return 影响行数，非 1 表示状态已被其他请求推进
     */
    private int tryStartResuming(String taskId) {
        LambdaUpdateWrapper<ReviewTask> uw = new LambdaUpdateWrapper<>();
        uw.eq(ReviewTask::getId, taskId)
                .eq(ReviewTask::getStatus, "NEEDS_REVIEW")
                .set(ReviewTask::getStatus, "RUNNING");
        return taskMapper.update(null, uw);
    }

    /**
     * 从中断处恢复执行 Graph。
     *
     * <p>inputs 传入完整状态（而非空 Map）：Checkpoint 只保留 keyStrategyFactory 中注册过的键，
     * 未注册的必需字段（taskId / resumeId / job_description / resume_text）需通过 inputs 补齐，
     * 否则恢复后的节点会因取不到值而失败。
     */
    private void resumeGraph(String taskId, RunnableConfig resumeConfig, Map<String, Object> inputs) {
        try {
            resumeReviewCompiledGraph.stream(inputs == null ? new HashMap<>() : inputs, resumeConfig)
                    .doOnError(e -> {
                        log.error("恢复后流程执行异常, taskId={}: {}", taskId, e.getMessage());
                        markFailed(taskId, e.getMessage());
                    })
                    .doOnComplete(() -> markCompleted(taskId))
                    .blockLast();
        } catch (Exception e) {
            log.error("恢复后流程执行异常, taskId={}", taskId, e);
            markFailed(taskId, e.getMessage());
        }
    }

    /**
     * 以 DB 为权威来源补齐基础输入字段（taskId / resumeId / job_description / 简历原文），
     * 保证无论 Checkpoint 保存了什么，下游节点都能拿到必需输入。
     */
    private void fillBaseFields(Map<String, Object> state, String taskId) {
        ReviewTask dbTask = taskMapper.selectById(taskId);
        if (dbTask == null) {
            markFailed(taskId, "任务不存在，无法恢复");
            throw new BizException(404, "任务不存在: " + taskId);
        }
        // DB 权威值覆盖：不信任 Checkpoint 中的这两个字段
        state.put(GraphStateKeys.TASK_ID, taskId);
        state.put(GraphStateKeys.RESUME_ID, dbTask.getResumeId());
        if (dbTask.getJobDescription() != null) {
            state.put(GraphStateKeys.JOB_DESCRIPTION, dbTask.getJobDescription());
        }

        // 简历原文缺失时回补，否则会基于空简历生成报告
        if (isBlank(state.get(GraphStateKeys.RESUME_TEXT)) && dbTask.getResumeId() != null) {
            try {
                Resume resume = resumeService.getById(dbTask.getResumeId());
                if (resume != null) {
                    String text = resumeService.extractText(resume);
                    if (!isBlank(text)) {
                        state.put(GraphStateKeys.RESUME_TEXT, text);
                    }
                }
            } catch (Exception e) {
                log.warn("回补简历原文失败, taskId={}: {}", taskId, e.getMessage());
            }
        }
    }

    private static boolean isBlank(Object o) {
        return o == null || String.valueOf(o).isBlank();
    }

    /**
     * 读取当前线程的 Checkpoint 状态；Checkpoint 缺失（如已过 TTL）时返回空 Map。
     */
    private Map<String, Object> loadCheckpointState(CompiledGraph compiledGraph, RunnableConfig config) {
        try {
            StateSnapshot snapshot = compiledGraph.getState(config);
            if (snapshot != null && snapshot.state() != null) {
                return new HashMap<>(snapshot.state().data());
            }
        } catch (Exception e) {
            log.warn("读取 Checkpoint 失败（可能已过期）: {}", e.getMessage());
        }
        return new HashMap<>();
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
