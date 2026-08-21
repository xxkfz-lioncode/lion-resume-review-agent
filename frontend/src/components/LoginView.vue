<template>
  <div class="login-page">
    <div class="login-card">
      <div class="login-logo">
        <el-icon :size="40" color="#409eff"><Avatar /></el-icon>
      </div>
      <h1 class="login-title">AI 简历审查系统</h1>
      <p class="login-subtitle">智能简历评估 · 岗位匹配 · 多维度诊断</p>

      <el-form class="login-form" label-position="top" @submit.prevent>
        <el-form-item>
          <el-input
            v-model="form.username"
            placeholder="用户名"
            size="large"
            :prefix-icon="User"
            clearable
          />
        </el-form-item>
        <el-form-item>
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            size="large"
            :prefix-icon="Lock"
            show-password
            @keyup.enter="doLogin"
          />
        </el-form-item>
        <el-button
          type="primary"
          size="large"
          class="login-btn"
          :loading="loading"
          @click="doLogin"
        >
          登 录
        </el-button>
      </el-form>

      <div class="login-tip">演示账号：admin / admin123</div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Lock, Avatar } from '@element-plus/icons-vue'
import api from '../api'
import { store } from '../store'

const form = ref({ username: '', password: '' })
const loading = ref(false)

const doLogin = async () => {
  if (!form.value.username.trim() || !form.value.password.trim()) {
    ElMessage.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    const data = await api.post('/auth/login', {
      username: form.value.username.trim(),
      password: form.value.password
    })
    localStorage.setItem('satoken', data.token)
    store.username = form.value.username.trim()
    store.loggedIn = true
  } catch (e) {
    ElMessage.error(`登录失败：${e.message}`)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e3a5f 0%, #2c5f8a 50%, #409eff 100%);
  position: relative;
  overflow: hidden;
}
.login-page::before,
.login-page::after {
  content: '';
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
}
.login-page::before {
  width: 420px;
  height: 420px;
  top: -120px;
  right: -100px;
}
.login-page::after {
  width: 520px;
  height: 520px;
  bottom: -180px;
  left: -140px;
}
.login-card {
  position: relative;
  z-index: 2;
  width: 400px;
  background: #fff;
  border-radius: 16px;
  padding: 40px 36px 28px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.25);
}
.login-logo {
  display: flex;
  justify-content: center;
  margin-bottom: 12px;
}
.login-title {
  text-align: center;
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 6px;
}
.login-subtitle {
  text-align: center;
  font-size: 13px;
  color: #909399;
  margin-bottom: 28px;
}
.login-form {
  margin-top: 4px;
}
.login-btn {
  width: 100%;
  margin-top: 8px;
}
.login-tip {
  text-align: center;
  color: #909399;
  font-size: 12px;
  margin-top: 18px;
}
</style>
