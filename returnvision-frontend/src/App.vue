<template>
  <div class="app-container">
    <el-header class="app-header">
      <h1>退运智录 - 快递面单OCR识别系统</h1>
    </el-header>
    <el-main>
      <el-tabs v-model="activeTab" class="main-tabs">
        <el-tab-pane label="上传识别" name="upload">
          <UploadPanel v-if="activeTab === 'upload'" />
        </el-tab-pane>
        <el-tab-pane label="记录列表" name="records">
          <RecordsPanel v-if="activeTab === 'records'" />
        </el-tab-pane>
      </el-tabs>
    </el-main>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import UploadPanel from './components/UploadPanel.vue';
import RecordsPanel from './components/RecordsPanel.vue';

const activeTab = ref('upload');
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
body { font-family: "Microsoft YaHei", sans-serif; background: #f5f7fa; }
.app-container { min-height: 100vh; }
.app-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  display: flex;
  align-items: center;
  padding: 0 30px;
  height: 60px;
}
.app-header h1 { font-size: 20px; font-weight: 500; }
.main-tabs { padding: 20px 30px; }
.main-tabs .el-tabs__header { margin-bottom: 20px; }
</style>
