import { createRouter, createWebHistory } from 'vue-router';
import type { RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    children: [
      {
        path: '',
        name: 'dashboard',
        component: () => import('@/views/DashboardView.vue'),
        meta: { title: '工作台' }
      },
      {
        path: 'agents/:agentKey',
        name: 'agent-run',
        component: () => import('@/views/AgentRunView.vue'),
        meta: { title: 'Agent 运行', roles: ['admin', 'operator'] }
      },
      {
        path: 'executions',
        name: 'executions',
        component: () => import('@/views/ExecutionsView.vue'),
        meta: { title: '执行记录' }
      },
      {
        path: 'executions/:executionId',
        name: 'execution-detail',
        component: () => import('@/views/ExecutionDetailView.vue'),
        meta: { title: '执行详情' }
      },
      {
        path: 'approvals',
        name: 'approvals',
        component: () => import('@/views/ApprovalsView.vue'),
        meta: { title: '审批' }
      },
      {
        path: 'followups',
        name: 'followups',
        component: () => import('@/views/FollowupsView.vue'),
        meta: { title: '随访任务' }
      },
      {
        path: 'knowledge',
        name: 'knowledge',
        component: () => import('@/views/KnowledgeView.vue'),
        meta: { title: '知识库' }
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/UsersView.vue'),
        meta: { title: '用户管理', roles: ['admin'] }
      }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();
  if (to.meta.public) {
    return to.path === '/login' && auth.token ? '/' : true;
  }
  if (!auth.token) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  try {
    await auth.ensureUser();
  } catch {
    auth.clear();
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  const roles = to.meta.roles as string[] | undefined;
  if (roles && auth.user && !roles.includes(auth.user.roleKey)) {
    return '/';
  }
  return true;
});

export default router;
