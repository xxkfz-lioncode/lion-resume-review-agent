<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  radarData: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false }
})

const chartEl = ref(null)
const panelEl = ref(null)
let chart = null
let observer = null

function render() {
  if (!chartEl.value) return
  if (!chart) chart = echarts.init(chartEl.value)

  const data = Array.isArray(props.radarData) ? props.radarData : []
  if (!data.length) {
    chart.clear()
    chart.setOption({
      title: {
        text: '等待评分数据…',
        left: 'center',
        top: 'middle',
        textStyle: { color: '#c0c4cc', fontSize: 14, fontWeight: 'normal' }
      }
    })
    return
  }

  const names = data.map((d) => d.name)
  const values = data.map((d) => Number(d.value) || 0)

  chart.setOption({
    tooltip: {},
    radar: {
      indicator: names.map((name) => ({ name, max: 5 })),
      radius: '65%',
      splitArea: { areaStyle: { color: ['rgba(64,158,255,0.02)', 'rgba(64,158,255,0.05)'] } },
      axisName: { color: '#606266', fontSize: 12 }
    },
    series: [
      {
        type: 'radar',
        data: [
          {
            value: values,
            name: '候选人',
            areaStyle: { color: 'rgba(64,158,255,0.25)' },
            lineStyle: { color: '#409eff', width: 2 },
            itemStyle: { color: '#409eff' }
          }
        ]
      }
    ]
  })
}

watch(() => props.radarData, render, { deep: true })
onMounted(() => {
  render()
  if (panelEl.value && typeof ResizeObserver !== 'undefined') {
    observer = new ResizeObserver(() => chart && chart.resize())
    observer.observe(panelEl.value)
  }
})
onBeforeUnmount(() => {
  if (observer) observer.disconnect()
  chart && chart.dispose()
})
</script>

<template>
  <div class="radar-panel">
    <div class="panel-header">
      <span class="panel-title">六维能力雷达图</span>
      <el-tag v-if="loading" size="small" type="warning">生成中</el-tag>
    </div>
    <div ref="chartEl" class="radar-chart"></div>
  </div>
</template>

<style scoped>
.radar-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  padding: 16px;
  box-sizing: border-box;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.radar-chart {
  flex: 1;
  min-height: 180px;
  width: 100%;
}
</style>
