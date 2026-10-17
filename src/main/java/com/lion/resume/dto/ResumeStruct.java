package com.lion.resume.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Data;

import java.util.List;

/**
 * 简历结构化模型（供 BeanOutputConverter 生成 JSON Schema，约束 LLM 输出）
 *
 * <p>字段上的 {@code @JsonPropertyDescription} 会写进 Schema 的 description，
 * 帮助模型理解每个字段的语义，显著降低字段缺失/命名漂移的概率。
 */
@Data
public class ResumeStruct {

    @JsonPropertyDescription("候选人姓名")
    private String name;

    @JsonPropertyDescription("联系方式")
    private Contact contact;

    @JsonPropertyDescription("教育经历列表，按时间倒序")
    private List<Education> education;

    @JsonPropertyDescription("技能关键词列表，如 Java、Spring Boot、MySQL")
    private List<String> skills;

    @JsonPropertyDescription("项目经历列表")
    private List<Project> projects;

    @JsonPropertyDescription("工作经历列表，按时间倒序")
    private List<Experience> experience;

    @JsonPropertyDescription("一句话个人总结，概括从业年限、技术方向、核心优势")
    private String summary;

    @Data
    public static class Contact {
        @JsonPropertyDescription("手机号，没有则留空字符串")
        private String phone;
        @JsonPropertyDescription("邮箱，没有则留空字符串")
        private String email;
    }

    @Data
    public static class Education {
        @JsonPropertyDescription("学校名称")
        private String school;
        @JsonPropertyDescription("学历，如 本科/硕士/博士")
        private String degree;
        @JsonPropertyDescription("专业名称")
        private String major;
    }

    @Data
    public static class Project {
        @JsonPropertyDescription("项目名称")
        private String name;
        @JsonPropertyDescription("在项目中担任的角色")
        private String role;
        @JsonPropertyDescription("项目职责与成果描述")
        private String desc;
    }

    @Data
    public static class Experience {
        @JsonPropertyDescription("公司名称")
        private String company;
        @JsonPropertyDescription("职位名称")
        private String position;
        @JsonPropertyDescription("任职起止时间")
        private String period;
        @JsonPropertyDescription("主要工作内容描述")
        private String desc;
    }
}
