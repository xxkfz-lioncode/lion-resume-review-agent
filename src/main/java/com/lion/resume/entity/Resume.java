package com.lion.resume.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历元数据
 */
@Data
@TableName("t_resume")
public class Resume {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 原始文件名 */
    private String fileName;

    /** 存储路径 */
    private String filePath;

    /** 文件大小(字节) */
    private Long fileSize;

    /** 文件类型 */
    private String contentType;

    /** 解析后的纯文本 */
    private String contentText;

    /** 解析后的结构化 JSON */
    private String contentJson;

    /** 解析状态: PENDING / PARSED / FAILED */
    private String parseStatus;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
