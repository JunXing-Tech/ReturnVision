<template>
  <!-- 登录页主容器 -->
  <div class="login-page">
    <!-- 步骤1：品牌区域 -->
    <div class="brand-side">
      <div class="brand-content">
        <div class="brand-logo">RV</div>
        <h1 class="brand-title">退运智录</h1>
        <p class="brand-subtitle">ReturnVision</p>
        <div class="brand-features">
          <div class="feature-item">
            <ScanLine />
            <span>双引擎 OCR 交叉验证</span>
          </div>
          <div class="feature-item">
            <Document />
            <span>DeepSeek AI 智能分析</span>
          </div>
          <div class="feature-item">
            <CloudUpload />
            <span>飞书多维表格自动同步</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 步骤2：登录表单区域 -->
    <div class="form-side">
      <div class="form-card">
        <!-- 步骤2.1：标题 -->
        <div class="form-header">
          <h2 class="form-title">登录</h2>
          <p class="form-desc">使用账号密码登录，或点击飞书图标快速登录</p>
        </div>

        <!-- 步骤2.2：账号密码表单 -->
        <form class="login-form" @submit.prevent="handleLogin">
          <div class="field">
            <label class="field-label">用户名</label>
            <input
              v-model="username"
              type="text"
              class="field-input"
              placeholder="请输入用户名"
              autocomplete="username"
              :disabled="loading"
            />
          </div>

          <div class="field">
            <label class="field-label">密码</label>
            <input
              v-model="password"
              type="password"
              class="field-input"
              placeholder="请输入密码"
              autocomplete="current-password"
              :disabled="loading"
            />
          </div>

          <!-- 错误提示 -->
          <div v-if="errorMsg" class="error-msg">
            {{ errorMsg }}
          </div>

          <button type="submit" class="login-btn" :disabled="loading">
            {{ loading ? '登录中...' : '登录' }}
          </button>
        </form>

        <!-- 步骤2.3：分隔线 -->
        <div class="divider">
          <span>或</span>
        </div>

        <!-- 步骤2.4：飞书 OAuth 登录 -->
        <button class="feishu-btn" @click="handleFeishuLogin" :disabled="loading">
          <svg class="feishu-icon" viewBox="0 0 24 24" fill="currentColor">
            <path d="M3.5 9.5l4-4.5h7l-4 4.5h-7zm0 5l4-4.5h7l-4 4.5h-7zm0 5l4-4.5h7l-4 4.5h-7z"/>
          </svg>
          <span>飞书登录</span>
        </button>

        <!-- 步骤2.5：初始密码提示 -->
        <div class="hint">
          初始账号：admin / admin123（首次登录后请修改密码）
        </div>
      </div>
    </div>

    <!-- 步骤3：改密弹窗（首次登录强制改密） -->
    <div v-if="showChangePassword" class="modal-overlay">
      <div class="modal-card">
        <h3 class="modal-title">首次登录请修改密码</h3>
        <p class="modal-desc">检测到您使用的是初始密码，请修改后继续使用</p>

        <div class="field">
          <label class="field-label">旧密码</label>
          <input v-model="oldPassword" type="password" class="field-input" placeholder="请输入旧密码" />
        </div>
        <div class="field">
          <label class="field-label">新密码</label>
          <input v-model="newPassword" type="password" class="field-input" placeholder="请输入新密码" />
        </div>
        <div class="field">
          <label class="field-label">确认新密码</label>
          <input v-model="confirmPassword" type="password" class="field-input" placeholder="请再次输入新密码" />
        </div>

        <div v-if="changePwdError" class="error-msg">
          {{ changePwdError }}
        </div>

        <div class="modal-actions">
          <button class="btn-secondary" @click="handleSkipChangePwd" :disabled="loading">稍后修改</button>
          <button class="btn-primary" @click="handleChangePassword" :disabled="loading">
            {{ loading ? '提交中...' : '确认修改' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// 步骤4：组件状态与登录逻辑
import { ref } from 'vue';
import { ScanLine, Document, CloudUpload } from '../icons';
import api from '../api';
import { useAuth } from '../composables/useAuth';

const emit = defineEmits(['login-success']);

const { setTokens } = useAuth();

// 账号密码表单状态
const username = ref('');
const password = ref('');
const loading = ref(false);
const errorMsg = ref('');

// 改密弹窗状态
const showChangePassword = ref(false);
const oldPassword = ref('');
const newPassword = ref('');
const confirmPassword = ref('');
const changePwdError = ref('');

/**
 * 步骤5：账号密码登录
 */
const handleLogin = async () => {
  if (!username.value || !password.value) {
    errorMsg.value = '用户名和密码不能为空';
    return;
  }

  loading.value = true;
  errorMsg.value = '';

  try {
    const resp = await api.login(username.value, password.value);
    const { access_token, refresh_token, user } = resp.data.data;
    setTokens(access_token, refresh_token, user);

    // 初始密码需强制改密
    if (user.must_change_password) {
      oldPassword.value = password.value;
      showChangePassword.value = true;
    } else {
      emit('login-success');
    }
  } catch (err) {
    const msg = err.response?.data?.msg || '登录失败，请重试';
    errorMsg.value = msg;
  } finally {
    loading.value = false;
  }
};

/**
 * 步骤6：飞书 OAuth 登录
 */
const handleFeishuLogin = async () => {
  loading.value = true;
  errorMsg.value = '';

  try {
    const resp = await api.getFeishuAuthUrl();
    const { auth_url } = resp.data.data;
    // 跳转飞书授权页
    window.location.href = auth_url;
  } catch (err) {
    errorMsg.value = '飞书登录暂不可用';
  } finally {
    loading.value = false;
  }
};

/**
 * 步骤7：修改密码
 */
const handleChangePassword = async () => {
  if (!oldPassword.value || !newPassword.value) {
    changePwdError.value = '旧密码和新密码不能为空';
    return;
  }
  if (newPassword.value !== confirmPassword.value) {
    changePwdError.value = '两次输入的新密码不一致';
    return;
  }
  if (newPassword.value === 'admin123') {
    changePwdError.value = '新密码不能与初始密码相同';
    return;
  }

  loading.value = true;
  changePwdError.value = '';

  try {
    await api.changePassword(oldPassword.value, newPassword.value);
    showChangePassword.value = false;
    // 更新本地 user 的 must_change_password 标志
    const { user } = useAuth();
    if (user.value) {
      user.value.must_change_password = false;
      localStorage.setItem('returnvision-user', JSON.stringify(user.value));
    }
    emit('login-success');
  } catch (err) {
    changePwdError.value = err.response?.data?.msg || '修改密码失败';
  } finally {
    loading.value = false;
  }
};

/**
 * 步骤8：跳过改密（允许用户稍后修改）
 */
const handleSkipChangePwd = () => {
  showChangePassword.value = false;
  emit('login-success');
};
</script>

<style scoped>
.login-page {
  display: flex;
  min-height: 100vh;
  width: 100%;
}

/* 品牌区域 */
.brand-side {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, var(--brand-primary, #14b8a6) 0%, #0d9488 100%);
  color: #fff;
  padding: 48px;
}

.brand-content {
  max-width: 420px;
}

.brand-logo {
  width: 72px;
  height: 72px;
  background: rgba(255, 255, 255, 0.15);
  border-radius: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 24px;
  backdrop-filter: blur(10px);
}

.brand-title {
  font-size: 42px;
  font-weight: 700;
  margin: 0 0 8px 0;
  letter-spacing: -1px;
}

.brand-subtitle {
  font-size: 16px;
  opacity: 0.8;
  margin: 0 0 48px 0;
}

.brand-features {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 15px;
  opacity: 0.95;
}

.feature-item svg {
  width: 20px;
  height: 20px;
  opacity: 0.9;
}

/* 表单区域 */
.form-side {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px;
  background: var(--bg-primary, #f9fafb);
}

.form-card {
  width: 100%;
  max-width: 400px;
}

.form-header {
  margin-bottom: 32px;
}

.form-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 8px 0;
  color: var(--text-primary, #111827);
}

.form-desc {
  font-size: 14px;
  color: var(--text-secondary, #6b7280);
  margin: 0;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary, #6b7280);
}

.field-input {
  padding: 10px 14px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 8px;
  font-size: 14px;
  background: var(--bg-card, #fff);
  color: var(--text-primary, #111827);
  transition: border-color 0.2s;
}

.field-input:focus {
  outline: none;
  border-color: var(--brand-primary, #14b8a6);
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.1);
}

.field-input:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error-msg {
  padding: 10px 14px;
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
  border-radius: 8px;
  font-size: 13px;
}

.login-btn {
  padding: 11px 16px;
  background: var(--brand-primary, #14b8a6);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.2s;
}

.login-btn:hover:not(:disabled) {
  background: #0d9488;
}

.login-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.divider {
  display: flex;
  align-items: center;
  margin: 24px 0;
  color: var(--text-tertiary, #9ca3af);
  font-size: 13px;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--border-color, #e5e7eb);
}

.divider span {
  padding: 0 12px;
}

.feishu-btn {
  width: 100%;
  padding: 11px 16px;
  background: #fff;
  color: var(--text-primary, #111827);
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 8px;
  font-size: 15px;
  font-weight: 500;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: background 0.2s;
}

.feishu-btn:hover:not(:disabled) {
  background: #f9fafb;
}

.feishu-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.feishu-icon {
  width: 18px;
  height: 18px;
  color: #3370ff;
}

.hint {
  margin-top: 24px;
  padding: 10px 14px;
  background: rgba(59, 130, 246, 0.08);
  color: #3b82f6;
  border-radius: 8px;
  font-size: 12px;
  text-align: center;
}

/* 改密弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  backdrop-filter: blur(4px);
}

.modal-card {
  background: var(--bg-card, #fff);
  border-radius: 16px;
  padding: 32px;
  width: 90%;
  max-width: 440px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
}

.modal-title {
  font-size: 20px;
  font-weight: 700;
  margin: 0 0 8px 0;
  color: var(--text-primary, #111827);
}

.modal-desc {
  font-size: 13px;
  color: var(--text-secondary, #6b7280);
  margin: 0 0 24px 0;
}

.modal-card .field {
  margin-bottom: 14px;
}

.modal-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

.btn-secondary {
  flex: 1;
  padding: 10px 16px;
  background: transparent;
  color: var(--text-secondary, #6b7280);
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
}

.btn-secondary:hover:not(:disabled) {
  background: var(--bg-hover, #f3f4f6);
}

.btn-primary {
  flex: 1;
  padding: 10px 16px;
  background: var(--brand-primary, #14b8a6);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}

.btn-primary:hover:not(:disabled) {
  background: #0d9488;
}

.btn-primary:disabled,
.btn-secondary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 响应式：窄屏隐藏品牌区 */
@media (max-width: 768px) {
  .brand-side {
    display: none;
  }
  .form-side {
    padding: 24px;
  }
}
</style>
