package com.lion.resume.dto;

import lombok.Data;

import java.util.Map;

/**
 * HR 人工复核请求
 */
@Data
public class ApproveRequest {

    /** 任务ID */
    private String taskId;

    /** HR 修改后的六维评分（可只传被修改的维度） */
    private Map<String, Double> adjustedScores;

    /** HR 复核意见 */
    private String reviewComment;
}
