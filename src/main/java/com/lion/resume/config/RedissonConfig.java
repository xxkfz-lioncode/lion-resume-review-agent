package com.lion.resume.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 客户端配置
 *
 * <p>仅在 Checkpoint 存储选用 Redis 时创建（默认），选用 MySQL 时不连 Redis。
 * 具体 Saver 的选择见 CheckpointSaverConfig。
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
}
