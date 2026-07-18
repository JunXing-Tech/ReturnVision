// 步骤1：鉴权 composable
// 职责：管理 access_token / refresh_token / 用户信息，提供登录登出方法
// 用法：const { isAuthenticated, currentUser, login, logout, getAuthHeader } = useAuth();

import { ref, computed } from 'vue';

const ACCESS_TOKEN_KEY = 'returnvision-access-token';
const REFRESH_TOKEN_KEY = 'returnvision-refresh-token';
const USER_KEY = 'returnvision-user';

const accessToken = ref(localStorage.getItem(ACCESS_TOKEN_KEY) || '');
const refreshToken = ref(localStorage.getItem(REFRESH_TOKEN_KEY) || '');
const user = ref(JSON.parse(localStorage.getItem(USER_KEY) || 'null'));

const isAuthenticated = computed(() => !!accessToken.value);

const persist = () => {
  if (accessToken.value) {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken.value);
  } else {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
  }
  if (refreshToken.value) {
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken.value);
  } else {
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  }
  if (user.value) {
    localStorage.setItem(USER_KEY, JSON.stringify(user.value));
  } else {
    localStorage.removeItem(USER_KEY);
  }
};

const setTokens = (access, refresh, userInfo) => {
  accessToken.value = access;
  refreshToken.value = refresh;
  user.value = userInfo;
  persist();
};

const updateAccessToken = (access) => {
  accessToken.value = access;
  persist();
};

const clear = () => {
  accessToken.value = '';
  refreshToken.value = '';
  user.value = null;
  persist();
};

export function useAuth() {
  return {
    accessToken,
    refreshToken,
    user,
    isAuthenticated,
    mustChangePassword: computed(() => user.value?.must_change_password === true),
    setTokens,
    updateAccessToken,
    clear,
    getAuthHeader: () => {
      const token = accessToken.value;
      return token ? { Authorization: `Bearer ${token}` } : {};
    },
  };
}
