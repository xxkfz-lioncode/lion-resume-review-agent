<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled, Document } from '@element-plus/icons-vue'
import api from '../api'
import { store } from '../store'

const canStart = computed(() => !!store.uploadedResume)

const emit = defineEmits(['started'])

const fileList = ref([])
const jobDescription = ref('')
const starting = ref(false)
const uploadUrl = '/api/resume/upload'
const uploadHeaders = computed(() => {
  if (!store.loggedIn) return {}
  return { satoken: localStorage.getItem('satoken') }
})

const beforeUpload = (file) => {
  const sizeOk = file.size / 1024 / 1024 < 20
  if (!sizeOk) ElMessage.warning('文件不能超过 20MB')
  return sizeOk
}

const handleUploadSuccess = (response) => {
  const data = response.data || response
  fileList.value = [{ name: data.fileName, size: data.fileSize, resumeId: data.resumeId }]
  store.uploadedResume = data
  ElMessage.success(`简历上传成功: ${data.fileName}`)
}

const handleUploadError = () => {
  ElMessage.error('上传失败，请检查后端服务')
}

const clearUpload = () => {
  store.uploadedResume = null
  fileList.value = []
}

const startReview = async () => {
  const resume = store.uploadedResume
  if (!resume) {
    ElMessage.warning('请先上传简历')
    return
  }
  starting.value = true
  try {
    const data = await api.post('/review/start', {
      resumeId: resume.resumeId,
      jobDescription: jobDescription.value
    })
    ElMessage.success('审查任务已启动')
    emit('started', data.taskId)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    starting.value = false
  }
}
</script>

<template>
  <div class="upload-panel">
    <div class="panel-header">
      <span class="panel-title">简历上传</span>
    </div>

    <el-upload
      v-if="!store.uploadedResume"
      drag
      :action="uploadUrl"
      :headers="uploadHeaders"
      :before-upload="beforeUpload"
      :on-success="handleUploadSuccess"
      :on-error="handleUploadError"
      :show-file-list="false"
      style="width: 100%"
    >
      <el-icon style="font-size: 40px; color: #909399"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖拽简历到此处，或 <em>点击上传</em></div>
      <template #tip>
        <div class="el-upload__tip">支持 PDF / TXT / MD，最大 20MB</div>
      </template>
    </el-upload>

    <div v-else class="uploaded-card">
      <el-icon :size="28" color="#67C23A"><Document /></el-icon>
      <div class="uploaded-meta">
        <div class="uploaded-name">{{ store.uploadedResume.fileName }}</div>
        <div class="uploaded-size">{{ (store.uploadedResume.fileSize / 1024).toFixed(1) }} KB</div>
      </div>
      <el-button text type="primary" @click="clearUpload">重新上传</el-button>
    </div>

    <div class="field-label">岗位描述（JD）</div>
    <el-input
      v-model="jobDescription"
      type="textarea"
      :rows="6"
      placeholder="粘贴目标岗位描述，例如：&#10;岗位：高级 Java 工程师&#10;要求：5 年以上后端开发经验，熟悉 Spring Cloud、高并发架构、分布式中间件…"
    />

    <el-button
      type="primary"
      size="large"
      style="width: 100%; margin-top: 16px"
      :loading="starting"
      @click="startReview"
    >
      发起 AI 简历审查
    </el-button>
  </div>
</template>

<style scoped>
.upload-panel {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  padding: 16px;
}
.panel-header {
  margin-bottom: 12px;
}
.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.uploaded-info {
  margin-top: 12px;
}
.uploaded-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 8px;
  margin-bottom: 12px;
}
.uploaded-meta {
  flex: 1;
  min-width: 0;
}
.uploaded-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  word-break: break-all;
}
.uploaded-size {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.field-label {
  margin: 14px 0 8px;
  font-size: 13px;
  color: #606266;
  font-weight: 600;
}
</style>
