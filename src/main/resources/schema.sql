-- =====================================================
-- AI 招聘审查智能体 - MySQL 建表脚本
-- 执行方式: mysql -u root -p resume_review < schema.sql
-- =====================================================

-- 简历表
CREATE TABLE IF NOT EXISTS t_resume (
    id           BIGINT PRIMARY KEY COMMENT '主键(雪花ID)',
    file_name    VARCHAR(255) NOT NULL COMMENT '原始文件名',
    file_path    VARCHAR(512) COMMENT '存储路径',
    file_size    BIGINT COMMENT '文件大小(字节)',
    content_type VARCHAR(128) COMMENT '文件类型',
    content_text TEXT COMMENT '解析后的纯文本',
    content_json TEXT COMMENT '解析后的结构化JSON',
    parse_status VARCHAR(32)  DEFAULT 'PENDING' COMMENT '解析状态: PENDING/PARSED/FAILED',
    deleted      TINYINT      DEFAULT 0 COMMENT '逻辑删除: 0-正常 1-删除',
    create_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历元数据';

-- 审查任务表
CREATE TABLE IF NOT EXISTS t_review_task (
    id              VARCHAR(64) PRIMARY KEY COMMENT '任务ID(对外暴露,用于SSE订阅)',
    resume_id       BIGINT       NOT NULL COMMENT '简历ID',
    job_description TEXT COMMENT '岗位描述',
    status          VARCHAR(32)  DEFAULT 'PENDING' COMMENT '任务状态',
    progress        INT          DEFAULT 0 COMMENT '整体进度 0-100',
    current_node    VARCHAR(64) COMMENT '当前节点',
    error_message   TEXT COMMENT '失败原因',
    create_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     TIMESTAMP    DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_task_resume (resume_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历审查任务';

-- 审查报告表
CREATE TABLE IF NOT EXISTS t_review_report (
    id                 BIGINT PRIMARY KEY COMMENT '主键(雪花ID)',
    task_id            VARCHAR(64) UNIQUE NOT NULL COMMENT '任务ID',
    resume_id          BIGINT      NOT NULL COMMENT '简历ID',
    radar_data         TEXT COMMENT '六维雷达图数据JSON',
    overall_score      DECIMAL(3, 1) COMMENT '综合评分 0-5',
    summary            TEXT COMMENT '文字诊断摘要',
    detail_report      TEXT COMMENT '详细报告(Markdown)',
    needs_human_review TINYINT     DEFAULT 0 COMMENT '是否需要人工复核',
    hr_review_comment  TEXT COMMENT 'HR复核意见',
    hr_adjusted_scores TEXT COMMENT 'HR调整后的评分JSON',
    create_time        TIMESTAMP   DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        TIMESTAMP   DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_report_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审查报告';
