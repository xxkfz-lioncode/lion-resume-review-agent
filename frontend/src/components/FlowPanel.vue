<script setup>
import { computed, watch, ref } from 'vue'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import StatusNode from './StatusNode.vue'
import { store } from '../store'

const props = defineProps({
  topology: { type: Object, default: null }
})

const nodeTypes = { status: StatusNode }

// 节点布局坐标（流程管线样式）
const LAYOUT = {
  __START__: { x: 200, y: 0 },
  parse_resume: { x: 200, y: 140 },
  skill_scoring: { x: -120, y: 300 },
  project_scoring: { x: 200, y: 300 },
  culture_scoring: { x: 520, y: 300 },
  synthesis: { x: 200, y: 460 },
  human_review: { x: 200, y: 600 },
  generate_report: { x: 200, y: 740 },
  __END__: { x: 200, y: 880 }
}

const nodes = computed(() => {
  if (!props.topology?.nodes) return []
  return props.topology.nodes.map((n) => {
    const state = store.nodeStates[n.id] || {}
    const pos = LAYOUT[n.id] || { x: 200, y: 100 }
    return {
      id: n.id,
      type: 'status',
      position: pos,
      data: {
        id: n.id,
        label: n.label,
        status: state.status || '',
        message: state.message || n.description || '',
        statusText: state.status ? undefined : '等待'
      }
    }
  })
})

const edges = computed(() => {
  if (!props.topology?.edges) return []
  return props.topology.edges.map(([source, target]) => ({
    id: `${source}-${target}`,
    source,
    target,
    type: 'smoothstep',
    animated: isActiveEdge(source, target),
    style: { stroke: isActiveEdge(source, target) ? '#409eff' : '#c0c4cc', strokeWidth: 2 },
    markerEnd: { type: 'arrow', color: isActiveEdge(source, target) ? '#409eff' : '#c0c4cc', width: 10, height: 10 }
  }))
})

function isActiveEdge(source, target) {
  const s = store.nodeStates[source]?.status
  return s === 'running' || s === 'completed' || s === 'stream'
}

const { fitView } = useVueFlow()
watch(
  () => props.topology,
  () => setTimeout(() => fitView({ padding: 0.2, duration: 300 }), 100)
)
</script>

<template>
  <div class="flow-panel">
    <div class="panel-header">
      <span class="panel-title">流程追踪面板</span>
      <el-tag v-if="store.taskId" size="small" type="info">task: {{ store.taskId.slice(0, 8) }}</el-tag>
    </div>
    <div class="progress-row">
      <div class="progress-bar" style="flex: 1">
        <div class="progress-fill" :style="{ width: store.progress + '%' }"></div>
      </div>
      <span class="progress-text">{{ store.progress }}%</span>
    </div>
    <div class="flow-canvas">
      <VueFlow
        v-if="nodes.length"
        :nodes="nodes"
        :edges="edges"
        :node-types="nodeTypes"
        :fit-view-on-init="true"
        :min-zoom="0.3"
        :max-zoom="1.5"
        :default-viewport="{ zoom: 0.8 }"
      >
        <Background pattern-color="#e5e7eb" :gap="20" />
        <Controls />
      </VueFlow>
      <el-empty v-else description="等待创建审查任务…" />
    </div>
  </div>
</template>

<style scoped>
.flow-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  padding: 16px;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.progress-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}
.progress-text {
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
  min-width: 40px;
}
.flow-canvas {
  flex: 1;
  min-height: 400px;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  overflow: hidden;
  background: #fafbfc;
}
</style>
