package com.lion.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 审查报告
 */
@Data
@TableName("t_review_report")
public class ReviewReport {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String taskId;

    private Long resumeId;

    /** 六维雷达图数据 JSON */
    private String radarData;

    /** 综合评分 0-5 */
    private BigDecimal overallScore;

    /** 文字诊断摘要 */
    private String summary;

    /** 详细报告（Markdown） */
    private String detailReport;

    /** 是否需要人工复核 */
    private Boolean needsHumanReview;

    /** HR 复核意见 */
    private String hrReviewComment;

    /** HR 调整后的评分 JSON */
    private String hrAdjustedScores;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
