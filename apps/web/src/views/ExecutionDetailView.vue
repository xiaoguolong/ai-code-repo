<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getExecution } from '@/api/platform';
import type { ExecutionRecord, KnowledgeCitation } from '@/api/types';
import { ApiError } from '@/api/http';
import { formatTime, parseJson } from '@/utils/format';
import StatusTag from '@/components/StatusTag.vue';
import PageHeader from '@/components/PageHeader.vue';

interface OutputShape {
  report?: string;
  answer?: string;
  question?: string;
  summary?: string;
  windowDays?: number;
  hitCount?: number;
  degraded?: boolean;
  riskLevel?: string;
  approvalId?: string;
  approvalStatus?: string;
  citations?: KnowledgeCitation[];
  tasks?: Array<{ title?: string; detail?: string; priority?: string; dueAt?: string }>;
  anomalies?: Array<{ rule?: string; severity?: string; detail?: string }>;
  [key: string]: unknown;
}

const route = useRoute();
const router = useRouter();
const loading = ref(true);
const error = ref('');
const record = ref<ExecutionRecord | null>(null);

const output = computed<OutputShape | null>(() =>
  record.value ? parseJson<OutputShape>(record.value.outputJson) : null
);
const input = computed<Record<string, unknown> | null>(() =>
  record.value ? parseJson<Record<string, unknown>>(record.value.inputJson) : null
);
const otherKeys = computed(() => {
  if (!output.value) {
    return [];
  }
  const known = new Set([
    'report',
    'answer',
    'question',
    'summary',
    'windowDays',
    'hitCount',
    'degraded',
    'riskLevel',
    'approvalId',
    'approvalStatus',
    'citations',
    'tasks',
    'anomalies',
    'task',
    'patientId',
    'steps',
    'totalSteps'
  ]);
  return Object.keys(output.value).filter((key) => !known.has(key));
});

async function load(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    record.value = await getExecution(String(route.params.executionId));
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '执行详情加载失败';
  } finally {
    loading.value = false;
  }
}

onMounted(() => void load());
</script>

<template>
  <div>
    <PageHeader title="执行详情" :subtitle="record?.executionId">
      <template #extra>
        <a-space>
          <a-button @click="router.push('/executions')">返回列表</a-button>
          <a-button :loading="loading" @click="load">刷新</a-button>
        </a-space>
      </template>
    </PageHeader>

    <a-alert v-if="error" type="error" :message="error" show-icon style="margin-bottom: 16px" />

    <a-spin :spinning="loading">
      <template v-if="record">
        <a-card class="page-card">
          <a-descriptions bordered :column="2" size="small">
            <a-descriptions-item label="状态">
              <StatusTag :status="record.status" />
            </a-descriptions-item>
            <a-descriptions-item label="Agent">{{ record.agentKey }}</a-descriptions-item>
            <a-descriptions-item label="类型">{{ record.agentType }}</a-descriptions-item>
            <a-descriptions-item label="租户">{{ record.tenantKey }}</a-descriptions-item>
            <a-descriptions-item label="模型">{{ record.model || '-' }}</a-descriptions-item>
            <a-descriptions-item label="Token">
              {{ record.usage?.totalTokens ?? 0 }}（输入 {{ record.usage?.promptTokens ?? 0 }} /
              输出 {{ record.usage?.completionTokens ?? 0 }}）
            </a-descriptions-item>
            <a-descriptions-item label="开始时间">{{ formatTime(record.startedAt) }}</a-descriptions-item>
            <a-descriptions-item label="完成时间">{{ formatTime(record.finishedAt) }}</a-descriptions-item>
          </a-descriptions>

          <a-alert
            v-if="record.errorMessage"
            type="error"
            :message="record.errorMessage"
            show-icon
            style="margin-top: 16px"
          />
        </a-card>

        <a-card v-if="output" class="page-card" title="输出">
          <a-space wrap style="margin-bottom: 12px">
            <a-tag v-if="output.riskLevel" color="red">风险：{{ output.riskLevel }}</a-tag>
            <a-tag v-if="output.hitCount !== undefined">命中：{{ output.hitCount }}</a-tag>
            <a-tag v-if="output.degraded" color="orange">降级</a-tag>
            <a-button
              v-if="output.approvalId"
              size="small"
              type="primary"
              @click="router.push({ path: '/approvals', query: { approvalId: output.approvalId } })"
            >
              查看审批
            </a-button>
          </a-space>

          <div v-if="output.report" style="margin-bottom: 16px">
            <h4>报告</h4>
            <pre class="pre-wrap">{{ output.report }}</pre>
          </div>
          <div v-if="output.answer" style="margin-bottom: 16px">
            <h4>答案</h4>
            <pre class="pre-wrap">{{ output.answer }}</pre>
          </div>
          <div v-if="output.summary" style="margin-bottom: 16px">
            <h4>摘要</h4>
            <pre class="pre-wrap">{{ output.summary }}</pre>
          </div>

          <div v-if="output.citations && output.citations.length" style="margin-bottom: 16px">
            <h4>引用（{{ output.citations.length }}）</h4>
            <a-table
              :data-source="output.citations"
              :pagination="false"
              row-key="documentId"
              size="small"
            >
              <a-table-column title="文档" data-index="documentName" key="documentName" />
              <a-table-column title="切片" data-index="chunkIndex" key="chunkIndex" width="80" />
              <a-table-column title="相似度" data-index="score" key="score" width="100" />
              <a-table-column title="内容" data-index="content" key="content" />
            </a-table>
          </div>

          <div v-if="output.tasks && output.tasks.length" style="margin-bottom: 16px">
            <h4>随访任务（{{ output.tasks.length }}）</h4>
            <a-list size="small" :data-source="output.tasks">
              <template #renderItem="{ item }">
                <a-list-item>
                  <div>
                    <div style="font-weight: 500">{{ item.title }}</div>
                    <div style="color: #8c8c8c">{{ item.detail }}</div>
                    <div style="color: #bfbfbf; font-size: 12px">到期：{{ formatTime(item.dueAt) }}</div>
                  </div>
                  <a-tag>{{ item.priority }}</a-tag>
                </a-list-item>
              </template>
            </a-list>
          </div>

          <div v-if="output.anomalies && output.anomalies.length" style="margin-bottom: 16px">
            <h4>异常规则（{{ output.anomalies.length }}）</h4>
            <a-list size="small" :data-source="output.anomalies">
              <template #renderItem="{ item }">
                <a-list-item>
                  <div>
                    <a-tag color="orange">{{ item.severity || 'INFO' }}</a-tag>
                    <span style="font-weight: 500">{{ item.rule }}</span>
                    <div style="color: #8c8c8c">{{ item.detail }}</div>
                  </div>
                </a-list-item>
              </template>
            </a-list>
          </div>

          <a-collapse v-if="otherKeys.length" ghost>
            <a-collapse-panel key="other" header="其他字段（原始 JSON）">
              <pre class="pre-wrap mono">
{{ JSON.stringify(Object.fromEntries(otherKeys.map((key) => [key, output?.[key]])), null, 2) }}
              </pre>
            </a-collapse-panel>
          </a-collapse>
        </a-card>

        <a-card class="page-card" title="入参">
          <pre class="pre-wrap mono">{{ JSON.stringify(input, null, 2) }}</pre>
        </a-card>
      </template>
    </a-spin>
  </div>
</template>
