<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import api from '../api'

// 节点ID → 中文名
const NODE_LABELS = {
  __START__: '开始',
  __END__: '结束',
  parse_resume: '解析简历',
  skill_scoring: '技能匹配评分',
  project_scoring: '项目经历评分',
  culture_scoring: '文化匹配评分',
  synthesis: '综合诊断',
  human_review: '人工复核',
  generate_report: '生成报告'
}
function labelOf(id) {
  const fixed = NODE_LABELS[id]
  if (fixed) return fixed
  // 框架内部包装节点：xxx__(业务节点) → 业务节点（框架路由）
  const m = String(id).match(/^(.*)__\((.+)\)$/)
  if (m) return `${labelOf(m[2])}（框架路由）`
  return id
}
function isInternalNode(id) {
  return /^(.*)__\(.+\)$/.test(String(id))
}

const hideInternal = ref(true)
const tasks = ref([])
const selectedTask = ref('')
const statusFilter = ref('')
const nodeFilter = ref('')
const logs = ref([])
const loading = ref(false)
const errorTip = ref('')

function formatDuration(ms) {
  const v = Number(ms || 0)
  if (v < 1000) return `${v} ms`
  return `${(v / 1000).toFixed(2)} s`
}
function formatTime(v) {
  if (!v) return '-'
  const d = new Date(v)
  if (isNaN(d)) return v
  return d.toLocaleString('zh-CN', { hour12: false })
}
function statusText(s) {
  if (s === 'completed') return '成功'
  if (s === 'error') return '失败'
  if (s === 'pending') return '挂起'
  return s || '-'
}
function statusTagType(s) {
  if (s === 'completed') return 'success'
  if (s === 'error') return 'danger'
  if (s === 'pending') return 'warning'
  return 'info'
}
function statusColor(s) {
  if (s === 'completed') return '#67c23a'
  if (s === 'error') return '#f56c6c'
  if (s === 'pending') return '#9254de'
  return '#409eff'
}

// ---------- 数据加载 ----------
async function loadTasks() {
  try {
    tasks.value = await api.get('/review/node-logs/tasks', { params: { limit: 20 } }) || []
    if (tasks.value.length && !selectedTask.value) {
      selectedTask.value = String(tasks.value[0].taskId)
    }
  } catch (e) {
    ElMessage.error('任务列表加载失败: ' + e.message)
  }
}

async function loadLogs() {
  loading.value = true
  errorTip.value = ''
  try {
    const params = { limit: 500 }
    if (selectedTask.value) params.taskId = selectedTask.value
    if (statusFilter.value) params.status = statusFilter.value
    if (nodeFilter.value) params.nodeId = nodeFilter.value
    logs.value = await api.get('/review/node-logs', { params }) || []
    renderChart()
  } catch (e) {
    errorTip.value = '日志加载失败：' + e.message
    logs.value = []
    if (chart) chart.clear()
  } finally {
    loading.value = false
  }
}

// 过滤掉框架内部节点后的可见日志
const visibleLogs = computed(() => {
  if (!hideInternal.value) return logs.value
  return logs.value.filter((l) => !isInternalNode(l.nodeId))
})

// ---------- 概览 ----------
const summary = computed(() => {
  const list = visibleLogs.value
  const totalMs = list.reduce((s, l) => s + Number(l.durationMs || 0), 0)
  const completed = list.filter((l) => l.status === 'completed').length
  const errored = list.filter((l) => l.status === 'error').length
  const durations = list.map((l) => Number(l.durationMs || 0))
  return {
    count: list.length,
    totalMs,
    completed,
    errored,
    avgMs: list.length ? Math.round(totalMs / list.length) : 0,
    maxMs: durations.length ? Math.max(...durations) : 0
  }
})

// ---------- 甘特式耗时时间轴 ----------
const chartRef = ref(null)
let chart = null

// 按开始时间升序，计算每个节点相对首个节点的起始偏移
const chartRows = computed(() => {
  const list = visibleLogs.value.filter((l) => l.startTime)
  if (!list.length) return []
  return [...list].sort((a, b) => new Date(a.startTime) - new Date(b.startTime))
})

function renderChart() {
  if (!chart) return
  const rows = chartRows.value
  if (!rows.length) {
    chart.clear()
    return
  }
  const minStart = new Date(rows[0].startTime).getTime()
  const categories = rows.map((l) => labelOf(l.nodeId))
  const offsets = rows.map((l) => (new Date(l.startTime).getTime() - minStart) / 1000)
  const durations = rows.map((l) => Number(l.durationMs || 0) / 1000)

  chart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params) => {
        const p = params.find((x) => x.seriesName === '耗时')
        if (!p) return ''
        const row = rows[p.dataIndex]
        return [
          `<b>${labelOf(row.nodeId)}</b>`,
          `状态：${statusText(row.status)}`,
          `耗时：${formatDuration(row.durationMs)}`,
          `开始：${formatTime(row.startTime)}`,
          `结束：${formatTime(row.endTime)}`
        ].join('<br/>')
      }
    },
    grid: { left: 130, right: 50, top: 20, bottom: 34 },
    xAxis: {
      type: 'value',
      name: '秒(s)',
      nameTextStyle: { fontSize: 11 },
      splitLine: { lineStyle: { type: 'dashed' } }
    },
    yAxis: {
      type: 'category',
      data: categories,
      inverse: true,
      axisLabel: { fontSize: 12 },
      axisTick: { show: false }
    },
    series: [
      {
        name: '起始偏移',
        type: 'bar',
        stack: 'total',
        barWidth: 16,
        silent: true,
        itemStyle: { color: 'transparent' },
        emphasis: { itemStyle: { color: 'transparent' } },
        data: offsets
      },
      {
        name: '耗时',
        type: 'bar',
        stack: 'total',
        barWidth: 16,
        label: {
          show: true,
          position: 'right',
          fontSize: 11,
          formatter: (p) => `${Number(p.value).toFixed(2)}s`
        },
        data: durations.map((v, i) => ({
          value: v,
          itemStyle: { color: statusColor(rows[i].status), borderRadius: 3 }
        }))
      }
    ]
  }, true)
}

function onResize() {
  chart && chart.resize()
}

watch([selectedTask, statusFilter, nodeFilter], () => loadLogs())
watch(hideInternal, () => {
  renderChart()
})

onMounted(async () => {
  chart = echarts.init(chartRef.value)
  window.addEventListener('resize', onResize)
  await loadTasks()
  await loadLogs()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  if (chart) {
    chart.dispose()
    chart = null
  }
})
</script>

<template>
  <div class="log-query-view">
    <!-- 顶部筛选 -->
    <div class="filter-bar">
      <span class="view-title">节点执行日志查询</span>
      <el-select
        v-model="selectedTask"
        placeholder="选择任务"
        size="small"
        clearable
        filterable
        style="width: 420px"
      >
        <el-option
          v-for="t in tasks"
          :key="t.taskId"
          :value="String(t.taskId)"
          :label="String(t.taskId)"
        >
          <div class="task-option">
            <span class="task-id">{{ t.taskId }}</span>
            <span class="task-meta">
              {{ t.nodeCount }} 节点 · {{ formatDuration(t.totalDurationMs) }} · 失败 {{ t.errorCount }}
              <template v-if="t.firstStartTime"> · {{ formatTime(t.firstStartTime) }}</template>
            </span>
          </div>
        </el-option>
      </el-select>
      <el-select v-model="statusFilter" placeholder="状态" size="small" clearable style="width: 120px">
        <el-option label="成功" value="completed" />
        <el-option label="失败" value="error" />
        <el-option label="挂起" value="pending" />
      </el-select>
      <el-select v-model="nodeFilter" placeholder="节点" size="small" clearable style="width: 170px">
        <el-option v-for="(v, k) in NODE_LABELS" :key="k" :label="v" :value="k" />
      </el-select>
      <el-checkbox v-model="hideInternal" size="small">隐藏框架内部节点</el-checkbox>
      <el-button size="small" type="primary" :loading="loading" @click="loadLogs">查询</el-button>
    </div>

      <div v-if="errorTip" class="error-tip">{{ errorTip }}</div>

    <!-- 概览卡片 -->
    <div class="summary-row">
      <div class="sum-card">
        <span class="sum-val">{{ summary.count }}</span>
        <span class="sum-label">节点执行数</span>
      </div>
      <div class="sum-card">
        <span class="sum-val">{{ formatDuration(summary.totalMs) }}</span>
        <span class="sum-label">总耗时</span>
      </div>
      <div class="sum-card">
        <span class="sum-val">{{ formatDuration(summary.avgMs) }}</span>
        <span class="sum-label">平均耗时</span>
      </div>
      <div class="sum-card">
        <span class="sum-val">{{ formatDuration(summary.maxMs) }}</span>
        <span class="sum-label">最慢节点</span>
      </div>
      <div class="sum-card">
        <span class="sum-val" style="color: #67c23a">{{ summary.completed }}</span>
        <span class="sum-label">成功</span>
      </div>
      <div class="sum-card">
        <span class="sum-val" style="color: #f56c6c">{{ summary.errored }}</span>
        <span class="sum-label">失败</span>
      </div>
    </div>

    <!-- 耗时时间轴 -->
    <div class="chart-card">
      <div class="card-title">
        流程耗时时间轴（横条 = 节点耗时，起点 = 相对开始时刻）
        <span v-if="selectedTask" class="cur-task">taskId：{{ selectedTask }}</span>
      </div>
      <div ref="chartRef" class="chart-box"></div>
    </div>

    <!-- 明细表 -->
    <div class="table-card">
      <div class="card-title">执行明细</div>
      <el-table :data="visibleLogs" size="small" stripe border height="100%" empty-text="暂无日志数据">
        <el-table-column prop="nodeId" label="节点" min-width="140">
          <template #default="{ row }">{{ labelOf(row.nodeId) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="durationMs" label="耗时" width="110" sortable>
          <template #default="{ row }">
            <span class="mono">{{ formatDuration(row.durationMs) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="180">
          <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column prop="endTime" label="结束时间" width="180">
          <template #default="{ row }">{{ formatTime(row.endTime) }}</template>
        </el-table-column>
        <el-table-column prop="attempt" label="尝试" width="70" />
        <el-table-column prop="userId" label="操作用户" width="110" />
        <el-table-column prop="errorMsg" label="错误信息" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="err">{{ row.errorMsg || '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
.log-query-view {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 16px;
  overflow: auto;
}
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}
.view-title {
  font-size: 17px;
  font-weight: 700;
  color: #303133;
  margin-right: 6px;
}
/* 任务下拉选项：完整 taskId + 统计信息两行展示 */
.task-option {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
  padding: 2px 0;
}
.task-id {
  font-family: 'Roboto Mono', Consolas, Monaco, monospace;
  font-size: 12px;
  color: #303133;
  word-break: break-all;
}
.task-meta {
  font-size: 11px;
  color: #909399;
}
.error-tip {
  font-size: 12px;
  color: #e6a23c;
  flex-shrink: 0;
}
.summary-row {
  display: flex;
  gap: 12px;
  flex-shrink: 0;
}
.sum-card {
  flex: 1;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}
.sum-val {
  font-size: 20px;
  font-weight: 700;
  color: #303133;
  font-family: 'Roboto Mono', Consolas, Monaco, monospace;
  font-variant-numeric: tabular-nums;
}
.sum-label {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.chart-card,
.table-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 14px 16px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  flex-shrink: 0;
}
.card-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.card-title::before {
  content: '';
  width: 3px;
  height: 13px;
  border-radius: 2px;
  background: #409eff;
}
.cur-task {
  font-size: 11px;
  font-weight: 400;
  color: #909399;
  font-family: 'Roboto Mono', Consolas, Monaco, monospace;
  word-break: break-all;
}
.chart-box {
  height: 300px;
  width: 100%;
}
.table-card {
  flex: 1;
  min-height: 260px;
  display: flex;
  flex-direction: column;
}
.mono {
  font-family: 'Roboto Mono', Consolas, Monaco, monospace;
}
.err {
  color: #f56c6c;
}
</style>
