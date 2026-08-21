package com.lion.resume.config;

import com.lion.resume.sse.GraphEventSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis 发布订阅配置：跨实例转发 Graph 节点事件到本机连接的 SSE 客户端
 */
@Configuration
public class RedisPubSubConfig {

    public static final String GRAPH_EVENT_CHANNEL = "resume:graph:events";

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            GraphEventSubscriber subscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(subscriber, new ChannelTopic(GRAPH_EVENT_CHANNEL));
        return container;
    }
}
