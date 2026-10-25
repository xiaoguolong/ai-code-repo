import { computed, ref } from 'vue';
import { defineStore } from 'pinia';
import * as api from '@/api/platform';
import { TOKEN_KEY } from '@/api/http';
import type { CurrentUser } from '@/api/types';

/**
 * 认证会话（Week 29）。
 *
 * token 存 sessionStorage（关闭标签即失效）；用户信息由 /auth/me 恢复。
 * 401 的统一处理在 main.ts 注册的 unauthorizedHandler。
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref(sessionStorage.getItem(TOKEN_KEY) ?? '');
  const user = ref<CurrentUser | null>(null);
  const loading = ref(false);

  const isAdmin = computed(() => user.value?.roleKey === 'admin');
  const canRunAgents = computed(
    () => user.value?.roleKey === 'admin' || user.value?.roleKey === 'operator'
  );
  const roleLabel = computed(() => {
    switch (user.value?.roleKey) {
      case 'admin':
        return '管理员';
      case 'operator':
        return '运营';
      case 'viewer':
        return '只读';
      default:
        return user.value?.roleKey ?? '-';
    }
  });

  async function login(username: string, password: string): Promise<void> {
    loading.value = true;
    try {
      const result = await api.login(username, password);
      token.value = result.token;
      sessionStorage.setItem(TOKEN_KEY, result.token);
      await loadMe();
    } finally {
      loading.value = false;
    }
  }

  async function loadMe(): Promise<CurrentUser> {
    const current = await api.me();
    user.value = current;
    return current;
  }

  async function ensureUser(): Promise<void> {
    if (!token.value) {
      return;
    }
    if (!user.value) {
      await loadMe();
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

  return {
    token,
    user,
    loading,
    isAdmin,
    canRunAgents,
    roleLabel,
    login,
    loadMe,
    ensureUser,
    logout,
    clear
  };
});
