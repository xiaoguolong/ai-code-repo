<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  approvalReminders,
  followUpReminders,
  listApprovals,
  listExecutions
} from '@/api/platform';
import type { Approval, ExecutionRecord, FollowUpReminderItem } from '@/api/types';
import StatusTag from '@/components/StatusTag.vue';
import PageHeader from '@/components/PageHeader.vue';
import { formatTime, truncate } from '@/utils/format';
import { ApiError } from '@/api/http';

const router = useRouter();
const loading = ref(true);
const error = ref('');
const executions = ref<ExecutionRecord[]>([]);
const pendingApprovals = ref<Approval[]>([]);
const overdueItems = ref<FollowUpReminderItem[]>([]);
const overdueApprovals = ref(0);

const columns = [
  { title: '执行 ID', dataIndex: 'executionId', key: 'executionId', width: 260, ellipsis: true },
  { title: 'Agent', dataIndex: 'agentKey', key: 'agentKey', width: 180 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 120 },
  { title: 'Token', dataIndex: ['usage', 'totalTokens'], key: 'tokens', width: 90 },
  { title: '开始时间', dataIndex: 'startedAt', key: 'startedAt', width: 180 }
];

async function load(): Promise<void> {
  loading.value = true;
  error.value = '';
  const [execResult, approvalResult, approvalReminderResult, followupReminderResult] =
    await Promise.allSettled([
      listExecutions(),
      listApprovals('PENDING', 50),
      approvalReminders(20),
      followUpReminders(7)
    ]);
  if (execResult.status === 'fulfilled') {
    executions.value = execResult.value;
  } else {
    error.value = execResult.reason instanceof ApiError ? execResult.reason.message : '执行记录加载失败';
  }
  if (approvalResult.status === 'fulfilled') {
    pendingApprovals.value = approvalResult.value.items;
  }
  if (approvalReminderResult.status === 'fulfilled') {
    overdueApprovals.value = approvalReminderResult.value.total;
  }
  if (followupReminderResult.status === 'fulfilled') {
    overdueItems.value = followupReminderResult.value.overdue;
  }
  loading.value = false;
}

function openExecution(record: ExecutionRecord): void {
  void router.push(`/executions/${record.executionId}`);
}

onMounted(() => void load());
</script>

<template>
  <div>
    <PageHeader title="工作台" subtitle="今天的待办与最近执行">
      <template #extra>
        <a-button :loading="loading" @click="load">刷新</a-button>
      </template>
    </PageHeader>

    <a-alert v-if="error" type="error" :message="error" show-icon style="margin-bottom: 16px" />

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card class="stat-card" hoverable @click="router.push('/executions')">
          <a-statistic title="执行记录" :value="executions.length" suffix="条" />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card class="stat-card" hoverable @click="router.push('/approvals')">
          <a-statistic title="待审审批" :value="pendingApprovals.length" suffix="条" />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card class="stat-card" hoverable @click="router.push('/approvals?tab=OVERDUE')">
          <a-statistic title="超时审批" :value="overdueApprovals" suffix="条" />
        </a-card>
      </a-col>
      <a-col :xs="24" :sm="12" :lg="6">
        <a-card class="stat-card" hoverable @click="router.push('/followups?tab=REMINDERS')">
          <a-statistic title="逾期随访" :value="overdueItems.length" suffix="条" />
        </a-card>
      </a-col>
    </a-row>

    <a-card style="margin-top: 16px" title="快速开始">
      <a-space wrap>
        <a-button type="primary" @click="router.push('/agents/patient-analysis')">患者分析</a-button>
        <a-button @click="router.push('/agents/operations-analysis')">运营日报</a-button>
        <a-button @click="router.push('/agents/knowledge-qa')">知识库问答</a-button>
        <a-button @click="router.push('/agents/followup')">随访计划</a-button>
      </a-space>
    </a-card>

    <a-card style="margin-top: 16px" title="最近执行">
      <a-table
        :data-source="executions.slice(0, 8)"
        :columns="columns"
        :pagination="false"
        :loading="loading"
        row-key="executionId"
        size="middle"
        :custom-row="(record: ExecutionRecord) => ({ onClick: () => openExecution(record), class: 'clickable-row' })"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <StatusTag :status="record.status" />
          </template>
          <template v-else-if="column.key === 'executionId'">
            <span class="mono">{{ truncate(record.executionId, 28) }}</span>
          </template>
          <template v-else-if="column.key === 'startedAt'">
            {{ formatTime(record.startedAt) }}
          </template>
        </template>
      </a-table>
      <a-empty v-if="!loading && executions.length === 0" description="还没有执行记录" />
    </a-card>
  </div>
</template>
