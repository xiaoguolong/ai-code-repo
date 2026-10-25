<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { message } from 'ant-design-vue';
import { disableUser, enableUser, listUsers } from '@/api/platform';
import type { PlatformUserItem } from '@/api/types';
import { ApiError } from '@/api/http';
import { useAuthStore } from '@/stores/auth';
import { formatTime } from '@/utils/format';
import PageHeader from '@/components/PageHeader.vue';

const auth = useAuthStore();
const loading = ref(false);
const error = ref('');
const users = ref<PlatformUserItem[]>([]);

const columns = [
  { title: 'ID', dataIndex: 'userId', key: 'userId', width: 80 },
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '角色', dataIndex: 'roleKey', key: 'roleKey', width: 110 },
  { title: '租户', dataIndex: 'tenantKey', key: 'tenantKey', width: 120 },
  { title: '状态', key: 'state', width: 170 },
  { title: '操作', key: 'actions', width: 140 }
];

async function load(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    const result = await listUsers();
    users.value = result.items;
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '用户列表加载失败';
  } finally {
    loading.value = false;
  }
}

async function toggle(user: PlatformUserItem | Record<string, unknown>): Promise<void> {
  const target = user as PlatformUserItem;
  try {
    if (target.disabled) {
      await enableUser(target.userId);
      message.success('已恢复');
    } else {
      await disableUser(target.userId);
      message.success('已停用');
    }
    await load();
  } catch (e) {
    message.error(e instanceof ApiError ? e.message : '操作失败');
  }
}

onMounted(() => void load());
</script>

<template>
  <div>
    <PageHeader title="用户管理" subtitle="停用不删行；停用会踢出该用户平台会话，恢复后需重新登录">
      <template #extra>
        <a-button :loading="loading" @click="load">刷新</a-button>
      </template>
    </PageHeader>

    <a-alert v-if="error" type="error" :message="error" show-icon style="margin-bottom: 16px" />

    <a-card>
      <a-table
        :data-source="users"
        :columns="columns"
        :loading="loading"
        :pagination="false"
        row-key="userId"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'state'">
            <a-tag :color="record.disabled ? 'red' : 'green'">
              {{ record.disabled ? '已停用' : '正常' }}
            </a-tag>
            <div v-if="record.disabled" style="color: #8c8c8c; font-size: 12px">
              {{ formatTime(record.disabledAt) }}
            </div>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-popconfirm
              :title="record.disabled ? '恢复该用户登录？' : '停用该用户并踢出会话？'"
              ok-text="确认"
              cancel-text="取消"
              @confirm="toggle(record)"
            >
              <a-button
                size="small"
                :danger="!record.disabled"
                :disabled="record.userId === auth.user?.userId"
              >
                {{ record.disabled ? '恢复' : '停用' }}
              </a-button>
            </a-popconfirm>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>
