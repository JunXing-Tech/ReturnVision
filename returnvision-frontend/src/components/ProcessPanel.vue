<template>
  <div class="process-panel">
    <!-- 空状态 -->
    <div v-if="!result" class="empty-state">
      <el-icon :size="48" color="#94a3b8"><Tickets /></el-icon>
      <p>请先在主页上传快递面单</p>
    </div>

    <template v-if="result">
      <!-- 竖向时间线 -->
      <div class="timeline">
        <div v-for="(step, i) in steps" :key="i" class="timeline-item">
          <div class="timeline-left">
            <div :class="['step-circle', step.status]">
              <el-icon v-if="step.status === 'done'" :size="14"><Check /></el-icon>
              <el-icon v-else-if="step.status === 'active'" class="is-loading" :size="14"><Loading /></el-icon>
            </div>
            <div v-if="i < steps.length - 1" :class="['step-line', step.status]"></div>
          </div>
          <div class="timeline-right">
            <div class="step-header">
              <span class="step-title">{{ step.title }}</span>
              <el-tag v-if="step.tag" :type="step.tagType" size="small">{{ step.tag }}</el-tag>
            </div>
            <p v-if="step.detail" class="step-detail">{{ step.detail }}</p>
          </div>
        </div>
      </div>

      <!-- 结果摘要 -->
      <div class="result-section">
        <div class="result-status-bar">
          <el-tag :type="validationTagType(result.cross_validation)" effect="dark">
            {{ validationLabel(result.cross_validation) }}
          </el-tag>
          <el-tag type="info" effect="plain">置信度: {{ result.confidence }}</el-tag>
          <el-tag type="info" effect="plain">ID: {{ result.record_id }}</el-tag>
        </div>

        <el-row :gutter="20">
          <!-- 图片预览 -->
          <el-col :xs="24" :md="8">
            <div class="image-card">
              <el-image :src="result.image_url" fit="contain" style="max-height: 350px; border-radius: 8px"
                :preview-src-list="[result.image_url]" />
            </div>
          </el-col>

          <!-- 可编辑表单 -->
          <el-col :xs="24" :md="16">
            <div class="form-card">
              <el-form :model="editData" label-width="80px" size="default">
                <el-row :gutter="12">
                  <el-col :span="12"><el-form-item label="运单号"><el-input v-model="editData.waybill_no" /></el-form-item></el-col>
                  <el-col :span="12"><el-form-item label="快递公司"><el-input v-model="editData.express_company" /></el-form-item></el-col>
                  <el-col :span="12"><el-form-item label="收件人"><el-input v-model="editData.rec_name" /></el-form-item></el-col>
                  <el-col :span="12"><el-form-item label="收件电话"><el-input v-model="editData.rec_phone" /></el-form-item></el-col>
                  <el-col :span="24"><el-form-item label="收件地址"><el-input v-model="editData.rec_address" /></el-form-item></el-col>
                  <el-col :span="12"><el-form-item label="寄件人"><el-input v-model="editData.sender_name" /></el-form-item></el-col>
                  <el-col :span="12"><el-form-item label="寄件电话"><el-input v-model="editData.sender_phone" /></el-form-item></el-col>
                  <el-col :span="24"><el-form-item label="寄件地址"><el-input v-model="editData.sender_address" /></el-form-item></el-col>
                  <el-col :span="12"><el-form-item label="托寄物"><el-input v-model="editData.goods" /></el-form-item></el-col>
                </el-row>
              </el-form>

              <!-- DeepSeek分析 -->
              <div v-if="result.return_reason" class="analysis-box">
                <div class="analysis-title">DeepSeek 分析</div>
                <div class="analysis-content">
                  <span class="analysis-label">退货原因：</span>{{ result.return_reason }}
                  <span class="analysis-label" style="margin-left:16px">分类：</span>
                  <el-tag type="warning" size="small">{{ result.return_category }}</el-tag>
                </div>
              </div>

              <!-- 校验警告 -->
              <el-alert
                v-for="(w, i) in (result.validation?.warnings || [])" :key="i"
                :title="w" type="warning" :closable="false" show-icon
                style="margin-top: 8px"
              />

              <!-- 操作按钮 -->
              <div class="action-bar">
                <el-button type="primary" size="large" @click="handleConfirm" :loading="confirming">
                  确认写入飞书
                </el-button>
                <el-button size="large" @click="handleReset">重新上传</el-button>
              </div>
            </div>
          </el-col>
        </el-row>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, watch, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Tickets, Check, Loading } from '@element-plus/icons-vue';
import api from '../api';

const props = defineProps({ result: Object });
const emit = defineEmits(['confirmed', 'reset']);

const confirming = ref(false);
const editData = reactive({});
const steps = ref([]);
const timers = [];

const buildSteps = (result) => {
  const v = result.validation || {};
  return [
    { title: '图片上传COS', status: 'done', detail: '已上传至腾讯云COS', tag: '完成', tagType: 'success' },
    { title: '智谱OCR识别', status: 'done', detail: 'GLM-OCR + glm-4-flash字段提取', tag: '完成', tagType: 'success' },
    { title: '阿里云OCR识别', status: 'done', detail: 'RecognizeWaybill面单识别', tag: '完成', tagType: 'success' },
    { title: '交叉验证', status: 'done', detail: `结果: ${result.cross_validation}, 置信度: ${result.confidence}`, tag: result.cross_validation, tagType: result.cross_validation === 'accept' ? 'success' : 'warning' },
    { title: 'DeepSeek分析', status: 'done', detail: result.return_reason ? `原因: ${result.return_reason}` : '跳过', tag: result.return_category || '无', tagType: 'info' },
    { title: '数据校验', status: 'done', detail: v.warnings?.length ? `通过，${v.warnings.length}个警告` : '全部通过', tag: v.passed ? '通过' : '不通过', tagType: v.passed ? 'success' : 'danger' },
    { title: '数据存储', status: 'done', detail: `记录ID: ${result.record_id}`, tag: '已保存', tagType: 'success' },
    { title: '等待确认', status: 'active', detail: '请核对信息后确认写入飞书', tag: '进行中', tagType: 'warning' },
  ];
};

const startAnimation = (targetSteps) => {
  // 初始化全部为pending
  steps.value = targetSteps.map(s => ({ ...s, status: 'pending' }));
  // 逐步推进
  targetSteps.forEach((step, i) => {
    const t = setTimeout(() => {
      if (steps.value[i]) {
        steps.value[i].status = step.status === 'active' ? 'active' : 'done';
      }
    }, i * 400);
    timers.push(t);
  });
};

watch(() => props.result, (val) => {
  if (!val) { steps.value = []; return; }
  // 填充编辑数据
  Object.keys(editData).forEach(k => delete editData[k]);
  if (val.data) Object.assign(editData, val.data);
  // 构建并动画
  const target = buildSteps(val);
  startAnimation(target);
}, { immediate: true });

onUnmounted(() => timers.forEach(clearTimeout));

const handleConfirm = async () => {
  confirming.value = true;
  try {
    const res = await api.confirm(props.result.record_id, editData);
    if (res.data.code === 0) {
      ElMessage.success('已写入飞书多维表格');
      emit('confirmed');
    } else {
      ElMessage.error(res.data.msg);
    }
  } catch (err) {
    ElMessage.error('确认失败：' + (err.response?.data?.msg || err.message));
  } finally {
    confirming.value = false;
  }
};

const handleReset = () => emit('reset');

const validationTagType = (v) => ({ accept: 'success', review: 'warning', manual: 'danger' }[v] || 'info');
const validationLabel = (v) => ({ accept: '自动采用', review: '需复核', manual: '转人工' }[v] || v);
</script>

<style scoped>
.process-panel { display: flex; flex-direction: column; gap: 24px; }

.empty-state { text-align: center; padding: 80px 0; color: var(--color-text-muted); }
.empty-state p { margin-top: 12px; font-size: 15px; }

/* Timeline */
.timeline { background: #fff; border-radius: var(--radius-lg); padding: 24px; box-shadow: var(--shadow-sm); }
.timeline-item { display: flex; gap: 16px; }
.timeline-left { display: flex; flex-direction: column; align-items: center; }
.step-circle {
  width: 28px; height: 28px; border-radius: 50%; display: flex; align-items: center; justify-content: center;
  flex-shrink: 0; transition: var(--transition);
}
.step-circle.done { background: var(--color-primary); color: #fff; }
.step-circle.active { background: #fff; border: 2px solid var(--color-primary); color: var(--color-primary); }
.step-circle.pending { background: var(--color-bg-secondary); border: 2px solid var(--color-border); }
.step-line { width: 2px; flex: 1; min-height: 32px; margin: 4px 0; }
.step-line.done { background: var(--color-primary); }
.step-line.active, .step-line.pending { background: var(--color-border); }
.timeline-right { padding-bottom: 20px; flex: 1; }
.step-header { display: flex; align-items: center; gap: 8px; }
.step-title { font-size: 14px; font-weight: 600; color: var(--color-text); }
.step-detail { font-size: 12px; color: var(--color-text-secondary); margin-top: 4px; }

/* Result */
.result-section { display: flex; flex-direction: column; gap: 16px; }
.result-status-bar { display: flex; gap: 8px; flex-wrap: wrap; }
.image-card { background: #fff; border-radius: var(--radius-lg); padding: 12px; box-shadow: var(--shadow-sm); }
.form-card { background: #fff; border-radius: var(--radius-lg); padding: 20px; box-shadow: var(--shadow-sm); }
.analysis-box { background: #fefce8; border-radius: var(--radius-md); padding: 12px 16px; margin-top: 12px; }
.analysis-title { font-size: 13px; font-weight: 600; color: var(--color-text-secondary); margin-bottom: 6px; }
.analysis-content { font-size: 14px; color: var(--color-text); }
.analysis-label { font-weight: 600; color: var(--color-text-secondary); }
.action-bar { margin-top: 16px; display: flex; gap: 12px; }

@media (max-width: 768px) {
  .timeline { padding: 16px; }
  .form-card { padding: 16px; }
}
</style>
