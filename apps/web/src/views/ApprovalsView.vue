<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { message } from 'ant-design-vue';
import {
  approvalReminders,
  decideApproval,
  getApproval,
  listApprovals
} from '@/api/platform';
import type { Approval, ApprovalReminderItem } from '@/api/types';
import { ApiError } from '@/api/http';
import { formatTime, truncate } from '@/utils/format';
import StatusTag from '@/components/StatusTag.vue';
import PageHeader from '@/components/PageHeader.vue';

type TabKey = 'PENDING' | 'APPROVED' | 'REJECTED' | 'OVERDUE';
type Row = Approval | ApprovalReminderItem;

const route = useRoute();
const activeTab = ref<TabKey>('PENDING');
const loading = ref(false);
const error = ref('');
const rows = ref<Row[]>([]);
const thresholdHours = ref(24);
const detailOpen = ref(false);
const decisionOpen = ref(false);
const decisionApproved = ref(true);
const comment = ref('');
const deciding = ref(false);
const current = ref<Approval | null>(null);

const columns = [
  { title: '审批 ID', dataIndex: 'approvalId', key: 'approvalId', width: 260, ellipsis: true },
  { title: '患者', dataIndex: 'patientId', key: 'patientId', width: 90 },
  { title: '风险', dataIndex: 'riskLevel', key: 'riskLevel', width: 80 },
  { title: 'Agent', dataIndex: 'agentKey', key: 'agentKey', width: 160 },
  { title: '状态/超时', key: 'state', width: 140 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 }
];

const tabTitle = computed(() => {
  switch (activeTab.value) {
    case 'APPROVED':
      return '已批准';
    case 'REJECTED':
      return '已拒绝';
    case 'OVERDUE':
      return `超时（>${thresholdHours.value}h）`;
    default:
      return '待办';
  }
});

function isReminder(row: Row): row is ApprovalReminderItem {
  return 'overdueHours' in row;
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    if (activeTab.value === 'OVERDUE') {
      const result = await approvalReminders(50);
      thresholdHours.value = result.thresholdHours;
      rows.value = result.items;
    } else {
      const result = await listApprovals(activeTab.value, 100);
      rows.value = result.items;
    }
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '审批列表加载失败';
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

async function openRow(row: Row): Promise<void> {
  try {
    current.value = isReminder(row) ? await getApproval(row.approvalId) : row;
    detailOpen.value = true;
  } catch (e) {
    message.error(e instanceof ApiError ? e.message : '审批详情加载失败');
  }
}

function openDecision(approved: boolean): void {
  decisionApproved.value = approved;
  comment.value = '';
  decisionOpen.value = true;
}

async function submitDecision(): Promise<void> {
  if (!current.value) {
    return;
  }
  deciding.value = true;
  try {
    await decideApproval(current.value.approvalId, decisionApproved.value, comment.value.trim());
    message.success(decisionApproved.value ? '已批准' : '已拒绝');
    decisionOpen.value = false;
    detailOpen.value = false;
    await load();
  } catch (e) {
    const apiError = e instanceof ApiError ? e : null;
    message.error(apiError?.message ?? '决策失败');
    if (apiError?.status === 409) {
      detailOpen.value = false;
      await load();
    }
  } finally {
    deciding.value = false;
  }
}

async function openFromQuery(): Promise<void> {
  const approvalId = route.query.approvalId;
  if (typeof approvalId === 'string' && approvalId) {
    try {
      current.value = await getApproval(approvalId);
      detailOpen.value = true;
    } catch {
      // 忽略：可能已被删除/无权限
    }
  }
}

watch(activeTab, () => void load());
onMounted(async () => {
  await load();
  await openFromQuery();
});
</script>

<template>
  <div>
    <PageHeader title="审批" subtitle="高风险患者分析需要人工批准后才写记忆、生成随访">
      <template #extra>
        <a-button :loading="loading" @click="load">刷新</a-button>
      </template>
    </PageHeader>

    <a-alert v-if="error" type="error" :message="error" show-icon style="margin-bottom: 16px" />

    <a-card>
      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="PENDING" tab="待办" />
        <a-tab-pane key="APPROVED" tab="已批准" />
        <a-tab-pane key="REJECTED" tab="已拒绝" />
        <a-tab-pane key="OVERDUE" tab="超时提醒" />
      </a-tabs>

      <a-space style="margin-bottom: 12px">
        <a-tag color="blue">{{ tabTitle }}</a-tag>
        <span style="color: #8c8c8c">{{ rows.length }} 条</span>
      </a-space>

      <a-table
        :data-source="rows"
        :columns="columns"
        :loading="loading"
        :pagination="{ pageSize: 20, showSizeChanger: false }"
        :row-key="(record: Row) => record.approvalId"
        size="middle"
        :custom-row="
          (record: Row) => ({ onClick: () => openRow(record), class: 'clickable-row' })
        "
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'approvalId'">
            <span class="mono">{{ record.approvalId }}</span>
          </template>
          <template v-else-if="column.key === 'riskLevel'">
            <StatusTag :status="record.riskLevel" />
          </template>
          <template v-else-if="column.key === 'state'">
            <template v-if="'overdueHours' in record">
              <a-tag color="red">超时 {{ record.overdueHours }}h</a-tag>
            </template>
            <StatusTag v-else :status="record.status" />
          </template>
          <template v-else-if="column.key === 'createdAt'">
            {{ formatTime(record.createdAt) }}
          </template>
          <template v-else-if="column.key === 'patientId'">
            {{ record.patientId }}
          </template>
          <template v-else-if="column.key === 'agentKey'">
            {{ record.agentKey }}
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal v-model:open="detailOpen" title="审批详情" width="720px" :footer="null">
      <template v-if="current">
        <a-descriptions bordered :column="2" size="small" style="margin-bottom: 16px">
          <a-descriptions-item label="审批 ID" :span="2">
            <span class="mono">{{ current.approvalId }}</span>
          </a-descriptions-item>
          <a-descriptions-item label="执行 ID" :span="2">
            <router-link :to="`/executions/${current.executionId}`">
              <span class="mono">{{ truncate(current.executionId, 36) }}</span>
            </router-link>
          </a-descriptions-item>
          <a-descriptions-item label="患者">{{ current.patientId }}</a-descriptions-item>
          <a-descriptions-item label="风险">
            <StatusTag :status="current.riskLevel" />
          </a-descriptions-item>
          <a-descriptions-item label="Agent">{{ current.agentKey }}</a-descriptions-item>
          <a-descriptions-item label="状态">
            <StatusTag :status="current.status" />
          </a-descriptions-item>
          <a-descriptions-item label="创建时间">{{ formatTime(current.createdAt) }}</a-descriptions-item>
          <a-descriptions-item label="决策时间">{{ formatTime(current.decidedAt) }}</a-descriptions-item>
          <a-descriptions-item label="意见" :span="2">{{ current.comment || '-' }}</a-descriptions-item>
        </a-descriptions>

        <h4>任务</h4>
        <pre class="pre-wrap mono">{{ current.task }}</pre>

        <a-space v-if="current.status === 'PENDING'" style="margin-top: 16px">
          <a-button type="primary" @click="openDecision(true)">批准</a-button>
          <a-button danger @click="openDecision(false)">拒绝</a-button>
        </a-space>
      </template>
    </a-modal>

    <a-modal
      v-model:open="decisionOpen"
      :title="decisionApproved ? '批准审批' : '拒绝审批'"
      :confirm-loading="deciding"
      @ok="submitDecision"
    >
      <a-alert
        :type="decisionApproved ? 'success' : 'warning'"
        :message="
          decisionApproved
            ? '批准后会写入长期记忆，并生成随访任务'
            : '拒绝后不写记忆、不生成随访任务'
        "
        show-icon
        style="margin-bottom: 12px"
      />
      <a-textarea
        v-model:value="comment"
        :rows="3"
        placeholder="审批意见（可选）"
        :maxlength="500"
        show-count
      />
    </a-modal>
  </div>
</template>
