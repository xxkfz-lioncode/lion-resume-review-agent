package com.lion.resume.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 单维度评分结果（供 BeanOutputConverter 生成 JSON Schema，约束 LLM 输出）
 *
 * <p>dimensions 用 Map 承接，键由各子类的 {@code dimensionKey()} 决定，
 * 这样 Schema 是通用的，不需要为每个维度单独定义类型。
 */
@Data
public class ScoreResult {

    @JsonPropertyDescription("该维度的综合评分，0-5 的数字，保留一位小数")
    private Double score;

    @JsonPropertyDescription("子维度评分对象，键为维度标识（如 skill_match / project_experience / culture_fit），值为 0-5 的数字")
    private Map<String, Double> dimensions;

    @JsonPropertyDescription("简短分析，一到两句话说明评分依据")
    private String analysis;

    @JsonPropertyDescription("评分理由列表，每条一句话，列出 2-4 条")
    private List<String> reasons;
}
