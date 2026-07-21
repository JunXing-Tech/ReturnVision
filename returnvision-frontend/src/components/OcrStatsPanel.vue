<template>
  <div class="screen-shell">
    <!-- 步骤1：Hero 区 -->
    <section class="hero">
      <div class="eyebrow">数据洞察</div>
      <h1 class="hero-title">OCR 准确率仪表盘</h1>
      <p class="hero-subtitle">双引擎识别质量监控 · 字段级置信度分析</p>
    </section>

    <!-- 步骤2：筛选 + 统计卡片 -->
    <section class="card">
      <div class="card-header">
        <div class="card-heading">
          <div class="eyebrow">概览</div>
          <h3 class="card-title">近 {{ days }} 天统计</h3>
        </div>
        <div class="toolbar">
          <select v-model="days" class="filter-select" @change="loadStats">
            <option :value="7">近 7 天</option>
            <option :value="14">近 14 天</option>
            <option :value="30">近 30 天</option>
            <option :value="90">近 90 天</option>
          </select>
          <button class="icon-btn" :disabled="loading" @click="loadStats">
            <Refresh />
          </button>
        </div>
      </div>

      <!-- 步骤3：KPI 卡片 -->
      <div v-loading="loading" class="kpi-grid">
        <div class="kpi-card">
          <span class="eyebrow">总调用</span>
          <div class="metric-value mono">{{ summary.total_calls ?? '-' }}</div>
          <div class="metric-foot">双引擎合计</div>
        </div>
        <div class="kpi-card">
          <span class="eyebrow">整体成功率</span>
          <div class="metric-value" :class="rateClass(summary.success_rate)">
            {{ formatRate(summary.success_rate) }}
          </div>
          <div class="metric-foot">成功/总调用</div>
        </div>
        <div class="kpi-card">
          <span class="eyebrow">平均耗时</span>
          <div class="metric-value mono">{{ summary.avg_duration_ms ?? '-' }} ms</div>
          <div class="metric-foot">单次识别</div>
        </div>
        <div class="kpi-card">
          <span class="eyebrow">智谱 / 阿里云</span>
          <div class="metric-value metric-dual">
            <span :class="rateClass(summary.zhipu_success_rate)">{{ formatRate(summary.zhipu_success_rate) }}</span>
            <span class="metric-sep">/</span>
            <span :class="rateClass(summary.aliyun_success_rate)">{{ formatRate(summary.aliyun_success_rate) }}</span>
          </div>
          <div class="metric-foot">双引擎对比</div>
        </div>
      </div>
    </section>

    <!-- 步骤4：字段级准确率 -->
    <section class="card">
      <div class="card-header">
        <div class="card-heading">
          <div class="eyebrow">字段级</div>
          <h3 class="card-title">字段平均置信度</h3>
        </div>
      </div>
      <div v-loading="loading" class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th>字段名</th>
              <th>平均置信度</th>
              <th>可视化</th>
              <th>样本数</th>
              <th>状态</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="f in fieldAccuracy" :key="f.field">
              <td>{{ fieldLabel(f.field) }}</td>
              <td class="mono">{{ formatRate(f.avg_confidence) }}</td>
              <td>
                <div class="bar-wrap">
                  <div class="bar-fill" :class="rateClass(f.avg_confidence)" :style="{ width: (f.avg_confidence * 100) + '%' }"></div>
                </div>
              </td>
              <td class="mono">{{ f.sample_count }}</td>
              <td>
                <span :class="['status-tag', f.avg_confidence >= 0.8 ? 'status-success' : 'status-warn']">
                  {{ f.avg_confidence >= 0.8 ? '良好' : '需关注' }}
                </span>
              </td>
            </tr>
            <tr v-if="fieldAccuracy.length === 0">
              <td colspan="5" class="empty-row">暂无字段置信度数据（需阿里云 OCR 成功后积累）</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <!-- 步骤5：7 天趋势 -->
    <section class="card">
      <div class="card-header">
        <div class="card-heading">
          <div class="eyebrow">趋势</div>
          <h3 class="card-title">每日成功率</h3>
        </div>
      </div>
      <div v-loading="loading" class="trend-wrap">
        <div v-if="trend.length === 0" class="empty-row">暂无趋势数据</div>
        <div v-else class="trend-chart">
          <div v-for="t in trend" :key="t.date" class="trend-bar-group">
            <div class="trend-bar-wrap">
              <div class="trend-bar" :class="rateClass(t.success_rate)" :style="{ height: (t.success_rate * 100) + '%' }"></div>
            </div>
            <div class="trend-label">{{ t.date.slice(5) }}</div>
            <div class="trend-rate">{{ formatRate(t.success_rate) }}</div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { Refresh } from '../icons';
import api from '../api';

const loading = ref(false);
const days = ref(7);
const summary = ref({});
const fieldAccuracy = ref([]);
const trend = ref([]);

const fieldLabels = {
  waybill_no: '运单号',
  rec_name: '收件人姓名',
  rec_phone: '收件人电话',
  rec_address: '收件人地址',
  sender_name: '寄件人姓名',
  sender_phone: '寄件人电话',
  sender_address: '寄件人地址',
  express_company: '快递公司',
  goods: '托寄物',
};

const fieldLabel = (f) => fieldLabels[f] || f;

const formatRate = (r) => {
  if (r == null) return '-';
  return (r * 100).toFixed(1) + '%';
};

const rateClass = (r) => {
  if (r == null) return '';
  if (r >= 0.9) return 'rate-good';
  if (r >= 0.8) return 'rate-ok';
  return 'rate-bad';
};

const loadStats = async () => {
  loading.value = true;
  try {
    const resp = await api.getOcrStats(days.value);
    if (resp.data.code === 0) {
      const d = resp.data.data;
      summary.value = d.summary || {};
      fieldAccuracy.value = d.field_accuracy || [];
      trend.value = d.trend || [];
    }
  } catch (err) {
    console.error('加载 OCR 统计失败', err);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadStats();
});
</script>

<style scoped>
/* 步骤15：OCR 统计页面样式（源力设计系统 v3.0） */
.screen-shell {
  padding: calc(var(--spacing) * 5);
  max-width: 1400px;
  margin: 0 auto;
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
.card-title { font-size: 14px; font-weight: 600; color: var(--color-fg); }
.toolbar { display: flex; gap: 8px; align-items: center; }

/* 筛选 select */
.filter-select {
  height: 34px;
  padding: 0 12px;
  border: 1px solid var(--color-input);
  border-radius: var(--radius);
  font-size: 13px;
  font-family: var(--font-sans);
  background: var(--color-bg);
  color: var(--color-fg);
  cursor: pointer;
  outline: none;
  transition: border-color var(--transition), box-shadow var(--transition);
}
.filter-select:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(56, 123, 255, 0.15);
}

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

/* KPI 卡片网格 */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  padding: 20px;
}
.kpi-card {
  padding: 16px;
  background: var(--color-muted);
  border-radius: var(--radius);
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.metric-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-fg);
  margin: 4px 0;
}
.metric-dual { font-size: 18px; display: flex; align-items: center; gap: 4px; }
.metric-sep { color: var(--color-fg-muted); }
.metric-foot { font-size: 11px; color: var(--color-fg-muted); }
.mono { font-family: var(--font-mono); }

/* 置信度状态色（与源力状态色对齐） */
.rate-good { color: var(--color-success-strong); }
.rate-ok { color: var(--color-warning-strong); }
.rate-bad { color: var(--color-error-strong); }

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
}
.data-table td {
  padding: 12px 16px;
  border-top: 1px solid var(--color-border);
  color: var(--color-fg);
  vertical-align: middle;
}
.data-table tbody tr:hover { background: var(--color-muted); }

/* 置信度进度条 */
.bar-wrap {
  width: 160px;
  height: 8px;
  background: var(--color-bg-5, var(--color-muted));
  border-radius: 4px;
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s;
}
.bar-fill.rate-good { background: var(--color-success); }
.bar-fill.rate-ok { background: var(--color-warning); }
.bar-fill.rate-bad { background: var(--color-error); }

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
.status-success { background: var(--color-success-subtle); color: var(--color-success-strong); }
.status-warn { background: var(--color-warning-subtle); color: var(--color-warning-strong); }

/* 空状态 */
.empty-row { text-align: center; color: var(--color-fg-muted); padding: 40px 16px; }

/* 趋势柱状图 */
.trend-wrap { padding: 20px; }
.trend-chart {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  height: 200px;
  overflow-x: auto;
}
.trend-bar-group {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 60px;
  flex: 1;
}
.trend-bar-wrap {
  width: 32px;
  height: 140px;
  background: var(--color-muted);
  border-radius: 4px;
  display: flex;
  align-items: flex-end;
  overflow: hidden;
}
.trend-bar {
  width: 100%;
  border-radius: 4px 4px 0 0;
  transition: height 0.3s;
  min-height: 2px;
}
.trend-bar.rate-good { background: var(--color-chart-1); }
.trend-bar.rate-ok { background: var(--color-warning); }
.trend-bar.rate-bad { background: var(--color-error); }
.trend-label {
  font-size: 11px;
  color: var(--color-fg-muted);
  margin-top: 8px;
  font-family: var(--font-mono);
}
.trend-rate {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-fg);
  font-family: var(--font-mono);
}
</style>
