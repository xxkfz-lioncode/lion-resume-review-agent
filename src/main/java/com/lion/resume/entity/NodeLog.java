package com.lion.resume.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 节点执行日志（后续可扩展：耗时分析、失败告警、审计）。
 *
 * <p>只记录元数据，不存 OverAllState 大字段（简历原文、报告正文），避免撑爆表。
 */
@Data
@TableName("t_graph_node_log")
public class NodeLog {

    /** 主键 */
    private String id;

    /** 业务任务ID */
    private String taskId;

    /** 操作用户ID */
    private String userId;

    /** Graph threadId */
    private String threadId;

    /** 节点ID */
    private String nodeId;

    /** 尝试次数（重试累加，无重试为 1） */
    private Integer attempt;

    /** 状态：RUNNING / COMPLETED / ERROR */
    private String status;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /** 耗时(ms) */
    private Long durationMs;

    /** 失败原因（成功为 null） */
    private String errorMsg;

    private LocalDateTime createTime;
}
