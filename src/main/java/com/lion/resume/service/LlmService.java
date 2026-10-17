package com.lion.resume.service;

import com.lion.resume.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 千问大模型调用服务（DashScope / qwen-plus）
 * - 内置容错重试：LLM 限流、网络抖动时自动重试（指数退避）
 * - 可观测性：异步线程池已配置 {@code ContextPropagatingTaskDecorator}，
 *   trace context 自动从 HTTP 请求贯穿到 LLM 调用，无需手动创建 Span
 */
@Slf4j
@Service
public class LlmService {

    private final ChatClient chatClient;

    @Value("${resume.llm-retry-times:3}")
    private int retryTimes;

    @Value("${resume.llm-retry-delay-ms:1500}")
    private long retryDelayMs;

    public LlmService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * 同步调用（带重试）
     */
    public String call(String systemPrompt, String userPrompt) {
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
     * 结构化输出（JSON Schema 约束）：
     * 由 {@link BeanOutputConverter} 根据目标类型生成 JSON Schema 并注入提示词，
     * 模型按 schema 输出，再反序列化为目标对象，比「提示词里手写字段名」稳定得多。
     *
     * @throws RuntimeException 模型输出不符合 schema 或解析失败时抛出，由调用方决定是否降级
     */
    public <T> T callForObject(String systemPrompt, String userPrompt, Class<T> type) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(type);
        String response = call(systemPrompt + "\n\n" + converter.getFormat(), userPrompt);
        // 先剥掉可能的 markdown 代码块/前后说明文字，再按 schema 反序列化
        return converter.convert(extractJson(response));
    }

    /**
     * 流式调用（打字机效果）
     */
    public Flux<String> stream(String systemPrompt, String userPrompt) {
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
}
