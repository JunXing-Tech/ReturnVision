import { ref } from 'vue';

// 全局共享应用状态
const uploadResult = ref(null);
const activeTab = ref('home');

export function useAppState() {
  const setResult = (val) => { uploadResult.value = val; };
  const goToTab = (tab) => { activeTab.value = tab; };
  const reset = () => { uploadResult.value = null; activeTab.value = 'home'; };
  const confirmed = () => { uploadResult.value = null; activeTab.value = 'records'; };

  return { uploadResult, activeTab, setResult, goToTab, reset, confirmed };
}
