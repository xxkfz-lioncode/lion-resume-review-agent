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
}
