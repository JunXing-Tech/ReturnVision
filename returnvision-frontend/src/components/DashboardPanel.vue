<template>
  <!-- 工作台主容器 -->
  <div class="screen-shell">
    <!-- 步骤1：Hero 区域（标题 + 上传入口） -->
    <section class="hero">
      <div class="hero-text">
        <span class="eyebrow">概览</span>
        <h1 class="hero-title">退运智录工作台</h1>
        <p class="hero-subtitle">拍照识别快递面单，双引擎交叉验证，AI分析退货原因，一键写入飞书</p>
      </div>
      <button class="btn-primary" @click="emit('navigate', 'recognition')">
        <CloudUpload />
        <span>上传识别</span>
      </button>
    </section>

    <!-- 步骤2：KPI 卡片网格（4 张核心指标卡） -->
    <section class="kpi-grid">
      <div v-for="kpi in kpis" :key="kpi.eyebrow" class="kpi-card">
        <span class="eyebrow">{{ kpi.eyebrow }}</span>
        <h3 class="kpi-title">{{ kpi.title }}</h3>
        <div class="metric-value">{{ kpi.value }}</div>
        <div class="kpi-delta">
          <TrendingUp />
          <span>{{ kpi.delta }}</span>
        </div>
        <div class="metric-foot">{{ kpi.foot }}</div>
      </div>
    </section>

    <!-- 步骤3：主内容区（最近记录 + 趋势图表） -->
    <section class="main-content">
      <!-- 左侧：最近退货记录表格 -->
      <div class="card">
        <div class="card-header">
          <div class="card-heading">
            <span class="eyebrow">近期</span>
            <h3 class="card-title">最近退货记录</h3>
          </div>
          <button class="btn-ghost" @click="emit('navigate', 'records')">
            <span>查看全部</span>
            <ArrowRight />
          </button>
        </div>
        <div class="table-wrap">
          <table class="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>运单号</th>
                <th>快递公司</th>
                <th>收件人</th>
                <th>退货原因</th>
                <th>状态</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in records" :key="r.id">
                <td>{{ r.id }}</td>
                <td class="mono">{{ r.waybillNo || r.waybill_no || '-' }}</td>
                <td>{{ r.expressCompany || r.express_company || '-' }}</td>
                <td>{{ r.recName || r.rec_name || '-' }}</td>
                <td>{{ r.returnReason || r.return_reason || '-' }}</td>
                <td>
                  <span :class="['table-pill', r.status === 'pending' ? 'pill-pending' : 'pill-synced']">
                    {{ statusLabel(r.status) }}
                  </span>
                </td>
                <td class="mono">{{ formatTime(r.createdAt || r.created_at) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="table-note">最近5条记录，点击查看全部进入退货记录页</p>
      </div>

      <!-- 右侧：近7日退货趋势柱状图 -->
      <div class="card">
        <div class="card-header">
          <div class="card-heading">
            <span class="eyebrow">趋势</span>
            <h3 class="card-title">近7日退货趋势</h3>
          </div>
        </div>
        <!-- 柱状图（含Y轴刻度） -->
        <div class="chart-area">
          <!-- Y轴刻度 -->
          <div class="y-axis">
            <span v-for="tick in yTicks" :key="tick" class="y-tick">{{ tick }}</span>
          </div>
          <!-- 柱状图主体 -->
          <div class="bar-chart">
            <div v-for="bar in chartData" :key="bar.label" class="bar-col">
              <!-- 数值标签 -->
              <span class="bar-value">{{ bar.value }}</span>
              <div class="bar" :style="{ height: bar.heightPct + '%', background: bar.color }"></div>
            </div>
          </div>
        </div>
        <!-- X轴标签 -->
        <div class="x-labels">
          <span v-for="bar in chartData" :key="bar.label">{{ bar.label }}</span>
        </div>
        <!-- 图例 -->
        <div class="legend">
          <span class="legend-item"><span class="swatch swatch-daily"></span>日常</span>
          <span class="legend-item"><span class="swatch swatch-today"></span>今日</span>
        </div>
        <!-- 趋势说明 -->
        <p class="chart-note">{{ trendNote }}</p>
      </div>
    </section>
  </div>
</template>

<script setup>
// 步骤4：依赖引入（图标、API、Vue 生命周期）
import { ref, computed, onMounted, watch } from 'vue';
import { TrendingUp, CloudUpload, ArrowRight } from '../icons';
import api from '../api';

// 步骤4.5：接收 active prop（切回工作台时自动刷新数据）
const props = defineProps({
  active: { type: Boolean, default: true },
});

// 步骤5：事件声明（导航至识别页 / 记录页）
const emit = defineEmits(['navigate']);

// 步骤6：KPI 指标数据（含硬编码兜底值，接口失败时展示）
const kpis = ref([
  { eyebrow: '待处理', title: '待确认', value: '-', delta: '', foot: '需人工复核' },
  { eyebrow: '已完成', title: '已同步', value: '-', delta: '', foot: '已写入飞书' },
  { eyebrow: '今日', title: '今日新增', value: '-', delta: '', foot: '今日识别' },
  { eyebrow: '累计', title: '总记录', value: '-', delta: '', foot: '全部退货记录' },
]);

// 步骤7：最近退货记录
const records = ref([]);

// 步骤8：近7日趋势数据（从后端加载，含日期+数量）
const trendData = ref([]);

// 步骤9：计算属性 - 从 trendData 构建柱状图数据
const chartData = computed(() => {
  // 补全最近7天（含今天），缺失的天补0
  const today = new Date();
  const dateMap = new Map();
  // 初始化最近7天
  for (let i = 6; i >= 0; i--) {
    const d = new Date(today);
    d.setDate(d.getDate() - i);
    const key = formatDateKey(d);
    const label = formatLabel(d);
    dateMap.set(key, { label, value: 0 });
  }
  // 填入后端返回的数据
  if (trendData.value && trendData.value.length) {
    trendData.value.forEach(item => {
      // 兼容 {date:"2026-07-15", count:4} 和 {date: "2026-07-15 00:00:00"} 等格式
      const rawDate = String(item.date || '').split(' ')[0];
      if (dateMap.has(rawDate)) {
        dateMap.get(rawDate).value = item.count || 0;
      }
    });
  }
  // 转为数组，计算柱高百分比
  const bars = Array.from(dateMap.values());
  const maxVal = Math.max(...bars.map(b => b.value), 1);
  const todayLabel = formatLabel(today);
  return bars.map(b => ({
    label: b.label,
    value: b.value,
    heightPct: Math.max((b.value / maxVal) * 100, 2), // 最小2%避免看不见
    color: b.label === todayLabel ? 'var(--color-chart-3)' : 'var(--color-chart-2)',
  }));
});

// 步骤10：Y轴刻度（与柱高基准 maxVal 一致，避免柱子达不到顶）
const yTicks = computed(() => {
  const maxVal = Math.max(...chartData.value.map(b => b.value), 1);
  // 刻度取最大值的均分（0/25%/50%/75%/100%），保留1位小数
  const fmt = (n) => Number.isInteger(n) ? n : Math.round(n * 10) / 10;
  return [fmt(maxVal), fmt(maxVal * 0.75), fmt(maxVal * 0.5), fmt(maxVal * 0.25), 0];
});

// 步骤11：趋势说明文字（含日均对比）
const trendNote = computed(() => {
  const bars = chartData.value;
  if (!bars.length) return '暂无趋势数据';
  const todayBar = bars[bars.length - 1];
  const todayVal = todayBar.value;
  // 日均 = 前6天的平均值（不含今天）
  const prev6 = bars.slice(0, -1);
  const dailyAvg = prev6.length
    ? Math.round(prev6.reduce((sum, b) => sum + b.value, 0) / prev6.length * 10) / 10
    : 0;
  if (todayVal > dailyAvg) {
    return `今日退货量 ${todayVal} 条，高于日均水平 ${dailyAvg} 条`;
  } else if (todayVal < dailyAvg) {
    return `今日退货量 ${todayVal} 条，低于日均水平 ${dailyAvg} 条`;
  } else {
    return `今日退货量 ${todayVal} 条，与日均水平 ${dailyAvg} 条持平`;
  }
});

// 步骤12：日期格式化辅助
function formatDateKey(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
function formatLabel(d) {
  return `${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

// 步骤13：状态中文标签映射
const statusLabel = (s) => ({ pending: '待确认', synced: '已同步' }[s] || s);

// 步骤14：时间格式化（兼容数组 [y,m,d,h,min] 与 ISO 字符串）
const formatTime = (t) => {
  if (!t) return '-';
  if (Array.isArray(t)) {
    const [y, m, d, h = 0, min = 0] = t;
    return `${y}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')} ${String(h).padStart(2, '0')}:${String(min).padStart(2, '0')}`;
  }
  const d = new Date(t);
  if (isNaN(d)) return '-';
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
};

// 步骤15：数据加载函数（供onMounted和watch复用）
const loadDashboardData = async () => {
  // 加载仪表盘统计
  try {
    const res = await api.getDashboardStats();
    if (res.data.code === 0 && res.data.data) {
      const s = res.data.data;
      kpis.value[0].value = s.pending_count ?? s.pending ?? '-';
      kpis.value[1].value = s.synced_count ?? s.synced ?? '-';
      kpis.value[2].value = s.today_count ?? s.todayAdded ?? '-';
      kpis.value[3].value = s.total_count ?? s.total ?? '-';
      if (Array.isArray(s.trend)) {
        trendData.value = s.trend;
      }
    }
  } catch (err) {
    // 接口不可用时保持默认值
  }

  // 加载最近5条退货记录
  try {
    const res = await api.getRecords('', 1, 5);
    if (res.data.code === 0 && res.data.data && Array.isArray(res.data.data.records)) {
      records.value = res.data.data.records;
    }
  } catch (err) {
    // 接口不可用时保持空
  }
};

// 挂载时加载
onMounted(loadDashboardData);

// 步骤15.1：切回工作台时自动刷新（检测到active变化且为true时重新拉取数据）
watch(() => props.active, (newVal) => {
  if (newVal) {
    loadDashboardData();
  }
});
</script>

<style scoped>
/* 步骤16：工作台外壳 */
.screen-shell {
  padding: calc(var(--spacing) * 5);
  display: flex;
  flex-direction: column;
  gap: calc(var(--spacing) * 4);
}

/* ===== Hero 区域 ===== */
.hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: calc(var(--spacing) * 4);
}
.hero-text {
  display: flex;
  flex-direction: column;
  gap: calc(var(--spacing) * 1.5);
}
.eyebrow {
  font-family: var(--font-mono);
  font-size: 10.5px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-fg-muted);
}
.hero-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--color-fg);
  line-height: 1.2;
}
.hero-subtitle {
  font-size: 13px;
  color: var(--color-fg-muted);
}

/* ===== 按钮通用样式 ===== */
.btn-primary {
  height: 34px;
  background: var(--color-primary);
  color: var(--color-primary-fg);
  border: none;
  border-radius: var(--radius);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 16px;
  transition: var(--transition);
  flex-shrink: 0;
  font-family: var(--font-sans);
}
.btn-primary:hover { opacity: 0.9; }

.btn-ghost {
  height: 34px;
  background: transparent;
  border: 1px solid var(--color-border);
  color: var(--color-fg);
  border-radius: var(--radius);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 16px;
  transition: var(--transition);
  font-family: var(--font-sans);
}
.btn-ghost:hover { background: var(--color-accent); }

/* ===== KPI 卡片网格 ===== */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: calc(var(--spacing) * 3);
}
.kpi-card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-card);
  padding: calc(var(--spacing) * 3.5);
  display: flex;
  flex-direction: column;
  gap: calc(var(--spacing) * 3);
}
.kpi-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-fg);
}
.metric-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-fg);
  line-height: 1;
}
.kpi-delta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-success-strong);
}
.kpi-delta :deep(.el-icon) { width: 14px; height: 14px; flex-shrink: 0; }
.metric-foot {
  font-size: 11px;
  color: var(--color-fg-muted);
}

/* ===== 主内容区网格 ===== */
.main-content {
  display: grid;
  grid-template-columns: minmax(0, 1.65fr) minmax(300px, 0.95fr);
  gap: calc(var(--spacing) * 3);
}

/* ===== 通用卡片 ===== */
.card {
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
  background: var(--color-card);
  padding: calc(var(--spacing) * 3.5);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: calc(var(--spacing) * 3);
}
.card-heading {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.card-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-fg);
}

/* ===== 数据表格 ===== */
.table-wrap {
  overflow-x: auto;
}
.data-table {
  width: 100%;
  border-collapse: collapse;
}
.data-table th {
  font-family: var(--font-mono);
  font-size: 10.5px;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-fg-muted);
  border-bottom: 1px solid var(--color-border);
  padding: 8px 12px;
  text-align: left;
  white-space: nowrap;
}
.data-table td {
  font-size: 12.5px;
  border-bottom: 1px solid var(--color-border);
  padding: 10px 12px;
  color: var(--color-fg);
  white-space: nowrap;
}
.data-table .mono {
  font-family: var(--font-mono);
}
.data-table tbody tr:hover {
  background: var(--color-muted);
}
.table-pill {
  display: inline-flex;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 600;
}
.pill-pending {
  background: var(--color-accent);
  color: var(--color-accent-fg);
}
.pill-synced {
  background: var(--color-success-subtle);
  color: var(--color-success-strong);
}
.table-note {
  font-size: 12px;
  color: var(--color-fg-muted);
  margin-top: calc(var(--spacing) * 3);
}

/* ===== 趋势图区域 ===== */
.chart-area {
  display: flex;
  gap: calc(var(--spacing) * 2);
  height: 200px;
}
/* Y轴刻度 */
.y-axis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  align-items: flex-end;
  padding: 0 4px 0 0;
  height: 100%;
}
.y-tick {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--color-fg-muted);
  line-height: 1;
}
/* 柱状图 */
.bar-chart {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  align-items: end;
  height: 100%;
  gap: calc(var(--spacing) * 2);
  flex: 1;
  min-width: 0;
}
.bar-col {
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  align-items: center;
  height: 100%;
  gap: 4px;
}
.bar-value {
  font-family: var(--font-mono);
  font-size: 10.5px;
  color: var(--color-fg);
  line-height: 1;
}
.bar {
  width: 100%;
  max-width: 44px;
  border-radius: 2px 2px 0 0;
  transition: var(--transition);
}
/* X轴标签 */
.x-labels {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: calc(var(--spacing) * 2);
  margin-top: calc(var(--spacing) * 2);
  padding-left: 28px;
}
.x-labels span {
  font-family: var(--font-mono);
  font-size: 10.5px;
  color: var(--color-fg-muted);
  text-align: center;
}
/* 图例 */
.legend {
  display: flex;
  gap: calc(var(--spacing) * 4);
  margin-top: calc(var(--spacing) * 3);
  padding-left: 28px;
}
.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-fg-muted);
}
.swatch {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  display: inline-block;
}
.swatch-daily { background: var(--color-chart-2); }
.swatch-today { background: var(--color-chart-3); }
.chart-note {
  font-size: 12px;
  color: var(--color-fg-muted);
  margin-top: calc(var(--spacing) * 2);
  padding-left: 28px;
}

/* ===== 响应式：窄屏退化为 2 列 KPI + 单列主内容 ===== */
@media (max-width: 992px) {
  .kpi-grid { grid-template-columns: repeat(2, 1fr); }
  .main-content { grid-template-columns: 1fr; }
}
</style>
