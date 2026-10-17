<script setup>
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { store } from '../store'

const props = defineProps({
  visible: { type: Boolean, default: false },
  taskId: { type: String, default: '' },
  pendingData: { type: Object, default: null },
  reopen: { type: Boolean, default: false }
})

const emit = defineEmits(['update:visible', 'approved'])

const scores = ref([])
const comment = ref('')
const submitting = ref(false)

watch(
  () => props.visible,
  (v) => {
    if (v && props.pendingData?.radar_data) {
      const adjusted = props.pendingData.hr_adjusted_scores || {}
      scores.value = props.pendingData.radar_data.map((d) => ({
        name: d.name,
        value: Number(adjusted[d.name] != null ? adjusted[d.name] : d.value || 0)
      }))
      comment.value = props.pendingData.hr_comment || ''
    }
  }
)

const overall = () => {
  if (!scores.value.length) return 0
  const sum = scores.value.reduce((a, s) => a + s.value, 0)
  return (sum / scores.value.length).toFixed(1)
}

const confirm = async () => {
  submitting.value = true
  try {
    const adjusted = {}
    scores.value.forEach((s) => (adjusted[s.name] = s.value))
    const url = props.reopen ? '/review/reapprove' : '/review/approve'
    await api.post(url, {
      taskId: props.taskId,
      adjustedScores: adjusted,
      reviewComment: comment.value
    })
    ElMessage.success(props.reopen ? '已更新评分，正在重新生成报告…' : '已确认复核，正在恢复报告生成…')
    emit('update:visible', false)
    emit('approved')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    :title="props.reopen ? 'HR 重新复核（更新评分并重新生成报告）' : 'HR 人工复核（Human-in-the-Loop）'"
    width="640px"
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-alert
      v-if="pendingData"
      type="warning"
      :closable="false"
      show-icon
      :title="props.reopen
        ? `当前综合评分 ${pendingData.overall_score}，可调整评分后重新生成报告`
        : `AI 综合评分 ${pendingData.overall_score} 低于及格线，请确认或调整各维度评分后继续`"
      style="margin-bottom: 16px"
    />

    <div v-if="pendingData?.diagnosis_summary" class="summary-text">
      {{ pendingData.diagnosis_summary }}
    </div>

    <div class="score-list">
      <div v-for="s in scores" :key="s.name" class="score-row">
        <span class="score-name">{{ s.name }}</span>
        <el-slider v-model="s.value" :min="0" :max="5" :step="0.1" style="flex: 1" />
        <span class="score-value">{{ s.value.toFixed(1) }}</span>
      </div>
    </div>

    <div class="overall-row">
      调整后综合评分：<b style="color: #e6a23c">{{ overall() }}</b> / 5
    </div>

    <el-input
      v-model="comment"
      type="textarea"
      :rows="3"
      placeholder="复核意见（可选），如：技术栈匹配但薪资期望偏高，建议调整评分后继续…"
      style="margin-top: 12px"
    />

    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="confirm">
        {{ props.reopen ? '更新评分并重新生成报告' : '确认评分并继续' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.summary-text {
  background: #f4f4f5;
  border-radius: 8px;
  padding: 10px 12px;
  color: #606266;
  font-size: 13px;
  margin-bottom: 12px;
}
.score-list {
  max-height: 300px;
  overflow: auto;
}
.score-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 0;
}
.score-name {
  width: 80px;
  font-size: 13px;
  color: #303133;
}
.score-value {
  width: 40px;
  text-align: right;
  font-weight: 600;
  color: #409eff;
}
.overall-row {
  margin-top: 8px;
  font-size: 13px;
  color: #606266;
}
</style>
