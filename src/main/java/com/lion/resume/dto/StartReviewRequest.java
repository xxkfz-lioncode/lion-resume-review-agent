package com.lion.resume.dto;

import lombok.Data;

/**
 * 发起审查请求
 */
@Data
public class StartReviewRequest {

    /** 简历ID（上传后返回的字符串） */
    private String resumeId;

    /** 岗位描述 */
    private String jobDescription;
}
