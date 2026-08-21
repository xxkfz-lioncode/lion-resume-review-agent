<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { Timer } from '@element-plus/icons-vue'
import api from './api'
import { store, taskStatusText } from './store'
import LoginView from './components/LoginView.vue'
import UploadPanel from './components/UploadPanel.vue'
import FlowPanel from './components/FlowPanel.vue'
import RadarChart from './components/RadarChart.vue'
import ReportPanel from './components/ReportPanel.vue'
import ReportHistory from './components/ReportHistory.vue'
import ReviewModal from './components/ReviewModal.vue'

// ---------- 登录态（独立页面切换） ----------
const checkLogin = async () => {
  try {
    const info = await api.get('/auth/info')
    store.loggedIn = info.login
    store.username = info.username
  } catch {
    store.loggedIn = false
  }
}

const logout = async () => {
  try { await api.post('/auth/logout') } catch {}
  localStorage.removeItem('satoken')
  store.loggedIn = false
  store.setTask('')
  store.topology = null
  store.radarPreview = null
  store.report = null
  store.uploadedResume = null
  if (eventSource) { eventSource.close(); eventSource = null }
}

// ---------- SSE ----------
let eventSource = null

function connectSSE(taskId) {
  if (eventSource) eventSource.close()
  eventSource = new EventSource(`/api/review/stream/${taskId}`)

  eventSource.addEventListener('topology', (e) => {
    try {
      store.topology = JSON.parse(e.data)
    } catch {}
  })

  eventSource.addEventListener('node', (e) => {
    const evt = JSON.parse(e.data)
    handleNodeEvent(evt)
  })

  eventSource.addEventListener('task', (e) => {
    const evt = JSON.parse(e.data)
    store.taskStatus = evt.status === 'completed' ? 'COMPLETED'
      : evt.status === 'running' ? 'RUNNING'
      : evt.status === 'error' ? 'FAILED' : store.taskStatus
    store.progress = evt.progress ?? store.progress
    if (evt.message) store.pushLog(`[任务] ${evt.message}`)
    if (evt.status === 'completed') fetchReport(taskId)
    if (evt.status === 'error') {
      store.nodeStates[evt.node || '__END__'] = { status: 'error', message: evt.message }
    }
  })

  eventSource.addEventListener('stream', (e) => {
    const evt = JSON.parse(e.data)
    if (evt.data) store.streamingText += evt.data
  })

  eventSource.addEventListener('info', (e) => {
    const evt = JSON.parse(e.data)
    if (evt.message) store.pushLog(`[信息] ${evt.message}`)
  })

  eventSource.onopen = () => store.pushLog('SSE 通道已建立，开始监听 Graph 节点状态')
  eventSource.onerror = () => store.pushLog('SSE 连接断开，正在自动重连…')
}

function handleNodeEvent(evt) {
  store.nodeStates[evt.node] = {
    status: evt.status,
    progress: evt.progress,
    message: evt.message || ''
  }
  if (evt.progress != null && evt.progress > store.progress) {
    store.progress = evt.progress
  }
  if (evt.message) store.pushLog(`[${evt.node}] ${evt.message}`)

  // 人工复核挂起：弹出 HR 审核框
  if (evt.node === 'human_review' && evt.status === 'pending' && evt.data) {
    store.pendingReview = evt.data
    store.reviewVisible = true
  }
  // 复核恢复：关闭审核框
  if (evt.node === 'human_review' && evt.status === 'resumed') {
    store.reviewVisible = false
    store.streamingText = ''
  }
  // 评分数据实时渲染雷达图
  if (evt.status === 'completed' && evt.data?.radar_data) {
    store.radarPreview = evt.data.radar_data
  }
}

// ---------- 报告拉取 ----------
const fetchReport = async (taskId) => {
  store.reportLoading = true
  try {
    const detail = await api.get(`/review/task/${taskId}`)
    store.report = detail.report
    if (detail.task) {
      store.taskStatus = detail.task.status
      store.progress = detail.task.progress || store.progress
    }
    if (detail.topology) store.topology = detail.topology
  } catch (e) {
    ElMessage.error('报告拉取失败: ' + e.message)
  } finally {
    store.reportLoading = false
  }
}

// ---------- 事件 ----------
function onStarted(taskId) {
  store.setTask(taskId)
  connectSSE(taskId)
}

// ---------- 历史报告 ----------
const reportHistoryVisible = ref(false)

function handleRestart(taskId) {
  reportHistoryVisible.value = false
  store.setTask(taskId)
  connectSSE(taskId)
  ElMessage.success('已重新发起审查，请查看主面板')
}

// ---------- 面板尺寸（拖拽调整） ----------
const leftColRef = ref(null)
const rightColRef = ref(null)
const leftWidth = ref(320)
const rightWidth = ref(460)
const leftTopRatio = ref(0.45)
const rightTopRatio = ref(0.35)

const clamp = (v, min, max) => Math.min(Math.max(v, min), max)

// 左列上传面板 / 日志区 flex 比例（无日志时上传面板占满）
const leftTopFlex = computed(() => (store.logs.length ? leftTopRatio.value : 1))
const leftBottomFlex = computed(() => (store.logs.length ? 1 - leftTopRatio.value : 0))

// 拖动列分割线（调整左右列宽度）
function startResizeX(event, side) {
  event.preventDefault()
  const main = document.querySelector('.app-main')
  if (!main) return
  const rect = main.getBoundingClientRect()
  const onMove = (e) => {
    if (side === 'left') {
      leftWidth.value = clamp(e.clientX - rect.left, 240, Math.round(rect.width * 0.45))
    } else {
      rightWidth.value = clamp(rect.right - e.clientX, 300, Math.round(rect.width * 0.45))
    }
  }
  const onUp = () => {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}

// 拖动列内水平分割线（调整上下面板高度）
function startResizeY(event, area) {
  event.preventDefault()
  const startY = event.clientY
  const startRatio = area === 'left' ? leftTopRatio.value : rightTopRatio.value
  const el = area === 'left' ? leftColRef.value : rightColRef.value
  if (!el) return
  const h = el.getBoundingClientRect().height
  const onMove = (e) => {
    const r = clamp(startRatio + (e.clientY - startY) / h, 0.2, 0.8)
    if (area === 'left') leftTopRatio.value = r
    else rightTopRatio.value = r
  }
  const onUp = () => {
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
  document.body.style.cursor = 'row-resize'
  document.body.style.userSelect = 'none'
}

function onApproved() {
  store.streamingText = ''
  store.pushLog('HR 已确认评分，等待报告生成…')
}

const radarData = computed(() => {
  const reportRadar = store.report?.radarData
  if (reportRadar) {
    try {
      return JSON.parse(reportRadar)
    } catch {
      return reportRadar
    }
  }
  return store.radarPreview || []
})

onMounted(checkLogin)
onBeforeUnmount(() => eventSource && eventSource.close())
</script>

<template>
  <!-- 未登录：独立登录页面 -->
  <LoginView v-if="!store.loggedIn" />

  <!-- 已登录：审查系统主面板 -->
  <div v-else class="app-shell">
    <!-- 顶部 -->
    <header class="app-header">
      <div class="header-left">
        <span class="logo">📄</span>
        <span class="title">企业招聘简历审查系统</span>
        <el-tag size="small" type="info" effect="plain">Spring AI Alibaba Graph 工作流</el-tag>
      </div>
      <div class="header-right">
        <el-tag v-if="store.taskId" :type="store.taskStatus === 'FAILED' ? 'danger' : 'primary'" effect="light">
          任务状态：{{ taskStatusText }}
        </el-tag>
        <el-button v-if="store.loggedIn" :icon="Timer" @click="reportHistoryVisible = true">
          历史报告
        </el-button>
        <el-button v-if="store.loggedIn" link @click="logout">{{ store.username }} · 退出</el-button>
      </div>
    </header>

    <!-- 主体 -->
    <main
      class="app-main"
      :style="{ '--left-w': leftWidth + 'px', '--right-w': rightWidth + 'px' }"
    >
      <aside class="left-col" ref="leftColRef">
        <div
          class="col-pane"
          :style="{ flexGrow: leftTopFlex, flexShrink: leftTopFlex, flexBasis: 0 }"
        >
          <UploadPanel @started="onStarted" />
        </div>
        <template v-if="store.logs.length">
          <div class="resizer-h" @mousedown="startResizeY($event, 'left')"></div>
          <div
            class="col-pane log-pane"
            :style="{ flexGrow: leftBottomFlex, flexShrink: leftBottomFlex, flexBasis: 0 }"
          >
            <div class="log-card">
              <div class="panel-title" style="margin-bottom: 8px">实时日志</div>
              <div class="log-list">
                <div v-for="(log, i) in store.logs" :key="i" class="log-line">{{ log }}</div>
              </div>
            </div>
          </div>
        </template>
      </aside>

      <div class="resizer-v" @mousedown="startResizeX($event, 'left')"></div>

      <section class="center-col">
        <FlowPanel :topology="store.topology" />
      </section>

      <div class="resizer-v" @mousedown="startResizeX($event, 'right')"></div>

      <aside class="right-col" ref="rightColRef">
        <div
          class="col-pane"
          :style="{ flexGrow: rightTopRatio, flexShrink: rightTopRatio, flexBasis: 0 }"
        >
          <RadarChart :radar-data="radarData" :loading="!!store.streamingText" />
        </div>
        <div class="resizer-h" @mousedown="startResizeY($event, 'right')"></div>
        <div
          class="col-pane"
          :style="{ flexGrow: 1 - rightTopRatio, flexShrink: 1 - rightTopRatio, flexBasis: 0 }"
        >
          <ReportPanel :report="store.report" :streaming-text="store.streamingText" />
        </div>
      </aside>
    </main>

    <!-- HR 人工复核弹窗 -->
    <ReviewModal
      v-model:visible="store.reviewVisible"
      :task-id="store.taskId"
      :pending-data="store.pendingReview"
      @approved="onApproved"
    />

    <!-- 历史报告 -->
    <ReportHistory v-model="reportHistoryVisible" @restart="handleRestart" />
  </div>
</template>

<style scoped>
.app-shell {
  height: 100vh;
  display: flex;
  flex-direction: column;
}
.app-header {
  height: 56px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  z-index: 10;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.logo { font-size: 22px; }
.title { font-size: 17px; font-weight: 700; color: #303133; }
.header-right { display: flex; align-items: center; gap: 12px; }

.app-main {
  --left-w: 320px;
  --right-w: 460px;
  flex: 1;
  display: grid;
  grid-template-columns: var(--left-w) 8px 1fr 8px var(--right-w);
  gap: 12px;
  padding: 16px;
  overflow: hidden;
}
.left-col, .right-col {
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow: hidden;
  min-height: 0;
}
.center-col {
  min-width: 0;
}
.col-pane {
  min-height: 0;
  overflow: auto;
}

/* 列间纵向分割线（调宽度） */
.resizer-v {
  cursor: col-resize;
  position: relative;
  z-index: 5;
}
.resizer-v::after {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  width: 3px;
  transform: translateX(-50%);
  border-radius: 2px;
  background: #e4e7ed;
  transition: background 0.2s;
}
.resizer-v:hover::after,
.resizer-v:active::after {
  background: #409eff;
}

/* 列内水平分割线（调高度） */
.resizer-h {
  cursor: row-resize;
  height: 8px;
  position: relative;
  flex-shrink: 0;
  z-index: 5;
}
.resizer-h::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  top: 50%;
  height: 3px;
  transform: translateY(-50%);
  border-radius: 2px;
  background: #e4e7ed;
  transition: background 0.2s;
}
.resizer-h:hover::after,
.resizer-h:active::after {
  background: #409eff;
}

.log-pane {
  display: flex;
}
.log-card {
  width: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  min-height: 0;
}
.log-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  font-size: 12px;
  font-family: Consolas, Monaco, monospace;
}
.log-line {
  color: #606266;
  padding: 2px 0;
  border-bottom: 1px dashed #f0f2f5;
}
@media (max-width: 1400px) {
  .app-main {
    grid-template-columns: var(--left-w) 8px 1fr 8px var(--right-w);
  }
}
@media (max-width: 1100px) {
  .app-main {
    grid-template-columns: 1fr;
    overflow: auto;
  }
  .resizer-v {
    display: none;
  }
}
</style>
