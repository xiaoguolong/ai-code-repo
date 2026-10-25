<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { ApiError } from '@/api/http';

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const form = reactive({ username: '', password: '' });
const loading = ref(false);
const error = ref('');

async function submit(): Promise<void> {
  error.value = '';
  loading.value = true;
  try {
    await auth.login(form.username, form.password);
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/';
    await router.replace(redirect);
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '登录失败，请稍后再试';
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <a-card class="login-card" :bordered="false">
      <div style="text-align: center; margin-bottom: 24px">
        <h1 style="margin-bottom: 4px; color: #1677ff; font-size: 26px">医疗 SaaS AI Agent 平台</h1>
        <div style="color: #8c8c8c; font-size: 13px">登录后使用患者分析、运营日报、知识库问答与随访</div>
      </div>

      <a-alert
        v-if="error"
        type="error"
        :message="error"
        show-icon
        style="margin-bottom: 16px"
      />

      <a-form :model="form" layout="vertical" @finish="submit">
        <a-form-item
          label="用户名"
          name="username"
          :rules="[{ required: true, message: '请输入用户名' }]"
        >
          <a-input
            v-model:value="form.username"
            size="large"
            placeholder="admin / operator / viewer"
            autocomplete="username"
          />
        </a-form-item>
        <a-form-item
          label="密码"
          name="password"
          :rules="[{ required: true, message: '请输入密码' }]"
        >
          <a-input-password
            v-model:value="form.password"
            size="large"
            placeholder="请输入密码"
            autocomplete="current-password"
          />
        </a-form-item>
        <a-form-item style="margin-bottom: 8px">
          <a-button type="primary" html-type="submit" size="large" block :loading="loading">
            登录
          </a-button>
        </a-form-item>
      </a-form>

      <div style="color: #bfbfbf; font-size: 12px; text-align: center; margin-top: 12px">
        演示账号：admin / admin123 ｜ operator / operator123 ｜ viewer / viewer123
      </div>
    </a-card>
  </div>
</template>
