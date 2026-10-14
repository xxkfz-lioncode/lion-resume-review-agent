package com.lion.resume.graph.action;

import com.alibaba.cloud.ai.graph.OverAllState;
import cn.hutool.json.JSONUtil;
import com.lion.resume.common.constant.GraphNodes;
import com.lion.resume.common.constant.GraphStateKeys;
import com.lion.resume.component.TaskProgressService;
import com.lion.resume.entity.Resume;
import com.lion.resume.graph.ResumeReviewProps;
import com.lion.resume.service.LlmService;
import com.lion.resume.service.ResumeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parse Node（解析节点）：
 * 将简历文件抽取文本，并结构化输出（mock 模式走本地规则，生产可替换为 OCR/LLM 结构化）
 */
@Slf4j
@Component
public class ParseResumeAction extends BaseTrackingAction {

    private final ResumeService resumeService;
    private final LlmService llmService;

    public ParseResumeAction(TaskProgressService progressService, ResumeReviewProps props,
                             ResumeService resumeService, LlmService llmService) {
        super(progressService, props);
        this.resumeService = resumeService;
        this.llmService = llmService;
    }

    @Override
    public String nodeId() {
        return GraphNodes.PARSE;
    }

    @Override
    protected Map<String, Object> doExecute(OverAllState state) {
        Map<String, Object> data = state.data();
        Long resumeId = Long.valueOf(String.valueOf(data.get(GraphStateKeys.RESUME_ID)));
        Resume resume = resumeService.getById(resumeId);

        if (!"PARSED".equals(resume.getParseStatus())) {
            String text = resumeService.extractText(resume);
            Map<String, Object> structured = structure(text);
            resume.setContentText(text);
            resume.setContentJson(JSONUtil.toJsonStr(structured));
            resume.setParseStatus("PARSED");
            resumeService.update(resume);
        }

        // Hutool 遇到 null 会抛 NPE，这里先判空再解析（fastjson2 原行为是返回 null）
        Map<String, Object> content = resume.getContentJson() == null
                ? null : JSONUtil.parseObj(resume.getContentJson());
        if (content == null) {
            content = new HashMap<>();
        }
        Map<String, Object> result = new HashMap<>();
        result.put(GraphStateKeys.RESUME_CONTENT, content);
        result.put(GraphStateKeys.RESUME_TEXT, resume.getContentText() == null ? "" : resume.getContentText());
        return result;
    }

    private Map<String, Object> structure(String text) {
        if (props.isMockParse()) {
            return structureByRules(text);
        }
        String system = "你是专业的简历解析引擎。将简历文本转换为结构化 JSON，"
                + "字段：name, contact, education[], skills[], projects[], experience[], summary。只输出 JSON。";
        String response = llmService.call(system, text);
        try {
            Map<String, Object> parsed = JSONUtil.parseObj(LlmService.extractJson(response));
            if (!parsed.isEmpty()) {
                return parsed;
            }
        } catch (Exception e) {
            log.warn("LLM 结构化简历失败，降级为规则解析: {}", e.getMessage());
        }
        return structureByRules(text);
    }

    /**
     * 本地规则结构化（演示模式）
     */
    private Map<String, Object> structureByRules(String text) {
        Map<String, Object> result = new HashMap<>();
        String content = text == null ? "" : text.trim();

        // 姓名：取第一行非空文本
        String name = "候选人";
        for (String line : content.split("\\n")) {
            String s = line.trim();
            if (!s.isEmpty() && !s.matches(".*(电话|邮箱|@|简历|张三|李四|王五).*")) {
                name = s.length() > 8 ? s.substring(0, 8) : s;
                break;
            }
        }
        result.put("name", name);

        Map<String, Object> contact = new HashMap<>();
        contact.put("email", content.contains("@") ? "见简历原文" : "见简历原文");
        result.put("contact", contact);

        // 技能：按关键词粗提取
        String[] skillKeywords = {"Java", "Python", "Go", "Spring", "MySQL", "Redis", "Kafka",
                "Kubernetes", "Docker", "React", "Vue", "机器学习", "微服务", "分布式"};
        List<String> skills = new ArrayList<>();
        for (String kw : skillKeywords) {
            if (content.toLowerCase().contains(kw.toLowerCase())) {
                skills.add(kw);
            }
        }
        if (skills.isEmpty()) {
            skills.add("Java");
            skills.add("Spring Boot");
            skills.add("MySQL");
        }
        result.put("skills", skills);

        result.put("education", List.of(Map.of(
                "school", "示例大学", "degree", "本科", "major", "计算机相关专业")));
        result.put("projects", List.of(Map.of(
                "name", "示例项目", "role", "开发工程师",
                "desc", "参与核心模块设计与开发，内容详见简历原文")));
        result.put("summary", "候选人简历已解析成功，详细内容见原文。");
        return result;
    }
}
