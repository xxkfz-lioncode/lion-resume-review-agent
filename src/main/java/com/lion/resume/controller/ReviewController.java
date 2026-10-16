package com.lion.resume.controller;

import com.lion.resume.common.R;
import com.lion.resume.common.enums.NodeStatus;
import com.lion.resume.dto.ApproveRequest;
import com.lion.resume.dto.GraphStatusEvent;
import com.lion.resume.dto.StartReviewRequest;
import com.lion.resume.service.GraphExportService;
import com.lion.resume.service.ReviewTaskService;
import com.lion.resume.sse.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.Map;

/**
 * 简历审查控制器：
 * - POST /api/review/start        发起审查，返回 taskId
 * - GET  /api/review/stream/{id}  SSE 订阅 Graph 节点实时状态（核心）
 * - POST /api/review/approve      HR 人工复核确认（人机协同恢复）
 * - GET  /api/review/task/{id}    任务详情（任务 + 报告 + 拓扑）
 * - GET  /api/review/topology     Graph 拓扑（前端绘制流程面板）
 */
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewTaskService reviewTaskService;
    private final GraphExportService graphExportService;
    private final SseEmitterManager sseEmitterManager;

    /**
     * 发起简历审查任务（人机协同起点）
     *
     * @param request 请求体，包含 resumeId（简历ID）与 jobDescription（职位描述）
     * @return 返回 taskId（审查任务ID）
     */
    @PostMapping("/start")
    public R<Map<String, Object>> start(@RequestBody StartReviewRequest request) {
        String taskId = reviewTaskService.startReview(request.getResumeId(),
                request.getJobDescription());
        return R.ok(Map.of("taskId", taskId));
    }

    /**
     * SSE 订阅端点：前端通过 EventSource 监听节点状态
     */
    @GetMapping(value = "/stream/{taskId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter stream(@PathVariable String taskId) {
        SseEmitter emitter = sseEmitterManager.register(taskId);
        // 连接建立后立即推送拓扑，供前端渲染流程面板
        try {
            emitter.send(SseEmitter.event()
                    .name("topology")
                    .id("topology-1")
                    .data(reviewTaskService.topology()));
        } catch (Exception ignored) {
        }
        // 补发 __START__ 完成事件：startReview 时前端尚未建立 SSE 连接，原事件已丢失
        try {
            emitter.send(SseEmitter.event()
                    .name("node")
                    .id("start-" + System.currentTimeMillis())
                    .data(GraphStatusEvent.builder()
                            .type("node")
                            .taskId(taskId)
                            .node("__START__")
                            .status(NodeStatus.COMPLETED.getValue())
                            .progress(0)
                            .message("Task started")
                            .timestamp(System.currentTimeMillis())
                            .build()));
        } catch (Exception ignored) {
        }
        return emitter;
    }

    /**
     * HR 人工复核确认（人机协同恢复）
     * <p>对自动审查结果中的评分进行人工调整并补充复核意见。
     *
     * @param request 请求体，包含 taskId（任务ID）、adjustedScores（调整后的评分）、reviewComment（复核意见）
     * @return 统一响应结果，无业务数据
     */
    @PostMapping("/approve")
    public R<Void> approve(@RequestBody ApproveRequest request) {
        reviewTaskService.approve(request.getTaskId(), request.getAdjustedScores(),
                request.getReviewComment());
        return R.ok();
    }

    /**
     * 查询审查任务详情（任务 + 报告 + 拓扑）
     *
     * @param taskId 审查任务ID
     * @return 任务详情数据
     */
    @GetMapping("/task/{taskId}")
    public R<Map<String, Object>> taskDetail(@PathVariable String taskId) {
        return R.ok(reviewTaskService.getTaskDetail(taskId));
    }

    /**
     * 历史报告列表（分页 + 关键词）
     */
    @GetMapping("/reports")
    public R<Map<String, Object>> reports(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int size,
                                          @RequestParam(required = false) String keyword) {
        return R.ok(reviewTaskService.listReports(page, size, keyword));
    }

    /**
     * 删除报告（级联删除关联任务）
     */
    @DeleteMapping("/reports/{reportId}")
    public R<Void> deleteReport(@PathVariable String reportId) {
        reviewTaskService.deleteReport(Long.valueOf(reportId));
        return R.ok();
    }

    /**
     * 获取审查 Graph 拓扑，供前端绘制流程面板
     *
     * @return 图拓扑数据（节点与边）
     */
    @GetMapping("/topology")
    public R<Map<String, Object>> topology() {
        return R.ok(reviewTaskService.topology());
    }

    /**
     * 导出完整执行图（框架 GraphRepresentation 生成）
     * <p>基于 Mermaid / PlantUML 文本，前端可静态渲染整个执行流程
     * （含主图与 HR 复核后置子图），也可用于文档/分享。
     *
     * @param format 图格式：mermaid（默认）| plantuml
     * @return {@code {main: 主图文本, post: 后置子图文本}}
     */
    @GetMapping("/graph")
    public R<Map<String, String>> graph(@RequestParam(defaultValue = "mermaid") String format) {
        return R.ok(graphExportService.graphText(format));
    }

    /**
     * 心跳/连通性检查接口
     *
     * @return 返回 pong=true 与服务端当前时间戳
     */
    @GetMapping("/ping")
    public R<Map<String, Object>> ping() {
        Map<String, Object> map = new HashMap<>();
        map.put("pong", true);
        map.put("time", System.currentTimeMillis());
        return R.ok(map);
    }
}
