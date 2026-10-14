package com.lion.resume.config;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Redisson + Graph Checkpoint 配置
 *
 * <p>为官方 Human-in-the-Loop 机制提供持久化能力：
 * {@code InterruptableAction} 中断后，Graph 状态（Checkpoint）必须落盘，
 * 才能在 HR 确认后通过 {@code CompiledGraph#updateState} + {@code resume} 恢复执行。
 *
 * <p>这里选用 {@link RedisSaver}（而非 MysqlSaver），与项目既有的 Redis 基础设施保持一致；
 * 底层强制依赖 {@link RedissonClient}，故引入 redisson 依赖。
 *
 * <p>注意：本配置仅创建独立的 {@link RedissonClient} Bean，不接管
 * {@code RedisConnectionFactory}，因此不影响 StringRedisTemplate / Sa-Token 的既有行为。
 */
@Slf4j
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    /** Checkpoint 保留时长：需覆盖 HR 复核的响应周期（默认 72 小时） */
    @Value("${resume.checkpoint-ttl-hours:72}")
    private long checkpointTtlHours;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                .setConnectTimeout(5000)
                .setTimeout(5000);
        // 空密码场景（本地无密码 Redis）：Redisson 不接受空字符串密码，必须跳过设置
        if (password != null && !password.isBlank()) {
            config.useSingleServer().setPassword(password);
        }
        log.info("Redisson 初始化: {}:{}, database={}", host, port, database);
        return Redisson.create(config);
    }

    @Bean
    public BaseCheckpointSaver checkpointSaver(RedissonClient redissonClient) {
        StateSerializer stateSerializer = new SpringAIJacksonStateSerializer(OverAllState::new);
        log.info("Graph Checkpoint 持久化: RedisSaver, ttl={}h", checkpointTtlHours);
        return RedisSaver.builder()
                .redisson(redissonClient)
                .stateSerializer(stateSerializer)
                .ttl(checkpointTtlHours, TimeUnit.HOURS)
                .build();
    }
}
