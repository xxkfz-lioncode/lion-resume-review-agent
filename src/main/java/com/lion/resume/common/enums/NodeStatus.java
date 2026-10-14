package com.lion.resume.common.enums;

import lombok.Getter;

/**
 * 节点执行状态（SSE 推送给前端的 {@code status} 字段取值）。
 *
 * <p>与 {@link TaskStatus} 是两个维度：本枚举描述「单个图节点」的瞬时状态，
 * {@code TaskStatus} 描述「整个审查任务」的生命周期，两者的 PENDING / COMPLETED 含义不同。
 *
 * <p>一般节点流转：{@link #RUNNING} → ({@link #STREAM} / {@link #RETRYING}) → {@link #COMPLETED} 或 {@link #ERROR}。
 * 人工复核节点特殊：{@link #PENDING} 挂起等待 HR → HR 确认后 {@link #RESUMED} 恢复。
 */
@Getter
public enum NodeStatus {

    /** 节点开始执行 */
    RUNNING("running"),

    /** 节点执行成功 */
    COMPLETED("completed"),

    /** 节点执行失败（含重试耗尽） */
    ERROR("error"),

    /** 挂起等待人工介入，前端高亮为「待复核」并弹出评分确认框 */
    PENDING("pending"),

    /** LLM 调用失败正在重试 */
    RETRYING("retrying"),

    /** 流式增量输出中（如报告正文逐段生成、OpenAI 风格增量） */
    STREAM("stream"),

    /** HR 复核完成，Graph 从中断处恢复继续执行 */
    RESUMED("resumed");

    /** 推送给前端的状态值，前端按此字符串判断样式 */
    private final String value;

    NodeStatus(String value) {
        this.value = value;
    }
}
