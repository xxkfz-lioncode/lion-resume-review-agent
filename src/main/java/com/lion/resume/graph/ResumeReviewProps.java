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

    /** 边缘候选阈值：六维均分低于该值触发人工复核 */
    @Value("${resume.borderline-score:3.0}")
    private double borderlineScore;

    /** 模拟解析：本地规则结构化简历，不依赖 OCR/LLM 解析 */
    @Value("${resume.mock-parse:true}")
    private boolean mockParse;
}
