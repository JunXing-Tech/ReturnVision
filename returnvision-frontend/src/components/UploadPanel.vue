<template>
  <div class="upload-panel">
    <!-- 上传区域 -->
    <el-row :gutter="20" v-if="!result">
      <el-col :span="24">
        <el-upload
          ref="uploadRef"
          drag
          :auto-upload="true"
          :show-file-list="false"
          :http-request="handleUpload"
          accept="image/*"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽快递面单图片到此处，或<em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">支持 JPG/PNG 格式，文件大小不超过 10MB</div>
          </template>
        </el-upload>
      </el-col>
    </el-row>

    <!-- 加载中 -->
    <el-row v-if="loading" :gutter="20">
      <el-col :span="24">
        <el-card shadow="hover">
          <div class="loading-container">
            <el-icon class="is-loading" :size="40"><Loading /></el-icon>
            <p>正在识别中，请稍候...</p>
            <p class="loading-tip">双引擎OCR + DeepSeek分析</p>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 识别结果 -->
    <el-row v-if="result" :gutter="20">
      <!-- 左侧：图片预览 -->
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>面单图片</template>
          <el-image :src="result.image_url" fit="contain" style="max-height: 400px" :preview-src-list="[result.image_url]" />
        </el-card>
      </el-col>

      <!-- 右侧：识别结果 -->
      <el-col :span="16">
        <!-- 交叉验证状态 -->
        <el-card shadow="hover" class="result-card">
          <template #header>
            <div class="card-header">
              <span>识别结果</span>
              <el-tag :type="validationTagType(result.cross_validation)" size="large">
                {{ validationLabel(result.cross_validation) }}
              </el-tag>
              <el-tag type="info" size="large">置信度: {{ result.confidence }}</el-tag>
            </div>
          </template>
          <el-form :model="editData" label-width="100px" size="default">
            <el-row :gutter="10">
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
        </el-card>

        <!-- LLM分析 -->
        <el-card shadow="hover" class="result-card" v-if="result.return_reason">
          <template #header>DeepSeek 分析</template>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="退货原因">{{ result.return_reason }}</el-descriptions-item>
            <el-descriptions-item label="退货分类">
              <el-tag type="warning">{{ result.return_category }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 校验警告 -->
        <el-card shadow="hover" class="result-card" v-if="result.validation && result.validation.warnings.length > 0">
          <template #header>
            <div class="card-header">
              <span>数据校验</span>
              <el-tag :type="result.validation.passed ? 'success' : 'danger'">
                {{ result.validation.passed ? '通过' : '不通过' }}
              </el-tag>
            </div>
          </template>
          <el-alert
            v-for="(w, i) in result.validation.warnings"
            :key="i"
            :title="w"
            type="warning"
            :closable="false"
            show-icon
            style="margin-bottom: 5px"
          />
        </el-card>

        <!-- 操作按钮 -->
        <div class="action-buttons">
          <el-button type="primary" size="large" @click="handleConfirm" :loading="confirming">
            确认写入飞书
          </el-button>
          <el-button size="large" @click="handleReset">重新上传</el-button>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { UploadFilled, Loading } from '@element-plus/icons-vue';
import api from '../api';

const loading = ref(false);
const confirming = ref(false);
const result = ref(null);
const editData = reactive({});

const handleUpload = async (options) => {
  loading.value = true;
  result.value = null;
  try {
    const res = await api.upload(options.file);
    if (res.data.code === 0) {
      result.value = res.data.data;
      Object.assign(editData, result.value.data);
      ElMessage.success('识别完成');
    } else {
      ElMessage.error(res.data.msg);
    }
  } catch (err) {
    ElMessage.error('上传失败：' + (err.response?.data?.msg || err.message));
  } finally {
    loading.value = false;
  }
};

const handleConfirm = async () => {
  confirming.value = true;
  try {
    const res = await api.confirm(result.value.record_id, editData);
    if (res.data.code === 0) {
      ElMessage.success('已写入飞书多维表格');
      handleReset();
    } else {
      ElMessage.error(res.data.msg);
    }
  } catch (err) {
    ElMessage.error('确认失败：' + (err.response?.data?.msg || err.message));
  } finally {
    confirming.value = false;
  }
};

const handleReset = () => {
  result.value = null;
  Object.keys(editData).forEach(k => delete editData[k]);
};

const validationTagType = (val) => {
  const map = { accept: 'success', review: 'warning', manual: 'danger' };
  return map[val] || 'info';
};

const validationLabel = (val) => {
  const map = { accept: '自动采用', review: '需复核', manual: '转人工' };
  return map[val] || val;
};
</script>

<style scoped>
.upload-panel { max-width: 1200px; margin: 0 auto; }
.loading-container { text-align: center; padding: 60px 0; }
.loading-container p { margin-top: 15px; color: #666; }
.loading-tip { font-size: 13px; color: #999; }
.result-card { margin-bottom: 15px; }
.card-header { display: flex; align-items: center; gap: 10px; }
.action-buttons { text-align: center; margin-top: 20px; }
</style>
