<template>
  <!-- 步骤1：退货报表页面外壳 -->
  <div class="screen-shell">
    <!-- 步骤2：Hero 区 -->
    <section class="hero">
      <div class="eyebrow">分析</div>
      <h1 class="hero-title">退货报表</h1>
      <p class="hero-subtitle">多维度退货数据分析 · 分类占比 / 快递占比 / 原因 TOP10 / 趋势</p>
    </section>

    <!-- 步骤3：KPI 汇总卡（4 个横排） -->
    <div class="kpi-grid">
      <div class="kpi-card">
        <span class="kpi-eyebrow">总量</span>
        <div class="kpi-value kpi-value-num">{{ kpis.total }}</div>
        <div class="kpi-foot">近 {{ days }} 天退货总量</div>
      </div>
      <div class="kpi-card">
        <span class="kpi-eyebrow">日均</span>
        <div class="kpi-value kpi-value-num">{{ kpis.avg }}</div>
        <div class="kpi-foot">日均退货量</div>
      </div>
      <div class="kpi-card">
        <span class="kpi-eyebrow">最高分类</span>
        <div class="kpi-value kpi-value-text kpi-value-green">{{ kpis.topCategory }}</div>
        <div class="kpi-foot">占比 {{ kpis.topCategoryPct }}</div>
      </div>
      <div class="kpi-card">
        <span class="kpi-eyebrow">最高快递</span>
        <div class="kpi-value kpi-value-text kpi-value-blue">{{ kpis.topExpress }}</div>
        <div class="kpi-foot">占比 {{ kpis.topExpressPct }}</div>
      </div>
    </div>

    <!-- 步骤4：筛选条 -->
    <div class="filter-card">
      <div class="filter-group">
        <span class="filter-label">时间范围</span>
        <div class="pill-toggle" role="tablist" aria-label="时间范围选择">
          <button
            v-for="d in [7, 30, 90]"
            :key="d"
            :class="['pill-toggle-item', days === d ? 'active' : '']"
            role="tab"
            :aria-selected="days === d"
            @click="changeDays(d)"
          >近 {{ d }} 天</button>
        </div>
      </div>
      <button class="icon-btn" :disabled="loading" @click="loadReport" aria-label="刷新报表">
        <Refresh />
      </button>
    </div>

    <!-- 步骤5：图表网格（2x2，宽图跨列） -->
    <div v-loading="loading" class="chart-grid">
      <!-- 步骤5a：退货分类占比（饼图 + 图例） -->
      <div class="chart-card">
        <div class="chart-card-header">
          <div class="chart-card-title-group">
            <span class="chart-eyebrow">维度1</span>
            <h3 class="chart-card-title">退货分类占比</h3>
          </div>
        </div>
        <div class="chart-body">
          <div class="donut-body">
            <div ref="categoryChartRef" class="donut-chart" role="img" aria-label="退货分类占比饼图"></div>
            <div class="legend">
              <div v-for="(item, i) in categoryLegend" :key="item.name" class="legend-item">
                <span class="legend-dot" :style="{ background: CHART_COLORS.palette[i % 5] }"></span>
                <span class="legend-name">{{ item.name }}</span>
                <span class="legend-pct">{{ item.pct }}</span>
                <span class="legend-count">{{ item.count }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 步骤5b：快递公司占比（饼图 + 图例） -->
      <div class="chart-card">
        <div class="chart-card-header">
          <div class="chart-card-title-group">
            <span class="chart-eyebrow">维度2</span>
            <h3 class="chart-card-title">快递公司占比</h3>
          </div>
        </div>
        <div class="chart-body">
          <div class="donut-body">
            <div ref="expressChartRef" class="donut-chart" role="img" aria-label="快递公司占比饼图"></div>
            <div class="legend">
              <div v-for="(item, i) in expressLegend" :key="item.name" class="legend-item">
                <span class="legend-dot" :style="{ background: CHART_COLORS.palette[i % 5] }"></span>
                <span class="legend-name">{{ item.name }}</span>
                <span class="legend-pct">{{ item.pct }}</span>
                <span class="legend-count">{{ item.count }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 步骤5c：退货原因 TOP10（横向柱状图） -->
      <div class="chart-card chart-card-wide">
        <div class="chart-card-header">
          <div class="chart-card-title-group">
            <span class="chart-eyebrow">维度3</span>
            <h3 class="chart-card-title">退货原因 TOP10</h3>
          </div>
          <span class="chart-card-meta">单位：件</span>
        </div>
        <div class="chart-body">
          <div ref="reasonChartRef" class="bar-chart-container" role="img" aria-label="退货原因TOP10柱状图"></div>
        </div>
      </div>

      <!-- 步骤5d：N 天趋势（折线图） -->
      <div class="chart-card chart-card-wide">
        <div class="chart-card-header">
          <div class="chart-card-title-group">
            <span class="chart-eyebrow">维度4</span>
            <h3 class="chart-card-title">{{ days }} 天退货趋势</h3>
          </div>
          <span class="chart-card-meta">单位：件</span>
        </div>
        <div class="chart-body">
          <div ref="trendChartRef" class="trend-chart-container" role="img" aria-label="退货趋势折线图"></div>
        </div>
      </div>
    </div>

    <!-- 步骤6：空数据提示 -->
    <div v-if="!loading && isEmpty" class="empty-card">
      <p class="empty-text">所选时间范围内无已同步退货记录</p>
    </div>
  </div>
</template>

<script setup>
// 步骤7：ECharts 按需引入
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts/core';
import { PieChart, BarChart, LineChart } from 'echarts/charts';
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { Refresh } from '../icons';
import api from '../api';

echarts.use([PieChart, BarChart, LineChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer]);

// 步骤8：状态
const loading = ref(false);
const days = ref(7);
const isEmpty = ref(false);
const reportData = ref(null);

// 图表 DOM 引用
const categoryChartRef = ref(null);
const expressChartRef = ref(null);
const reasonChartRef = ref(null);
const trendChartRef = ref(null);

// ECharts 实例
let categoryChart = null;
let expressChart = null;
let reasonChart = null;
let trendChart = null;

// 步骤9：ECharts 颜色常量（JS 上下文无法用 CSS 变量，统一管理）
const CHART_COLORS = {
  palette: ['#1664ff', '#309256', '#bd7e00', '#ee3f38', '#737a87'],
  primary: '#1664ff',
  primaryAreaStart: 'rgba(22, 100, 255, 0.15)',
  primaryAreaEnd: 'rgba(22, 100, 255, 0.01)',
  axisLine: '#dde2e9',
  axisLabel: '#737a87',
  splitLine: '#f6f8fa',
  borderColor: '#ffffff',
};

// 步骤10：KPI 汇总（从报表数据计算）
const kpis = computed(() => {
  const d = reportData.value;
  if (!d) return { total: '-', avg: '-', topCategory: '-', topCategoryPct: '-', topExpress: '-', topExpressPct: '-' };

  // 总量：趋势数据求和
  const trend = d.trend || [];
  const total = trend.reduce((sum, item) => sum + (item.count || 0), 0);
  const avg = trend.length > 0 ? Math.round(total / trend.length) : 0;

  // 最高分类
  const catBreakdown = d.category_breakdown || [];
  const topCat = catBreakdown.length > 0 ? catBreakdown[0] : null;
  const topCatPct = topCat && total > 0 ? ((topCat.count / total) * 100).toFixed(1) + '%' : '-';

  // 最高快递
  const expBreakdown = d.express_breakdown || [];
  const topExp = expBreakdown.length > 0 ? expBreakdown[0] : null;
  const topExpPct = topExp && total > 0 ? ((topExp.count / total) * 100).toFixed(1) + '%' : '-';

  return {
    total: total.toLocaleString(),
    avg: avg.toLocaleString(),
    topCategory: topCat ? topCat.name : '-',
    topCategoryPct: topCatPct,
    topExpress: topExp ? topExp.name : '-',
    topExpressPct: topExpPct,
  };
});

// 步骤11：饼图图例数据（带百分比）
const categoryLegend = computed(() => {
  const d = reportData.value;
  if (!d || !d.category_breakdown) return [];
  const total = d.category_breakdown.reduce((s, i) => s + i.count, 0) || 1;
  return d.category_breakdown.map(item => ({
    name: item.name,
    pct: ((item.count / total) * 100).toFixed(1) + '%',
    count: item.count,
  }));
});

const expressLegend = computed(() => {
  const d = reportData.value;
  if (!d || !d.express_breakdown) return [];
  const total = d.express_breakdown.reduce((s, i) => s + i.count, 0) || 1;
  return d.express_breakdown.map(item => ({
    name: item.name,
    pct: ((item.count / total) * 100).toFixed(1) + '%',
    count: item.count,
  }));
});

// 步骤12：加载报表
const loadReport = async () => {
  loading.value = true;
  try {
    const { data } = await api.getReport(days.value);
    if (data.code === 0) {
      reportData.value = data.data;
      isEmpty.value = isReportEmpty(data.data);
      await nextTick();
      renderCharts();
    } else {
      ElMessage.error(data.msg || '加载报表失败');
    }
  } catch (e) {
    ElMessage.error('加载报表失败：' + (e.message || '网络错误'));
  } finally {
    loading.value = false;
  }
};

const changeDays = (d) => {
  if (days.value === d) return;
  days.value = d;
  loadReport();
};

const isReportEmpty = (d) => {
  return (
    (!d.category_breakdown || d.category_breakdown.length === 0) &&
    (!d.express_breakdown || d.express_breakdown.length === 0) &&
    (!d.reason_top10 || d.reason_top10.length === 0) &&
    (!d.trend || d.trend.length === 0)
  );
};

// 步骤13：渲染所有图表（切换时先 clear）
const renderCharts = () => {
  if (!reportData.value) return;
  renderPieChart('category');
  renderPieChart('express');
  renderBarChart();
  renderLineChart();
};

// 步骤14：渲染饼图（环形 + 无内置 label，用外部图例）
const renderPieChart = (chartKey) => {
  const el = chartKey === 'category' ? categoryChartRef.value : expressChartRef.value;
  if (!el) return;
  let chart = chartKey === 'category' ? categoryChart : expressChart;
  if (!chart) {
    chart = echarts.init(el);
    if (chartKey === 'category') categoryChart = chart;
    else expressChart = chart;
  }
  chart.clear();
  const breakdown = chartKey === 'category'
    ? (reportData.value.category_breakdown || [])
    : (reportData.value.express_breakdown || []);
  const pieData = breakdown.map(item => ({ name: item.name, value: item.count }));
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
    color: CHART_COLORS.palette,
    series: [{
      type: 'pie',
      radius: ['45%', '72%'],
      center: ['50%', '50%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 6, borderColor: CHART_COLORS.borderColor, borderWidth: 2 },
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 13, fontWeight: 'bold' } },
      data: pieData.length > 0 ? pieData : [{ name: '无数据', value: 1 }],
    }],
  });
};

// 步骤15：渲染柱状图（横向 + 标签外置 + 数值右标）
const renderBarChart = () => {
  if (!reasonChartRef.value) return;
  if (!reasonChart) reasonChart = echarts.init(reasonChartRef.value);
  reasonChart.clear();
  const breakdown = reportData.value.reason_top10 || [];
  const names = breakdown.map(item => item.name).reverse();
  const counts = breakdown.map(item => item.count).reverse();
  reasonChart.setOption({
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    grid: { left: '3%', right: '6%', bottom: '3%', top: '3%', containLabel: true },
    xAxis: {
      type: 'value',
      axisLine: { lineStyle: { color: CHART_COLORS.axisLine } },
      axisLabel: { color: CHART_COLORS.axisLabel, fontSize: 11 },
      splitLine: { lineStyle: { color: CHART_COLORS.splitLine } },
    },
    yAxis: {
      type: 'category',
      data: names,
      axisLine: { lineStyle: { color: CHART_COLORS.axisLine } },
      axisLabel: { color: CHART_COLORS.axisLabel, fontSize: 12 },
    },
    series: [{
      type: 'bar',
      data: counts,
      barWidth: '55%',
      itemStyle: { color: CHART_COLORS.primary, borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', color: CHART_COLORS.axisLabel, fontSize: 11, fontFamily: 'monospace' },
    }],
  });
};

// 步骤16：渲染折线图（smooth + 渐变面积 + 数据点）
const renderLineChart = () => {
  if (!trendChartRef.value) return;
  if (!trendChart) trendChart = echarts.init(trendChartRef.value);
  trendChart.clear();
  const trend = reportData.value.trend || [];
  const dates = trend.map(item => item.date);
  const counts = trend.map(item => item.count);
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '5%', containLabel: true },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: CHART_COLORS.axisLine } },
      axisLabel: { color: CHART_COLORS.axisLabel, fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { lineStyle: { color: CHART_COLORS.axisLine } },
      axisLabel: { color: CHART_COLORS.axisLabel, fontSize: 11 },
      splitLine: { lineStyle: { color: CHART_COLORS.splitLine } },
    },
    series: [{
      type: 'line',
      data: counts,
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { color: CHART_COLORS.primary, width: 2 },
      itemStyle: { color: CHART_COLORS.primary },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: CHART_COLORS.primaryAreaStart },
          { offset: 1, color: CHART_COLORS.primaryAreaEnd },
        ]),
      },
    }],
  });
};

// 步骤17：resize
const handleResize = () => {
  categoryChart?.resize();
  expressChart?.resize();
  reasonChart?.resize();
  trendChart?.resize();
};

onMounted(() => {
  loadReport();
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  categoryChart?.dispose();
  expressChart?.dispose();
  reasonChart?.dispose();
  trendChart?.dispose();
});
</script>

<style scoped>
/* 步骤18：退货报表页面样式（源力设计系统 v3.0，对齐设计稿 report.html） */
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

/* KPI 卡片网格（4 列） */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: calc(var(--spacing) * 4);
}
.kpi-card {
  display: flex;
  flex-direction: column;
  gap: calc(var(--spacing) * 2);
  padding: 20px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  box-shadow: var(--shadow-md);
  min-width: 0;
}
.kpi-eyebrow {
  font-family: var(--font-mono);
  font-size: 10.5px;
  font-weight: 500;
  color: var(--color-fg-muted);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  white-space: nowrap;
}
.kpi-value { line-height: 1.1; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.kpi-value-num {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-fg);
  font-family: var(--font-mono);
}
.kpi-value-text { font-size: 18px; font-weight: 600; }
.kpi-value-green { color: var(--color-success-strong); }
.kpi-value-blue { color: var(--color-primary); }
.kpi-foot {
  font-size: 11px;
  color: var(--color-fg-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 筛选条 */
.filter-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px 20px;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  box-shadow: var(--shadow-md);
}
.filter-group { display: flex; align-items: center; gap: 12px; min-width: 0; }
.filter-label {
  font-family: var(--font-mono);
  font-size: 10.5px;
  font-weight: 500;
  color: var(--color-fg-muted);
  letter-spacing: 0.04em;
  white-space: nowrap;
}
.pill-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0;
  padding: 2px;
  background: var(--color-muted);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
}
.pill-toggle-item {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 28px;
  padding: 0 16px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-fg-muted);
  font-size: 13px;
  font-weight: 500;
  font-family: var(--font-sans);
  cursor: pointer;
  white-space: nowrap;
  transition: var(--transition);
}
.pill-toggle-item:hover:not(.active) { color: var(--color-fg); }
.pill-toggle-item.active {
  background: var(--color-card);
  color: var(--color-primary);
  font-weight: 600;
  box-shadow: var(--shadow-sm);
}

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
  margin-left: auto;
  flex-shrink: 0;
}
.icon-btn:hover:not(:disabled) { background: var(--color-muted); color: var(--color-fg); }
.icon-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.icon-btn svg { width: 14px; height: 14px; }

/* 图表网格（2x2，宽图跨列） */
.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: calc(var(--spacing) * 4);
}
.chart-card {
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  box-shadow: var(--shadow-md);
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.chart-card-wide { grid-column: 1 / -1; }
.chart-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.chart-card-title-group { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
.chart-eyebrow {
  font-family: var(--font-mono);
  font-size: 10.5px;
  font-weight: 500;
  color: var(--color-fg-muted);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  white-space: nowrap;
}
.chart-card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-fg);
  line-height: 1.3;
  white-space: nowrap;
  margin: 0;
}
.chart-card-meta {
  font-size: 11px;
  color: var(--color-fg-muted);
  font-family: var(--font-mono);
  white-space: nowrap;
  flex-shrink: 0;
}
.chart-body { min-height: 0; }

/* 饼图 + 图例布局 */
.donut-body {
  display: flex;
  align-items: center;
  gap: 24px;
  flex-wrap: nowrap;
}
.donut-chart {
  width: 180px;
  height: 180px;
  flex-shrink: 0;
}
.legend {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
  min-width: 0;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  flex-shrink: 0;
}
.legend-name {
  font-size: 13px;
  color: var(--color-fg);
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}
.legend-pct {
  font-size: 13px;
  color: var(--color-fg-muted);
  font-family: var(--font-mono);
  white-space: nowrap;
  flex-shrink: 0;
  width: 52px;
  text-align: right;
}
.legend-count {
  font-size: 11px;
  color: var(--color-fg-muted);
  font-family: var(--font-mono);
  white-space: nowrap;
  flex-shrink: 0;
  width: 48px;
  text-align: right;
}

/* 柱状图/折线图容器 */
.bar-chart-container { width: 100%; height: 360px; }
.trend-chart-container { width: 100%; height: 280px; }

/* 空状态 */
.empty-card {
  padding: 40px 20px;
  text-align: center;
  background: var(--color-card);
  border: 1px solid var(--color-border);
  border-radius: 12px;
}
.empty-text { font-size: 13px; color: var(--color-fg-muted); margin: 0; }

/* 响应式 */
@media (max-width: 1080px) {
  .kpi-grid { grid-template-columns: repeat(2, 1fr); }
  .chart-grid { grid-template-columns: 1fr; }
  .chart-card-wide { grid-column: auto; }
  .donut-body { flex-direction: column; gap: 16px; }
  .legend { width: 100%; }
}
@media (max-width: 640px) {
  .screen-shell { padding: calc(var(--spacing) * 3); }
  .kpi-grid { grid-template-columns: 1fr; }
  .filter-card { flex-direction: column; align-items: stretch; gap: 12px; }
  .filter-card .icon-btn { margin-left: 0; width: 100%; }
  .pill-toggle { width: 100%; justify-content: center; }
  .pill-toggle-item { flex: 1; }
  .donut-chart { width: 140px; height: 140px; }
}
</style>
