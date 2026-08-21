package com.lion.resume.common.enums;

import lombok.Getter;

/**
 * 审查任务状态
 */
@Getter
public enum TaskStatus {

    PENDING("待审查"),
    RUNNING("审查中"),
    NEEDS_REVIEW("待人工复核"),
    COMPLETED("已完成"),
    FAILED("失败");

    private final String desc;

    TaskStatus(String desc) {
        this.desc = desc;
    }
}
