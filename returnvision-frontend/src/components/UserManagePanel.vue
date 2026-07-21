<template>
  <!-- 步骤1：用户管理页面外壳 -->
  <div class="screen-shell">
    <!-- 步骤2：Hero 区 -->
    <section class="hero">
      <div class="eyebrow">管理</div>
      <h1 class="hero-title">用户管理</h1>
      <p class="hero-subtitle">管理员后台 · 创建/编辑/禁用/删除用户账号</p>
    </section>

    <!-- 步骤3：用户卡片 -->
    <section class="card">
      <div class="card-header">
        <div class="card-heading">
          <div class="eyebrow">数据</div>
          <h3 class="card-title">用户列表</h3>
        </div>
        <div class="toolbar">
          <button class="btn-primary" @click="openCreateDialog">
            <Plus /> 新建用户
          </button>
          <button class="icon-btn" :disabled="loading" @click="loadUsers">
            <Refresh />
          </button>
        </div>
      </div>

      <!-- 步骤4：用户表格 -->
      <div v-loading="loading" class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>用户名</th>
              <th>显示名</th>
              <th>角色</th>
              <th>飞书绑定</th>
              <th>状态</th>
              <th>最后登录</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="user in users" :key="user.id">
              <td>{{ user.id }}</td>
              <td>{{ user.username }}</td>
              <td>{{ user.display_name || '-' }}</td>
              <td>
                <span v-for="role in user.roles" :key="role" :class="['role-tag', `role-${role.toLowerCase()}`]">
                  {{ roleText(role) }}
                </span>
              </td>
              <td>{{ user.feishu_user_id ? '已绑定' : '未绑定' }}</td>
              <td>
                <span :class="['status-tag', user.status === 'active' ? 'status-active' : 'status-disabled']">
                  {{ user.status === 'active' ? '启用' : '禁用' }}
                </span>
              </td>
              <td>{{ formatTime(user.last_login_at) }}</td>
              <td>
                <button class="action-btn" @click="openEditDialog(user)">编辑</button>
                <button class="action-btn" @click="openResetPasswordDialog(user)">重置密码</button>
                <button
                  v-if="user.status === 'active'"
                  class="action-btn action-warn"
                  @click="toggleStatus(user)"
                >禁用</button>
                <button
                  v-else
                  class="action-btn action-success"
                  @click="toggleStatus(user)"
                >启用</button>
                <button
                  class="action-btn action-danger"
                  :disabled="user.id === currentUserId"
                  @click="deleteUser(user)"
                >删除</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- 步骤5：新建/编辑用户弹窗 -->
    <div v-if="showUserDialog" class="modal-overlay" @click.self="closeUserDialog">
      <div class="modal-card">
        <h3 class="modal-title">{{ editingUser ? '编辑用户' : '新建用户' }}</h3>

        <div class="form-field">
          <label class="field-label">用户名</label>
          <input
            v-model="formData.username"
            type="text"
            class="field-input"
            :disabled="editingUser"
            placeholder="登录用户名"
          />
        </div>

        <div v-if="!editingUser" class="form-field">
          <label class="field-label">初始密码</label>
          <input v-model="formData.password" type="password" class="field-input" placeholder="用户首次登录后可修改" />
        </div>

        <div class="form-field">
          <label class="field-label">显示名</label>
          <input v-model="formData.displayName" type="text" class="field-input" placeholder="如：张三" />
        </div>

        <div class="form-field">
          <label class="field-label">角色</label>
          <div class="role-checkboxes">
            <label v-for="opt in roleOptions" :key="opt.code" class="role-checkbox">
              <input type="checkbox" :value="opt.code" v-model="formData.roleCodes" />
              <span>{{ opt.name }}</span>
              <span class="role-desc">{{ opt.desc }}</span>
            </label>
          </div>
        </div>

        <div class="form-field">
          <label class="field-label">飞书 user_id（可选）</label>
          <input v-model="formData.feishuUserId" type="text" class="field-input" placeholder="绑定后可用飞书 OAuth 登录" />
        </div>

        <div v-if="formError" class="error-msg">{{ formError }}</div>

        <div class="modal-actions">
          <button class="btn-secondary" @click="closeUserDialog" :disabled="submitting">取消</button>
          <button class="btn-primary" @click="submitUserForm" :disabled="submitting">
            {{ submitting ? '提交中...' : '确认' }}
          </button>
        </div>
      </div>
    </div>

    <!-- 步骤6：重置密码弹窗 -->
    <div v-if="showResetDialog" class="modal-overlay" @click.self="showResetDialog = false">
      <div class="modal-card">
        <h3 class="modal-title">重置密码</h3>
        <p class="modal-desc">为用户 <b>{{ resetTargetUser?.username }}</b> 重置密码，重置后该用户需重新登录</p>

        <div class="form-field">
          <label class="field-label">新密码</label>
          <input v-model="newPassword" type="password" class="field-input" placeholder="请输入新密码" />
        </div>

        <div v-if="formError" class="error-msg">{{ formError }}</div>

        <div class="modal-actions">
          <button class="btn-secondary" @click="showResetDialog = false" :disabled="submitting">取消</button>
          <button class="btn-primary" @click="submitResetPassword" :disabled="submitting">
            {{ submitting ? '提交中...' : '确认重置' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// 步骤7：组件状态与业务逻辑
import { ref, onMounted, computed } from 'vue';
import { Plus, Refresh } from '../icons';
import api from '../api';
import { useAuth } from '../composables/useAuth';

const { user: currentUser } = useAuth();
const currentUserId = computed(() => currentUser.value?.id);

const users = ref([]);
const loading = ref(false);

// 用户弹窗状态
const showUserDialog = ref(false);
const editingUser = ref(null);
const submitting = ref(false);
const formError = ref('');
const formData = ref({
  username: '',
  password: '',
  displayName: '',
  roleCodes: ['STAFF'],
  feishuUserId: '',
});

// 重置密码弹窗状态
const showResetDialog = ref(false);
const resetTargetUser = ref(null);
const newPassword = ref('');

// 角色选项
const roleOptions = [
  { code: 'STAFF', name: '客服', desc: '录入退货记录' },
  { code: 'SUPERVISOR', name: '主管', desc: '审核+统计+导出' },
  { code: 'ADMIN', name: '管理员', desc: '用户管理+全权限' },
];

const roleText = (code) => {
  const opt = roleOptions.find(o => o.code === code);
  return opt ? opt.name : code;
};

const formatTime = (t) => {
  if (!t) return '从未登录';
  return new Date(t).toLocaleString('zh-CN');
};

/**
 * 步骤8：加载用户列表
 */
const loadUsers = async () => {
  loading.value = true;
  try {
    const resp = await api.listUsers();
    users.value = resp.data.data.users;
  } catch (err) {
    console.error('加载用户失败', err);
  } finally {
    loading.value = false;
  }
};

/**
 * 步骤9：打开新建用户弹窗
 */
const openCreateDialog = () => {
  editingUser.value = null;
  formData.value = {
    username: '',
    password: '',
    displayName: '',
    roleCodes: ['STAFF'],
    feishuUserId: '',
  };
  formError.value = '';
  showUserDialog.value = true;
};

/**
 * 步骤10：打开编辑用户弹窗
 */
const openEditDialog = (user) => {
  editingUser.value = user;
  formData.value = {
    username: user.username,
    password: '',
    displayName: user.display_name || '',
    roleCodes: [...user.roles],
    feishuUserId: user.feishu_user_id || '',
  };
  formError.value = '';
  showUserDialog.value = true;
};

const closeUserDialog = () => {
  showUserDialog.value = false;
  formError.value = '';
};

/**
 * 步骤11：提交用户表单（新建/编辑）
 */
const submitUserForm = async () => {
  formError.value = '';

  if (!formData.value.username) {
    formError.value = '用户名不能为空';
    return;
  }
  if (!editingUser.value && !formData.value.password) {
    formError.value = '初始密码不能为空';
    return;
  }
  if (formData.value.roleCodes.length === 0) {
    formError.value = '至少选择一个角色';
    return;
  }

  submitting.value = true;
  try {
    if (editingUser.value) {
      await api.updateUser(editingUser.value.id, {
        displayName: formData.value.displayName,
        roleCodes: formData.value.roleCodes,
        feishuUserId: formData.value.feishuUserId,
      });
    } else {
      await api.createUser(
        formData.value.username,
        formData.value.password,
        formData.value.displayName,
        formData.value.roleCodes,
        formData.value.feishuUserId
      );
    }
    showUserDialog.value = false;
    await loadUsers();
  } catch (err) {
    formError.value = err.response?.data?.msg || '操作失败';
  } finally {
    submitting.value = false;
  }
};

/**
 * 步骤12：切换用户状态（启用/禁用）
 */
const toggleStatus = async (user) => {
  const newStatus = user.status === 'active' ? 'disabled' : 'active';
  try {
    await api.updateUser(user.id, { status: newStatus });
    await loadUsers();
  } catch (err) {
    alert(err.response?.data?.msg || '操作失败');
  }
};

/**
 * 步骤13：删除用户
 */
const deleteUser = async (user) => {
  if (!confirm(`确认删除用户 ${user.username}？此操作不可恢复`)) return;
  try {
    await api.deleteUser(user.id);
    await loadUsers();
  } catch (err) {
    alert(err.response?.data?.msg || '删除失败');
  }
};

/**
 * 步骤14：重置密码
 */
const openResetPasswordDialog = (user) => {
  resetTargetUser.value = user;
  newPassword.value = '';
  formError.value = '';
  showResetDialog.value = true;
};

const submitResetPassword = async () => {
  if (!newPassword.value) {
    formError.value = '新密码不能为空';
    return;
  }
  submitting.value = true;
  try {
    await api.resetPassword(resetTargetUser.value.id, newPassword.value);
    showResetDialog.value = false;
    alert('密码已重置，请告知用户用新密码登录');
  } catch (err) {
    formError.value = err.response?.data?.msg || '重置失败';
  } finally {
    submitting.value = false;
  }
};

onMounted(() => {
  loadUsers();
});
</script>

<style scoped>
/* 步骤15：用户管理页面样式（源力设计系统 v3.0） */
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
.hero-title { font-size: 22px; font-weight: 600; color: var(--color-fg); }
.hero-subtitle { font-size: 13px; color: var(--color-fg-muted); }

/* 卡片 */
.card {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  overflow: hidden;
  box-shadow: var(--shadow-md);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
}
.card-heading { display: flex; flex-direction: column; gap: 2px; }
.card-title { font-size: 14px; font-weight: 600; color: var(--color-fg); }
.toolbar { display: flex; gap: 8px; align-items: center; }

/* 按钮 */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 14px;
  height: 34px;
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
.btn-primary svg { width: 14px; height: 14px; }

.btn-secondary {
  display: inline-flex;
  align-items: center;
  height: 34px;
  padding: 0 14px;
  background: transparent;
  color: var(--color-fg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 13px;
  font-family: var(--font-sans);
  cursor: pointer;
  transition: var(--transition);
}
.btn-secondary:hover { background: var(--color-muted); }

/* 图标按钮 */
.icon-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  cursor: pointer;
  color: var(--color-fg-muted);
  transition: var(--transition);
}
.icon-btn:hover:not(:disabled) { background: var(--color-muted); color: var(--color-fg); }
.icon-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.icon-btn svg { width: 14px; height: 14px; }

/* 表格 */
.table-wrap { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table th {
  text-align: left;
  padding: 10px 16px;
  background: var(--color-muted);
  color: var(--color-fg-muted);
  font-family: var(--font-mono);
  font-weight: 500;
  font-size: 10.5px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  white-space: nowrap;
}
.data-table td {
  padding: 12px 16px;
  border-top: 1px solid var(--color-border);
  color: var(--color-fg);
  white-space: nowrap;
}
.data-table tbody tr:hover { background: var(--color-muted); }
.data-table td:first-child { font-family: var(--font-mono); font-size: 12px; }
.data-table td:nth-child(2) { font-family: var(--font-mono); }

/* 角色徽章（胶囊形，色块区分） */
.role-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  margin-right: 4px;
}
.role-staff {
  background: var(--color-success-subtle);
  color: var(--color-success-strong);
}
.role-supervisor {
  background: var(--color-warning-subtle);
  color: var(--color-warning-strong);
}
.role-admin {
  background: var(--color-primary);
  color: var(--color-primary-fg);
}

/* 状态徽章 */
.status-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
}
.status-active {
  background: var(--color-success-subtle);
  color: var(--color-success-strong);
}
.status-disabled {
  background: var(--color-error-subtle);
  color: var(--color-error-strong);
}

/* 行内操作按钮 */
.action-btn {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  height: 26px;
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 12px;
  font-family: var(--font-sans);
  color: var(--color-fg);
  cursor: pointer;
  margin-right: 4px;
  transition: var(--transition);
}
.action-btn:hover:not(:disabled) { background: var(--color-muted); }
.action-warn { color: var(--color-warning-strong); border-color: var(--color-warning-subtle); }
.action-warn:hover:not(:disabled) { background: var(--color-warning-subtle); }
.action-success { color: var(--color-success-strong); border-color: var(--color-success-subtle); }
.action-success:hover:not(:disabled) { background: var(--color-success-subtle); }
.action-danger { color: var(--color-error-strong); border-color: var(--color-error-subtle); }
.action-danger:hover:not(:disabled) { background: var(--color-error-subtle); }
.action-btn:disabled { opacity: 0.4; cursor: not-allowed; }

/* 弹窗 */
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
  max-width: 480px;
  box-shadow: var(--shadow-lg);
}
.modal-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0 0 8px 0;
  color: var(--color-fg);
}
.modal-desc {
  font-size: 13px;
  color: var(--color-fg-muted);
  margin: 0 0 20px 0;
}
.modal-desc b { color: var(--color-fg); font-weight: 600; }

/* 表单字段 */
.form-field { margin-bottom: 16px; }
.field-label {
  display: block;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-fg-muted);
  margin-bottom: 6px;
}
.field-input {
  width: 100%;
  height: 34px;
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
.field-input:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(56, 123, 255, 0.15);
}
.field-input:disabled {
  opacity: 0.6;
  background: var(--color-muted);
  cursor: not-allowed;
}

/* 角色复选框 */
.role-checkboxes { display: flex; flex-direction: column; gap: 8px; }
.role-checkbox {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  cursor: pointer;
  color: var(--color-fg);
}
.role-checkbox .role-desc {
  font-size: 12px;
  color: var(--color-fg-muted);
  margin-left: auto;
}

/* 错误提示 */
.error-msg {
  padding: 10px 14px;
  background: var(--color-error-subtle);
  color: var(--color-error-strong);
  border-radius: var(--radius);
  font-size: 13px;
  margin-bottom: 16px;
}

/* 弹窗操作按钮 */
.modal-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}

/* 响应式 */
@media (max-width: 992px) {
  .screen-shell { padding: calc(var(--spacing) * 3); }
  .card-header { flex-wrap: wrap; }
  .table-wrap { overflow-x: auto; }
}
</style>
