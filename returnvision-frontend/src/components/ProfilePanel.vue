<template>
  <!-- 步骤1：个人中心页面外壳 -->
  <div class="screen-shell">
    <!-- 步骤2：Hero 区 -->
    <section class="hero">
      <div class="eyebrow">账户</div>
      <h1 class="hero-title">个人中心</h1>
      <p class="hero-subtitle">查看账户信息 · 修改显示名 · 修改密码</p>
    </section>

    <!-- 步骤3：账户信息卡片 -->
    <section class="card">
      <div class="card-header">
        <div class="card-heading">
          <div class="eyebrow">信息</div>
          <h3 class="card-title">账户信息</h3>
        </div>
        <button v-if="!editingProfile" class="btn-primary" @click="startEditProfile">
          <Pen /> 修改显示名
        </button>
      </div>

      <div v-loading="loading" class="profile-grid">
        <div class="profile-field">
          <div class="field-label">用户名</div>
          <div class="field-value">{{ profile.username || '-' }}</div>
          <div class="field-hint">登录标识，不可修改</div>
        </div>

        <div class="profile-field">
          <div class="field-label">显示名</div>
          <div v-if="!editingProfile" class="field-value">{{ profile.display_name || '-' }}</div>
          <input v-else v-model="editDisplayName" type="text" class="field-input" placeholder="请输入显示名" />
          <div class="field-hint">用于界面展示</div>
        </div>

        <div class="profile-field">
          <div class="field-label">角色</div>
          <div class="field-value">
            <span v-for="role in (profile.roles || [])" :key="role" :class="['role-tag', `role-${role.toLowerCase()}`]">
              {{ roleText(role) }}
            </span>
          </div>
          <div class="field-hint">由管理员分配</div>
        </div>

        <div class="profile-field">
          <div class="field-label">飞书绑定</div>
          <div class="field-value">
            <span :class="['status-tag', profile.feishu_bound ? 'status-active' : 'status-disabled']">
              {{ profile.feishu_bound ? '已绑定' : '未绑定' }}
            </span>
          </div>
          <div class="field-hint">由管理员绑定</div>
        </div>

        <div class="profile-field">
          <div class="field-label">最后登录时间</div>
          <div class="field-value">{{ formatTime(profile.last_login_at) }}</div>
        </div>

        <div class="profile-field">
          <div class="field-label">账号创建时间</div>
          <div class="field-value">{{ formatTime(profile.created_at) }}</div>
        </div>
      </div>

      <!-- 编辑模式下的操作按钮 -->
      <div v-if="editingProfile" class="edit-actions">
        <button class="btn-secondary" @click="cancelEditProfile" :disabled="submitting">取消</button>
        <button class="btn-primary" @click="submitProfile" :disabled="submitting">
          {{ submitting ? '保存中...' : '保存' }}
        </button>
      </div>

      <div v-if="profileError" class="error-msg">{{ profileError }}</div>
    </section>

    <!-- 步骤4：修改密码卡片 -->
    <section class="card">
      <div class="card-header">
        <div class="card-heading">
          <div class="eyebrow">安全</div>
          <h3 class="card-title">修改密码</h3>
        </div>
      </div>

      <div class="password-form">
        <div class="form-field">
          <label class="field-label">旧密码</label>
          <input v-model="passwordForm.oldPassword" type="password" class="field-input" placeholder="请输入当前密码" />
        </div>
        <div class="form-field">
          <label class="field-label">新密码</label>
          <input v-model="passwordForm.newPassword" type="password" class="field-input" placeholder="请输入新密码" />
        </div>
        <div class="form-field">
          <label class="field-label">确认新密码</label>
          <input v-model="passwordForm.confirmPassword" type="password" class="field-input" placeholder="请再次输入新密码" />
        </div>

        <div v-if="passwordError" class="error-msg">{{ passwordError }}</div>
        <div v-if="passwordSuccess" class="success-msg">{{ passwordSuccess }}</div>

        <button class="btn-primary" @click="submitPassword" :disabled="submitting">
          {{ submitting ? '提交中...' : '确认修改密码' }}
        </button>
      </div>
    </section>
  </div>
</template>

<script setup>
// 步骤5：组件状态与业务逻辑
import { ref, onMounted } from 'vue';
import { Pen } from '../icons';
import api from '../api';
import { useAuth } from '../composables/useAuth';

const { user, setTokens } = useAuth();

const profile = ref({});
const loading = ref(false);
const submitting = ref(false);

// 编辑显示名状态
const editingProfile = ref(false);
const editDisplayName = ref('');
const profileError = ref('');

// 修改密码状态
const passwordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
});
const passwordError = ref('');
const passwordSuccess = ref('');

const roleOptions = [
  { code: 'STAFF', name: '客服' },
  { code: 'SUPERVISOR', name: '主管' },
  { code: 'ADMIN', name: '管理员' },
];

const roleText = (code) => {
  const opt = roleOptions.find(o => o.code === code);
  return opt ? opt.name : code;
};

const formatTime = (t) => {
  if (!t) return '-';
  return new Date(t).toLocaleString('zh-CN');
};

/**
 * 步骤6：加载个人资料
 */
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

/**
 * 步骤7：修改显示名
 */
const startEditProfile = () => {
  editDisplayName.value = profile.value.display_name || '';
  profileError.value = '';
  editingProfile.value = true;
};

const cancelEditProfile = () => {
  editingProfile.value = false;
  profileError.value = '';
};

const submitProfile = async () => {
  if (!editDisplayName.value) {
    profileError.value = '显示名不能为空';
    return;
  }
  submitting.value = true;
  profileError.value = '';
  try {
    await api.updateProfile(editDisplayName.value);
    profile.value.display_name = editDisplayName.value;
    // 同步更新 useAuth 中的 user 信息
    if (user.value) {
      user.value.display_name = editDisplayName.value;
      localStorage.setItem('returnvision-user', JSON.stringify(user.value));
    }
    editingProfile.value = false;
  } catch (err) {
    profileError.value = err.response?.data?.msg || '修改失败';
  } finally {
    submitting.value = false;
  }
};

/**
 * 步骤8：修改密码
 */
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
.screen-shell {
  padding: 24px 32px;
  max-width: 900px;
  margin: 0 auto;
}

.hero {
  margin-bottom: 24px;
}

.eyebrow {
  font-size: 12px;
  font-weight: 600;
  color: var(--brand-primary, #14b8a6);
  text-transform: uppercase;
  letter-spacing: 1px;
  margin-bottom: 8px;
}

.hero-title {
  font-size: 28px;
  font-weight: 700;
  margin: 0 0 4px 0;
  color: var(--text-primary, #111827);
}

.hero-subtitle {
  font-size: 14px;
  color: var(--text-secondary, #6b7280);
  margin: 0;
}

.card {
  background: var(--bg-card, #fff);
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-color, #e5e7eb);
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
  color: var(--text-primary, #111827);
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.profile-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--text-secondary, #6b7280);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.field-value {
  font-size: 15px;
  color: var(--text-primary, #111827);
  display: flex;
  align-items: center;
  gap: 6px;
}

.field-hint {
  font-size: 12px;
  color: var(--text-tertiary, #9ca3af);
}

.field-input {
  padding: 8px 12px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 8px;
  font-size: 14px;
  background: var(--bg-card, #fff);
  color: var(--text-primary, #111827);
}

.field-input:focus {
  outline: none;
  border-color: var(--brand-primary, #14b8a6);
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.1);
}

.role-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.role-staff {
  background: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
}

.role-supervisor {
  background: rgba(168, 85, 247, 0.1);
  color: #a855f7;
}

.role-admin {
  background: rgba(20, 184, 166, 0.1);
  color: #14b8a6;
}

.status-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.status-active {
  background: rgba(34, 197, 94, 0.1);
  color: #22c55e;
}

.status-disabled {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.edit-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--border-color, #e5e7eb);
}

.btn-primary {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: var(--brand-primary, #14b8a6);
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.btn-primary:hover:not(:disabled) {
  background: #0d9488;
}

.btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.btn-secondary {
  padding: 8px 14px;
  background: transparent;
  color: var(--text-secondary, #6b7280);
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 8px;
  font-size: 13px;
  cursor: pointer;
}

.btn-secondary:hover:not(:disabled) {
  background: var(--bg-hover, #f3f4f6);
}

.password-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-width: 400px;
}

.form-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.error-msg {
  padding: 10px 14px;
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
  border-radius: 8px;
  font-size: 13px;
}

.success-msg {
  padding: 10px 14px;
  background: rgba(34, 197, 94, 0.1);
  color: #16a34a;
  border-radius: 8px;
  font-size: 13px;
}

@media (max-width: 768px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
