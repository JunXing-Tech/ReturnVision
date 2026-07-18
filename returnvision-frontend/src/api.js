import axios from 'axios';
import { useAuth } from './composables/useAuth';

const api = axios.create({
  baseURL: '/api',
  timeout: 120000,
});

// 步骤1：请求拦截器 - 自动注入 Authorization Header
api.interceptors.request.use((config) => {
  const { getAuthHeader } = useAuth();
  const headers = getAuthHeader();
  if (headers.Authorization) {
    config.headers.Authorization = headers.Authorization;
  }
  return config;
});

// 步骤2：响应拦截器 - 401 时尝试用 refresh token 刷新，失败则跳登录
let isRefreshing = false;
let refreshPromise = null;

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    // 401 且未在刷新中 且非登录接口 -> 尝试刷新
    if (error.response?.status === 401
        && !originalRequest._retry
        && !originalRequest.url.includes('/auth/')) {
      originalRequest._retry = true;
      const { refreshToken, updateAccessToken, clear } = useAuth();

      if (!refreshToken.value) {
        clear();
        return Promise.reject(error);
      }

      // 并发刷新合并
      if (!isRefreshing) {
        isRefreshing = true;
        refreshPromise = axios.post('/api/auth/refresh', {
          refresh_token: refreshToken.value,
        }).then((resp) => {
          updateAccessToken(resp.data.data.access_token);
          return resp.data.data.access_token;
        }).catch((err) => {
          clear();
          throw err;
        }).finally(() => {
          isRefreshing = false;
          refreshPromise = null;
        });
      }

      try {
        const newToken = await refreshPromise;
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return api(originalRequest);
      } catch (refreshErr) {
        return Promise.reject(refreshErr);
      }
    }
    return Promise.reject(error);
  }
);

export default {
  // ==================== 鉴权接口 ====================

  /**
   * 账号密码登录
   */
  login(username, password) {
    return api.post('/auth/login', { username, password });
  },

  /**
   * 获取飞书 OAuth 授权 URL
   */
  getFeishuAuthUrl() {
    return api.get('/auth/feishu/url');
  },

  /**
   * 飞书 OAuth 回调
   */
  feishuCallback(code, state) {
    return api.post('/auth/feishu/callback', { code, state });
  },

  /**
   * 登出
   */
  logout() {
    return api.post('/auth/logout');
  },

  /**
   * 获取当前用户信息
   */
  getMe() {
    return api.get('/auth/me');
  },

  /**
   * 修改密码
   */
  changePassword(oldPassword, newPassword) {
    return api.post('/auth/change-password', {
      old_password: oldPassword,
      new_password: newPassword,
    });
  },

  // ==================== 用户管理接口（F01.1，仅 ADMIN） ====================

  /**
   * 获取用户列表
   */
  listUsers() {
    return api.get('/admin/users');
  },

  /**
   * 创建用户
   */
  createUser(username, password, displayName, roleCodes, feishuUserId) {
    return api.post('/admin/users', {
      username,
      password,
      display_name: displayName,
      role_codes: roleCodes,
      feishu_user_id: feishuUserId || null,
    });
  },

  /**
   * 编辑用户
   */
  updateUser(id, { displayName, roleCodes, status, feishuUserId }) {
    return api.put(`/admin/users/${id}`, {
      display_name: displayName,
      role_codes: roleCodes,
      status,
      feishu_user_id: feishuUserId,
    });
  },

  /**
   * 删除用户
   */
  deleteUser(id) {
    return api.delete(`/admin/users/${id}`);
  },

  /**
   * 重置密码
   */
  resetPassword(id, newPassword) {
    return api.post(`/admin/users/${id}/reset-password`, {
      new_password: newPassword,
    });
  },

  // ==================== 业务接口 ====================

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
   * 注意：fetch 不能复用 axios 拦截器，需手动注入 Authorization Header
   * @param {File} file 上传的面单图片
   * @param {Function} onStep 回调 (step, label, status, subSteps?)
   * @param {Function} onResult 回调 (resultData)
   * @param {Function} onError 回调 (errorMsg)
   * @returns {Function} cancelFn 取消函数
   */
  uploadSSE(file, onStep, onResult, onError) {
    const controller = new AbortController();
    const { getAuthHeader } = useAuth();

    const run = async () => {
      try {
        const formData = new FormData();
        formData.append('file', file);

        // 手动注入 Authorization Header（SSE 用 fetch，不能复用 axios 拦截器）
        const headers = { ...getAuthHeader() };

        const resp = await fetch('/api/upload/sse', {
          method: 'POST',
          headers,
          body: formData,
          signal: controller.signal,
        });

        if (resp.status === 401) {
          onError('登录已过期，请重新登录');
          return;
        }
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
