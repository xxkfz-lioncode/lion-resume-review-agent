<script setup>
import { computed } from 'vue'
import MarkdownIt from 'markdown-it'
import { Download } from '@element-plus/icons-vue'
import { store } from '../store'
import { useReportExport } from '../composables/useReportExport'

const props = defineProps({
  report: { type: Object, default: null },
  streamingText: { type: String, default: '' }
})

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true
})

const rendered = computed(() => {
  const text = props.streamingText || props.report?.detailReport || ''
  if (!text) return ''
  return md.render(text)
})

const streaming = computed(() => !!props.streamingText && !!store.taskId)

// 报告完整生成后允许导出
const canExport = computed(() => !!props.report?.detailReport)

const overallScore = computed(() =>
  Number(props.report?.overallScore || 0).toFixed(1)
)

const { exportHtml: exportHtmlFile, exportMarkdown: exportMarkdownFile } = useReportExport()

const handleExport = (command) => {
  const payload = {
    detailReport: props.report.detailReport,
    overallScore: props.report.overallScore,
    radarData: props.report.radarData,
    fileName: 'AI简历审查报告'
  }
  if (command === 'html') exportHtmlFile(payload)
  else if (command === 'md') exportMarkdownFile(payload)
}
</script>

<template>
  <div class="report-panel">
    <div class="panel-header">
      <span class="panel-title">AI 诊断报告</span>
      <div class="header-actions">
        <el-tag v-if="streaming" size="small" type="success" effect="dark">打字机生成中…</el-tag>
        <el-tag v-else-if="props.report" size="small" type="primary">综合评分 {{ overallScore }} / 5</el-tag>
        <el-dropdown v-if="canExport" @command="handleExport">
          <el-button type="primary" size="small" :icon="Download">导出报告</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="html">HTML 文件（可直接打印）</el-dropdown-item>
              <el-dropdown-item command="md">Markdown 文件</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
    <div class="report-body">
      <div v-if="rendered" class="markdown" v-html="rendered"></div>
      <el-empty v-else description="审查完成后在此展示 AI 诊断报告（流式打字机效果）" />
    </div>
  </div>
</template>

<style scoped>
.report-panel {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  padding: 16px;
  height: 100%;
  display: flex;
  flex-direction: column;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.report-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 8px;
}
.markdown {
  font-size: 13px;
  line-height: 1.7;
  color: #303133;
}
.markdown h1 { font-size: 20px; margin: 8px 0; }
.markdown h2 { font-size: 16px; margin: 12px 0 6px; border-left: 4px solid #409eff; padding-left: 8px; }
.markdown h3 { font-size: 14px; margin: 8px 0 4px; }
.markdown p { margin: 6px 0; }
.markdown li { margin: 4px 0; }
</style>
