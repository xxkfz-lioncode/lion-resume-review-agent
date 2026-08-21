package com.lion.resume.common.enums;

import lombok.Getter;

/**
 * 节点执行状态（用于 SSE 推送）
 */
@Getter
public enum NodeStatus {

    RUNNING("running"),
    COMPLETED("completed"),
    ERROR("error"),
    PENDING("pending"),
    RETRYING("retrying"),
    STREAM("stream"),
    RESUMED("resumed");

    private final String value;

    NodeStatus(String value) {
        this.value = value;
    }
}
