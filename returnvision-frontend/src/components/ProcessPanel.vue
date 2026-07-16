<template>
  <div class="process-panel">
    <div v-if="!result" class="empty-state">
      <el-icon :size="48" color="#94a3b8"><Tickets /></el-icon>
      <p>请先在主页上传快递面单</p>
    </div>

    <template v-if="result">
      <div class="timeline">
        <div v-for="(step, i) in allSteps" :key="i" class="timeline-item">
          <div class="timeline-left">
            <div :class="['step-circle', step.status]">
              <el-icon v-if="step.status === 'done'" :size="14"><Check /></el-icon>
              <el-icon v-else-if="step.status === 'active'" class="is-loading" :size="14"><Loading /></el-icon>
            </div>
            <div v-if="i < allSteps.length - 1" :class="['step-line', step.status]"></div>
          </div>
          <div class="timeline-right">
            <div class="step-header">
              <span class="step-title">{{ step.title }}</span>
              <el-tag v-if="step.status !== 'pending'" :type="step.tagType" size="small">{{ step.tag }}</el-tag>
            </div>
            <p v-if="step.status !== 'pending' && step.detail" class="step-detail">{{ step.detail }}</p>
          </div>
        </div>
      </div>

      <!-- 结果区域在动画完成后显示 -->
      <transition name="fade">
        <div v-if="showResult" class="result-section">
          <div class="result-status-bar">
            <el-tag :type="validationTagType(result.cross_validation)" effect="dark">
              {{ validationLabel(result.cross_validation) }}
            </el-tag>
            <el-tag type="info" effect="plain">置信度: {{ result.confidence }}</el-tag>
            <el-tag type="info" effect="plain">ID: {{ result.record_id }}</el-tag>
          </div>

          <el-row :gutter="20">
            <el-col :xs="24" :md="8">
              <div class="image-card">
                <el-image :src="result.image_url" fit="contain" style="max-height: 350px; border-radius: 8px"
                  :preview-src-list="[result.image_url]" />
              </div>
            </el-col>
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

                <div v-if="result.return_reason" class="analysis-box">
                  <div class="analysis-title">DeepSeek 分析</div>
                  <div class="analysis-content">
                    <span class="analysis-label">退货原因：</span>{{ result.return_reason }}
                    <span class="analysis-label" style="margin-left:16px">分类：</span>
                    <el-tag type="warning" size="small">{{ result.return_category }}</el-tag>
                  </div>
                </div>

                <el-alert v-for="(w, i) in (result.validation?.warnings || [])" :key="i"
                  :title="w" type="warning" :closable="false" show-icon style="margin-top: 8px" />

                <div class="action-bar">
                  <el-button type="primary" size="large" @click="handleConfirm" :loading="confirming">确认写入飞书</el-button>
                  <el-button size="large" @click="handleReset">重新上传</el-button>
                </div>
              </div>
            </el-col>
          </el-row>
        </div>
      </transition>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Tickets, Check, Loading } from '@element-plus/icons-vue';
import api from '../api';
import { useAppState } from '../composables/useAppState';

const { uploadResult: result, confirmed, reset } = useAppState();
const confirming = ref(false);
const editData = reactive({});
const currentStep = ref(0);
const showResult = ref(false);
let timer = null;

const rawSteps = computed(() => {
  if (!result.value) return [];
  const r = result.value;
  const v = r.validation || {};
  return [
    { title: '图片上传COS', detail: '已上传至腾讯云COS', tag: '完成', tagType: 'success' },
    { title: '智谱OCR识别', detail: 'GLM-OCR + glm-4-flash字段提取', tag: '完成', tagType: 'success' },
    { title: '阿里云OCR识别', detail: 'RecognizeWaybill面单识别', tag: '完成', tagType: 'success' },
    { title: '交叉验证', detail: `结果: ${r.cross_validation}, 置信度: ${r.confidence}`, tag: r.cross_validation, tagType: r.cross_validation === 'accept' ? 'success' : 'warning' },
    { title: 'DeepSeek分析', detail: r.return_reason ? `原因: ${r.return_reason}` : '跳过', tag: r.return_category || '无', tagType: 'info' },
    { title: '数据校验', detail: v.warnings?.length ? `通过，${v.warnings.length}个警告` : '全部通过', tag: v.passed ? '通过' : '不通过', tagType: v.passed ? 'success' : 'danger' },
    { title: '数据存储', detail: `记录ID: ${r.record_id}`, tag: '已保存', tagType: 'success' },
    { title: '等待确认', detail: '请核对信息后确认写入飞书', tag: '进行中', tagType: 'warning' },
  ];
});

const allSteps = computed(() => {
  return rawSteps.value.map((s, i) => ({
    ...s,
    status: i < currentStep.value ? 'done' : i === currentStep.value ? 'active' : 'pending',
  }));
});

const startAnimation = () => {
  currentStep.value = 0;
  showResult.value = false;
  if (timer) clearInterval(timer);
  timer = setInterval(() => {
    currentStep.value++;
    if (currentStep.value >= rawSteps.value.length) {
      currentStep.value = rawSteps.value.length - 1;
      clearInterval(timer);
      timer = null;
      setTimeout(() => { showResult.value = true; }, 200);
    }
  }, 350);
};

watch(() => result.value, (val) => {
  Object.keys(editData).forEach(k => delete editData[k]);
  if (val?.data) Object.assign(editData, val.data);
  if (val) startAnimation();
  else { currentStep.value = 0; showResult.value = false; }
}, { immediate: true });

onUnmounted(() => { if (timer) clearInterval(timer); });

const handleConfirm = async () => {
  confirming.value = true;
  try {
    const res = await api.confirm(result.value.record_id, editData);
    if (res.data.code === 0) { ElMessage.success('已写入飞书多维表格'); confirmed(); }
    else ElMessage.error(res.data.msg);
  } catch (err) { ElMessage.error('确认失败：' + (err.response?.data?.msg || err.message)); }
  finally { confirming.value = false; }
};

const handleReset = () => reset();
const validationTagType = (v) => ({ accept: 'success', review: 'warning', manual: 'danger' }[v] || 'info');
const validationLabel = (v) => ({ accept: '自动采用', review: '需复核', manual: '转人工' }[v] || v);
</script>

<style scoped>
.process-panel { display: flex; flex-direction: column; gap: 24px; }
.empty-state { text-align: center; padding: 80px 0; color: var(--color-text-muted); }
.empty-state p { margin-top: 12px; font-size: 15px; }
.timeline { background: #fff; border-radius: var(--radius-lg); padding: 24px; box-shadow: var(--color-shadow); }
.timeline-item { display: flex; gap: 16px; }
.timeline-left { display: flex; flex-direction: column; align-items: center; }
.step-circle { width: 28px; height: 28px; border-radius: 50%; display: flex; align-items: center; justify-content: center; flex-shrink: 0; transition: all 0.3s; }
.step-circle.done { background: var(--color-primary); color: #fff; }
.step-circle.active { background: #fff; border: 2px solid var(--color-primary); color: var(--color-primary); animation: pulse 1.5s infinite; }
.step-circle.pending { background: var(--color-bg-secondary); border: 2px solid var(--color-border); }
@keyframes pulse { 0%,100% { opacity: 1; } 50% { opacity: 0.5; } }
.step-line { width: 2px; flex: 1; min-height: 28px; margin: 4px 0; background: var(--color-border); transition: background 0.3s; }
.step-line.done { background: var(--color-primary); }
.timeline-right { padding-bottom: 16px; flex: 1; }
.step-header { display: flex; align-items: center; gap: 8px; }
.step-title { font-size: 14px; font-weight: 600; }
.step-detail { font-size: 12px; color: var(--color-text-secondary); margin-top: 4px; }
.result-section { display: flex; flex-direction: column; gap: 16px; }
.result-status-bar { display: flex; gap: 8px; flex-wrap: wrap; }
.image-card { background: #fff; border-radius: var(--radius-lg); padding: 12px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.form-card { background: #fff; border-radius: var(--radius-lg); padding: 20px; box-shadow: 0 1px 3px rgba(0,0,0,0.05); }
.analysis-box { background: #fefce8; border-radius: 8px; padding: 12px 16px; margin-top: 12px; }
.analysis-title { font-size: 13px; font-weight: 600; color: var(--color-text-secondary); margin-bottom: 6px; }
.analysis-content { font-size: 14px; }
.analysis-label { font-weight: 600; color: var(--color-text-secondary); }
.action-bar { margin-top: 16px; display: flex; gap: 12px; }
.fade-enter-active { transition: opacity 0.4s; }
.fade-enter-from { opacity: 0; }
@media (max-width: 768px) { .timeline { padding: 16px; } .form-card { padding: 16px; } }
</style>
