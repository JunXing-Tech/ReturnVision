<template>
  <!-- 步骤1：个人中心页面外壳 -->
  <div class="screen-shell">
    <!-- 步骤2：Hero 区 -->
    <section class="hero">
      <div class="eyebrow">账户</div>
      <h1 class="hero-title">个人中心</h1>
      <p class="hero-subtitle">管理你的账户信息与安全设置</p>
    </section>

    <!-- 步骤3：三列等宽布局（账户信息 / 修改显示名 / 修改密码） -->
    <div class="profile-triple">
      <!-- 模块1：账户信息 -->
      <section class="card">
        <div class="card-header">
          <div class="card-heading">
            <div class="eyebrow">信息</div>
            <h3 class="card-title">账户信息</h3>
          </div>
        </div>
        <div v-loading="loading" class="card-body">
          <!-- 头像 + 用户名摘要 -->
          <div class="account-hero">
            <div class="account-avatar">{{ userInitials }}</div>
            <div class="account-meta">
              <div class="account-name">{{ profile.display_name || profile.username || '-' }}</div>
              <div class="account-roles">
                <span v-for="role in (profile.roles || [])" :key="role" :class="['role-tag', `role-${role.toLowerCase()}`]">
                  {{ roleText(role) }}
                </span>
              </div>
            </div>
          </div>
          <!-- 信息字段 -->
          <div class="info-list">
            <div class="info-field">
              <span class="info-label">用户名</span>
              <span class="info-value mono">{{ profile.username || '-' }}</span>
            </div>
            <div class="info-field">
              <span class="info-label">飞书绑定</span>
              <span :class="['status-tag', profile.feishu_bound ? 'status-active' : 'status-disabled']">
                {{ profile.feishu_bound ? '已绑定' : '未绑定' }}
              </span>
            </div>
            <div class="info-field">
              <span class="info-label">最后登录</span>
              <span class="info-value mono">{{ formatTime(profile.last_login_at) }}</span>
            </div>
            <div class="info-field">
              <span class="info-label">注册时间</span>
              <span class="info-value mono">{{ formatTime(profile.created_at) }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 模块2：修改显示名 -->
      <section class="card">
        <div class="card-header">
          <div class="card-heading">
            <div class="eyebrow">设置</div>
            <h3 class="card-title">修改显示名</h3>
          </div>
        </div>
        <div class="card-body">
          <div class="form-field">
            <label class="form-label">当前显示名</label>
            <div class="form-static">{{ profile.display_name || '-' }}</div>
          </div>
          <div class="form-field">
            <label class="form-label">新显示名</label>
            <input v-model="editDisplayNameInline" type="text" class="form-input" placeholder="请输入新的显示名" />
          </div>
          <div v-if="profileError" class="error-msg">{{ profileError }}</div>
          <div class="form-actions">
            <button class="btn-primary" @click="submitProfileInline" :disabled="submitting">
              {{ submitting ? '保存中...' : '保存' }}
            </button>
          </div>
        </div>
      </section>

      <!-- 模块3：修改密码 -->
      <section class="card">
        <div class="card-header">
          <div class="card-heading">
            <div class="eyebrow">安全</div>
            <h3 class="card-title">修改密码</h3>
          </div>
        </div>
        <div class="card-body">
          <div class="form-field">
            <label class="form-label">当前密码</label>
            <input v-model="passwordForm.oldPassword" type="password" class="form-input" placeholder="请输入当前密码" />
          </div>
          <div class="form-field">
            <label class="form-label">新密码</label>
            <input v-model="passwordForm.newPassword" type="password" class="form-input" placeholder="请输入新密码" />
          </div>
          <div class="form-field">
            <label class="form-label">确认新密码</label>
            <input v-model="passwordForm.confirmPassword" type="password" class="form-input" placeholder="请再次输入新密码" />
          </div>
          <div v-if="passwordError" class="error-msg">{{ passwordError }}</div>
          <div v-if="passwordSuccess" class="success-msg">{{ passwordSuccess }}</div>
          <div class="form-actions">
            <button class="btn-primary" @click="submitPassword" :disabled="submitting">
              {{ submitting ? '提交中...' : '确认修改' }}
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
// 步骤5：组件状态与业务逻辑
import { ref, computed, onMounted } from 'vue';
import api from '../api';
import { useAuth } from '../composables/useAuth';

const { user, setTokens } = useAuth();

const profile = ref({});
const loading = ref(false);
const submitting = ref(false);

// 角色映射
const roleMap = { STAFF: '客服', SUPERVISOR: '主管', ADMIN: '管理员' };
const roleText = (code) => roleMap[code] || code;

// 用户首字母（头像）
const userInitials = computed(() => {
  const name = profile.value.display_name || profile.value.username || user.value?.username || 'RV';
  return name.slice(0, 2).toUpperCase();
});

// 修改显示名
const editDisplayNameInline = ref('');
const profileError = ref('');

// 修改密码
const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' });
const passwordError = ref('');
const passwordSuccess = ref('');

const formatTime = (t) => {
  if (!t) return '-';
  return new Date(t).toLocaleString('zh-CN');
};

// 加载个人资料
const loadProfile = async () => {
  loading.value = true;
  try {
    const resp = await api.getProfile();
    profile.value = resp.data.data;
  } catch (err) {
    console.error('加载个人资料失败', err);
  } finally {
    loading.value = false;
  }
};

// 提交修改显示名
const submitProfileInline = async () => {
  if (!editDisplayNameInline.value) {
    profileError.value = '显示名不能为空';
    return;
  }
  submitting.value = true;
  profileError.value = '';
  try {
    await api.updateProfile(editDisplayNameInline.value);
    profile.value.display_name = editDisplayNameInline.value;
    if (user.value) {
      user.value.display_name = editDisplayNameInline.value;
      localStorage.setItem('returnvision-user', JSON.stringify(user.value));
    }
    editDisplayNameInline.value = '';
  } catch (err) {
    profileError.value = err.response?.data?.msg || '修改失败';
  } finally {
    submitting.value = false;
  }
};

// 提交修改密码
const submitPassword = async () => {
  passwordError.value = '';
  passwordSuccess.value = '';
  if (!passwordForm.value.oldPassword || !passwordForm.value.newPassword) {
    passwordError.value = '旧密码和新密码不能为空';
    return;
  }
  if (passwordForm.value.newPassword !== passwordForm.value.confirmPassword) {
    passwordError.value = '两次输入的新密码不一致';
    return;
  }
  submitting.value = true;
  try {
    await api.changePassword(passwordForm.value.oldPassword, passwordForm.value.newPassword);
    passwordSuccess.value = '密码修改成功，下次登录请使用新密码';
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' };
  } catch (err) {
    passwordError.value = err.response?.data?.msg || '修改密码失败';
  } finally {
    submitting.value = false;
  }
};

onMounted(() => {
  loadProfile();
});
</script>

<style scoped>
/* 步骤15：个人中心页面样式（源力设计系统 v3.0，三列等宽） */
.screen-shell {
  padding: calc(var(--spacing) * 5);
  display: flex;
  flex-direction: column;
  gap: calc(var(--spacing) * 4);
}

/* Hero 区 */
.hero { display: flex; flex-direction: column; gap: 4px; }
.eyebrow {
  font-family: var(--font-mono);
  font-size: 10.5px;
  font-weight: 500;
  color: var(--color-fg-muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
  margin-bottom: 4px;
}
.hero-title { font-size: 22px; font-weight: 600; color: var(--color-fg); margin: 0; }
.hero-subtitle { font-size: 13px; color: var(--color-fg-muted); margin: 0; }

/* 三列等宽布局（核心：3个模块大小一致） */
.profile-triple {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: calc(var(--spacing) * 4);
  align-items: stretch;
}

/* 卡片（统一高度，stretch 拉伸） */
.card {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  box-shadow: var(--shadow-md);
  display: flex;
  flex-direction: column;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
}
.card-heading { display: flex; flex-direction: column; gap: 2px; }
.card-title { font-size: 14px; font-weight: 600; color: var(--color-fg); margin: 0; }
.card-body {
  padding: 20px;
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 账户信息卡 - 头像区 */
.account-hero {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
}
.account-avatar {
  width: 48px;
  height: 48px;
  border-radius: 999px;
  background: var(--color-primary);
  color: var(--color-primary-fg);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 600;
  font-family: var(--font-mono);
  flex-shrink: 0;
}
.account-meta { flex: 1; min-width: 0; }
.account-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-fg);
  margin-bottom: 4px;
}
.account-roles { display: flex; gap: 4px; flex-wrap: wrap; }

/* 账户信息卡 - 信息列表 */
.info-list { display: flex; flex-direction: column; gap: 12px; }
.info-field {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.info-label {
  font-size: 12px;
  color: var(--color-fg-muted);
  flex-shrink: 0;
}
.info-value {
  font-size: 13px;
  color: var(--color-fg);
  text-align: right;
}
.mono { font-family: var(--font-mono); }

/* 角色徽章 */
.role-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  font-family: var(--font-sans);
}
.role-staff { background: var(--color-success-subtle); color: var(--color-success-strong); }
.role-supervisor { background: var(--color-warning-subtle); color: var(--color-warning-strong); }
.role-admin { background: var(--color-primary); color: var(--color-primary-fg); }

/* 状态徽章 */
.status-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  font-family: var(--font-sans);
}
.status-active { background: var(--color-success-subtle); color: var(--color-success-strong); }
.status-disabled { background: var(--color-error-subtle); color: var(--color-error-strong); }

/* 表单字段 */
.form-field { display: flex; flex-direction: column; gap: 6px; }
.form-label {
  font-family: var(--font-mono);
  font-size: 10.5px;
  font-weight: 500;
  color: var(--color-fg-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}
.form-static {
  font-size: 14px;
  color: var(--color-fg);
  padding: 8px 12px;
  background: var(--color-muted);
  border-radius: var(--radius);
}
.form-input {
  width: 100%;
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--color-input);
  border-radius: var(--radius);
  font-size: 13px;
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

/* 表单操作区（推到底部，让三列高度一致） */
.form-actions {
  margin-top: auto;
  padding-top: 8px;
}

/* 按钮 */
.btn-primary {
  width: 100%;
  height: 36px;
  background: var(--color-primary);
  color: var(--color-primary-fg);
  border: none;
  border-radius: var(--radius);
  font-size: 13px;
  font-weight: 500;
  font-family: var(--font-sans);
  cursor: pointer;
  transition: var(--transition);
}
.btn-primary:hover:not(:disabled) { background: var(--color-primary-hover); }
.btn-primary:disabled { opacity: 0.55; cursor: not-allowed; }

/* 提示消息 */
.error-msg {
  padding: 8px 12px;
  background: var(--color-error-subtle);
  color: var(--color-error-strong);
  border-radius: var(--radius);
  font-size: 12px;
}
.success-msg {
  padding: 8px 12px;
  background: var(--color-success-subtle);
  color: var(--color-success-strong);
  border-radius: var(--radius);
  font-size: 12px;
}

/* 响应式 */
@media (max-width: 1100px) {
  .profile-triple { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 768px) {
  .screen-shell { padding: calc(var(--spacing) * 3); }
  .profile-triple { grid-template-columns: 1fr; }
}
</style>
