<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import {
  cancelIngestJob,
  createBase,
  createIngestJob,
  deleteBase,
  deleteDocument,
  getIngestJob,
  ingestDocument,
  listBases,
  listDocumentChunks,
  listDocuments,
  listIngestJobs,
  retryIngestJob
} from '@/api/enterprise';
import type {
  DocumentChunk,
  EnterpriseDocument,
  EnterpriseKnowledgeBase,
  IngestJobDetail,
  IngestJobSummary,
  IngestStep
} from '@/api/types';
import { ApiError } from '@/api/http';
import { useEnterpriseAuthStore } from '@/stores/enterpriseAuth';
import { formatTime } from '@/utils/format';
import PageHeader from '@/components/PageHeader.vue';

const store = useEnterpriseAuthStore();
const loginForm = reactive({ username: 'admin', password: '' });
const loginError = ref('');
const loggingIn = ref(false);

const bases = ref<EnterpriseKnowledgeBase[]>([]);
const selectedBaseId = ref<number | undefined>(undefined);
const documents = ref<EnterpriseDocument[]>([]);
const loadingBases = ref(false);
const loadingDocs = ref(false);
const docsCard = ref<{ $el?: HTMLElement } | null>(null);
const error = ref('');

const createOpen = ref(false);
const creating = ref(false);
const createForm = reactive({ name: '', description: '' });

const ingestOpen = ref(false);
const ingesting = ref(false);
const ingestForm = reactive({ name: '', content: '' });

/* ---------- Week 32：PDF/OCR 入库作业 ---------- */

const jobs = ref<IngestJobSummary[]>([]);
const loadingJobs = ref(false);
const uploading = ref(false);
const drawerOpen = ref(false);
const jobDetail = ref<IngestJobDetail | null>(null);
const loadingDetail = ref(false);
let pollTimer: number | undefined;

const jobColumns = [
  { title: '文件', dataIndex: 'fileName', key: 'fileName' },
  { title: '状态', dataIndex: 'status', key: 'status', width: 120 },
  { title: '页/块', key: 'counts', width: 100 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 160 },
  { title: '操作', key: 'actions', width: 150 }
];

/** 节点 key → 中文名。 */
const STEP_LABELS: Record<string, string> = {
  upload: '上传文件',
  ocr: 'OCR 识别',
  clean: 'LLM 清洗',
  chunk: '切片',
  embed: '向量化',
  index: '建索引'
};

function stepLabel(step: IngestStep): string {
  return `${step.stepNo}. ${STEP_LABELS[step.stepKey] ?? step.stepKey}`;
}

function statusColor(status: string): string {
  switch (status) {
    case 'SUCCEEDED':
      return 'green';
    case 'FAILED':
      return 'red';
    case 'RUNNING':
      return 'blue';
    case 'CANCELLED':
      return 'orange';
    default:
      return 'default';
  }
}

async function loadJobs(): Promise<void> {
  if (!selectedBaseId.value) {
    jobs.value = [];
    return;
  }
  loadingJobs.value = true;
  try {
    jobs.value = await listIngestJobs(selectedBaseId.value);
  } catch (e) {
    message.error(e instanceof ApiError ? e.message : '入库作业加载失败');
  } finally {
    loadingJobs.value = false;
  }
}

async function onPickFile(file: File): Promise<boolean> {
  if (!selectedBaseId.value) {
    message.warning('先选择知识库');
    return false;
  }
  uploading.value = true;
  try {
    const created = await createIngestJob(selectedBaseId.value, file);
    message.success(created.duplicate ? '同一文件已入库，已打开原作业' : '已提交，正在处理');
    await loadJobs();
    await openJob({ jobId: created.jobId } as IngestJobSummary);
  } catch (e) {
    message.error(e instanceof ApiError ? e.message : '提交失败');
  } finally {
    uploading.value = false;
  }
  return false;
}

async function openJob(job: IngestJobSummary): Promise<void> {
  drawerOpen.value = true;
  jobDetail.value = null;
  await loadDetail(job.jobId);
  startPolling(job.jobId);
}

/** 表格行点击：Record 断言成领域类型，并可顺带触发重试/取消。 */
async function openJobRecord(
  record: Record<string, unknown>,
  action?: 'retry' | 'cancel'
): Promise<void> {
  await openJob(record as unknown as IngestJobSummary);
  if (action === 'retry') {
    await retryJob();
  } else if (action === 'cancel') {
    await cancelJob();
  }
}

async function loadDetail(jobId: string): Promise<void> {
  loadingDetail.value = true;
  try {
    jobDetail.value = await getIngestJob(jobId);
  } catch (e) {
    message.error(e instanceof ApiError ? e.message : '作业详情加载失败');
  } finally {
    loadingDetail.value = false;
  }
}

function startPolling(jobId: string): void {
  stopPolling();
  pollTimer = window.setInterval(async () => {
    const status = jobDetail.value?.status;
    if (!drawerOpen.value || !status || (status !== 'PENDING' && status !== 'RUNNING')) {
      stopPolling();
      return;
    }
    await loadDetail(jobId);
    await loadJobs();
  }, 2000);
}

function stopPolling(): void {
  if (pollTimer !== undefined) {
    window.clearInterval(pollTimer);
    pollTimer = undefined;
  }
}

async function retryJob(): Promise<void> {
  if (!jobDetail.value) {
    return;
  }
  try {
    await retryIngestJob(jobDetail.value.jobId);
    message.success('已重新提交');
    await loadDetail(jobDetail.value.jobId);
    startPolling(jobDetail.value.jobId);
    await loadJobs();
  } catch (e) {
    message.error(e instanceof ApiError ? e.message : '重试失败');
  }
}

async function cancelJob(): Promise<void> {
  if (!jobDetail.value) {
    return;
  }
  try {
    await cancelIngestJob(jobDetail.value.jobId);
    message.success('已取消');
    await loadDetail(jobDetail.value.jobId);
    await loadJobs();
  } catch (e) {
    message.error(e instanceof ApiError ? e.message : '取消失败');
  }
}

onUnmounted(stopPolling);

const selectedBase = computed(() =>
  bases.value.find((item) => item.id === selectedBaseId.value)
);

const baseColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
  { title: '名称', dataIndex: 'name', key: 'name' },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 160 },
  { title: '操作', key: 'actions', width: 80 }
];

const docColumns = [
  { title: '文档', dataIndex: 'name', key: 'name' },
  { title: '切片', dataIndex: 'chunkCount', key: 'chunkCount', width: 70 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 160 },
  { title: '操作', key: 'actions', width: 80 }
];

async function submitLogin(): Promise<void> {
  loginError.value = '';
  loggingIn.value = true;
  try {
    await store.login(loginForm.username, loginForm.password);
    await loadBases();
  } catch (e) {
    loginError.value = e instanceof ApiError ? e.message : '企业登录失败';
  } finally {
    loggingIn.value = false;
  }
}

async function loadBases(): Promise<void> {
  loadingBases.value = true;
  error.value = '';
  try {
    bases.value = await listBases();
    if (!selectedBaseId.value || !bases.value.some((item) => item.id === selectedBaseId.value)) {
      selectedBaseId.value = bases.value[0]?.id;
    }
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '知识库列表加载失败';
  } finally {
    loadingBases.value = false;
  }
}

async function loadDocs(): Promise<void> {
  if (!selectedBaseId.value) {
    documents.value = [];
    return;
  }
  loadingDocs.value = true;
  try {
    documents.value = await listDocuments(selectedBaseId.value);
  } catch (e) {
    message.error(e instanceof ApiError ? e.message : '文档列表加载失败');
  } finally {
    loadingDocs.value = false;
  }
}

/** 选中知识库并把文档卡片滚到可视区（窄窗口下文档卡在知识库列表下方）。 */
async function selectBase(id: number): Promise<void> {
  selectedBaseId.value = id;
  await nextTick();
  docsCard.value?.$el?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function submitCreate(): Promise<void> {
  if (!createForm.name.trim()) {
    message.warning('请输入知识库名称');
    return;
  }
  creating.value = true;
  try {
    const created = await createBase(createForm.name.trim(), createForm.description.trim());
    message.success('知识库已创建');
    createOpen.value = false;
    createForm.name = '';
    createForm.description = '';
    await loadBases();
    selectedBaseId.value = created.id;
  } catch (e) {
    message.error(e instanceof ApiError ? e.message : '创建失败');
  } finally {
    creating.value = false;
  }
}

async function removeBase(
  base: EnterpriseKnowledgeBase | Record<string, unknown>
): Promise<void> {
  const target = base as EnterpriseKnowledgeBase;
  try {
    await deleteBase(target.id);
    message.success('知识库已删除');
    if (selectedBaseId.value === target.id) {
      selectedBaseId.value = undefined;
      documents.value = [];
    }
    await loadBases();
  } catch (e) {
    message.error(e instanceof ApiError ? e.message : '删除失败');
  }
}

async function submitIngest(): Promise<void> {
  if (!selectedBaseId.value) {
    return;
  }
  if (!ingestForm.name.trim() || !ingestForm.content.trim()) {
    message.warning('文档名称和正文不能为空');
    return;
  }
  ingesting.value = true;
  try {
    const result = await ingestDocument(
      selectedBaseId.value,
      ingestForm.name.trim(),
      ingestForm.content
    );
    message.success(`入库完成：${result.chunkCount} 个切片`);
    ingestOpen.value = false;
    ingestForm.name = '';
    ingestForm.content = '';
    await loadDocs();
  } catch (e) {
    message.error(e instanceof ApiError ? e.message : '入库失败');
  } finally {
    ingesting.value = false;
  }
}

async function removeDoc(doc: EnterpriseDocument | Record<string, unknown>): Promise<void> {
  const target = doc as EnterpriseDocument;
  if (!selectedBaseId.value) {
    return;
  }
  try {
    await deleteDocument(selectedBaseId.value, target.documentId);
    message.success('文档已删除');
    await loadDocs();
  } catch (e) {
    message.error(e instanceof ApiError ? e.message : '删除失败');
  }
}

/* ---------- 文档分块查看 ---------- */

const chunkDrawerOpen = ref(false);
const chunkDocName = ref('');
const chunks = ref<DocumentChunk[]>([]);
const loadingChunks = ref(false);

async function openChunks(doc: EnterpriseDocument | Record<string, unknown>): Promise<void> {
  const target = doc as EnterpriseDocument;
  if (!selectedBaseId.value) {
    return;
  }
  chunkDocName.value = target.name;
  chunks.value = [];
  chunkDrawerOpen.value = true;
  loadingChunks.value = true;
  try {
    chunks.value = await listDocumentChunks(selectedBaseId.value, target.documentId);
  } catch (e) {
    message.error(e instanceof ApiError ? e.message : '分块加载失败');
  } finally {
    loadingChunks.value = false;
  }
}

async function logout(): Promise<void> {
  await store.logout();
  bases.value = [];
  documents.value = [];
  selectedBaseId.value = undefined;
}

watch(selectedBaseId, () => {
  void loadDocs();
  void loadJobs();
});

onMounted(async () => {
  if (!store.token) {
    return;
  }
  try {
    await store.ensureUser();
    await loadBases();
    await loadJobs();
  } catch (e) {
    if (e instanceof ApiError && e.status === 401) {
      store.clear();
    }
  }
});
</script>

<template>
  <div>
    <PageHeader
      title="企业知识库"
      subtitle="建库 / 纯文本入库 / 文档管理；之后回平台「知识库」导入绑定"
    >
      <template #extra>
        <a-space v-if="store.token">
          <a-tag color="geekblue">企业账号：{{ store.username || '-' }}</a-tag>
          <a-button @click="logout">退出企业登录</a-button>
        </a-space>
      </template>
    </PageHeader>

    <a-card v-if="!store.token" style="max-width: 460px; margin: 0 auto">
      <h3 style="margin-top: 0">企业登录</h3>
      <a-alert
        type="info"
        show-icon
        message="企业端与平台账号同源（SSO）；只有完成 SSO 映射的管理员可登录。"
        style="margin-bottom: 16px"
      />
      <a-alert v-if="loginError" type="error" :message="loginError" show-icon style="margin-bottom: 16px" />
      <a-form :model="loginForm" layout="vertical" @finish="submitLogin">
        <a-form-item label="用户名" name="username" :rules="[{ required: true, message: '请输入用户名' }]">
          <a-input v-model:value="loginForm.username" autocomplete="username" />
        </a-form-item>
        <a-form-item label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }]">
          <a-input-password v-model:value="loginForm.password" autocomplete="current-password" />
        </a-form-item>
        <a-button type="primary" html-type="submit" block :loading="loggingIn">登录企业端</a-button>
      </a-form>
    </a-card>

    <template v-else>
      <a-alert v-if="error" type="error" :message="error" show-icon style="margin-bottom: 16px" />

      <a-row :gutter="[16, 16]">
        <a-col :xs="24" :lg="11">
          <a-card title="知识库" :loading="loadingBases">
            <template #extra>
              <a-space>
                <a-button size="small" :loading="loadingBases" @click="loadBases">刷新</a-button>
                <a-button size="small" type="primary" @click="createOpen = true">新建</a-button>
              </a-space>
            </template>
            <a-table
              :data-source="bases"
              :columns="baseColumns"
              :pagination="false"
              row-key="id"
              size="small"
              :row-class-name="
                (record: EnterpriseKnowledgeBase) =>
                  record.id === selectedBaseId ? 'ant-table-row-selected' : ''
              "
              :custom-row="
                (record: EnterpriseKnowledgeBase) => ({
                  onClick: () => selectBase(record.id),
                  class: 'clickable-row'
                })
              "
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'createdAt'">{{ formatTime(record.createdAt) }}</template>
                <template v-else-if="column.key === 'name'">
                  <div style="font-weight: 500">{{ record.name }}</div>
                  <div v-if="record.description" style="color: #8c8c8c; font-size: 12px">
                    {{ record.description }}
                  </div>
                </template>
                <template v-else-if="column.key === 'actions'">
                  <a-popconfirm
                    title="删除知识库不可恢复；平台绑定会在对账后变 MISSING。确认？"
                    ok-text="删除"
                    cancel-text="取消"
                    @confirm="removeBase(record)"
                  >
                    <a-button size="small" danger @click.stop>删除</a-button>
                  </a-popconfirm>
                </template>
              </template>
            </a-table>
            <a-empty v-if="!loadingBases && bases.length === 0" description="还没有知识库，先新建一个" />
          </a-card>
        </a-col>

        <a-col :xs="24" :lg="13">
          <a-card
            ref="docsCard"
            :title="selectedBase ? `文档：${selectedBase.name}` : '文档'"
            :loading="loadingDocs"
          >
            <template #extra>
              <a-space>
                <a-button size="small" :loading="loadingDocs" @click="loadDocs">刷新</a-button>
                <a-button
                  size="small"
                  type="primary"
                  :disabled="!selectedBaseId"
                  @click="ingestOpen = true"
                >
                  入库文档
                </a-button>
              </a-space>
            </template>
            <a-table
              :data-source="documents"
              :columns="docColumns"
              :pagination="false"
              row-key="documentId"
              size="small"
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.key === 'createdAt'">{{ formatTime(record.createdAt) }}</template>
                <template v-else-if="column.key === 'name'">
                  <div style="font-weight: 500">{{ record.name }}</div>
                  <div class="mono" style="color: #8c8c8c; font-size: 12px">
                    {{ record.documentId }}
                  </div>
                </template>
                <template v-else-if="column.key === 'actions'">
                  <a-space>
                    <a-button size="small" @click.stop="openChunks(record)">切片</a-button>
                    <a-popconfirm
                      title="删除该文档及其切片？"
                      ok-text="删除"
                      cancel-text="取消"
                      @confirm="removeDoc(record)"
                    >
                      <a-button size="small" danger>删除</a-button>
                    </a-popconfirm>
                  </a-space>
                </template>
              </template>
            </a-table>
            <a-empty
              v-if="!loadingDocs && documents.length === 0"
              :description="selectedBaseId ? '该知识库还没有文档' : '先选择左侧知识库'"
            />
          </a-card>
        </a-col>
      </a-row>

      <a-card title="PDF/OCR 入库作业" style="margin-top: 16px">
        <template #extra>
          <a-space>
            <a-upload
              :show-upload-list="false"
              :before-upload="onPickFile"
              accept=".pdf,.png,.jpg,.jpeg"
            >
              <a-button type="primary" :loading="uploading" :disabled="!selectedBaseId">
                上传 PDF/图片
              </a-button>
            </a-upload>
            <a-button :loading="loadingJobs" @click="loadJobs">刷新</a-button>
          </a-space>
        </template>
        <a-table
          :data-source="jobs"
          :columns="jobColumns"
          :pagination="false"
          row-key="jobId"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'fileName'">
              <div style="font-weight: 500">{{ record.fileName }}</div>
              <div class="mono" style="color: #8c8c8c; font-size: 12px">{{ record.jobId }}</div>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag>
            </template>
            <template v-else-if="column.key === 'counts'">
              {{ record.pageCount }} 页 / {{ record.chunkCount }} 块
            </template>
            <template v-else-if="column.key === 'createdAt'">
              {{ formatTime(record.createdAt) }}
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a-button size="small" @click="openJobRecord(record)">查看</a-button>
                <a-button
                  v-if="record.status === 'FAILED'"
                  size="small"
                  type="link"
                  @click="openJobRecord(record, 'retry')"
                >
                  重试
                </a-button>
                <a-button
                  v-if="record.status === 'RUNNING' || record.status === 'PENDING'"
                  size="small"
                  type="link"
                  danger
                  @click="openJobRecord(record, 'cancel')"
                >
                  取消
                </a-button>
              </a-space>
            </template>
          </template>
        </a-table>
        <a-empty
          v-if="!loadingJobs && jobs.length === 0"
          :description="selectedBaseId ? '还没有入库作业，上传一个 PDF 试试' : '先选择左侧知识库'"
        />
      </a-card>
    </template>

    <a-drawer v-model:open="drawerOpen" title="入库作业（节点视图）" width="760" @close="stopPolling">
      <a-spin :spinning="loadingDetail">
        <template v-if="jobDetail">
          <div class="job-head">
            <div>
              <div style="font-size: 16px; font-weight: 600">{{ jobDetail.fileName }}</div>
              <div class="mono" style="color: #8c8c8c; font-size: 12px">{{ jobDetail.jobId }}</div>
            </div>
            <a-tag :color="statusColor(jobDetail.status)">{{ jobDetail.status }}</a-tag>
          </div>
          <a-descriptions :column="3" size="small" style="margin: 12px 0">
            <a-descriptions-item label="页数">{{ jobDetail.pageCount }}</a-descriptions-item>
            <a-descriptions-item label="切片">{{ jobDetail.chunkCount }}</a-descriptions-item>
            <a-descriptions-item label="文档">{{ jobDetail.documentId || '-' }}</a-descriptions-item>
          </a-descriptions>
          <a-alert
            v-if="jobDetail.errorMessage"
            type="error"
            :message="jobDetail.errorMessage"
            show-icon
            style="margin-bottom: 12px"
          />
          <a-timeline>
            <a-timeline-item
              v-for="step in jobDetail.steps"
              :key="step.stepNo"
              :color="step.status === 'FAILED' ? 'red' : step.status === 'SUCCEEDED' ? 'green' : 'gray'"
            >
              <div class="step-card">
                <div class="step-head">
                  <strong>{{ stepLabel(step) }}</strong>
                  <a-tag :color="statusColor(step.status)">{{ step.status }}</a-tag>
                  <span class="step-duration">{{ step.durationMs }} ms</span>
                </div>
                <div class="step-meta">输入：{{ step.inputSummary || '-' }}</div>
                <div class="step-meta">输出：{{ step.outputSummary || '-' }}</div>
                <a-collapse v-if="step.promptText" ghost>
                  <a-collapse-panel
                    key="prompt"
                    :header="`查看 prompt 原文（${step.promptTokens} → ${step.completionTokens} token）`"
                  >
                    <pre class="prompt-pre">{{ step.promptText }}</pre>
                  </a-collapse-panel>
                </a-collapse>
                <div v-else class="step-meta" style="color: #bfbfbf">该步无 prompt</div>
                <a-alert v-if="step.errorMessage" type="error" :message="step.errorMessage" />
              </div>
            </a-timeline-item>
          </a-timeline>
          <a-space>
            <a-button v-if="jobDetail.status === 'FAILED'" type="primary" @click="retryJob">从失败步重试</a-button>
            <a-button
              v-if="jobDetail.status === 'RUNNING' || jobDetail.status === 'PENDING'"
              danger
              @click="cancelJob"
            >
              取消
            </a-button>
          </a-space>
        </template>
      </a-spin>
    </a-drawer>

    <a-drawer v-model:open="chunkDrawerOpen" :title="`分块：${chunkDocName}`" width="720">
      <a-spin :spinning="loadingChunks">
        <a-empty v-if="!loadingChunks && chunks.length === 0" description="没有分块（或该文档未入库成功）" />
        <div v-for="chunk in chunks" :key="chunk.chunkIndex" class="chunk-card">
          <div class="chunk-head">
            <a-tag color="blue">#{{ chunk.chunkIndex + 1 }}</a-tag>
            <span class="step-duration">{{ chunk.chars }} 字</span>
          </div>
          <pre class="prompt-pre">{{ chunk.content }}</pre>
        </div>
      </a-spin>
    </a-drawer>

    <a-modal
      v-model:open="createOpen"
      title="新建知识库"
      :confirm-loading="creating"
      @ok="submitCreate"
    >
      <a-form layout="vertical">
        <a-form-item label="名称" required>
          <a-input v-model:value="createForm.name" :maxlength="255" placeholder="例如：内分泌科随访规范" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="createForm.description" :rows="3" :maxlength="1000" show-count />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="ingestOpen"
      title="文本入库"
      width="720px"
      :confirm-loading="ingesting"
      @ok="submitIngest"
    >
      <a-alert
        type="info"
        show-icon
        message="纯文本入库仍保留（回归用）；PDF/扫描件请用下方「PDF/OCR 入库作业」。"
        style="margin-bottom: 12px"
      />
      <a-form layout="vertical">
        <a-form-item label="文档名称" required>
          <a-input v-model:value="ingestForm.name" :maxlength="255" placeholder="例如：糖尿病随访规范.txt" />
        </a-form-item>
        <a-form-item label="正文" required>
          <a-textarea
            v-model:value="ingestForm.content"
            :rows="10"
            :maxlength="100000"
            show-count
            placeholder="粘贴纯文本内容；后端会解析、切片、向量化"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
<style scoped>
.job-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.step-card {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 10px 12px;
  background: #fafafa;
}
.step-head {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}
.step-duration {
  color: #8c8c8c;
  font-size: 12px;
}
.step-meta {
  color: #595959;
  font-size: 12px;
  margin-bottom: 4px;
  word-break: break-all;
}
.chunk-card {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 10px;
  background: #fafafa;
}
.chunk-head {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}
.prompt-pre {
  white-space: pre-wrap;
  word-break: break-word;
  background: #fff;
  border: 1px solid #f0f0f0;
  padding: 8px;
  font-size: 12px;
  max-height: 240px;
  overflow: auto;
  margin: 0;
}
</style>