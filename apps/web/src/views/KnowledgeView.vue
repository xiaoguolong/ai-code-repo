<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { message } from 'ant-design-vue';
import {
  deleteKnowledgeBinding,
  importKnowledgeBindings,
  listKnowledgeBindings,
  reconcileKnowledgeBindings,
  runAgent
} from '@/api/platform';
import type {
  ExecutionRecord,
  KnowledgeBinding,
  KnowledgeCitation,
  KnowledgeReconcileResult
} from '@/api/types';
import { ApiError } from '@/api/http';
import { useAuthStore } from '@/stores/auth';
import { formatTime, parseJson } from '@/utils/format';
import StatusTag from '@/components/StatusTag.vue';
import PageHeader from '@/components/PageHeader.vue';

interface QaOutput {
  answer?: string;
  citations?: KnowledgeCitation[];
  hitCount?: number;
  degraded?: boolean;
}

const auth = useAuthStore();
const loading = ref(false);
const error = ref('');
const bindings = ref<KnowledgeBinding[]>([]);
const importing = ref(false);
const reconciling = ref(false);
const reconcileResult = ref<KnowledgeReconcileResult | null>(null);
const asking = ref(false);
const qaResult = ref<ExecutionRecord | null>(null);
const qaForm = reactive({ knowledgeBaseId: undefined as number | undefined, question: '' });

const enabledBindings = computed(() =>
  bindings.value.filter((item) => item.enabled && item.syncStatus !== 'MISSING')
);
const qaOutput = computed<QaOutput | null>(() =>
  qaResult.value ? parseJson<QaOutput>(qaResult.value.outputJson) : null
);

const columns = [
  { title: '知识库 ID', dataIndex: 'knowledgeBaseId', key: 'knowledgeBaseId', width: 110 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '描述', dataIndex: 'description', key: 'description', ellipsis: true },
  { title: '同步状态', dataIndex: 'syncStatus', key: 'syncStatus', width: 140 },
  { title: '最后对账', dataIndex: 'lastSyncedAt', key: 'lastSyncedAt', width: 180 },
  { title: '操作', key: 'actions', width: 110 }
];

async function load(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    const result = await listKnowledgeBindings();
    bindings.value = result.items;
    const first = enabledBindings.value[0];
    if (first && !qaForm.knowledgeBaseId) {
      qaForm.knowledgeBaseId = first.knowledgeBaseId;
    }
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '绑定列表加载失败';
  } finally {
    loading.value = false;
  }
}

async function importBindings(): Promise<void> {
  importing.value = true;
  try {
    const result = await importKnowledgeBindings();
    message.success(`导入完成：新增 ${result.imported}，更新 ${result.updated}`);
    await load();
  } catch (e) {
    message.error(e instanceof ApiError ? e.message : '导入失败');
  } finally {
    importing.value = false;
  }
}

async function reconcile(): Promise<void> {
  reconciling.value = true;
  try {
    reconcileResult.value = await reconcileKnowledgeBindings();
    message.success('对账完成');
    await load();
  } catch (e) {
    message.error(e instanceof ApiError ? e.message : '对账失败');
  } finally {
    reconciling.value = false;
  }
}

async function remove(binding: KnowledgeBinding | Record<string, unknown>): Promise<void> {
  const target = binding as KnowledgeBinding;
  try {
    await deleteKnowledgeBinding(target.bindingId);
    message.success('已删除绑定');
    await load();
  } catch (e) {
    message.error(e instanceof ApiError ? e.message : '删除失败');
  }
}

async function ask(): Promise<void> {
  if (!qaForm.knowledgeBaseId) {
    message.warning('请选择知识库');
    return;
  }
  if (!qaForm.question.trim()) {
    message.warning('请输入问题');
    return;
  }
  asking.value = true;
  qaResult.value = null;
  try {
    qaResult.value = await runAgent('knowledge-qa', {
      knowledgeBaseId: qaForm.knowledgeBaseId,
      question: qaForm.question.trim()
    });
  } catch (e) {
    message.error(e instanceof ApiError ? e.message : '问答失败');
  } finally {
    asking.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <div>
    <PageHeader
      title="知识库"
      subtitle="平台只存绑定与问答；建库/入库在 enterprise（第 30 周页面补齐）"
    >
      <template #extra>
        <a-space>
          <a-button :loading="loading" @click="load">刷新</a-button>
          <a-button v-if="auth.isAdmin" :loading="importing" @click="importBindings">导入绑定</a-button>
          <a-button v-if="auth.isAdmin" :loading="reconciling" @click="reconcile">手动对账</a-button>
        </a-space>
      </template>
    </PageHeader>

    <a-alert v-if="error" type="error" :message="error" show-icon style="margin-bottom: 16px" />

    <a-alert
      v-if="reconcileResult"
      type="info"
      show-icon
      style="margin-bottom: 16px"
      :message="
        `对账：groups=${reconcileResult.groups} checked=${reconcileResult.checked} ` +
        `updated=${reconcileResult.updated} missing=${reconcileResult.missing} ` +
        `failed=${reconcileResult.failed} unbound=${reconcileResult.unbound}`
      "
    />

    <a-card class="page-card" title="租户绑定">
      <a-table
        :data-source="bindings"
        :columns="columns"
        :loading="loading"
        :pagination="false"
        row-key="bindingId"
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'syncStatus'">
            <StatusTag :status="record.syncStatus" />
          </template>
          <template v-else-if="column.key === 'lastSyncedAt'">
            {{ formatTime(record.lastSyncedAt) }}
          </template>
          <template v-else-if="column.key === 'actions'">
            <a-popconfirm
              v-if="auth.isAdmin"
              title="删除后本租户将无法检索该库，确认？"
              ok-text="删除"
              cancel-text="取消"
              @confirm="remove(record)"
            >
              <a-button size="small" danger>删除</a-button>
            </a-popconfirm>
            <span v-else style="color: #bfbfbf">仅 admin</span>
          </template>
        </template>
      </a-table>
      <a-empty v-if="!loading && bindings.length === 0" description="还没有知识库绑定" />
    </a-card>

    <a-card title="知识库问答">
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :xs="24" :md="8">
            <a-form-item label="知识库" required>
              <a-select
                v-model:value="qaForm.knowledgeBaseId"
                placeholder="选择已绑定知识库"
                :options="
                  enabledBindings.map((item) => ({
                    value: item.knowledgeBaseId,
                    label: `${item.name} (#${item.knowledgeBaseId})`
                  }))
                "
              />
            </a-form-item>
          </a-col>
          <a-col :xs="24" :md="16">
            <a-form-item label="问题" required>
              <a-input
                v-model:value="qaForm.question"
                placeholder="例如：糖尿病患者的随访频率是多少？"
                @press-enter="ask"
              />
            </a-form-item>
          </a-col>
        </a-row>
        <a-button type="primary" :loading="asking" @click="ask">提问</a-button>
      </a-form>

      <template v-if="qaOutput">
        <a-divider />
        <a-space wrap style="margin-bottom: 8px">
          <a-tag>命中 {{ qaOutput.hitCount ?? 0 }}</a-tag>
          <a-tag v-if="qaOutput.degraded" color="orange">降级：原文摘录</a-tag>
        </a-space>
        <pre class="pre-wrap">{{ qaOutput.answer }}</pre>
        <a-table
          v-if="qaOutput.citations && qaOutput.citations.length"
          :data-source="qaOutput.citations"
          :pagination="false"
          row-key="documentId"
          size="small"
          style="margin-top: 12px"
        >
          <a-table-column title="文档" data-index="documentName" key="documentName" />
          <a-table-column title="切片" data-index="chunkIndex" key="chunkIndex" width="80" />
          <a-table-column title="相似度" data-index="score" key="score" width="100" />
          <a-table-column title="内容" data-index="content" key="content" />
        </a-table>
      </template>
    </a-card>
  </div>
</template>
