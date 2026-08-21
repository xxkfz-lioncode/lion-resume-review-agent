import { reactive, computed } from 'vue'

/**
 * 全局状态：当前任务、节点状态、报告等
 */
export const store = reactive({
  // 认证
  loggedIn: false,
  username: '',

  // 当前任务
  taskId: '',
  taskStatus: '', // PENDING/RUNNING/NEEDS_REVIEW/COMPLETED/FAILED
  progress: 0,

  // Graph 拓扑
  topology: null,

  // 节点状态: { [nodeId]: { status, progress, message } }
  nodeStates: {},

  // 报告
  report: null,
  streamingText: '',
  reportLoading: false,

  // 上传的简历
  uploadedResume: null,

  // 人工复核弹窗
  pendingReview: null,
  reviewVisible: false,

  // 日志（消息流）
  logs: [],

  setTask(taskId) {
    this.taskId = taskId
    this.nodeStates = {}
    this.report = null
    this.streamingText = ''
    this.pendingReview = null
    this.reviewVisible = false
    this.logs = []
    this.progress = 0
    this.taskStatus = 'RUNNING'
  },

  pushLog(text) {
    const time = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    this.logs.unshift(`[${time}] ${text}`)
    if (this.logs.length > 50) this.logs.pop()
  }
})

/** 将节点状态映射为样式类 */
export function nodeStateClass(status) {
  const map = {
    running: 'is-running',
    completed: 'is-completed',
    error: 'is-error',
    retrying: 'is-retrying',
    pending: 'is-pending',
    waiting: 'is-waiting',
    resumed: 'is-resumed',
    stream: 'is-running'
  }
  return map[status] || ''
}

/** 节点状态文案 */
export function nodeStatusText(status) {
  const map = {
    running: '执行中…',
    completed: '已完成 ✓',
    error: '异常',
    retrying: '重试中…',
    pending: '待复核',
    waiting: '等待中',
    resumed: '已恢复',
    stream: '生成中…'
  }
  return map[status] || '等待'
}

export const taskStatusText = computed(() => {
  const map = {
    PENDING: '待审查',
    RUNNING: '审查中',
    NEEDS_REVIEW: '待人工复核',
    COMPLETED: '已完成',
    FAILED: '失败'
  }
  return map[store.taskStatus] || store.taskStatus
})
