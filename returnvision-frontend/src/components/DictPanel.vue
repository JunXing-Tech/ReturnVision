<template>
  <!-- 步骤1：字典管理页面外壳 -->
  <div class="screen-shell">
    <!-- 步骤2：Hero 区 -->
    <section class="hero">
      <div class="eyebrow">管理</div>
      <h1 class="hero-title">退货分类字典</h1>
      <p class="hero-subtitle">维护退货分类标准字典 · LLM 分析时从此字典选分类</p>
    </section>

    <!-- 步骤3：字典卡片 -->
    <section class="card">
      <div class="card-header">
        <div class="card-heading">
          <div class="eyebrow">数据</div>
          <h3 class="card-title">分类列表</h3>
        </div>
        <div class="toolbar">
          <button class="btn-primary" @click="openCreateDialog(null)">
            <Plus /> 新建一级分类
          </button>
          <button class="icon-btn" :disabled="loading" @click="loadCategories" aria-label="刷新字典">
            <Refresh />
          </button>
        </div>
      </div>

      <!-- 步骤4：字典树表格 -->
      <div v-loading="loading" class="table-wrap">
        <table class="data-table" aria-label="退货分类字典列表">
          <thead>
            <tr>
              <th>编码</th>
              <th>名称</th>
              <th>层级</th>
              <th>排序</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <template v-for="item in flatItems" :key="item._key">
              <!-- 一级项 -->
              <tr class="row-parent">
                <td class="mono">{{ item.item_code }}</td>
                <td>{{ item.item_label }}</td>
                <td><span class="level-tag level-1">一级</span></td>
                <td class="mono">{{ item.sort_order }}</td>
                <td>
                  <span :class="['status-tag', item.status === 'active' ? 'status-active' : 'status-disabled']">
                    {{ item.status === 'active' ? '启用' : '停用' }}
                  </span>
                </td>
                <td>
                  <button class="action-btn" @click="openCreateDialog(item)">加子项</button>
                  <button class="action-btn" @click="openEditDialog(item)">编辑</button>
                  <button
                    v-if="item.status === 'active'"
                    class="action-btn action-warn"
                    @click="disableItem(item)"
                  >停用</button>
                </td>
              </tr>
              <!-- 二级项（子项） -->
              <tr v-for="child in item.children || []" :key="child._key" class="row-child">
                <td class="mono indent">└ {{ child.item_code }}</td>
                <td>{{ child.item_label }}</td>
                <td><span class="level-tag level-2">二级</span></td>
                <td class="mono">{{ child.sort_order }}</td>
                <td>
                  <span :class="['status-tag', child.status === 'active' ? 'status-active' : 'status-disabled']">
                    {{ child.status === 'active' ? '启用' : '停用' }}
                  </span>
                </td>
                <td>
                  <button class="action-btn" @click="openEditDialog(child)">编辑</button>
                  <button
                    v-if="child.status === 'active'"
                    class="action-btn action-warn"
                    @click="disableItem(child)"
                  >停用</button>
                </td>
              </tr>
            </template>
            <tr v-if="!loading && flatItems.length === 0">
              <td colspan="6" class="empty-row">暂无字典项，点击右上角"新建一级分类"开始</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- 步骤5：新建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建字典项' : '编辑字典项'"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form :model="formData" label-width="90px" label-position="right">
        <el-form-item v-if="dialogMode === 'create'" label="父级">
          <span class="parent-hint">{{ formData._parentLabel || '（作为一级分类）' }}</span>
        </el-form-item>
        <el-form-item v-if="dialogMode === 'create'" label="编码" required>
          <el-input v-model="formData.item_code" placeholder="大写英文，如 QUALITY" maxlength="50" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="formData.item_label" placeholder="如 质量问题" maxlength="50" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="formData.sort_order" :min="0" :max="999" />
        </el-form-item>
        <el-form-item v-if="dialogMode === 'create'" label="是否叶子">
          <el-switch v-model="formData.is_leaf" />
          <span class="leaf-hint">叶子项可被 LLM 选择</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <button class="btn-ghost" @click="dialogVisible = false">取消</button>
        <button class="btn-primary" :disabled="submitting" @click="submitForm">
          {{ submitting ? '提交中...' : '确定' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
// 步骤6：状态与 API 调用
import { ref, computed, onMounted } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Plus, Refresh } from '../icons';
import api from '../api';

const loading = ref(false);
const submitting = ref(false);
const categories = ref([]);
const dialogVisible = ref(false);
const dialogMode = ref('create');
const formData = ref({
  dict_id: null,
  parent_id: null,
  item_code: '',
  item_label: '',
  is_leaf: true,
  sort_order: 0,
  _parentLabel: '',
  _editingId: null,
});

// 步骤7：扁平化字典树（一级 + 二级展开为表格行）
const flatItems = computed(() => {
  const items = categories.value || [];
  return items.map(parent => ({
    ...parent,
    _key: `p-${parent.id}`,
    children: (parent.children || []).map(child => ({
      ...child,
      _key: `c-${child.id}`,
    })),
  }));
});

// 步骤8：加载字典树
const loadCategories = async () => {
  loading.value = true;
  try {
    const { data } = await api.getDictCategories();
    if (data.code === 0) {
      categories.value = data.data?.items || [];
    } else {
      ElMessage.error(data.msg || '加载字典失败');
    }
  } catch (e) {
    ElMessage.error('加载字典失败：' + (e.message || '网络错误'));
  } finally {
    loading.value = false;
  }
};

// 步骤9：获取 return_category 字典 ID（从第一项的 dict_id 取）
const getDictId = () => {
  if (flatItems.value.length > 0) {
    return flatItems.value[0].dict_id;
  }
  return null;
};

// 步骤10：打开新建对话框
const openCreateDialog = (parent) => {
  dialogMode.value = 'create';
  formData.value = {
    dict_id: getDictId(),
    parent_id: parent ? parent.id : null,
    item_code: '',
    item_label: '',
    is_leaf: true,
    sort_order: 0,
    _parentLabel: parent ? parent.item_label : '',
    _editingId: null,
  };
  dialogVisible.value = true;
};

// 步骤11：打开编辑对话框
const openEditDialog = (item) => {
  dialogMode.value = 'edit';
  formData.value = {
    dict_id: item.dict_id,
    parent_id: item.parent_id,
    item_code: item.item_code,
    item_label: item.item_label,
    is_leaf: item.is_leaf,
    sort_order: item.sort_order,
    _parentLabel: '',
    _editingId: item.id,
  };
  dialogVisible.value = true;
};

// 步骤12：提交表单（新建/编辑）
const submitForm = async () => {
  if (!formData.value.item_label || formData.value.item_label.trim() === '') {
    ElMessage.warning('名称不能为空');
    return;
  }
  if (dialogMode.value === 'create' && (!formData.value.item_code || formData.value.item_code.trim() === '')) {
    ElMessage.warning('编码不能为空');
    return;
  }

  submitting.value = true;
  try {
    if (dialogMode.value === 'create') {
      const payload = {
        dict_id: formData.value.dict_id,
        parent_id: formData.value.parent_id,
        item_code: formData.value.item_code.trim().toUpperCase(),
        item_label: formData.value.item_label.trim(),
        is_leaf: formData.value.is_leaf,
        sort_order: formData.value.sort_order,
      };
      const { data } = await api.createDictItem(payload);
      if (data.code === 0) {
        ElMessage.success('创建成功');
        dialogVisible.value = false;
        await loadCategories();
      } else {
        ElMessage.error(data.msg || '创建失败');
      }
    } else {
      const payload = {
        item_label: formData.value.item_label.trim(),
        sort_order: formData.value.sort_order,
        is_leaf: formData.value.is_leaf,
      };
      const { data } = await api.updateDictItem(formData.value._editingId, payload);
      if (data.code === 0) {
        ElMessage.success('修改成功');
        dialogVisible.value = false;
        await loadCategories();
      } else {
        ElMessage.error(data.msg || '修改失败');
      }
    }
  } catch (e) {
    ElMessage.error('操作失败：' + (e.response?.data?.msg || e.message || '网络错误'));
  } finally {
    submitting.value = false;
  }
};

// 步骤13：停用字典项（二次确认 + 级联提示）
const disableItem = async (item) => {
  const hasChildren = item.children && item.children.length > 0;
  const confirmMsg = hasChildren
    ? `确定停用"${item.item_label}"？其下 ${item.children.length} 个子项将一并停用。`
    : `确定停用"${item.item_label}"？停用后新录入不再可选，历史记录不受影响。`;
  try {
    await ElMessageBox.confirm(confirmMsg, '停用确认', {
      confirmButtonText: '确定停用',
      cancelButtonText: '取消',
      type: 'warning',
    });
    const { data } = await api.disableDictItem(item.id);
    if (data.code === 0) {
      ElMessage.success(`已停用，影响 ${data.data?.affected || 1} 项`);
      await loadCategories();
    } else {
      ElMessage.error(data.msg || '停用失败');
    }
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error('停用失败：' + (e.response?.data?.msg || e.message || '网络错误'));
    }
  }
};

onMounted(loadCategories);
</script>

<style scoped>
/* 步骤14：字典管理页面样式（源力设计系统 v3.0）
   token 命名对齐 App.vue :root 的 --color-* 体系（非设计稿的 --yuanli-*）
   间距统一 *5 *4 与全站一致，圆角统一 radius/12px，disabled 用 error 语义 */
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

/* 卡片（与全站一致：12px 圆角 + shadow-md） */
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

/* 按钮（统一全站规范） */
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

.btn-ghost {
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
.btn-ghost:hover { background: var(--color-muted); }

/* 图标按钮 */
.icon-btn {
  width: 34px;
  height: 34px;
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

/* 表格（左右 padding 与 header 对齐） */
.table-wrap {
  padding: 0 20px 16px;
  overflow-x: auto;
}
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.data-table thead th {
  text-align: left;
  padding: 10px 12px;
  background: var(--color-muted);
  color: var(--color-fg-muted);
  font-family: var(--font-mono);
  font-weight: 500;
  font-size: 10.5px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  white-space: nowrap;
}
.data-table tbody td {
  padding: 12px 12px;
  border-top: 1px solid var(--color-border);
  color: var(--color-fg);
  vertical-align: middle;
}
.data-table tbody tr:hover { background: var(--color-muted); }

/* 层级区分：一级加粗，二级降重变灰 */
.row-parent { font-weight: 500; }
.row-child td { color: var(--color-fg-muted); font-weight: 400; }
.indent { padding-left: 28px !important; }
.mono { font-family: var(--font-mono); }

/* 层级徽章（胶囊形） */
.level-tag {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 500;
  font-family: var(--font-sans);
}
.level-1 { background: var(--color-accent); color: var(--color-primary); }
.level-2 { background: var(--color-muted); color: var(--color-fg-muted); }

/* 状态徽章（与全站一致：启用 success / 停用 error） */
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

/* 行内操作按钮（圆角统一 radius 8px，与全站一致） */
.action-btn {
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
.action-btn:hover { background: var(--color-muted); }
.action-warn { color: var(--color-warning-strong); border-color: var(--color-warning-subtle); }
.action-warn:hover { background: var(--color-warning-subtle); color: var(--color-warning-strong); }

/* 空状态（padding 与全站一致） */
.empty-row { text-align: center; color: var(--color-fg-muted); padding: 40px 16px; }

/* 弹窗提示文字 */
.parent-hint, .leaf-hint {
  font-size: 12px;
  color: var(--color-fg-muted);
  margin-left: 8px;
}

/* 响应式 */
@media (max-width: 768px) {
  .screen-shell { padding: calc(var(--spacing) * 3); }
  .card-header { flex-wrap: wrap; gap: 12px; }
  .table-wrap { padding: 0 12px 12px; }
}
</style>
