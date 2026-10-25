<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { listExecutions } from '@/api/platform';
import type { ExecutionRecord } from '@/api/types';
import { ApiError } from '@/api/http';
import { formatTime } from '@/utils/format';
import StatusTag from '@/components/StatusTag.vue';
import PageHeader from '@/components/PageHeader.vue';

const router = useRouter();
const loading = ref(true);
const error = ref('');
const executions = ref<ExecutionRecord[]>([]);
const statusFilter = ref<string>('ALL');
const agentFilter = ref<string>('ALL');

const agents = computed(() => Array.from(new Set(executions.value.map((item) => item.agentKey))));
const filtered = computed(() =>
  executions.value.filter(
    (item) =>
      (statusFilter.value === 'ALL' || item.status === statusFilter.value) &&
      (agentFilter.value === 'ALL' || item.agentKey === agentFilter.value)
  )
);

const columns = [
  { title: '执行 ID', dataIndex: 'executionId', key: 'executionId', width: 280, ellipsis: true },
  { title: 'Agent', dataIndex: 'agentKey', key: 'agentKey', width: 180 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: 'Token', dataIndex: ['usage', 'totalTokens'], key: 'tokens', width: 90 },
  { title: '开始时间', dataIndex: 'startedAt', key: 'startedAt', width: 180 },
  { title: '完成时间', dataIndex: 'finishedAt', key: 'finishedAt', width: 180 }
];

async function load(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    executions.value = await listExecutions();
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '执行记录加载失败';
  } finally {
    loading.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <div>
    <PageHeader title="执行记录" :subtitle="`共 ${executions.length} 条（admin 可见全部，其余仅本人）`">
      <template #extra>
        <a-button :loading="loading" @click="load">刷新</a-button>
      </template>
    </PageHeader>

    <a-alert v-if="error" type="error" :message="error" show-icon style="margin-bottom: 16px" />

    <a-card>
      <a-space wrap style="margin-bottom: 16px">
        <a-select v-model:value="statusFilter" style="width: 180px">
          <a-select-option value="ALL">全部状态</a-select-option>
          <a-select-option value="COMPLETED">完成</a-select-option>
          <a-select-option value="RUNNING">运行中</a-select-option>
          <a-select-option value="FAILED">失败</a-select-option>
          <a-select-option value="PENDING_APPROVAL">待审</a-select-option>
          <a-select-option value="REJECTED">已拒绝</a-select-option>
        </a-select>
        <a-select v-model:value="agentFilter" style="width: 200px">
          <a-select-option value="ALL">全部 Agent</a-select-option>
          <a-select-option v-for="item in agents" :key="item" :value="item">{{ item }}</a-select-option>
        </a-select>
        <a-tag>筛选后 {{ filtered.length }} 条</a-tag>
      </a-space>

      <a-table
        :data-source="filtered"
        :columns="columns"
        :loading="loading"
        :pagination="{ pageSize: 20, showSizeChanger: false }"
        row-key="executionId"
        size="middle"
        :custom-row="
          (record: ExecutionRecord) => ({
            onClick: () => router.push(`/executions/${record.executionId}`),
            class: 'clickable-row'
          })
        "
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <StatusTag :status="record.status" />
          </template>
          <template v-else-if="column.key === 'startedAt'">{{ formatTime(record.startedAt) }}</template>
          <template v-else-if="column.key === 'finishedAt'">{{ formatTime(record.finishedAt) }}</template>
          <template v-else-if="column.key === 'executionId'">
            <span class="mono">{{ record.executionId }}</span>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>
