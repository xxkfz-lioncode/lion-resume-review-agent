package com.lion.resume.config;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.CreateOption;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.concurrent.TimeUnit;

/**
 * Graph Checkpoint 持久化配置（可切换存储）
 * 支持两种使用方式：redis、mysql
 * 默认使用 redis 存储，如果需要使用 mysql 存储，需要在 application.yml 中配置 resume.checkpoint-store=mysql
 */
@Slf4j
@Configuration
public class CheckpointSaverConfig {

    /** Checkpoint 保留时长：需覆盖 HR 复核的响应周期（默认 72 小时），仅 Redis 模式生效 */
    @Value("${resume.checkpoint-ttl-hours:72}")
    private long checkpointTtlHours;

    @Bean
    @ConditionalOnProperty(name = "resume.checkpoint-store", havingValue = "redis", matchIfMissing = true)
    public BaseCheckpointSaver redisCheckpointSaver(RedissonClient redissonClient) {
        StateSerializer stateSerializer = new SpringAIJacksonStateSerializer(OverAllState::new);
        log.info("Graph Checkpoint 持久化: RedisSaver, ttl={}h", checkpointTtlHours);
        return RedisSaver.builder()
                .redisson(redissonClient)
                .stateSerializer(stateSerializer)
                .ttl(checkpointTtlHours, TimeUnit.HOURS)
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "resume.checkpoint-store", havingValue = "mysql")
    public BaseCheckpointSaver mysqlCheckpointSaver(DataSource dataSource) {
        StateSerializer stateSerializer = new SpringAIJacksonStateSerializer(OverAllState::new);
        // CreateOption.CREATE_IF_NOT_EXISTS：启动时自动建 GRAPH_THREAD / GRAPH_CHECKPOINT 表（库需提前建好）
        log.info("Graph Checkpoint 持久化: MysqlSaver（自动建表 GRAPH_THREAD / GRAPH_CHECKPOINT）");
        return MysqlSaver.builder()
                .dataSource(dataSource)
                .stateSerializer(stateSerializer)
                // CREATE_NONE：什么都不做，表必须你自己提前建好，否则运行时报 "表不存在"；
                // CREATE_IF_NOT_EXISTS：推荐，首次启动自动建表，重启不会丢数据；
                // CREATE_OR_REPLACE：先 DROP 再 CREATE，每次启动都会清空历史 checkpoint（演示/测试用，生产别用）。
                .createOption(CreateOption.CREATE_IF_NOT_EXISTS)
                .build();
    }
}
