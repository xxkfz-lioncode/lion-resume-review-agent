package com.lion.resume.graph.action;

import cn.hutool.json.JSONObject;
import com.alibaba.cloud.ai.graph.OverAllState;
import cn.hutool.json.JSONUtil;
import com.lion.resume.common.constant.GraphNodes;
import com.lion.resume.common.constant.GraphStateKeys;
import com.lion.resume.common.exception.BizException;
import com.lion.resume.component.TaskProgressService;
import com.lion.resume.dto.ResumeStruct;
import com.lion.resume.entity.Resume;
import com.lion.resume.graph.ResumeReviewProps;
import com.lion.resume.service.LlmService;
import com.lion.resume.service.ResumeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;


import java.util.HashMap;
import java.util.Map;

/**
 * Parse Node（解析节点）：
 * 将简历文件抽取文本，并调用 LLM 按 JSON Schema 结构化输出（失败则节点重试，最终失败即任务失败）
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

        // contentJson 与 parseStatus 是同一处写入的，走到这里必然非空
        JSONObject content = JSONUtil.parseObj(resume.getContentJson());
        Map<String, Object> result = new HashMap<>();
        result.put(GraphStateKeys.RESUME_CONTENT, content);
        result.put(GraphStateKeys.RESUME_TEXT, resume.getContentText());
        return result;
    }

    private Map<String, Object> structure(String text) {
        // JSON Schema 约束输出：字段名/类型由 ResumeStruct 决定，避免模型自由发挥导致字段漂移
        String system = "你是专业的简历解析引擎。从简历原文中抽取信息，"
                + "严格按给定的 JSON Schema 输出；缺失的字段用空字符串或空数组，不要编造内容。";
        ResumeStruct struct = llmService.callForObject(system, text, ResumeStruct.class);
        if (struct == null) {
            throw new BizException(500, "简历结构化失败：模型未返回有效内容");
        }
        return JSONUtil.parseObj(JSONUtil.toJsonStr(struct));
    }
}
