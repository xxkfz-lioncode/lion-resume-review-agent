package com.lion.resume.service;

import com.lion.resume.common.exception.BizException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 千问大模型调用服务（DashScope / qwen-plus）
 * - 内置容错重试：LLM 限流、网络抖动时自动重试（指数退避）
 * - 演示模式：未配置真实 API Key 时（占位符）自动降级为模拟响应，
 *   保证整条 Graph 工作流在无 Key 环境下也能完整跑通，便于本地体验
 * - 可观测性：异步线程池已配置 {@code ContextPropagatingTaskDecorator}，
 *   trace context 自动从 HTTP 请求贯穿到 LLM 调用，无需手动创建 Span
 */
@Slf4j
@Service
public class LlmService {

    private static final String PLACEHOLDER_KEY = "sk-your-dashscope-api-key";

    private final ChatClient chatClient;

    @Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @Value("${resume.llm-retry-times:3}")
    private int retryTimes;

    @Value("${resume.llm-retry-delay-ms:1500}")
    private long retryDelayMs;

    /** 演示模式标志 */
    private boolean mockMode;

    public LlmService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @PostConstruct
    public void init() {
        this.mockMode = apiKey == null || apiKey.isBlank() || PLACEHOLDER_KEY.equals(apiKey.trim());
        if (mockMode) {
            log.warn("未检测到真实 DASHSCOPE_API_KEY，已进入【演示模式】：LLM 节点将返回模拟数据。"
                    + "配置环境变量 DASHSCOPE_API_KEY 后即可接入真实千问大模型。");
        }
    }

    /**
     * 同步调用（带重试 / 演示模式降级）
     */
    public String call(String systemPrompt, String userPrompt) {
        if (mockMode) {
            return mockResponse(systemPrompt, userPrompt);
        }

        int attempt = 0;
        while (true) {
            try {
                String content = chatClient.prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .call()
                        .content();
                log.debug("LLM 调用成功, 长度={}", content == null ? 0 : content.length());
                return content;
            } catch (Exception e) {
                attempt++;
                log.warn("LLM 调用失败(第{}次): {}", attempt, e.getMessage());
                if (attempt >= retryTimes) {
                    throw new BizException(500, "千问大模型调用失败: " + e.getMessage());
                }
                try {
                    Thread.sleep(retryDelayMs * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new BizException(500, "LLM 重试被中断");
                }
            }
        }
    }

    /**
     * 流式调用（打字机效果）
     */
    public Flux<String> stream(String systemPrompt, String userPrompt) {
        if (mockMode) {
            String text = mockResponse(systemPrompt, userPrompt);
            return Flux.fromIterable(java.util.Arrays.asList(
                    text.split("(?<=[。；\\n])")));
        }
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .stream()
                .content();
    }

    /**
     * 提取 JSON 块（容错：兼容模型输出中夹杂的说明文字 / markdown 代码块）
     */
    public static String extractJson(String content) {
        if (content == null) {
            return "{}";
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return content.substring(start, end + 1);
        }
        return "{}";
    }

    /**
     * 演示模式模拟响应：根据提示词关键词返回与真实模型同构的结果
     */
    private String mockResponse(String systemPrompt, String userPrompt) {
        String p = systemPrompt + userPrompt;
        // 注意匹配顺序：结构化/报告/六维 优先，避免简历原文中的关键词误命中
        if (p.contains("结构化") || p.contains("解析") || p.contains("OCR")) {
            return "{\"name\":\"张伟\",\"contact\":{\"phone\":\"138****8888\",\"email\":\"zhangwei@example.com\"},"
                    + "\"education\":[{\"school\":\"浙江大学\",\"degree\":\"硕士\",\"major\":\"计算机科学与技术\"}],"
                    + "\"skills\":[\"Java\",\"Spring Boot\",\"MyBatis\",\"MySQL\",\"Redis\",\"Kubernetes\",\"Kafka\"],"
                    + "\"projects\":[{\"name\":\"智能营销中台\",\"role\":\"后端负责人\",\"desc\":\"负责订单与用户系统的微服务架构设计与实现，支撑日均千万级请求\"}],"
                    + "\"summary\":\"8年后端开发经验，专注高并发微服务架构，具备大型电商系统落地经验\"}";
        }
        if (p.contains("报告") || p.contains("markdown")) {
            return "# 简历审查报告\n\n## 综合评分\n\n**4.2 / 5.0**，候选人整体表现优秀，建议进入下一轮面试。\n\n"
                    + "## 六维分析\n\n- 技能匹配：4.3/5，与岗位高度匹配\n- 项目经验：4.1/5，含金量较高\n"
                    + "- 文化契合：4.5/5，契合度良好\n- 教育背景：3.8/5\n- 沟通表达：4.2/5\n- 潜力成长：4.4/5\n\n"
                    + "## 优势\n\n1. 8年后端开发经验，微服务架构能力强\n2. 主导过大型系统建设，业务理解深入\n\n"
                    + "## 建议\n\n建议安排技术面与团队面，重点考察其系统设计能力。";
        }
        if (p.contains("六维") || p.contains("radar") || p.contains("综合诊断")) {
            return "{\"education\":3.8,\"communication\":4.2,\"growth_potential\":4.4,"
                    + "\"summary\":\"该候选人整体素质优秀，技术能力强，项目经验扎实，成长潜力突出，建议重点跟进。\"}";
        }
        if (p.contains("技能") || p.contains("skill")) {
            return "{\"score\":4.3,\"dimensions\":{\"skill_match\":4.3},\"analysis\":\"候选人核心技能与岗位要求高度匹配，\""
                    + ",\"reasons\":[\"熟练掌握 Java/Spring 全家桶\",\"具备高并发微服务实战经验\",\"数据库与中间件技能扎实\"]}";
        }
        if (p.contains("项目") || p.contains("experience")) {
            return "{\"score\":4.1,\"dimensions\":{\"project_experience\":4.1},\"analysis\":\"项目经验含金量较高，主导过核心系统建设，\""
                    + ",\"reasons\":[\"主导核心业务系统架构设计\",\"项目规模与复杂度匹配岗位要求\",\"具备量化业务成果输出\"]}";
        }
        if (p.contains("文化") || p.contains("culture")) {
            return "{\"score\":4.5,\"dimensions\":{\"culture_fit\":4.5},\"analysis\":\"候选人与团队文化契合度良好，\""
                    + ",\"reasons\":[\"价值观与公司契合度高\",\"具备良好的团队协作与沟通记录\",\"适应快节奏迭代开发模式\"]}";
        }
        return "{\"score\":4.0,\"dimensions\":{\"skill_match\":4.0},\"analysis\":\"模拟数据\",\"reasons\":[\"演示模式\"]}";
    }
}
