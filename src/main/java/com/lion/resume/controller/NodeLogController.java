package com.lion.resume.controller;

import com.lion.resume.common.R;
import com.lion.resume.entity.NodeLog;
import com.lion.resume.service.NodeLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 节点执行日志查询接口（「日志查询」页面）
 */
@RestController
@RequestMapping("/api/review/node-logs")
public class NodeLogController {

    private final NodeLogService nodeLogService;

    public NodeLogController(NodeLogService nodeLogService) {
        this.nodeLogService = nodeLogService;
    }

    /**
     * 按条件查询节点执行日志（含耗时、状态、错误）。
     */
    @GetMapping
    public R<List<NodeLog>> query(@RequestParam(required = false) String taskId,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(required = false) String nodeId,
                                  @RequestParam(defaultValue = "200") int limit) {
        return R.ok(nodeLogService.query(taskId, status, nodeId, limit));
    }

    /**
     * 按任务聚合的概览列表（下拉选择任务用）。
     */
    @GetMapping("/tasks")
    public R<List<Map<String, Object>>> tasks(@RequestParam(defaultValue = "20") int limit) {
        return R.ok(nodeLogService.listTaskSummary(limit));
    }
}
