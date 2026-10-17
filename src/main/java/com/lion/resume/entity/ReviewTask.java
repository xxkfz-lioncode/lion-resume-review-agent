package com.lion.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历审查任务
 */
@Data
@TableName("t_review_task")
public class ReviewTask {

    /** 任务ID（对外暴露，用于 SSE 订阅） */
    @TableId(type = IdType.INPUT)
    private String id;

    private Long resumeId;

    /** 岗位描述 */
    private String jobDescription;

    /** 操作用户ID（发起审查的用户） */
    private String userId;

    /** 任务状态: PENDING / RUNNING / NEEDS_REVIEW / COMPLETED / FAILED */
    private String status;

    /** 整体进度 0-100 */
    private Integer progress;

    /** 当前节点 */
    private String currentNode;

    /** 失败原因 */
    private String errorMessage;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
