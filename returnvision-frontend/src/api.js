import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 120000,
});

export default {
  /**
   * 普通上传（非SSE，降级方案）
   */
  upload(file) {
    const formData = new FormData();
    formData.append('file', file);
    return api.post('/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  /**
   * SSE 流式上传：上传后通过 fetch 流式读取后端推送的处理步骤
   * 步骤1：上传至云存储
   * 步骤2：双引擎OCR并行识别
   * 步骤3：交叉验证+仲裁
   * 步骤4：DeepSeek语义分析
   * @param {File} file 上传的面单图片
   * @param {Function} onStep 回调 (step, label, status, subSteps?) 
   * @param {Function} onResult 回调 (resultData)
   * @param {Function} onError 回调 (errorMsg)
   * @returns {Function} cancelFn 取消函数
   */
  uploadSSE(file, onStep, onResult, onError) {
    const controller = new AbortController();
    
    const run = async () => {
      try {
        const formData = new FormData();
        formData.append('file', file);

        const resp = await fetch('/api/upload/sse', {
          method: 'POST',
          body: formData,
          signal: controller.signal,
        });

        if (!resp.ok) {
          onError(`上传失败：HTTP ${resp.status}`);
          return;
        }

        const reader = resp.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';

        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop(); // 保留不完整的行

          for (const line of lines) {
            const trimmed = line.trim();
            if (!trimmed || !trimmed.startsWith('data:')) continue;
            
            const jsonStr = trimmed.slice(5).trim();
            if (!jsonStr) continue;

            try {
              const evt = JSON.parse(jsonStr);
              if (evt.type === 'step') {
                onStep(evt.step, evt.label, evt.status, evt.subSteps);
              } else if (evt.type === 'result') {
                onResult(evt.data);
              } else if (evt.type === 'error') {
                onError(evt.msg || '处理失败');
              }
            } catch (e) {
              // JSON 解析失败，跳过
            }
          }
        }
      } catch (err) {
        if (err.name === 'AbortError') return;
        onError('上传失败：' + (err.message || '网络错误'));
      }
    };

    run();
    return () => controller.abort();
  },

  /**
   * 批量上传
   */
  batchUpload(files) {
    const formData = new FormData();
    files.forEach(f => formData.append('files', f));
    return api.post('/upload/batch', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
      timeout: 300000,
    });
  },

  /**
   * 确认单条记录写入飞书
   */
  confirm(recordId, editedData) {
    return api.post('/confirm', { record_id: recordId, edited_data: editedData });
  },

  /**
   * 批量确认写入飞书
   */
  batchConfirm(recordIds) {
    return api.post('/confirm/batch', { record_ids: recordIds });
  },

  /**
   * 获取记录列表
   */
  getRecords(status = '', page = 1, size = 20) {
    return api.get('/records', { params: { status, page, size } });
  },

  /**
   * 获取仪表盘统计数据
   */
  getDashboardStats() {
    return api.get('/dashboard/stats');
  },

  /**
   * 删除单条退货记录（仅允许删除非已同步记录）
   */
  deleteRecord(id) {
    return api.delete(`/records/${id}`);
  },

  /**
   * 批量删除退货记录
   * @param {number[]} recordIds 要删除的记录ID数组
   */
  batchDeleteRecords(recordIds) {
    return api.delete('/records/batch', { data: { record_ids: recordIds } });
  },
};
