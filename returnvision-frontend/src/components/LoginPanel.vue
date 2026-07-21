<template>
  <!-- 登录页主容器（居中实色白卡 + 淡光晕背景，对齐设计稿 login-v2） -->
  <div class="login-host">
    <!-- 背景光晕层：淡品牌蓝径向光晕 -->
    <div class="bg-glow"></div>

    <!-- 内容层：居中，max-width 440px -->
    <div class="login-content">
      <!-- 品牌头部 -->
      <div class="brand-head">
        <div class="brand-logo">RV</div>
        <h1 class="brand-title">退运智录</h1>
        <p class="brand-slogan">拍照识别快递面单，智能录入飞书</p>
      </div>

      <!-- 登录卡 -->
      <div class="login-card">
        <div class="card-head">
          <h2 class="card-title">登录工作台</h2>
          <p class="card-subtitle">欢迎回来</p>
        </div>

        <!-- 账号密码表单 -->
        <form class="login-form" @submit.prevent="handleLogin">
          <div class="form-field">
            <label class="form-label">用户名</label>
            <input v-model="username" type="text" class="form-input" placeholder="请输入用户名" autocomplete="username" :disabled="loading" />
          </div>
          <div class="form-field">
            <label class="form-label">密码</label>
            <input v-model="password" type="password" class="form-input" placeholder="请输入密码" autocomplete="current-password" :disabled="loading" />
          </div>

          <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>

          <button type="submit" class="login-btn" :disabled="loading">
            {{ loading ? '登录中...' : '登录' }}
          </button>
        </form>

        <!-- 分隔线 -->
        <div class="divider"><span>或</span></div>

        <!-- 飞书登录 -->
        <button class="feishu-btn" @click="handleFeishuLogin" :disabled="loading">
          <svg class="feishu-icon" viewBox="0 0 24 24" fill="currentColor">
            <path d="M3.5 9.5l4-4.5h7l-4 4.5h-7zm0 5l4-4.5h7l-4 4.5h-7zm0 5l4-4.5h7l-4 4.5h-7z"/>
          </svg>
          <span>飞书登录</span>
        </button>

        <!-- 初始密码提示 -->
        <div class="hint">初始账号：admin / admin123（首次登录后请修改密码）</div>
      </div>

      <!-- 底部产品特性点 -->
      <div class="feature-points">
        <div class="feature-point">
          <div class="feature-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
          </div>
          <div class="feature-text">
            <div class="feature-title">双引擎交叉验证</div>
            <div class="feature-desc">智谱 + 阿里云双重识别</div>
          </div>
        </div>
        <div class="feature-point">
          <div class="feature-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .962 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.582a.5.5 0 0 1 0 .962L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.962 0z"/></svg>
          </div>
          <div class="feature-text">
            <div class="feature-title">DeepSeek 智能分析</div>
            <div class="feature-desc">AI 推断退货原因与分类</div>
          </div>
        </div>
        <div class="feature-point">
          <div class="feature-icon">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z"/><path d="m21.854 2.147-10.94 10.939"/></svg>
          </div>
          <div class="feature-text">
            <div class="feature-title">一键写入飞书</div>
            <div class="feature-desc">自动同步多维表格</div>
          </div>
        </div>
      </div>

      <!-- 底部版权 -->
      <div class="copyright">© 2026 退运智录 · JunXing Tech</div>
    </div>
  </div>

  <!-- 改密弹窗（首次登录强制改密） -->
  <div v-if="showChangePassword" class="modal-overlay">
    <div class="modal-card">
      <h3 class="modal-title">首次登录请修改密码</h3>
      <p class="modal-desc">检测到您使用的是初始密码，请修改后继续使用</p>
      <div class="form-field">
        <label class="form-label">旧密码</label>
        <input v-model="oldPassword" type="password" class="form-input" placeholder="请输入旧密码" />
      </div>
      <div class="form-field">
        <label class="form-label">新密码</label>
        <input v-model="newPassword" type="password" class="form-input" placeholder="请输入新密码" />
      </div>
      <div class="form-field">
        <label class="form-label">确认新密码</label>
        <input v-model="confirmPassword" type="password" class="form-input" placeholder="请再次输入新密码" />
      </div>
      <div v-if="changePwdError" class="error-msg">{{ changePwdError }}</div>
      <div class="modal-actions">
        <button class="btn-secondary" @click="handleSkipChangePwd" :disabled="loading">稍后修改</button>
        <button class="btn-primary" @click="handleChangePassword" :disabled="loading">
          {{ loading ? '提交中...' : '确认修改' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
// 步骤4：组件状态与登录逻辑
import { ref } from 'vue';
import api from '../api';
import { useAuth } from '../composables/useAuth';

const emit = defineEmits(['login-success']);

const setError = (msg) => { errorMsg.value = msg; };
defineExpose({ setError });

const { setTokens } = useAuth();

const username = ref('');
const password = ref('');
const loading = ref(false);
const errorMsg = ref('');

const showChangePassword = ref(false);
const oldPassword = ref('');
const newPassword = ref('');
const confirmPassword = ref('');
const changePwdError = ref('');

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
    if (user.must_change_password) {
      oldPassword.value = password.value;
      showChangePassword.value = true;
    } else {
      emit('login-success');
    }
  } catch (err) {
    errorMsg.value = err.response?.data?.msg || '登录失败，请重试';
  } finally {
    loading.value = false;
  }
};

const handleFeishuLogin = async () => {
  loading.value = true;
  errorMsg.value = '';
  try {
    const resp = await api.getFeishuAuthUrl();
    const { auth_url } = resp.data.data;
    window.location.href = auth_url;
  } catch (err) {
    errorMsg.value = '飞书登录暂不可用';
  } finally {
    loading.value = false;
  }
};

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

const handleSkipChangePwd = () => {
  showChangePassword.value = false;
  emit('login-success');
};
</script>

<style scoped>
/* 步骤15：登录页样式（源力设计系统 v3.0，居中实色白卡 + 淡光晕，对齐设计稿 login-v2） */
.login-host {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg);
  position: relative;
  overflow: hidden;
  padding: 40px 20px;
}

/* 背景光晕层：淡品牌蓝径向光晕 */
.bg-glow {
  position: absolute;
  top: -100px;
  left: 50%;
  transform: translateX(-50%);
  width: 800px;
  height: 600px;
  background: radial-gradient(ellipse at center, var(--color-primary) 0%, transparent 70%);
  opacity: 0.05;
  pointer-events: none;
  z-index: 0;
}

/* 内容层：居中 */
.login-content {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 440px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24px;
}

/* 品牌头部 */
.brand-head {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}
.brand-logo {
  width: 48px;
  height: 48px;
  border-radius: 999px;
  background: var(--color-primary);
  color: var(--color-primary-fg);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  font-family: var(--font-mono);
  margin-bottom: 4px;
}
.brand-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--color-fg);
  margin: 0;
  font-family: var(--font-sans);
}
.brand-slogan {
  font-size: 13px;
  color: var(--color-fg-muted);
  margin: 0;
}

/* 登录卡（实色白卡） */
.login-card {
  width: 100%;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  box-shadow: var(--shadow-lg);
  padding: 32px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.card-head { display: flex; flex-direction: column; gap: 4px; }
.card-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-fg);
  margin: 0;
}
.card-subtitle {
  font-size: 12px;
  color: var(--color-fg-muted);
  margin: 0;
}

/* 表单 */
.login-form { display: flex; flex-direction: column; gap: 16px; }
.form-field { display: flex; flex-direction: column; gap: 6px; }
.form-label {
  font-family: var(--font-mono);
  font-size: 10.5px;
  font-weight: 500;
  color: var(--color-fg-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}
.form-input {
  width: 100%;
  height: 40px;
  padding: 0 14px;
  border: 1px solid var(--color-input);
  border-radius: var(--radius);
  font-size: 14px;
  font-family: var(--font-sans);
  background: var(--color-bg);
  color: var(--color-fg);
  box-sizing: border-box;
  outline: none;
  transition: border-color var(--transition), box-shadow var(--transition);
}
.form-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(56, 123, 255, 0.15);
}
.form-input:disabled { opacity: 0.6; cursor: not-allowed; }

.error-msg {
  padding: 10px 14px;
  background: var(--color-error-subtle);
  color: var(--color-error-strong);
  border-radius: var(--radius);
  font-size: 13px;
}

/* 登录按钮 */
.login-btn {
  width: 100%;
  height: 40px;
  background: var(--color-primary);
  color: var(--color-primary-fg);
  border: none;
  border-radius: var(--radius);
  font-size: 14px;
  font-weight: 500;
  font-family: var(--font-sans);
  cursor: pointer;
  transition: var(--transition);
}
.login-btn:hover:not(:disabled) { background: var(--color-primary-hover); }
.login-btn:disabled { opacity: 0.6; cursor: not-allowed; }

/* 分隔线 */
.divider {
  display: flex;
  align-items: center;
  color: var(--color-fg-muted);
  font-size: 12px;
}
.divider::before, .divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--color-border);
}
.divider span { padding: 0 12px; }

/* 飞书登录 */
.feishu-btn {
  width: 100%;
  height: 40px;
  background: var(--color-card);
  color: var(--color-fg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 14px;
  font-weight: 500;
  font-family: var(--font-sans);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  transition: var(--transition);
}
.feishu-btn:hover:not(:disabled) { background: var(--color-muted); }
.feishu-btn:disabled { opacity: 0.6; cursor: not-allowed; }
.feishu-icon { width: 18px; height: 18px; color: #3370ff; }

/* 初始密码提示 */
.hint {
  padding: 10px 14px;
  background: var(--color-accent);
  color: var(--color-accent-fg);
  border-radius: var(--radius);
  font-size: 12px;
  text-align: center;
}

/* 底部产品特性点（3 个横排） */
.feature-points {
  display: flex;
  gap: 24px;
  justify-content: center;
  flex-wrap: wrap;
}
.feature-point {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  text-align: center;
  max-width: 120px;
}
.feature-icon {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-primary);
}
.feature-icon svg { width: 20px; height: 20px; }
.feature-text { display: flex; flex-direction: column; gap: 2px; }
.feature-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-fg);
}
.feature-desc {
  font-size: 10px;
  color: var(--color-fg-muted);
}

/* 底部版权 */
.copyright {
  font-size: 11px;
  color: var(--color-fg-muted);
  text-align: center;
}

/* 改密弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(12, 13, 14, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.modal-card {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  padding: 28px;
  width: 90%;
  max-width: 440px;
  box-shadow: var(--shadow-lg);
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.modal-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: var(--color-fg);
}
.modal-desc { font-size: 13px; color: var(--color-fg-muted); margin: 0; }
.modal-actions { display: flex; gap: 12px; margin-top: 8px; }
.btn-secondary {
  flex: 1;
  height: 38px;
  background: transparent;
  color: var(--color-fg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 14px;
  font-family: var(--font-sans);
  cursor: pointer;
  transition: var(--transition);
}
.btn-secondary:hover:not(:disabled) { background: var(--color-muted); }
.btn-primary {
  flex: 1;
  height: 38px;
  background: var(--color-primary);
  color: var(--color-primary-fg);
  border: none;
  border-radius: var(--radius);
  font-size: 14px;
  font-weight: 500;
  font-family: var(--font-sans);
  cursor: pointer;
  transition: var(--transition);
}
.btn-primary:hover:not(:disabled) { background: var(--color-primary-hover); }
.btn-primary:disabled, .btn-secondary:disabled { opacity: 0.6; cursor: not-allowed; }

/* 响应式 */
@media (max-width: 480px) {
  .login-host { padding: 20px 16px; }
  .login-card { padding: 24px; }
  .feature-points { gap: 16px; }
  .feature-point { max-width: 100px; }
}
</style>
