<template>
  <el-dialog
    v-model="visible"
    title="历史报告"
    fullscreen
    class="report-history-dialog"
  >
    <!-- 统计卡片 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ stats.total }}</div>
          <div class="stat-label">报告总数</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ Number(stats.avgScore || 0).toFixed(1) }}</div>
          <div class="stat-label">平均评分</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ Number(stats.maxScore || 0).toFixed(1) }}</div>
          <div class="stat-label">最高评分</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-value">{{ stats.needsReviewCount }}</div>
          <div class="stat-label">待复核</div>
        </div>
      </el-col>
    </el-row>

    <!-- 工具栏 -->
    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索简历文件名 / 报告摘要"
        clearable
        style="width: 340px"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <!-- 表格 -->
    <el-table :data="list" v-loading="loading" stripe height="calc(100vh - 340px)">
      <el-table-column prop="fileName" label="简历文件" min-width="200" show-overflow-tooltip />
      <el-table-column label="综合评分" width="110">
        <template #default="{ row }">
          <el-tag :type="scoreTag(row.overallScore)">
            {{ Number(row.overallScore || 0).toFixed(1) }} / 5
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="summary" label="报告摘要" min-width="280" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.taskStatus)">{{ statusLabel(row.taskStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="生成耗时" width="100">
        <template #default="{ row }">
          <span class="duration-text">{{ formatDuration(row.duration) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="生成时间" width="160">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="openDetail(row)">查看详情</el-button>
          <el-button text type="warning" size="small" @click="openRestart(row)">重新审查</el-button>
          <el-button text type="danger" size="small" @click="doDelete(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无历史报告" />
      </template>
    </el-table>

    <!-- 分页 -->
    <div class="pager">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="load"
        @size-change="onSizeChange"
      />
    </div>
  </el-dialog>

  <!-- 重新审查弹窗 -->
  <el-dialog
    v-model="restartVisible"
    title="重新审查"
    width="520px"
    :close-on-click-modal="false"
    :show-close="!restartLoading"
    :close-on-press-escape="!restartLoading"
  >
    <div style="margin-bottom: 12px">
      <strong>简历：</strong>{{ restartRow?.fileName || '-' }}
    </div>
    <el-input
      v-model="restartJd"
      type="textarea"
      :rows="5"
      placeholder="请填写新的岗位描述（JD），AI 将基于该 JD 重新评估此简历"
      maxlength="2000"
      show-word-limit
      :disabled="restartLoading"
    />
    <div v-if="restartLoading" class="restart-progress">
      <el-progress :percentage="restartPercent" :status="restartStatus" :stroke-width="10" />
      <div class="restart-status-text">{{ restartStatusText }}</div>
    </div>
    <template #footer>
      <el-button :disabled="restartLoading" @click="restartVisible = false">取消</el-button>
      <el-button type="primary" :loading="restartLoading" @click="doRestart">开始审查</el-button>
    </template>
  </el-dialog>

  <!-- 详情抽屉 -->
  <el-drawer
    v-model="detailVisible"
    size="62%"
    title="报告详情"
    :z-index="2200"
  >
    <div v-loading="detailLoading" class="detail-wrap">
      <template v-if="detail">
        <div class="detail-header">
          <div class="detail-title">
            <el-icon :size="20" color="#409eff"><Document /></el-icon>
            <span>{{ detail.resume?.fileName || '简历' }}</span>
          </div>
          <el-dropdown @command="doExport">
            <el-button type="primary" size="small" :icon="Download">导出报告</el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="html">HTML 文件（可直接打印）</el-dropdown-item>
                <el-dropdown-item command="md">Markdown 文件</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>

        <div class="detail-meta">
          <el-tag :type="scoreTag(detail.report?.overallScore)">
            综合评分 {{ Number(detail.report?.overallScore || 0).toFixed(1) }} / 5
          </el-tag>
          <el-tag v-if="detail.report?.needsHumanReview" type="warning">待人工复核</el-tag>
          <span class="detail-time">生成于 {{ formatTime(detail.report?.updateTime) }}</span>
        </div>

        <div v-if="detail.report?.hrReviewComment" class="hr-review">
          <div class="hr-title">HR 复核意见</div>
          <div class="hr-content">{{ detail.report.hrReviewComment }}</div>
        </div>

        <div class="detail-section-title">六维能力评估</div>
        <div class="detail-radar">
          <RadarChart :radar-data="detailRadar" :loading="false" />
        </div>

        <div class="detail-section-title">详细诊断报告</div>
        <div class="markdown-body" v-html="detailRendered"></div>
      </template>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import MarkdownIt from 'markdown-it'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Refresh, Document, Download } from '@element-plus/icons-vue'
import api from '../api'
import RadarChart from './RadarChart.vue'
import { useReportExport } from '../composables/useReportExport'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'restart'])

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true
})

const { exportHtml: exportHtmlFile, exportMarkdown: exportMarkdownFile } = useReportExport()

// ---------- 列表 ----------
const list = ref([])
const total = ref(0)
const stats = ref({ total: 0, avgScore: 0, maxScore: 0, needsReviewCount: 0 })
const page = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const keyword = ref('')

// ---------- 详情 ----------
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

// ---------- 重新审查 ----------
const restartVisible = ref(false)
const restartLoading = ref(false)
const restartRow = ref(null)
const restartJd = ref('')
const restartPercent = ref(0)
const restartStatusText = ref('')
const restartStatus = ref('')

let restartEventSource = null

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const STATUS_MAP = {
  COMPLETED: ['success', '已完成'],
  FAILED: ['danger', '失败'],
  NEEDS_REVIEW: ['warning', '待复核'],
  RUNNING: ['primary', '运行中'],
  PENDING: ['info', '待处理']
}
const statusType = (s) => STATUS_MAP[s]?.[0] || 'info'
const statusLabel = (s) => STATUS_MAP[s]?.[1] || s || '-'
const scoreTag = (v) => {
  const n = Number(v || 0)
  return n >= 4 ? 'success' : n >= 3 ? 'warning' : 'danger'
}
const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '-')
const formatDuration = (s) => {
  const sec = Number(s || 0)
  if (sec < 60) return `${sec} 秒`
  if (sec < 3600) return `${Math.floor(sec / 60)} 分 ${sec % 60} 秒`
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  return `${h} 时 ${m} 分`
}

const load = async () => {
  loading.value = true
  try {
    const data = await api.get('/review/reports', {
      params: {
        page: page.value,
        pageSize: pageSize.value,
        keyword: keyword.value || undefined
      }
    })
    list.value = data.list || []
    total.value = data.total || 0
    stats.value = data.stats || stats.value
  } catch (e) {
    ElMessage.error(`报告列表加载失败：${e.message}`)
  } finally {
    loading.value = false
  }
}

const onSizeChange = () => {
  page.value = 1
  load()
}

let searchTimer = null
watch(keyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    page.value = 1
    load()
  }, 300)
})

watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      page.value = 1
      keyword.value = ''
      load()
    }
  }
)

// ---------- 详情 ----------
const detailRadar = computed(() => {
  const raw = detail.value?.report?.radarData
  if (!raw) return []
  try {
    const arr = typeof raw === 'string' ? JSON.parse(raw) : raw
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
})

const detailRendered = computed(() => {
  const text = detail.value?.report?.detailReport || ''
  return text ? md.render(text) : ''
})

const openDetail = async (row) => {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await api.get(`/review/task/${row.taskId}`)
  } catch (e) {
    ElMessage.error(`报告详情加载失败：${e.message}`)
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const doExport = (command) => {
  const report = detail.value?.report
  if (!report) return
  const payload = {
    detailReport: report.detailReport,
    overallScore: report.overallScore,
    radarData: report.radarData,
    fileName: `${detail.value?.resume?.fileName || '简历'}_审查报告`
  }
  if (command === 'html') exportHtmlFile(payload)
  else exportMarkdownFile(payload)
}

const openRestart = (row) => {
  restartRow.value = row
  restartJd.value = ''
  restartPercent.value = 0
  restartStatusText.value = ''
  restartStatus.value = ''
  restartVisible.value = true
}

const closeRestartSSE = () => {
  if (restartEventSource) { restartEventSource.close(); restartEventSource = null }
}

const doDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除「${row.fileName}」的审查报告吗？`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }
  try {
    await api.delete(`/review/reports/${row.reportId}`)
    ElMessage.success('删除成功')
    load()
  } catch (e) {
    ElMessage.error(`删除失败：${e.message}`)
  }
}

const doRestart = async () => {
  if (!restartJd.value.trim()) {
    ElMessage.warning('请填写岗位描述（JD）')
    return
  }
  restartLoading.value = true
  restartPercent.value = 0
  restartStatusText.value = '正在提交任务...'
  restartStatus.value = ''
  closeRestartSSE()

  let taskId = ''
  try {
    const res = await api.post('/review/start', {
      resumeId: restartRow.value.resumeId,
      jobDescription: restartJd.value.trim()
    })
    // 后端 R 封装返回 { taskId: 'xxx' }
    taskId = res?.taskId ?? res
    if (!taskId || typeof taskId !== 'string') {
      throw new Error('服务器未返回有效的任务 ID')
    }
  } catch (e) {
    ElMessage.error(`重新发起失败：${e.message}`)
    restartLoading.value = false
    return
  }

  // 连接 SSE 实时进度
  const token = localStorage.getItem('satoken')
  const url = `${window.location.origin}/api/review/stream/${encodeURIComponent(taskId)}?_=${Date.now()}${token ? '&satoken=' + encodeURIComponent(token) : ''}`
  try {
    restartEventSource = new EventSource(url)
  } catch (e) {
    ElMessage.error(`SSE 连接失败：${e.message}`)
    restartLoading.value = false
    return
  }

  restartEventSource.addEventListener('topology', (event) => {
    try {
      const data = JSON.parse(event.data)
      restartPercent.value = Math.min(Math.round((data.progress || 0) * 100), 99)
      restartStatusText.value = data.message || '正在审查...'
    } catch {}
  })

  restartEventSource.addEventListener('report', (event) => {
    try {
      const data = JSON.parse(event.data)
      restartPercent.value = 100
      restartStatusText.value = '审查完成'
      restartStatus.value = 'success'
      closeRestartSSE()
      setTimeout(() => {
        restartVisible.value = false
        restartLoading.value = false
        emit('restart', taskId)
      }, 600)
    } catch {}
  })

  restartEventSource.addEventListener('error', (event) => {
    try {
      const data = JSON.parse(event.data)
      restartPercent.value = 100
      restartStatus.value = 'exception'
      restartStatusText.value = data.message || '审查失败'
    } catch {
      restartStatus.value = 'exception'
      restartStatusText.value = '审查失败'
    }
    closeRestartSSE()
    restartLoading.value = false
  })

  restartEventSource.onerror = () => {
    if (restartPercent.value < 100) {
      restartStatus.value = 'exception'
      restartStatusText.value = '连接中断'
    }
    closeRestartSSE()
    restartLoading.value = false
  }
}
</script>

<style scoped>
.stat-row {
  margin-bottom: 16px;
}
.stat-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 16px;
  text-align: center;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #409eff;
}
.stat-label {
  font-size: 13px;
  color: #909399;
  margin-top: 4px;
}
.toolbar {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

/* ---------- 详情 ---------- */
.detail-wrap {
  min-height: 60vh;
}
.detail-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.detail-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.detail-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.detail-time {
  font-size: 12px;
  color: #909399;
}
.hr-review {
  background: #fdf6ec;
  border: 1px solid #f3d19e;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 16px;
}
.hr-title {
  font-size: 13px;
  font-weight: 600;
  color: #b88230;
  margin-bottom: 6px;
}
.hr-content {
  font-size: 14px;
  color: #606266;
  white-space: pre-wrap;
}
.detail-section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin: 16px 0 8px;
}
.detail-radar {
  height: 320px;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 12px;
  box-sizing: border-box;
}
.markdown-body {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 16px 20px;
  line-height: 1.8;
  font-size: 14px;
  color: #303133;
  min-height: 200px;
}
.markdown-body :deep(h2) {
  font-size: 17px;
  border-left: 4px solid #409eff;
  padding-left: 10px;
  margin: 20px 0 10px;
}
.markdown-body :deep(h3) {
  font-size: 15px;
  margin: 14px 0 8px;
}
.markdown-body :deep(p) {
  margin: 8px 0;
}
.markdown-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 12px 0;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #e4e7ed;
  padding: 6px 10px;
  text-align: left;
}
.markdown-body :deep(th) {
  background: #f5f7fa;
}
.markdown-body :deep(code) {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 4px;
}
.markdown-body :deep(pre) {
  background: #f5f7fa;
  padding: 12px 16px;
  border-radius: 8px;
  overflow: auto;
}
.markdown-body :deep(blockquote) {
  border-left: 4px solid #e4e7ed;
  margin: 8px 0;
  padding-left: 16px;
  color: #909399;
}
.restart-progress {
  margin-top: 16px;
}
.restart-status-text {
  text-align: center;
  font-size: 13px;
  color: #606266;
  margin-top: 8px;
}
.duration-text {
  font-size: 13px;
  color: #606266;
}
</style>
