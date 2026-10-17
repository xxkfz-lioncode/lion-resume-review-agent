package com.lion.resume.graph.action;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.action.InterruptableAction;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.lion.resume.common.constant.GraphNodes;
import com.lion.resume.common.constant.GraphStateKeys;
import com.lion.resume.common.enums.NodeStatus;
import com.lion.resume.component.TaskProgressService;
import com.lion.resume.dto.GraphStatusEvent;
import com.lion.resume.graph.ResumeReviewProps;
import com.lion.resume.sse.GraphEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Human-in-the-Loop（人机协同）节点 —— 基于官方中断机制（方案 B）。
 *
 * <p>本节点「无条件」挂起：任何任务都必须经 HR 复核确认后才进入 generate_report，
 * 由 {@link InterruptableAction#interruptAfter} 始终返回 {@link InterruptionMetadata} 实现。
 *
 * <p>恢复链路：HR 确认评分 → {@code ReviewTaskService#approve}
 * → {@code CompiledGraph#updateState} 写入调整后的状态
 * → {@code RunnableConfig#resume} 从中断处继续执行。
 *
 * <p>前置条件：图必须配置 {@code BaseCheckpointSaver}（见 RedissonConfig），
 * 否则中断后无法持久化与恢复。
 */
@Slf4j
@Component
public class HumanReviewAction extends BaseTrackingAction implements InterruptableAction {

    private final GraphEventPublisher eventPublisher;

    public HumanReviewAction(TaskProgressService progressService, ResumeReviewProps props,
                             GraphEventPublisher eventPublisher) {
        super(progressService, props);
        this.eventPublisher = eventPublisher;
    }

    @Override
    public String nodeId() {
        return GraphNodes.HUMAN_REVIEW;
    }

    @Override
    protected Map<String, Object> doExecute(OverAllState state, RunnableConfig config) {
        Map<String, Object> data = state.data();
        // 从 RunnableConfig 取任务ID（threadId = taskId），不再依赖 State 里的 task_id
        String taskId = resolveTaskId(state, config);
        double overall = AbstractScoringAction.toScore(data.get(GraphStateKeys.OVERALL_SCORE), 3.0);

        // 无条件进入人工复核：先落库状态，再推送 pending 事件（真正的挂起在 interruptAfter）
        progressService.updateTaskStatus(taskId, "NEEDS_REVIEW");
        // 无需再手动保存快照：Graph 中断时框架会自动写入 Checkpoint（RedisSaver）

        Map<String, Object> pushData = new HashMap<>();
        pushData.put(GraphStateKeys.RADAR_DATA, data.get(GraphStateKeys.RADAR_DATA));
        pushData.put(GraphStateKeys.OVERALL_SCORE, overall);
        pushData.put(GraphStateKeys.DIAGNOSIS_SUMMARY, data.getOrDefault(GraphStateKeys.DIAGNOSIS_SUMMARY, ""));

        eventPublisher.publish(GraphStatusEvent.builder()
                .type("node")
                .taskId(taskId)
                .node(nodeId())
                .status(NodeStatus.PENDING.getValue())
                .progress(80)
                .message("等待 HR 人工复核评分")
                .data(pushData)
                .timestamp(System.currentTimeMillis())
                .build());

        log.info("人工复核触发: taskId={}, overall={}", taskId, overall);

        // 返回值合并进 OverAllState：needs_human_review 供报告生成判断，review_reason 给前端展示
        Map<String, Object> result = new HashMap<>();
        result.put(GraphStateKeys.NEEDS_HUMAN_REVIEW, true);
        result.put(GraphStateKeys.REVIEW_REASON, String.format("AI 综合评分 %.1f，等待 HR 复核确认", overall));
        return result;
    }

    /** 挂起时不发送 completed，前端展示为"待复核"高亮 */
    @Override
    protected boolean emitCompleteEvent(Map<String, Object> result) {
        return false;
    }

    /**
     * 节点执行「前」中断判定（接口抽象方法，必须实现）。
     *
     * <p>本节点需要先拿到评分结果并推送给前端，故执行前一律不中断。
     */
    @Override
    public Optional<InterruptionMetadata> interrupt(String nodeId, OverAllState state, RunnableConfig config) {
        return Optional.empty();
    }

    /**
     * 节点执行「后」无条件中断（官方 HIL 机制）。
     *
     * <p>始终返回 {@link InterruptionMetadata} → Graph 在 human_review 后必定挂起，
     * 等待 {@code ReviewTaskService#approve} 恢复后才流向 generate_report。
     */
    @Override
    public Optional<InterruptionMetadata> interruptAfter(String nodeId, OverAllState state, Map<String, Object> result,
                                                         RunnableConfig config) {
        return Optional.of(InterruptionMetadata.builder(nodeId, state).build());
    }
}
