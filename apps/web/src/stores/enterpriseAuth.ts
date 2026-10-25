import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import * as api from '@/api/enterprise';
import type { EnterpriseUser } from '@/api/types';

const TOKEN_KEY = 'aicode_enterprise_token';

/**
 * 企业会话（Week 30）：token 与平台 sessionStorage key 完全分开。
 * 企业 401 只清本 store，不影响平台登录。
 */
export const useEnterpriseAuthStore = defineStore('enterpriseAuth', () => {
  const token = ref(sessionStorage.getItem(TOKEN_KEY) ?? '');
  const user = ref<EnterpriseUser | null>(null);
  const loading = ref(false);

  const username = computed(() => user.value?.username ?? '');

  async function login(name: string, password: string): Promise<void> {
    loading.value = true;
    try {
      const result = await api.login(name, password);
      token.value = result.token;
      sessionStorage.setItem(TOKEN_KEY, result.token);
      user.value = result.user;
    } finally {
      loading.value = false;
    }
  }

  async function ensureUser(): Promise<void> {
    if (!token.value) {
      return;
    }
    if (!user.value) {
      user.value = await api.me();
    }
  }

  async function logout(): Promise<void> {
    try {
      if (token.value) {
        await api.logout();
      }
    } finally {
      clear();
    }
  }

  function clear(): void {
    token.value = '';
    user.value = null;
    sessionStorage.removeItem(TOKEN_KEY);
  }

  return { token, user, loading, username, login, ensureUser, logout, clear };
});
