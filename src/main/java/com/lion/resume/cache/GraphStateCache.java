package com.lion.resume.cache;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Graph 执行状态缓存（Redis）
 * 每个任务执行过程中，将 OverAllState 数据快照写入 Redis，便于：
 * 1. 中断/重启后的状态恢复
 * 2. 人机协同（Human-in-the-Loop）挂起时保存中间评分
 * 3. 多实例部署下共享执行上下文
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphStateCache {

    private static final String STATE_KEY_PREFIX = "resume:graph:state:";
    private static final String PENDING_KEY_PREFIX = "resume:graph:pending:";
    private static final Duration STATE_TTL = Duration.ofHours(24);

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 保存 Graph 状态快照
     */
    public void saveState(String taskId, Map<String, Object> stateData) {
        try {
            stringRedisTemplate.opsForValue()
                    .set(STATE_KEY_PREFIX + taskId, JSON.toJSONString(stateData), STATE_TTL);
        } catch (Exception e) {
            log.warn("保存 Graph 状态到 Redis 失败: {}", e.getMessage());
        }
    }

    /**
     * 加载 Graph 状态快照
     */
    public Map<String, Object> loadState(String taskId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(STATE_KEY_PREFIX + taskId);
            if (json == null) {
                return new HashMap<>();
            }
            return JSON.parseObject(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("加载 Graph 状态失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 保存待人工复核的评分快照
     */
    public void savePendingReview(String taskId, Object pendingData) {
        try {
            stringRedisTemplate.opsForValue()
                    .set(PENDING_KEY_PREFIX + taskId, JSON.toJSONString(pendingData), STATE_TTL);
        } catch (Exception e) {
            log.warn("保存待复核数据到 Redis 失败: {}", e.getMessage());
        }
    }

    public Map<String, Object> loadPendingReview(String taskId) {
        try {
            String json = stringRedisTemplate.opsForValue().get(PENDING_KEY_PREFIX + taskId);
            if (json == null) {
                return new HashMap<>();
            }
            return JSON.parseObject(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("加载待复核数据失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    public void deletePendingReview(String taskId) {
        try {
            stringRedisTemplate.delete(PENDING_KEY_PREFIX + taskId);
        } catch (Exception e) {
            log.warn("删除待复核数据失败: {}", e.getMessage());
        }
    }
}
