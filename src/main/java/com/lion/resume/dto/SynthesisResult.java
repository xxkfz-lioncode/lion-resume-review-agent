package com.lion.resume.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

/**
 * 综合诊断结果（供 BeanOutputConverter 生成 JSON Schema，约束 LLM 输出）
 *
 * <p>补齐评分节点未覆盖的三个维度 + 诊断摘要；
 * growth_potential 通过 {@code @JsonProperty} 保持下划线键名，与雷达图/报告侧约定一致。
 */
@Data
public class SynthesisResult {

    @JsonPropertyDescription("教育背景维度评分，0-5 的数字，保留一位小数")
    private Double education;

    @JsonPropertyDescription("沟通表达维度评分，0-5 的数字，保留一位小数")
    private Double communication;

    @JsonProperty("growth_potential")
    @JsonPropertyDescription("潜力成长维度评分，0-5 的数字，保留一位小数")
    private Double growthPotential;

    @JsonPropertyDescription("综合诊断摘要，两到三句话，概括候选人整体匹配度、核心优势与主要风险")
    private String summary;
}
