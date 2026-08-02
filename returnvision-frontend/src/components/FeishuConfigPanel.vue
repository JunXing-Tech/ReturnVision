<template>
  <!-- 步骤1：飞书配置管理页面外壳（嵌入 App.vue 主内容区，非独立页） -->
  <div class="screen-shell">
    <!-- 步骤2：Hero 区 -->
    <section class="hero">
      <div class="eyebrow">管理</div>
      <h1 class="hero-title">飞书配置管理</h1>
      <p class="hero-subtitle">维护飞书应用配置 · 生成/撤销员工注册码</p>
    </section>

    <!-- 步骤3：飞书配置卡片 -->
    <section class="card">
      <div class="card-header">
        <div class="card-heading">
          <div class="eyebrow">数据</div>
          <h3 class="card-title">飞书配置列表</h3>
        </div>
        <div class="toolbar">
          <button class="icon-btn" :disabled="loadingConfigs" @click="loadConfigs" aria-label="刷新飞书配置">
            <Refresh />
          </button>
        </div>
      </div>

      <!-- 步骤4：飞书配置表格 -->
      <div class="table-wrap">
        <div v-if="loadingConfigs" class="loading-mask">加载中...</div>
        <table class="data-table">
          <thead>
            <tr>
              <th>组织名称</th>
              <th>App ID</th>
              <th>App Secret</th>
              <th>状态</th>
              <th>创建时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="config in configs" :key="config.id">
              <td>{{ config.org_name || '-' }}</td>
              <td class="mono">{{ config.app_id || '-' }}</td>
              <td class="mono secret">******</td>
              <td>
                <span :class="['tag', config.status === 'active' ? 'tag-active' : 'tag-disabled']">
                  {{ config.status === 'active' ? '启用' : '禁用' }}
                </span>
              </td>
              <td>{{ formatTime(config.created_at) }}</td>
              <td>
                <!-- 仅平台ADMIN 可禁用，且仅 active 时显示 -->
                <button
                  v-if="isPlatformAdmin && config.status === 'active'"
                  class="btn btn-danger"
                  @click="disableConfig(config)"
                >禁用</button>
                <span v-else class="readonly-hint">只读</span>
              </td>
            </tr>
            <tr v-if="!loadingConfigs && configs.length === 0">
              <td colspan="6" class="empty-row">{{ configError || '暂无飞书配置' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- 步骤5：注册码卡片 -->
    <section class="card">
      <div class="card-header">
        <div class="card-heading">
          <div class="eyebrow">数据</div>
          <h3 class="card-title">注册码列表</h3>
        </div>
        <div class="toolbar">
          <!-- 仅公司ADMIN 可生成注册码（平台ADMIN 无绑定公司，后端会拒绝） -->
          <button v-if="!isPlatformAdmin" class="btn-primary" @click="openCodeDialog">
            <Plus /> 生成注册码
          </button>
          <button class="icon-btn" :disabled="loadingCodes" @click="loadCodes" aria-label="刷新注册码">
            <Refresh />
          </button>
        </div>
      </div>

      <!-- 步骤6：注册码表格 -->
      <div class="table-wrap">
        <div v-if="loadingCodes" class="loading-mask">加载中...</div>
        <table class="data-table">
          <thead>
            <tr>
              <th>注册码</th>
              <th>最大次数</th>
              <th>已用</th>
              <th>过期时间</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="code in codes" :key="code.id">
              <td class="mono">{{ code.code }}</td>
              <td>{{ code.max_uses }}</td>
              <td>{{ code.used_count }}</td>
              <td>{{ code.expires_at || '永不过期' }}</td>
              <td>
                <span :class="['tag', code.status === 'active' ? 'tag-active' : 'tag-disabled']">
                  {{ code.status === 'active' ? '可用' : '已撤销' }}
                </span>
              </td>
              <td>
                <!-- active 状态可撤销（平台/公司ADMIN 后端均允许，公司ADMIN 仅限本公司） -->
                <button
                  v-if="code.status === 'active'"
                  class="btn btn-danger"
                  @click="revokeCode(code)"
                >撤销</button>
                <span v-else class="readonly-hint">-</span>
              </td>
            </tr>
            <tr v-if="!loadingCodes && codes.length === 0">
              <td colspan="6" class="empty-row">{{ codeError || '暂无注册码' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- 步骤7：生成注册码弹窗 -->
    <div v-if="showCodeDialog" class="modal-overlay" @click.self="closeCodeDialog">
      <div class="modal-card">
        <h3 class="modal-title">生成注册码</h3>
        <p class="modal-desc">为当前公司生成一个新的员工注册码，员工可用此码完成自助注册</p>

        <div class="form-field">
          <label class="field-label">最大可用次数</label>
          <input
            v-model.number="codeForm.max_uses"
            type="number"
            min="1"
            class="field-input"
            placeholder="默认 1"
          />
        </div>

        <div class="form-field">
          <label class="field-label">过期时间（留空 = 永不过期）</label>
          <input
            v-model="codeForm.expires_at"
            type="datetime-local"
            class="field-input"
          />
        </div>

        <div v-if="codeFormError" class="error-msg">{{ codeFormError }}</div>

        <div class="modal-actions">
          <button class="btn" :disabled="submitting" @click="closeCodeDialog">取消</button>
          <button class="btn-primary" :disabled="submitting" @click="submitCodeForm">
            {{ submitting ? '提交中...' : '确认生成' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
// 步骤8：组件状态与业务逻辑
import { ref, computed, onMounted } from 'vue';
import { Plus, Refresh } from '../icons';
import api from '../api';
import { useAuth } from '../composables/useAuth';

const { user } = useAuth();
// 平台ADMIN：feishu_config_id 为空（无绑定公司）；公司ADMIN：已绑定某公司配置
const isPlatformAdmin = computed(() => !(user.value?.feishu_config_id));

// 飞书配置列表状态
const configs = ref([]);
const loadingConfigs = ref(false);
const configError = ref('');

// 注册码列表状态
const codes = ref([]);
const loadingCodes = ref(false);
const codeError = ref('');

// 生成注册码弹窗状态
const showCodeDialog = ref(false);
const submitting = ref(false);
const codeFormError = ref('');
const codeForm = ref({
  max_uses: 1,
  expires_at: '',
});

/**
 * 步骤9：健壮的时间格式化（兼容 ISO 字符串 / Date 可解析值）
 */
const formatTime = (t) => {
  if (!t) return '-';
  const d = new Date(t);
  return isNaN(d.getTime()) ? String(t) : d.toLocaleString('zh-CN');
};

/**
 * 步骤10：加载飞书配置列表
 */
const loadConfigs = async () => {
  loadingConfigs.value = true;
  configError.value = '';
  try {
    const resp = await api.listFeishuConfigs();
    configs.value = resp.data?.data || [];
  } catch (err) {
    configError.value = err.response?.data?.msg || '加载飞书配置失败';
    console.error('[飞书配置] 加载失败', err);
  } finally {
    loadingConfigs.value = false;
  }
};

/**
 * 步骤11：加载注册码列表
 */
const loadCodes = async () => {
  loadingCodes.value = true;
  codeError.value = '';
  try {
    const resp = await api.listRegisterCodes();
    codes.value = resp.data?.data || [];
  } catch (err) {
    codeError.value = err.response?.data?.msg || '加载注册码失败';
    console.error('[注册码] 加载失败', err);
  } finally {
    loadingCodes.value = false;
  }
};

/**
 * 步骤12：禁用飞书配置（仅平台ADMIN，二次确认后调用）
 */
const disableConfig = async (config) => {
  if (!confirm(`确认禁用「${config.org_name || config.app_id}」的飞书配置？禁用后该公司相关功能将不可用`)) return;
  try {
    await api.disableFeishuConfig(config.id);
    await loadConfigs();
  } catch (err) {
    alert(err.response?.data?.msg || '禁用失败');
  }
};

/**
 * 步骤13：打开生成注册码弹窗（重置表单为默认值）
 */
const openCodeDialog = () => {
  codeForm.value = { max_uses: 1, expires_at: '' };
  codeFormError.value = '';
  showCodeDialog.value = true;
};

const closeCodeDialog = () => {
  showCodeDialog.value = false;
  codeFormError.value = '';
};

/**
 * 步骤14：datetime-local 值转后端格式
 *   "2026-08-10T18:00" -> "2026-08-10 18:00:00"（后端要求 yyyy-MM-dd HH:mm:ss）
 *   空值返回空字符串（后端视为永不过期）
 */
const toBackendDateTime = (localStr) => {
  if (!localStr) return '';
  const noT = localStr.replace('T', ' ');
  return noT.length === 16 ? noT + ':00' : noT;
};

/**
 * 步骤15：提交生成注册码表单
 */
const submitCodeForm = async () => {
  codeFormError.value = '';

  const maxUses = Number(codeForm.value.max_uses);
  if (!Number.isInteger(maxUses) || maxUses < 1) {
    codeFormError.value = '最大可用次数需为不小于 1 的整数';
    return;
  }

  submitting.value = true;
  try {
    await api.createRegisterCode(maxUses, toBackendDateTime(codeForm.value.expires_at));
    showCodeDialog.value = false;
    await loadCodes();
  } catch (err) {
    codeFormError.value = err.response?.data?.msg || '生成失败';
  } finally {
    submitting.value = false;
  }
};

/**
 * 步骤16：撤销注册码（二次确认后调用，操作后刷新列表）
 */
const revokeCode = async (code) => {
  if (!confirm(`确认撤销注册码「${code.code}」？撤销后员工将无法使用此码注册`)) return;
  try {
    await api.revokeRegisterCode(code.id);
    await loadCodes();
  } catch (err) {
    alert(err.response?.data?.msg || '撤销失败');
  }
};

// 步骤17：挂载时并行加载两个列表
onMounted(() => {
  loadConfigs();
  loadCodes();
});
</script>

<style scoped>
/* 步骤18：飞书配置管理页面样式（源力设计系统 v3.0，token 对齐 App.vue :root） */
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
.card-title { font-size: 14px; font-weight: 600; color: var(--color-fg); margin: 0; }
.toolbar { display: flex; gap: 8px; align-items: center; }

/* 主按钮 */
.btn-primary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
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

/* 基础按钮 / 危险按钮 */
.btn {
  display: inline-flex;
  align-items: center;
  height: 26px;
  padding: 0 10px;
  background: transparent;
  color: var(--color-fg);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  font-size: 12px;
  font-family: var(--font-sans);
  cursor: pointer;
  margin-right: 4px;
  transition: var(--transition);
}
.btn:hover:not(:disabled) { background: var(--color-muted); }
.btn:disabled { opacity: 0.4; cursor: not-allowed; }
.btn-danger {
  color: var(--color-error-strong);
  border-color: var(--color-error-subtle);
}
.btn-danger:hover:not(:disabled) { background: var(--color-error-subtle); }

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
.table-wrap { position: relative; overflow-x: auto; }
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
.mono { font-family: var(--font-mono); font-size: 12px; }
.secret { letter-spacing: 0.08em; color: var(--color-fg-muted); }
.readonly-hint { font-size: 12px; color: var(--color-fg-muted); }
.empty-row { text-align: center; color: var(--color-fg-muted); padding: 40px 16px; }

/* loading 遮罩（原生实现，不依赖 Element Plus） */
.loading-mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-card);
  color: var(--color-fg-muted);
  font-size: 13px;
  z-index: 5;
}

/* 状态标签 */
.tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  font-family: var(--font-sans);
}
.tag-active { background: var(--color-success-subtle); color: var(--color-success-strong); }
.tag-disabled { background: var(--color-error-subtle); color: var(--color-error-strong); }

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
.modal-actions .btn { margin-right: 0; }

/* 响应式 */
@media (max-width: 992px) {
  .screen-shell { padding: calc(var(--spacing) * 3); }
  .card-header { flex-wrap: wrap; }
  .table-wrap { overflow-x: auto; }
}
</style>
