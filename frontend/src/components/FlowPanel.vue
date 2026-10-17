<script setup>
import { computed, watch, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { VueFlow, useVueFlow } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import mermaid from 'mermaid'
import StatusNode from './StatusNode.vue'
import { store } from '../store'
import api from '../api'

const props = defineProps({
  topology: { type: Object, default: null }
})

const nodeTypes = { status: StatusNode }

// ---------- 视图切换：实时追踪 | 完整流程图 ----------
const viewMode = ref('live')
const graphLoading = ref(false)
const graphError = ref('')
const graphMainSvg = ref('')
const graphPostSvg = ref('')

mermaid.initialize({
  startOnLoad: false,
  theme: 'neutral',
  securityLevel: 'loose',
  flowchart: { curve: 'basis' }
})

let graphSeq = 0
async function renderMermaid(text) {
  const id = `fullgraph-${Date.now()}-${graphSeq++}`
  const { svg } = await mermaid.render(id, text)
  return svg
}

async function loadFullGraph() {
  graphLoading.value = true
  graphError.value = ''
  try {
    const { main, post } = await api.get('/review/graph?format=mermaid')
    graphMainSvg.value = await renderMermaid(main)
    graphPostSvg.value = post ? await renderMermaid(post) : ''
  } catch (e) {
    graphError.value = e.message
    ElMessage.error('流程图加载失败: ' + e.message)
  } finally {
    graphLoading.value = false
  }
}

watch(viewMode, (v) => {
  if (v === 'full' && !graphMainSvg.value) loadFullGraph()
})

// ---------- 实时追踪视图（Vue Flow） ----------
// 节点布局坐标（流程管线样式）
const LAYOUT = {
  __START__: { x: 200, y: 0 },
  parse_resume: { x: 200, y: 140 },
  skill_scoring: { x: -120, y: 300 },
  project_scoring: { x: 200, y: 300 },
  culture_scoring: { x: 520, y: 300 },
  synthesis: { x: 200, y: 460 },
  // human_review 右移，给 synthesis → generate_report 的「免复核」分支让出通道
  human_review: { x: 400, y: 600 },
  generate_report: { x: 200, y: 760 },
  __END__: { x: 200, y: 900 }
}

const nodes = computed(() => {
  if (!props.topology?.nodes) return []
  // 任务已完成但节点从未执行 → 说明被条件边跳过（如评分达标免人工复核）
  const finished = store.taskStatus === 'COMPLETED'
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
        statusText: state.status
          ? undefined
          : (finished ? '已跳过' : '等待')
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

async function onNodeClick({ node }) {
  if (node.id !== 'human_review') return
  if (!store.taskId) return
  if (store.taskStatus !== 'COMPLETED' && store.taskStatus !== 'NEEDS_REVIEW') return

  // 当前正在待复核：直接打开已有弹窗数据
  if (store.taskStatus === 'NEEDS_REVIEW' && store.pendingReview) {
    store.reviewVisible = true
    return
  }

  // 已完成的任务：从后端重新加载复核数据
  try {
    const res = await api.post(`/review/reopen/${store.taskId}`)
    store.pendingReview = res.data
    store.reviewVisible = true
  } catch (e) {
    ElMessage.error('打开复核窗口失败: ' + e.message)
  }
}
</script>

<template>
  <div class="flow-panel">
    <div class="panel-header">
      <span class="panel-title">流程追踪面板</span>
      <el-radio-group v-model="viewMode" size="small">
        <el-radio-button value="live">实时追踪</el-radio-button>
        <el-radio-button value="full">完整流程图</el-radio-button>
      </el-radio-group>
      <el-tag v-if="store.taskId" size="small" type="info">task: {{ store.taskId.slice(0, 8) }}</el-tag>
    </div>

    <!-- 实时追踪视图：SSE 驱动节点状态高亮 -->
    <template v-if="viewMode === 'live'">
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
        @node-click="onNodeClick"
        >
          <Background pattern-color="#e5e7eb" :gap="20" />
          <Controls />
        </VueFlow>
        <el-empty v-else description="等待创建审查任务…" />
      </div>
    </template>

    <!-- 完整流程图视图：框架 GraphRepresentation 生成的 Mermaid 静态图 -->
    <div v-else class="full-graph">
      <el-skeleton v-if="graphLoading" :rows="6" animated />
      <el-empty
        v-else-if="graphError"
        :description="'流程图加载失败：' + graphError"
      >
        <el-button size="small" @click="loadFullGraph">重试</el-button>
      </el-empty>
      <template v-else>
        <div class="graph-section">
          <div class="graph-title">主流程 · 简历审查工作流</div>
          <div class="graph-svg" v-html="graphMainSvg"></div>
        </div>
        <div class="graph-section">
          <div class="graph-title">后置子图 · HR 复核确认后</div>
          <div class="graph-svg" v-html="graphPostSvg"></div>
        </div>
        <div class="graph-tip">注：完整图为静态展示（含 START/END 与全部条件边），实时执行状态请切换到「实时追踪」。</div>
      </template>
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
  gap: 12px;
  margin-bottom: 12px;
}
.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  flex-shrink: 0;
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
/* 完整流程图视图 */
.full-graph {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.graph-section {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 12px;
}
.graph-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 8px;
}
.graph-svg :deep(svg) {
  max-width: 100%;
  height: auto;
}
.graph-tip {
  font-size: 12px;
  color: #909399;
  padding: 4px 2px;
}
</style>
