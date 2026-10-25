<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import {
  cancelFollowUp,
  completeFollowUp,
  followUpReminders,
  getFollowUp,
  listFollowUps
} from '@/api/platform';
import type { FollowUpReminderItem, FollowUpTask } from '@/api/types';
import { ApiError } from '@/api/http';
import { formatTime } from '@/utils/format';
import StatusTag from '@/components/StatusTag.vue';
import PageHeader from '@/components/PageHeader.vue';

type TabKey = 'PENDING' | 'COMPLETED' | 'CANCELLED' | 'REMINDERS';
type Row = FollowUpTask | FollowUpReminderItem;

const activeTab = ref<TabKey>('PENDING');
const loading = ref(false);
const error = ref('');
const rows = ref<Row[]>([]);
const actionOpen = ref(false);
const actionType = ref<'complete' | 'cancel'>('complete');
const note = ref('');
const submitting = ref(false);
const current = ref<FollowUpTask | null>(null);

const columns = [
  { title: '标题', dataIndex: 'title', key: 'title' },
  { title: '患者', dataIndex: 'patientId', key: 'patientId', width: 100 },
  { title: '优先级', dataIndex: 'priority', key: 'priority', width: 90 },
  { title: '状态/到期', key: 'state', width: 170 },
  { title: '操作', key: 'actions', width: 170 }
];

function isReminder(row: Row): row is FollowUpReminderItem {
  return 'overdueDays' in row;
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    if (activeTab.value === 'REMINDERS') {
      const result = await followUpReminders(7);
      rows.value = [...result.overdue, ...result.dueSoon];
    } else {
      const result = await listFollowUps(activeTab.value as never, undefined, 100);
      rows.value = result.tasks;
    }
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '随访任务加载失败';
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

async function openAction(
  row: Row | Record<string, unknown>,
  type: 'complete' | 'cancel'
): Promise<void> {
  const target = row as Row;
  try {
    current.value = isReminder(target) ? await getFollowUp(target.taskId) : target;
    actionType.value = type;
    note.value = '';
    actionOpen.value = true;
  } catch (e) {
    message.error(e instanceof ApiError ? e.message : '任务详情加载失败');
  }
}

async function submitAction(): Promise<void> {
  if (!current.value) {
    return;
  }
  submitting.value = true;
  try {
    if (actionType.value === 'complete') {
      await completeFollowUp(current.value.taskId, note.value.trim());
      message.success('任务已完成');
    } else {
      await cancelFollowUp(current.value.taskId, note.value.trim());
      message.success('任务已取消');
    }
    actionOpen.value = false;
    await load();
  } catch (e) {
    const apiError = e instanceof ApiError ? e : null;
    message.error(apiError?.message ?? '操作失败');
    if (apiError?.status === 409) {
      actionOpen.value = false;
      await load();
    }
  } finally {
    submitting.value = false;
  }
}

watch(activeTab, () => void load());
onMounted(() => void load());
</script>

<template>
  <div>
    <PageHeader title="随访任务" subtitle="完成/取消是原子状态迁移，重复操作会返回 409">
      <template #extra>
        <a-button :loading="loading" @click="load">刷新</a-button>
      </template>
    </PageHeader>

    <a-alert v-if="error" type="error" :message="error" show-icon style="margin-bottom: 16px" />

    <a-card>
      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="PENDING" tab="待办" />
        <a-tab-pane key="COMPLETED" tab="已完成" />
        <a-tab-pane key="CANCELLED" tab="已取消" />
        <a-tab-pane key="REMINDERS" tab="站内提醒" />
      </a-tabs>

      <a-space style="margin-bottom: 12px">
        <a-tag color="blue">{{ rows.length }} 条</a-tag>
      </a-space>

      <a-table
        :data-source="rows"
        :columns="columns"
        :loading="loading"
        :pagination="{ pageSize: 20, showSizeChanger: false }"
        :row-key="(record: Row) => ('taskId' in record ? record.taskId : '')"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'title'">
            <div style="font-weight: 500">{{ record.title }}</div>
            <div style="color: #8c8c8c; font-size: 12px">{{ record.patientId }}</div>
          </template>
          <template v-else-if="column.key === 'priority'">
            <StatusTag :status="record.priority" />
          </template>
          <template v-else-if="column.key === 'state'">
            <template v-if="'overdueDays' in record">
              <a-tag :color="record.overdueDays > 0 ? 'red' : 'orange'">
                {{ record.overdueDays > 0 ? `逾期 ${record.overdueDays} 天` : '即将到期' }}
              </a-tag>
              <div style="color: #8c8c8c; font-size: 12px">{{ formatTime(record.dueAt) }}</div>
            </template>
            <template v-else>
              <StatusTag :status="record.status" />
              <div style="color: #8c8c8c; font-size: 12px">到期：{{ formatTime(record.dueAt) }}</div>
            </template>
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a-button size="small" type="primary" @click="openAction(record, 'complete')">
                完成
              </a-button>
              <a-button size="small" danger @click="openAction(record, 'cancel')">取消</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <a-modal
      v-model:open="actionOpen"
      :title="actionType === 'complete' ? '完成任务' : '取消任务'"
      :confirm-loading="submitting"
      @ok="submitAction"
    >
      <template v-if="current">
        <a-descriptions :column="1" size="small" style="margin-bottom: 12px">
          <a-descriptions-item label="任务">{{ current.title }}</a-descriptions-item>
          <a-descriptions-item label="患者">{{ current.patientId }}</a-descriptions-item>
          <a-descriptions-item label="说明">{{ current.detail }}</a-descriptions-item>
        </a-descriptions>
      </template>
      <a-textarea
        v-model:value="note"
        :rows="3"
        :placeholder="actionType === 'complete' ? '完成备注（可选）' : '取消原因（可选）'"
        :maxlength="500"
        show-count
      />
    </a-modal>
  </div>
</template>
