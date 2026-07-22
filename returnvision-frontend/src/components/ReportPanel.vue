<template>
  <!-- 步骤1：退货报表页面外壳 -->
  <div class="screen-shell">
    <!-- 步骤2：Hero 区 -->
    <section class="hero">
      <div class="eyebrow">分析</div>
      <h1 class="hero-title">退货报表</h1>
      <p class="hero-subtitle">多维度退货数据分析 · 分类占比 / 快递占比 / 原因 TOP10 / 趋势</p>
    </section>

    <!-- 步骤3：时间范围筛选 -->
    <section class="card filter-card">
      <div class="filter-bar">
        <span class="filter-label">时间范围</span>
        <div class="day-toggle">
          <button
            v-for="d in [7, 30, 90]"
            :key="d"
            :class="['day-btn', days === d ? 'day-btn-active' : '']"
            @click="changeDays(d)"
          >近 {{ d }} 天</button>
        </div>
        <button class="icon-btn" :disabled="loading" @click="loadReport">
          <Refresh />
        </button>
      </div>
    </section>

    <!-- 步骤4：图表网格（2x2 布局） -->
    <div v-loading="loading" class="chart-grid">
      <!-- 步骤4a：退货分类占比（饼图） -->
      <section class="card chart-card">
        <div class="card-header">
          <div class="card-heading">
            <div class="eyebrow">维度1</div>
            <h3 class="card-title">退货分类占比</h3>
          </div>
        </div>
        <div ref="categoryChartRef" class="chart-body"></div>
      </section>

      <!-- 步骤4b：快递公司占比（饼图） -->
      <section class="card chart-card">
        <div class="card-header">
          <div class="card-heading">
            <div class="eyebrow">维度2</div>
            <h3 class="card-title">快递公司占比</h3>
          </div>
        </div>
        <div ref="expressChartRef" class="chart-body"></div>
      </section>

      <!-- 步骤4c：退货原因 TOP10（柱状图） -->
      <section class="card chart-card chart-wide">
        <div class="card-header">
          <div class="card-heading">
            <div class="eyebrow">维度3</div>
            <h3 class="card-title">退货原因 TOP10</h3>
          </div>
        </div>
        <div ref="reasonChartRef" class="chart-body"></div>
      </section>

      <!-- 步骤4d：N 天趋势（折线图） -->
      <section class="card chart-card chart-wide">
        <div class="card-header">
          <div class="card-heading">
            <div class="eyebrow">维度4</div>
            <h3 class="card-title">{{ days }} 天退货趋势</h3>
          </div>
        </div>
        <div ref="trendChartRef" class="chart-body"></div>
      </section>
    </div>

    <!-- 步骤5：空数据提示 -->
    <section v-if="!loading && isEmpty" class="card empty-card">
      <p class="empty-text">所选时间范围内无已同步退货记录</p>
    </section>
  </div>
</template>

<script setup>
// 步骤6：ECharts 按需引入（控制包体积）
import { ref, onMounted, onBeforeUnmount, nextTick } from 'vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts/core';
import { PieChart, BarChart, LineChart } from 'echarts/charts';
import {
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';
import { Refresh } from '../icons';
import api from '../api';

echarts.use([
  PieChart,
  BarChart,
  LineChart,
  TitleComponent,
  TooltipComponent,
  LegendComponent,
  GridComponent,
  CanvasRenderer,
]);

// 步骤7：状态
const loading = ref(false);
const days = ref(7);
const isEmpty = ref(false);

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

// 报表数据
const reportData = ref(null);

// 步骤8：图表色板（对齐 docs/12 源力 chart-1~5）
const chartColors = [
  '#1664ff', '#309256', '#bd7e00', '#ee3f38', '#737a87',
  '#387bff', '#7ccd94', '#f9c76d', '#ff706d', '#a1a1aa',
];

// 步骤9：加载报表
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

// 步骤10：切换时间范围
const changeDays = (d) => {
  if (days.value === d) return;
  days.value = d;
  loadReport();
};

// 步骤11：判断报表是否全空
const isReportEmpty = (d) => {
  return (
    (!d.category_breakdown || d.category_breakdown.length === 0) &&
    (!d.express_breakdown || d.express_breakdown.length === 0) &&
    (!d.reason_top10 || d.reason_top10.length === 0) &&
    (!d.trend || d.trend.length === 0)
  );
};

// 步骤12：渲染所有图表
const renderCharts = () => {
  if (!reportData.value) return;
  renderPieChart(categoryChartRef.value, categoryChart, reportData.value.category_breakdown, '退货分类');
  renderPieChart(expressChartRef.value, expressChart, reportData.value.express_breakdown, '快递公司');
  renderBarChart();
  renderLineChart();
};

// 步骤13：渲染饼图
const renderPieChart = (el, chart, breakdown, title) => {
  if (!el) return;
  if (!chart) {
    chart = echarts.init(el);
    if (el === categoryChartRef.value) categoryChart = chart;
    if (el === expressChartRef.value) expressChart = chart;
  }
  const pieData = (breakdown || []).map((item) => ({
    name: item.name,
    value: item.count,
  }));
  chart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)',
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center',
      textStyle: { fontSize: 12, color: '#737a87' },
    },
    color: chartColors,
    series: [
      {
        name: title,
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['40%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 6,
          borderColor: '#fff',
          borderWidth: 2,
        },
        label: {
          show: false,
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 14,
            fontWeight: 'bold',
          },
        },
        data: pieData.length > 0 ? pieData : [{ name: '无数据', value: 1 }],
      },
    ],
  });
};

// 步骤14：渲染柱状图（退货原因 TOP10）
const renderBarChart = () => {
  if (!reasonChartRef.value) return;
  if (!reasonChart) {
    reasonChart = echarts.init(reasonChartRef.value);
  }
  const breakdown = reportData.value.reason_top10 || [];
  const names = breakdown.map((item) => item.name);
  const counts = breakdown.map((item) => item.count);
  reasonChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10px',
      containLabel: true,
    },
    xAxis: {
      type: 'value',
      axisLine: { lineStyle: { color: '#dde2e9' } },
      axisLabel: { color: '#737a87', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f6f8fa' } },
    },
    yAxis: {
      type: 'category',
      data: names.reverse(),
      axisLine: { lineStyle: { color: '#dde2e9' } },
      axisLabel: { color: '#737a87', fontSize: 12 },
    },
    series: [
      {
        type: 'bar',
        data: counts.reverse(),
        barWidth: '60%',
        itemStyle: {
          color: '#1664ff',
          borderRadius: [0, 4, 4, 0],
        },
      },
    ],
  });
};

// 步骤15：渲染折线图（N 天趋势）
const renderLineChart = () => {
  if (!trendChartRef.value) return;
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value);
  }
  const trend = reportData.value.trend || [];
  const dates = trend.map((item) => item.date);
  const counts = trend.map((item) => item.count);
  trendChart.setOption({
    tooltip: {
      trigger: 'axis',
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      top: '10px',
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: dates,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#dde2e9' } },
      axisLabel: { color: '#737a87', fontSize: 11 },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLine: { lineStyle: { color: '#dde2e9' } },
      axisLabel: { color: '#737a87', fontSize: 11 },
      splitLine: { lineStyle: { color: '#f6f8fa' } },
    },
    series: [
      {
        type: 'line',
        data: counts,
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color: '#1664ff', width: 2 },
        itemStyle: { color: '#1664ff' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(22, 100, 255, 0.15)' },
            { offset: 1, color: 'rgba(22, 100, 255, 0.01)' },
          ]),
        },
      },
    ],
  });
};

// 步骤16：窗口 resize 时重绘图表
const handleResize = () => {
  categoryChart?.resize();
  expressChart?.resize();
  reasonChart?.resize();
  trendChart?.resize();
};

// 步骤17：生命周期
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
/* 步骤18：组件作用域样式，引用源力 token（docs/12 规范） */
.screen-shell {
  padding: calc(var(--yuanli-spacing) * 6);
  display: flex;
  flex-direction: column;
  gap: calc(var(--yuanli-spacing) * 5);
}

.hero {
  display: flex;
  flex-direction: column;
  gap: calc(var(--yuanli-spacing) * 1);
}

.eyebrow {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--yuanli-muted-foreground);
  font-weight: 500;
}

.hero-title {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--yuanli-foreground);
  margin: 0;
}

.hero-subtitle {
  font-size: 13px;
  color: var(--yuanli-muted-foreground);
  margin: 0;
}

.card {
  background: var(--yuanli-card);
  border: 1px solid var(--yuanli-border);
  border-radius: var(--yuanli-radius-lg);
  box-shadow: var(--yuanli-shadow-md);
  overflow: hidden;
}

.filter-card {
  padding: calc(var(--yuanli-spacing) * 4) calc(var(--yuanli-spacing) * 5);
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: calc(var(--yuanli-spacing) * 3);
}

.filter-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--yuanli-foreground);
}

.day-toggle {
  display: flex;
  gap: 2px;
  background: var(--yuanli-muted);
  border-radius: var(--yuanli-radius-md);
  padding: 2px;
}

.day-btn {
  height: 30px;
  padding: 0 calc(var(--yuanli-spacing) * 3);
  background: transparent;
  color: var(--yuanli-muted-foreground);
  border: none;
  border-radius: var(--yuanli-radius-sm);
  font-size: 13px;
  cursor: pointer;
  transition: var(--yuanli-transition);
}

.day-btn:hover {
  color: var(--yuanli-foreground);
}

.day-btn-active {
  background: var(--yuanli-card);
  color: var(--yuanli-primary);
  font-weight: 500;
  box-shadow: var(--yuanli-shadow-sm);
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  background: transparent;
  color: var(--yuanli-muted-foreground);
  border: 1px solid var(--yuanli-border);
  border-radius: var(--yuanli-radius-md);
  cursor: pointer;
  transition: var(--yuanli-transition);
  margin-left: auto;
}

.icon-btn:hover:not(:disabled) {
  background: var(--yuanli-accent);
  color: var(--yuanli-primary);
}

.icon-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.chart-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: calc(var(--yuanli-spacing) * 5);
}

.chart-card {
  display: flex;
  flex-direction: column;
}

.chart-wide {
  grid-column: span 2;
}

.card-header {
  padding: calc(var(--yuanli-spacing) * 3) calc(var(--yuanli-spacing) * 5);
  border-bottom: 1px solid var(--yuanli-border);
}

.card-heading {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--yuanli-foreground);
  margin: 0;
}

.chart-body {
  width: 100%;
  height: 320px;
  padding: calc(var(--yuanli-spacing) * 3);
}

.empty-card {
  padding: calc(var(--yuanli-spacing) * 8);
  text-align: center;
}

.empty-text {
  font-size: 14px;
  color: var(--yuanli-muted-foreground);
  margin: 0;
}

@media (max-width: 900px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
  .chart-wide {
    grid-column: span 1;
  }
}
</style>