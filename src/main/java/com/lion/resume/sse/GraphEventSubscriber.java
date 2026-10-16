package com.lion.resume.sse;

import cn.hutool.json.JSONUtil;
import com.lion.resume.dto.GraphStatusEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Graph event subscriber: forwards Redis broadcast events to local SSE clients.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphEventSubscriber implements MessageListener {

    private final SseEmitterManager emitterManager;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            // Must decode with UTF-8; platform default (GBK on Windows) will corrupt Chinese characters.
            GraphStatusEvent event = JSONUtil.toBean(new String(message.getBody(), StandardCharsets.UTF_8),
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
