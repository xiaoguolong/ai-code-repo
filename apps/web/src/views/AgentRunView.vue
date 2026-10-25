<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { listAgents, listKnowledgeBindings, runAgent } from '@/api/platform';
import type {
  AgentDefinition,
  ExecutionRecord,
  KnowledgeBinding,
  KnowledgeCitation
} from '@/api/types';
import { ApiError } from '@/api/http';
import { useAuthStore } from '@/stores/auth';
import { runDeniedReason } from '@/utils/permissions';
import { parseJson } from '@/utils/format';
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
  citations?: KnowledgeCitation[];
  tasks?: Array<{ title?: string; detail?: string; priority?: string; dueAt?: string }>;
  anomalies?: Array<{ rule?: string; severity?: string; detail?: string }>;
}

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const agentKey = computed(() => String(route.params.agentKey ?? ''));
const agents = ref<AgentDefinition[]>([]);
const bindings = ref<KnowledgeBinding[]>([]);
const running = ref(false);
const error = ref('');
const result = ref<ExecutionRecord | null>(null);

const form = reactive({
  patientId: 'P002',
  task: '',
  windowDays: 7,
  knowledgeBaseId: undefined as number | undefined,
  question: ''
});

const agent = computed(() => agents.value.find((item) => item.agentKey === agentKey.value));
const deniedReason = computed(() => runDeniedReason(auth.user?.roleKey));
const output = computed<OutputShape | null>(() =>
  result.value ? parseJson<OutputShape>(result.value.outputJson) : null
);
const boundBases = computed(() =>
  bindings.value.filter((item) => item.enabled && item.syncStatus !== 'MISSING')
);

async function load(): Promise<void> {
  error.value = '';
  try {
    agents.value = await listAgents();
    if (agentKey.value === 'knowledge-qa') {
      const list = await listKnowledgeBindings();
      bindings.value = list.items;
      const first = boundBases.value[0];
      if (first) {
        form.knowledgeBaseId = first.knowledgeBaseId;
      }
    }
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : 'Agent 列表加载失败';
  }
}

function validate(): string | null {
  if (agentKey.value === 'knowledge-qa') {
    if (!form.knowledgeBaseId) {
      return '请选择已绑定的知识库';
    }
    if (!form.question.trim()) {
      return '请输入问题';
    }
    return null;
  }
  if (agentKey.value === 'operations-analysis') {
    return null;
  }
  if (!form.patientId.trim()) {
    return '请输入患者编号';
  }
  return null;
}

async function submit(): Promise<void> {
  error.value = '';
  const invalid = validate();
  if (invalid) {
    error.value = invalid;
    return;
  }
  running.value = true;
  result.value = null;
  try {
    const input: Record<string, unknown> = {};
    if (form.task.trim()) {
      input.task = form.task.trim();
    }
    if (agentKey.value === 'patient-analysis' || agentKey.value === 'followup') {
      input.patientId = form.patientId.trim();
    } else if (agentKey.value === 'operations-analysis') {
      input.windowDays = form.windowDays;
    } else if (agentKey.value === 'knowledge-qa') {
      input.knowledgeBaseId = form.knowledgeBaseId;
      input.question = form.question.trim();
    }
    result.value = await runAgent(agentKey.value, input);
    message.success('运行完成');
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '运行失败，请稍后再试';
  } finally {
    running.value = false;
  }
}

watch(agentKey, () => {
  result.value = null;
  void load();
});
onMounted(() => void load());
</script>

<template>
  <div>
    <PageHeader
      :title="agent?.name || agentKey"
      :subtitle="agent?.description || '平台注册的 Agent'"
    >
      <template #extra>
        <a-button @click="router.push('/')">返回工作台</a-button>
      </template>
    </PageHeader>

    <a-alert v-if="error" type="error" :message="error" show-icon style="margin-bottom: 16px" />
    <a-alert
      v-if="deniedReason"
      type="warning"
      :message="deniedReason"
      show-icon
      style="margin-bottom: 16px"
    />

    <a-row :gutter="[16, 16]">
      <a-col :xs="24" :lg="10">
        <a-card title="运行参数">
          <a-descriptions v-if="agent" :column="1" size="small" style="margin-bottom: 16px">
            <a-descriptions-item label="类型">{{ agent.agentType }}</a-descriptions-item>
            <a-descriptions-item label="模型">{{ agent.model || '默认' }}</a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="agent.enabled ? 'green' : 'red'">
                {{ agent.enabled ? '启用' : '停用' }}
              </a-tag>
            </a-descriptions-item>
          </a-descriptions>

          <a-alert
            v-if="agent && !agent.enabled"
            type="warning"
            message="该 Agent 已停用"
            show-icon
            style="margin-bottom: 16px"
          />

          <a-form layout="vertical">
            <template v-if="agentKey === 'knowledge-qa'">
              <a-form-item label="知识库" required>
                <a-select
                  v-model:value="form.knowledgeBaseId"
                  placeholder="选择已绑定的知识库"
                  :options="
                    boundBases.map((item) => ({
                      value: item.knowledgeBaseId,
                      label: `${item.name} (#${item.knowledgeBaseId})`
                    }))
                  "
                />
              </a-form-item>
              <a-form-item label="问题" required>
                <a-textarea
                  v-model:value="form.question"
                  :rows="3"
                  placeholder="例如：糖尿病患者的随访频率是多少？"
                  :maxlength="4000"
                  show-count
                />
              </a-form-item>
            </template>

            <template v-else-if="agentKey === 'operations-analysis'">
              <a-form-item label="统计窗口（天）">
                <a-input-number v-model:value="form.windowDays" :min="1" :max="30" style="width: 100%" />
              </a-form-item>
            </template>

            <template v-else>
              <a-form-item label="患者编号" required>
                <a-input v-model:value="form.patientId" placeholder="例如：P002" />
              </a-form-item>
            </template>

            <a-form-item label="任务说明（可选）">
              <a-textarea v-model:value="form.task" :rows="2" placeholder="补充任务背景" />
            </a-form-item>

            <a-form-item>
              <a-button
                type="primary"
                :loading="running"
                :disabled="!!deniedReason || (agent ? !agent.enabled : false)"
                @click="submit"
              >
                运行 Agent
              </a-button>
              <span v-if="running" style="margin-left: 12px; color: #8c8c8c">
                运行中，长任务可能需要 30–60 秒…
              </span>
            </a-form-item>
          </a-form>
        </a-card>
      </a-col>

      <a-col :xs="24" :lg="14">
        <a-card title="运行结果">
          <a-empty v-if="!result" description="尚未运行" />
          <template v-else>
            <a-space wrap style="margin-bottom: 12px">
              <StatusTag :status="result.status" />
              <a-tag>模型：{{ result.model || '-' }}</a-tag>
              <a-tag>Token：{{ result.usage?.totalTokens ?? 0 }}</a-tag>
              <a-button size="small" @click="router.push(`/executions/${result.executionId}`)">
                查看执行详情
              </a-button>
            </a-space>

            <a-alert
              v-if="result.status === 'PENDING_APPROVAL'"
              type="warning"
              show-icon
              style="margin-bottom: 12px"
              message="高风险结果已进入人工审批"
              description="请在审批页批准或拒绝后，随访任务才会生成。"
            />

            <a-alert
              v-if="result.errorMessage"
              type="error"
              :message="result.errorMessage"
              show-icon
              style="margin-bottom: 12px"
            />

            <template v-if="output">
              <div v-if="output.report" style="margin-bottom: 12px">
                <h4>报告</h4>
                <pre class="pre-wrap">{{ output.report }}</pre>
              </div>
              <div v-if="output.answer" style="margin-bottom: 12px">
                <h4>答案</h4>
                <pre class="pre-wrap">{{ output.answer }}</pre>
                <a-alert
                  v-if="output.degraded"
                  type="warning"
                  message="模型不可用，当前为检索原文摘录"
                  show-icon
                  style="margin-top: 8px"
                />
              </div>
              <div v-if="output.summary" style="margin-bottom: 12px">
                <h4>摘要</h4>
                <pre class="pre-wrap">{{ output.summary }}</pre>
              </div>
              <div v-if="output.citations && output.citations.length">
                <h4>引用（{{ output.citations.length }}）</h4>
                <a-list size="small" :data-source="output.citations">
                  <template #renderItem="{ item }">
                    <a-list-item>
                      <div>
                        <div style="font-weight: 500">
                          {{ item.documentName || item.documentId }} #{{ item.chunkIndex }}
                          <a-tag color="blue">{{ item.score.toFixed(3) }}</a-tag>
                        </div>
                        <div style="color: #595959">{{ item.content }}</div>
                      </div>
                    </a-list-item>
                  </template>
                </a-list>
              </div>
              <div v-if="output.tasks && output.tasks.length">
                <h4>随访任务（{{ output.tasks.length }}）</h4>
                <a-list size="small" :data-source="output.tasks">
                  <template #renderItem="{ item }">
                    <a-list-item>
                      <div>
                        <div style="font-weight: 500">{{ item.title }}</div>
                        <div style="color: #8c8c8c">{{ item.detail }}</div>
                      </div>
                      <a-tag>{{ item.priority }}</a-tag>
                    </a-list-item>
                  </template>
                </a-list>
              </div>
            </template>
          </template>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>
