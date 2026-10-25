<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import {
  createBase,
  deleteBase,
  deleteDocument,
  ingestDocument,
  listBases,
  listDocuments
} from '@/api/enterprise';
import type {
  EnterpriseDocument,
  EnterpriseKnowledgeBase
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
const error = ref('');

const createOpen = ref(false);
const creating = ref(false);
const createForm = reactive({ name: '', description: '' });

const ingestOpen = ref(false);
const ingesting = ref(false);
const ingestForm = reactive({ name: '', content: '' });

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

async function logout(): Promise<void> {
  await store.logout();
  bases.value = [];
  documents.value = [];
  selectedBaseId.value = undefined;
}

watch(selectedBaseId, () => void loadDocs());

onMounted(async () => {
  if (!store.token) {
    return;
  }
  try {
    await store.ensureUser();
    await loadBases();
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
                  onClick: () => (selectedBaseId = record.id),
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
                  <a-popconfirm
                    title="删除该文档及其切片？"
                    ok-text="删除"
                    cancel-text="取消"
                    @confirm="removeDoc(record)"
                  >
                    <a-button size="small" danger>删除</a-button>
                  </a-popconfirm>
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
    </template>

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
        message="当前只支持纯文本（name + content）；PDF/OCR 走 enterprise 的另一条链路。"
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
