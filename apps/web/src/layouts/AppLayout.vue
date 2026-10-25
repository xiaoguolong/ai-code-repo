<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  AuditOutlined,
  BarChartOutlined,
  BellOutlined,
  BookOutlined,
  CheckSquareOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  ExperimentOutlined,
  HistoryOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  ScheduleOutlined,
  TeamOutlined,
  UserOutlined
} from '@ant-design/icons-vue';
import { useAuthStore } from '@/stores/auth';
import { menuForRole } from '@/utils/permissions';
import { approvalReminders, followUpReminders } from '@/api/platform';

const auth = useAuthStore();
const route = useRoute();
const router = useRouter();
const collapsed = ref(false);
const badges = ref({ approvals: 0, followups: 0 });
let timer: number | undefined;

const menu = computed(() => menuForRole(auth.user?.roleKey));
const selectedKeys = computed(() => {
  const path = route.path;
  if (path.startsWith('/agents/')) {
    return [path];
  }
  if (path.startsWith('/executions/')) {
    return ['/executions'];
  }
  return [path];
});

const iconMap: Record<string, unknown> = {
  '/': DashboardOutlined,
  '/agents/patient-analysis': ExperimentOutlined,
  '/agents/operations-analysis': BarChartOutlined,
  '/agents/knowledge-qa': BookOutlined,
  '/agents/followup': ScheduleOutlined,
  '/executions': HistoryOutlined,
  '/approvals': AuditOutlined,
  '/followups': CheckSquareOutlined,
  '/knowledge': DatabaseOutlined,
  '/users': TeamOutlined
};

function iconFor(key: string): unknown {
  return iconMap[key] ?? DashboardOutlined;
}

function onMenu(info: { key: string | number }): void {
  router.push(String(info.key));
}

async function loadBadges(): Promise<void> {
  try {
    const [approvals, followups] = await Promise.all([
      approvalReminders(20),
      followUpReminders(7)
    ]);
    badges.value = { approvals: approvals.total, followups: followups.total };
  } catch {
    // 角标失败不影响主页面；下一次路由/轮询会重试
  }
}

async function onLogout(): Promise<void> {
  await auth.logout();
  await router.replace('/login');
}

onMounted(() => {
  void loadBadges();
  timer = window.setInterval(() => void loadBadges(), 60_000);
});
onUnmounted(() => {
  if (timer) {
    window.clearInterval(timer);
  }
});
watch(() => route.fullPath, () => void loadBadges());
</script>

<template>
  <a-layout style="min-height: 100vh">
    <a-layout-sider v-model:collapsed="collapsed" collapsible :width="220" theme="light">
      <div
        style="
          height: 56px;
          display: flex;
          align-items: center;
          padding: 0 16px;
          font-weight: 600;
          color: #1677ff;
          white-space: nowrap;
          overflow: hidden;
        "
      >
        <DatabaseOutlined />
        <span v-if="!collapsed" style="margin-left: 8px">医疗 AI 平台</span>
      </div>
      <a-menu mode="inline" :selected-keys="selectedKeys" @click="onMenu">
        <a-menu-item v-for="item in menu" :key="item.key">
          <template #icon>
            <component :is="iconFor(item.key)" />
          </template>
          {{ item.label }}
        </a-menu-item>
      </a-menu>
    </a-layout-sider>

    <a-layout>
      <a-layout-header
        style="
          background: #fff;
          padding: 0 16px;
          display: flex;
          align-items: center;
          justify-content: space-between;
          box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
          position: sticky;
          top: 0;
          z-index: 10;
        "
      >
        <div style="display: flex; align-items: center; gap: 12px">
          <a-button type="text" @click="collapsed = !collapsed">
            <MenuUnfoldOutlined v-if="collapsed" />
            <MenuFoldOutlined v-else />
          </a-button>
          <span style="font-size: 16px; font-weight: 600">
            {{ (route.meta.title as string) || '工作台' }}
          </span>
        </div>

        <div style="display: flex; align-items: center; gap: 16px">
          <a-tooltip title="待办提醒（审批 + 随访）">
            <a-badge :count="badges.approvals + badges.followups" size="small">
              <a-button type="text" @click="router.push('/approvals')">
                <BellOutlined />
              </a-button>
            </a-badge>
          </a-tooltip>
          <a-tag color="blue">{{ auth.user?.tenantName || auth.user?.tenantKey || '-' }}</a-tag>
          <a-dropdown>
            <span style="cursor: pointer; display: inline-flex; align-items: center; gap: 6px">
              <a-avatar size="small" style="background-color: #1677ff">
                <UserOutlined />
              </a-avatar>
              <span>{{ auth.user?.username }}</span>
              <a-tag>{{ auth.roleLabel }}</a-tag>
            </span>
            <template #overlay>
              <a-menu>
                <a-menu-item key="logout" @click="onLogout">
                  <LogoutOutlined /> 退出登录
                </a-menu-item>
              </a-menu>
            </template>
          </a-dropdown>
        </div>
      </a-layout-header>

      <a-layout-content style="padding: 16px; overflow: auto">
        <router-view />
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>
