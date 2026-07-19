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
          <div class="metric-value">{{ summary.total_calls ?? '-' }}</div>
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
          <div class="metric-value">{{ summary.avg_duration_ms ?? '-' }} ms</div>
          <div class="metric-foot">单次识别</div>
        </div>
        <div class="kpi-card">
          <span class="eyebrow">智谱 / 阿里云</span>
          <div class="metric-value" style="font-size: 18px">
            <span :class="rateClass(summary.zhipu_success_rate)">{{ formatRate(summary.zhipu_success_rate) }}</span>
            <span style="color: var(--text-tertiary); margin: 0 4px">/</span>
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
              <td>{{ formatRate(f.avg_confidence) }}</td>
              <td>
                <div class="bar-wrap">
                  <div class="bar-fill" :class="rateClass(f.avg_confidence)" :style="{ width: (f.avg_confidence * 100) + '%' }"></div>
                </div>
              </td>
              <td>{{ f.sample_count }}</td>
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
.screen-shell {
  padding: 24px 32px;
  max-width: 1400px;
  margin: 0 auto;
}

.hero { margin-bottom: 24px; }
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
  margin-bottom: 16px;
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border-color, #e5e7eb);
}

.toolbar { display: flex; gap: 8px; align-items: center; }
.filter-select {
  padding: 6px 10px;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 6px;
  font-size: 13px;
  background: var(--bg-card, #fff);
  color: var(--text-primary, #111827);
}
.icon-btn {
  width: 32px; height: 32px;
  display: flex; align-items: center; justify-content: center;
  background: transparent;
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 8px;
  cursor: pointer;
  color: var(--text-secondary, #6b7280);
}
.icon-btn:hover:not(:disabled) { background: var(--bg-hover, #f3f4f6); }
.icon-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  padding: 20px;
}
.kpi-card {
  padding: 16px;
  background: var(--bg-secondary, #f9fafb);
  border-radius: 8px;
}
.metric-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary, #111827);
  margin: 8px 0 4px 0;
}
.metric-foot {
  font-size: 12px;
  color: var(--text-tertiary, #9ca3af);
}

.rate-good { color: #22c55e; }
.rate-ok { color: #f59e0b; }
.rate-bad { color: #ef4444; }

.table-wrap { overflow-x: auto; }
.data-table { width: 100%; border-collapse: collapse; font-size: 13px; }
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
  vertical-align: middle;
}

.bar-wrap {
  width: 160px;
  height: 8px;
  background: var(--bg-secondary, #f3f4f6);
  border-radius: 4px;
  overflow: hidden;
}
.bar-fill {
  height: 100%;
  border-radius: 4px;
  transition: width 0.3s;
}
.bar-fill.rate-good { background: #22c55e; }
.bar-fill.rate-ok { background: #f59e0b; }
.bar-fill.rate-bad { background: #ef4444; }

.status-tag {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}
.status-success { background: rgba(34,197,94,0.1); color: #22c55e; }
.status-warn { background: rgba(245,158,11,0.1); color: #f59e0b; }

.empty-row {
  text-align: center;
  color: var(--text-tertiary, #9ca3af);
  padding: 40px 16px;
}

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
  background: var(--bg-secondary, #f3f4f6);
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
.trend-bar.rate-good { background: #22c55e; }
.trend-bar.rate-ok { background: #f59e0b; }
.trend-bar.rate-bad { background: #ef4444; }
.trend-label {
  font-size: 11px;
  color: var(--text-tertiary, #9ca3af);
  margin-top: 8px;
}
.trend-rate {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-primary, #111827);
}
</style>
