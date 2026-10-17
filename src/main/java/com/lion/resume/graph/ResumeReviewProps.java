package com.lion.resume.graph;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 简历审查流程配置
 */
@Getter
@Component
public class ResumeReviewProps {

    /** LLM 节点失败重试次数 */
    @Value("${resume.llm-retry-times:3}")
    private int llmRetryTimes;

    /** LLM 重试间隔(毫秒) */
    @Value("${resume.llm-retry-delay-ms:1500}")
    private long llmRetryDelayMs;

    /**
     * 条件边阈值：综合评分 ≥ 该值且无维度短板时，跳过人工复核直接生成报告（0-5）。
     * 设为 5.01 即等价于「所有任务都必须人工复核」。
     */
    @Value("${resume.auto-approve-score:4.0}")
    private double autoApproveScore;

    /**
     * 条件边阈值：任一维度低于该值即视为存在短板，即便综合分达标也必须人工复核（0-5）。
     */
    @Value("${resume.review-dimension-floor:3.0}")
    private double reviewDimensionFloor;
}
