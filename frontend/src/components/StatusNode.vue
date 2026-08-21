<script setup>
import { computed } from 'vue'
import { nodeStateClass, nodeStatusText } from '../store'

const props = defineProps({
  data: {
    type: Object,
    required: true
  }
})

const stateClass = computed(() => nodeStateClass(props.data.status))
const statusText = computed(() => {
  if (props.data.statusText) return props.data.statusText
  return nodeStatusText(props.data.status)
})

const icon = computed(() => {
  switch (props.data.status) {
    case 'completed': return '✓'
    case 'error': return '✕'
    case 'running':
    case 'stream': return '◌'
    case 'retrying': return '↻'
    case 'pending':
    case 'waiting': return '⏸'
    case 'resumed': return '▶'
    default: return '○'
  }
})
</script>

<template>
  <div class="status-node" :class="stateClass">
    <div class="node-icon">{{ icon }}</div>
    <div class="node-body">
      <div class="node-label">{{ data.label }}</div>
      <div class="node-status-text" :title="data.message || ''">{{ statusText }}</div>
    </div>
  </div>
</template>
