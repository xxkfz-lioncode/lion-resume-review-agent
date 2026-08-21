package com.lion.resume.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 异步任务线程池（Graph 执行、LLM 并行调用）
 *
 * <p>两个线程池均配置 {@link ContextPropagatingTaskDecorator}：
 * 提交任务时自动捕获当前线程的 trace context（OpenTelemetry Span / Micrometer Observation），
 * 执行时恢复到工作线程，从而实现跨线程的分布式追踪链路贯穿。
 * 这样 LLM 调用无需手动创建 Span，Spring AI 自动插桩即可挂到 HTTP 请求的根 Span 下。
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "graphExecutor")
    public Executor graphExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("graph-exec-");
        // 关键：异步执行时自动传播 trace context（否则 Graph 线程的 LLM 调用成为孤儿 Span）
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.initialize();
        return executor;
    }

    @Bean(name = "llmExecutor")
    public Executor llmExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("llm-exec-");
        executor.setTaskDecorator(new ContextPropagatingTaskDecorator());
        executor.initialize();
        return executor;
    }
}
