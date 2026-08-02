<template>
  <!-- 注册页主容器（视觉与登录页一致：居中实色白卡 + 淡光晕背景） -->
  <div class="login-host">
    <!-- 背景光晕层：淡品牌蓝径向光晕 -->
    <div class="bg-glow"></div>

    <!-- 内容层：居中，max-width 440px -->
    <div class="login-content">
      <!-- 品牌头部（与登录页完全一致） -->
      <div class="brand-head">
        <div class="brand-logo">RV</div>
        <h1 class="brand-title">退运智录</h1>
        <p class="brand-slogan">拍照识别快递面单，智能录入飞书</p>
      </div>

      <!-- 注册卡 -->
      <div class="login-card">
        <div class="card-head">
          <h2 class="card-title">注册账号</h2>
          <p class="card-subtitle">加入退运智录</p>
        </div>

        <!-- Tab 切换：公司管理员 / 员工 -->
        <div class="tab-switcher">
          <button
            type="button"
            class="tab-btn"
            :class="{ active: activeTab === 'admin' }"
            :disabled="loading || !!successMsg"
            @click="switchTab('admin')"
          >公司管理员注册</button>
          <button
            type="button"
            class="tab-btn"
            :class="{ active: activeTab === 'staff' }"
            :disabled="loading || !!successMsg"
            @click="switchTab('staff')"
          >员工注册</button>
        </div>

        <!-- 公司管理员注册表单 -->
        <form v-if="activeTab === 'admin'" class="login-form" @submit.prevent="handleRegister">
          <div class="form-field">
            <label class="form-label">用户名</label>
            <input v-model="username" type="text" class="form-input" placeholder="请输入用户名" autocomplete="username" :disabled="loading || !!successMsg" />
          </div>
          <div class="form-field">
            <label class="form-label">密码</label>
            <input v-model="password" type="password" class="form-input" placeholder="8 位以上，含字母和数字" autocomplete="new-password" :disabled="loading || !!successMsg" />
          </div>
          <div class="form-field">
            <label class="form-label">公司名称</label>
            <input v-model="orgName" type="text" class="form-input" placeholder="请输入公司名称" :disabled="loading || !!successMsg" />
          </div>
          <div class="form-field">
            <label class="form-label">飞书 App ID</label>
            <input v-model="appId" type="text" class="form-input" placeholder="飞书应用 App ID" autocomplete="off" :disabled="loading || !!successMsg" />
          </div>
          <div class="form-field">
            <label class="form-label">飞书 App Secret</label>
            <input v-model="appSecret" type="password" class="form-input" placeholder="飞书应用 App Secret" autocomplete="off" :disabled="loading || !!successMsg" />
          </div>

          <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
          <div v-if="successMsg" class="success-msg">{{ successMsg }}</div>

          <button type="submit" class="login-btn" :disabled="loading || !!successMsg">
            {{ loading ? '注册中...' : '注册' }}
          </button>
        </form>

        <!-- 员工注册表单 -->
        <form v-else class="login-form" @submit.prevent="handleRegister">
          <div class="form-field">
            <label class="form-label">用户名</label>
            <input v-model="username" type="text" class="form-input" placeholder="请输入用户名" autocomplete="username" :disabled="loading || !!successMsg" />
          </div>
          <div class="form-field">
            <label class="form-label">密码</label>
            <input v-model="password" type="password" class="form-input" placeholder="8 位以上，含字母和数字" autocomplete="new-password" :disabled="loading || !!successMsg" />
          </div>
          <div class="form-field">
            <label class="form-label">注册码</label>
            <input v-model="registerCode" type="text" class="form-input" placeholder="请向管理员获取注册码" :disabled="loading || !!successMsg" />
          </div>

          <div v-if="errorMsg" class="error-msg">{{ errorMsg }}</div>
          <div v-if="successMsg" class="success-msg">{{ successMsg }}</div>

          <button type="submit" class="login-btn" :disabled="loading || !!successMsg">
            {{ loading ? '注册中...' : '注册' }}
          </button>
        </form>

        <!-- 分隔线 -->
        <div class="divider"><span>或</span></div>

        <!-- 返回登录 -->
        <button type="button" class="back-link" :disabled="loading" @click="emit('back-to-login')">
          ← 返回登录
        </button>
      </div>

      <!-- 底部版权 -->
      <div class="copyright">© 2026 退运智录 · JunXing Tech</div>
    </div>
  </div>
</template>

<script setup>
// 步骤1：组件状态与注册逻辑
import { ref, onUnmounted } from 'vue';
import api from '../api';

const emit = defineEmits(['back-to-login']);

// 步骤2：表单状态
const activeTab = ref('admin');
const username = ref('');
const password = ref('');
const orgName = ref('');
const appId = ref('');
const appSecret = ref('');
const registerCode = ref('');

const loading = ref(false);
const errorMsg = ref('');
const successMsg = ref('');

let redirectTimer = null;
const clearRedirectTimer = () => {
  if (redirectTimer) {
    clearTimeout(redirectTimer);
    redirectTimer = null;
  }
};

// 步骤3：切换 tab —— 清空提示并取消进行中的跳转倒计时
const switchTab = (tab) => {
  if (activeTab.value === tab) return;
  activeTab.value = tab;
  errorMsg.value = '';
  successMsg.value = '';
  clearRedirectTimer();
};

// 步骤4：提交注册
const handleRegister = async () => {
  // 步骤4.1：清空提示
  errorMsg.value = '';
  successMsg.value = '';

  // 步骤4.2：基础非空校验
  if (!username.value || !password.value) {
    errorMsg.value = '用户名和密码不能为空';
    return;
  }

  // 步骤4.3：密码强度校验（8 位以上，同时含字母和数字）
  if (!/^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(password.value)) {
    errorMsg.value = '密码需 8 位以上，且同时包含字母和数字';
    return;
  }

  // 步骤4.4：按 tab 校验各自必填字段
  if (activeTab.value === 'admin') {
    if (!orgName.value || !appId.value || !appSecret.value) {
      errorMsg.value = '请完整填写公司名称与飞书应用凭证';
      return;
    }
  } else {
    if (!registerCode.value) {
      errorMsg.value = '请输入注册码';
      return;
    }
  }

  // 步骤4.5：调用注册接口（后端错误码 2006/2007/2008 的 message 直接展示）
  loading.value = true;
  try {
    if (activeTab.value === 'admin') {
      await api.registerAdmin(username.value, password.value, orgName.value, appId.value, appSecret.value);
    } else {
      await api.registerStaff(username.value, password.value, registerCode.value);
    }
    // 步骤4.6：成功提示，3 秒后跳回登录
    successMsg.value = '注册成功，3 秒后自动返回登录...';
    redirectTimer = setTimeout(() => {
      emit('back-to-login');
    }, 3000);
  } catch (err) {
    errorMsg.value = err.response?.data?.msg || '注册失败，请重试';
  } finally {
    loading.value = false;
  }
};

// 步骤5：组件卸载时清理定时器，避免在已卸载组件上 emit
onUnmounted(clearRedirectTimer);
</script>

<style scoped>
/* 注册页样式（与 LoginPanel 视觉一致：居中实色白卡 + 淡光晕） */
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

/* 注册卡（实色白卡） */
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

/* Tab 切换器 */
.tab-switcher {
  display: flex;
  gap: 4px;
  background: var(--color-bg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  padding: 4px;
}
.tab-btn {
  flex: 1;
  height: 34px;
  background: transparent;
  color: var(--color-fg-muted);
  border: none;
  border-radius: calc(var(--radius) - 2px);
  font-size: 13px;
  font-family: var(--font-sans);
  cursor: pointer;
  transition: var(--transition);
}
.tab-btn:hover:not(:disabled):not(.active) {
  color: var(--color-fg);
  background: var(--color-card);
}
.tab-btn.active {
  background: var(--color-card);
  color: var(--color-primary);
  font-weight: 500;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}
.tab-btn:disabled { opacity: 0.6; cursor: not-allowed; }

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

/* 错误提示 */
.error-msg {
  padding: 10px 14px;
  background: var(--color-error-subtle);
  color: var(--color-error-strong);
  border-radius: var(--radius);
  font-size: 13px;
}

/* 成功提示 */
.success-msg {
  padding: 10px 14px;
  background: var(--color-success-subtle);
  color: var(--color-success-strong);
  border-radius: var(--radius);
  font-size: 13px;
}

/* 注册按钮 */
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

/* 返回登录链接 */
.back-link {
  width: 100%;
  height: 38px;
  background: transparent;
  color: var(--color-fg-muted);
  border: none;
  font-size: 13px;
  font-family: var(--font-sans);
  cursor: pointer;
  transition: var(--transition);
}
.back-link:hover:not(:disabled) { color: var(--color-primary); }
.back-link:disabled { opacity: 0.6; cursor: not-allowed; }

/* 底部版权 */
.copyright {
  font-size: 11px;
  color: var(--color-fg-muted);
  text-align: center;
}

/* 响应式 */
@media (max-width: 480px) {
  .login-host { padding: 20px 16px; }
  .login-card { padding: 24px; }
}
</style>
