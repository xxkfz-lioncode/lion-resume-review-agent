package com.lion.resume.sse;

import com.alibaba.fastjson2.JSON;
import com.lion.resume.dto.GraphStatusEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Graph 事件订阅器：收到 Redis 广播后转发给本机连接的 SSE 客户端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphEventSubscriber implements MessageListener {

    private final SseEmitterManager emitterManager;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            GraphStatusEvent event = JSON.parseObject(new String(message.getBody()),
                    GraphStatusEvent.class);
            boolean sent = emitterManager.send(event.getTaskId(), event);
            if (!sent) {
                log.debug("本机无 [{}] 的 SSE 客户端，忽略事件: {} {}", event.getTaskId(),
                        event.getNode(), event.getStatus());
            }
        } catch (Exception e) {
            log.warn("处理 Graph 事件失败: {}", e.getMessage());
        }
    }
}
