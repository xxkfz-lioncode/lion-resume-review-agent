package com.lion.resume.sse;

import com.alibaba.fastjson2.JSON;
import com.lion.resume.config.RedisPubSubConfig;
import com.lion.resume.dto.GraphStatusEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Graph 事件发布器：
 * 1. 优先通过 Redis Pub/Sub 广播（支持多实例横向扩展，任意实例的客户端都能收到）
 * 2. Redis 不可用时降级为本机直推
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphEventPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final SseEmitterManager emitterManager;

    public void publish(GraphStatusEvent event) {
        boolean published = false;
        try {
            stringRedisTemplate.convertAndSend(RedisPubSubConfig.GRAPH_EVENT_CHANNEL,
                    JSON.toJSONString(event));
            published = true;
        } catch (Exception e) {
            log.warn("Redis 广播 Graph 事件失败，降级为本机直推: {}", e.getMessage());
        }
        if (!published) {
            emitterManager.send(event.getTaskId(), event);
        }
    }
}
