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
.screen-shell {
  padding: 24px 32px;
  max-width: 1400px;
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
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color, #e5e7eb);
}

.toolbar {
  display: flex;
  gap: 8px;
  align-items: center;
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

.icon-btn {
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 8px;
  cursor: pointer;
  color: var(--text-secondary, #6b7280);
}

.icon-btn:hover:not(:disabled) {
  background: var(--bg-hover, #f3f4f6);
}

.icon-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.table-wrap {
  overflow-x: auto;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.data-table th {
  text-align: left;
  padding: 12px 16px;
  background: var(--bg-secondary, #f9fafb);
  color: var(--text-secondary, #6b7280);
  font-weight: 500;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.data-table td {
  padding: 12px 16px;
  border-top: 1px solid var(--border-color, #e5e7eb);
  color: var(--text-primary, #111827);
}

.role-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  margin-right: 4px;
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

.action-btn {
  padding: 4px 10px;
  background: transparent;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 6px;
  font-size: 12px;
  color: var(--text-secondary, #6b7280);
  cursor: pointer;
  margin-right: 4px;
}

.action-btn:hover:not(:disabled) {
  background: var(--bg-hover, #f3f4f6);
  color: var(--text-primary, #111827);
}

.action-warn {
  color: #f59e0b;
  border-color: rgba(245, 158, 11, 0.3);
}

.action-success {
  color: #22c55e;
  border-color: rgba(34, 197, 94, 0.3);
}

.action-danger {
  color: #ef4444;
  border-color: rgba(239, 68, 68, 0.3);
}

.action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

/* 弹窗 */
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
  padding: 28px;
  width: 90%;
  max-width: 480px;
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
  margin: 0 0 20px 0;
}

.form-field {
  margin-bottom: 16px;
}

.field-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-secondary, #6b7280);
  margin-bottom: 6px;
}

.field-input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 8px;
  font-size: 14px;
  background: var(--bg-card, #fff);
  color: var(--text-primary, #111827);
  box-sizing: border-box;
}

.field-input:focus {
  outline: none;
  border-color: var(--brand-primary, #14b8a6);
  box-shadow: 0 0 0 3px rgba(20, 184, 166, 0.1);
}

.field-input:disabled {
  opacity: 0.6;
  background: var(--bg-secondary, #f9fafb);
}

.role-checkboxes {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.role-checkbox {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  cursor: pointer;
}

.role-checkbox .role-desc {
  font-size: 12px;
  color: var(--text-secondary, #6b7280);
  margin-left: auto;
}

.error-msg {
  padding: 10px 14px;
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
  border-radius: 8px;
  font-size: 13px;
  margin-bottom: 16px;
}

.modal-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
}
</style>
