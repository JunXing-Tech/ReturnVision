import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  timeout: 120000,
});

export default {
  upload(file) {
    const formData = new FormData();
    formData.append('file', file);
    return api.post('/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  confirm(recordId, editedData) {
    return api.post('/confirm', { record_id: recordId, edited_data: editedData });
  },
  getRecords(status = '', page = 1, size = 20) {
    return api.get('/records', { params: { status, page, size } });
  },
  batchUpload(files) {
    const formData = new FormData();
    files.forEach(f => formData.append('files', f));
    return api.post('/records/batch', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
};
