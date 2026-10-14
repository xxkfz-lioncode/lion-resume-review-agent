package com.lion.resume.sse;

import cn.hutool.json.JSONUtil;
import com.lion.resume.dto.GraphStatusEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 连接管理器：维护 taskId -> SseEmitter 的映射
 */
@Slf4j
@Component
public class SseEmitterManager {

    private static final long TIMEOUT = 60 * 60 * 1000L; // 1 小时

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * 注册 SSE 连接。若该 taskId 已有连接（旧连接未释放），则先完成后旧连接。
     */
    public SseEmitter register(String taskId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);
        emitter.onCompletion(() -> emitters.remove(taskId, emitter));
        emitter.onTimeout(() -> emitters.remove(taskId, emitter));
        emitter.onError(e -> emitters.remove(taskId, emitter));
        SseEmitter old = emitters.put(taskId, emitter);
        if (old != null) {
            try {
                old.complete();
            } catch (Exception ignored) {
            }
        }
        return emitter;
    }

    /**
     * 向指定任务的 SSE 通道推送事件
     *
     * @return true 表示推送成功（客户端在线）
     */
    public boolean send(String taskId, GraphStatusEvent event) {
        SseEmitter emitter = emitters.get(taskId);
        if (emitter == null) {
            return false;
        }
        try {
            Map<String, Object> map = JSONUtil.parseObj(JSONUtil.toJsonStr(event));
            emitter.send(SseEmitter.event()
                    .name(event.getType() == null ? "message" : event.getType())
                    .id(String.valueOf(System.currentTimeMillis()))
                    .data(map));
            return true;
        } catch (IOException e) {
            log.warn("SSE 推送失败, taskId={}, 客户端可能已断开: {}", taskId, e.getMessage());
            emitters.remove(taskId, emitter);
            return false;
        } catch (Exception e) {
            log.error("SSE 推送异常, taskId={}", taskId, e);
            return false;
        }
    }

    public void complete(String taskId) {
        SseEmitter emitter = emitters.remove(taskId);
        if (emitter != null) {
            emitter.complete();
        }
    }
}
