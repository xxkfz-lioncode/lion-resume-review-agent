package com.lion.resume.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lion.resume.entity.NodeLog;
import com.lion.resume.mapper.NodeLogMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 节点执行日志查询服务（「日志查询」页面数据源）
 */
@Service
public class NodeLogService {

    private final NodeLogMapper nodeLogMapper;

    public NodeLogService(NodeLogMapper nodeLogMapper) {
        this.nodeLogMapper = nodeLogMapper;
    }

    /**
     * 按条件查询节点执行日志，按开始时间倒序。
     *
     * @param taskId 任务ID（可选）
     * @param status 状态（可选）：completed / error / pending
     * @param nodeId 节点ID（可选）
     * @param limit  最大条数，上限 1000
     */
    public List<NodeLog> query(String taskId, String status, String nodeId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        LambdaQueryWrapper<NodeLog> qw = new LambdaQueryWrapper<>();
        if (taskId != null && !taskId.isBlank()) {
            qw.eq(NodeLog::getTaskId, taskId);
        }
        if (status != null && !status.isBlank()) {
            qw.eq(NodeLog::getStatus, status);
        }
        if (nodeId != null && !nodeId.isBlank()) {
            qw.eq(NodeLog::getNodeId, nodeId);
        }
        qw.orderByDesc(NodeLog::getStartTime);
        qw.last("LIMIT " + safeLimit);
        return nodeLogMapper.selectList(qw);
    }

    /**
     * 按任务聚合的概览列表（供页面下拉选择任务）。
     */
    public List<Map<String, Object>> listTaskSummary(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return nodeLogMapper.selectTaskSummary(safeLimit);
    }
}
