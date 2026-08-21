package com.lion.resume.dto;

import com.lion.resume.common.enums.NodeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Graph 节点状态事件（SSE 推送 + Redis 事件总线载荷）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphStatusEvent {

    /** 事件类型：node / task / stream */
    private String type;

    /** 任务ID */
    private String taskId;

    /** 节点ID */
    private String node;

    /** 节点状态 */
    private String status;

    /** 整体进度百分比 0-100 */
    private Integer progress;

    /** 提示信息 */
    private String message;

    /** 附加数据（如流式片段、评分结果、报告） */
    private Object data;

    /** 事件时间戳 */
    private Long timestamp;
}
