import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  breaks: true
})

/**
 * 报告导出工具（HTML / Markdown）
 * 供主面板与历史报告详情页复用
 */
export function useReportExport() {
  const buildFileName = (prefix = 'AI简历审查报告') => {
    const d = new Date()
    const pad = (n) => String(n).padStart(2, '0')
    return `${prefix}_${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}`
  }

  const downloadBlob = (content, name, type) => {
    const blob = new Blob([content], { type })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = name
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`报告已导出：${name}`)
  }

  /**
   * @param {Object} p
   * @param {string} p.detailReport  Markdown 正文
   * @param {number|string} p.overallScore 综合评分
   * @param {string|Array} p.radarData 六维评分（JSON 字符串或数组）
   * @param {string} p.fileName 导出文件名（不含扩展名）
   */
  const exportHtml = ({ detailReport = '', overallScore = 0, radarData = '', fileName = buildFileName() }) => {
    const score = Number(overallScore || 0).toFixed(1)
    let rows = ''
    try {
      const data = typeof radarData === 'string' ? JSON.parse(radarData) : radarData
      if (Array.isArray(data)) {
        rows = data
          .map((d) => `<tr><td>${d.name || ''}</td><td>${Number(d.value || 0).toFixed(1)} / 5</td></tr>`)
          .join('')
      }
    } catch {
      rows = ''
    }
    const html = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${fileName}</title>
<style>
  body { font-family: "Microsoft YaHei", "PingFang SC", "Helvetica Neue", Arial, sans-serif; max-width: 920px; margin: 40px auto; padding: 0 28px; color: #303133; line-height: 1.8; }
  h1 { font-size: 24px; border-bottom: 2px solid #409eff; padding-bottom: 12px; }
  h2 { font-size: 18px; margin-top: 32px; border-left: 4px solid #409eff; padding-left: 10px; }
  h3 { font-size: 16px; }
  p { margin: 8px 0; }
  table { border-collapse: collapse; width: 100%; margin: 16px 0; }
  th, td { border: 1px solid #e4e7ed; padding: 8px 12px; text-align: left; }
  th { background: #f5f7fa; font-weight: 600; }
  code { background: #f5f7fa; padding: 2px 6px; border-radius: 4px; font-family: Consolas, monospace; }
  pre { background: #f5f7fa; padding: 12px 16px; border-radius: 8px; overflow: auto; }
  pre code { background: none; padding: 0; }
  blockquote { border-left: 4px solid #e4e7ed; margin: 8px 0; padding-left: 16px; color: #909399; }
  ul, ol { padding-left: 24px; }
  .meta { display: flex; gap: 16px; align-items: center; margin: 20px 0; padding: 16px 20px; background: #f0f9eb; border: 1px solid #b7eb8f; border-radius: 8px; }
  .score { font-size: 20px; font-weight: 700; color: #67c23a; }
  .score-label { color: #606266; font-size: 14px; }
  .radar-table { max-width: 480px; }
</style>
</head>
<body>
<h1>AI 简历审查报告</h1>
<div class="meta">
  <span class="score-label">综合评分</span>
  <span class="score">${score} / 5</span>
  <span class="score-label">报告生成时间：${new Date().toLocaleString('zh-CN')}</span>
</div>
<h2>六维能力评估</h2>
<table class="radar-table">
<thead><tr><th>评估维度</th><th>得分</th></tr></thead>
<tbody>${rows || '<tr><td colspan="2">暂无评分数据</td></tr>'}</tbody>
</table>
<h2>详细诊断报告</h2>
${md.render(detailReport || '')}
</body>
</html>`
    downloadBlob(html, `${fileName}.html`, 'text/html;charset=utf-8')
  }

  const exportMarkdown = ({ detailReport = '', fileName = buildFileName() }) => {
    downloadBlob(detailReport, `${fileName}.md`, 'text/markdown;charset=utf-8')
  }

  return { buildFileName, exportHtml, exportMarkdown }
}
