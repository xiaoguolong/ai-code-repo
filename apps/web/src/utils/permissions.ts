/** 角色与菜单（Week 29；仅前端展示控制，权限仍以后端 403 为准）。 */
export type RoleKey = 'admin' | 'operator' | 'viewer' | string;

export interface MenuItem {
  key: string;
  label: string;
  roles?: RoleKey[];
}

export const MENU_ITEMS: MenuItem[] = [
  { key: '/', label: '工作台' },
  { key: '/agents/patient-analysis', label: '患者分析' },
  { key: '/agents/operations-analysis', label: '运营日报' },
  { key: '/agents/knowledge-qa', label: '知识库问答' },
  { key: '/agents/followup', label: '随访计划' },
  { key: '/executions', label: '执行记录' },
  { key: '/approvals', label: '审批' },
  { key: '/followups', label: '随访任务' },
  { key: '/knowledge', label: '知识库' },
  { key: '/users', label: '用户管理', roles: ['admin'] }
];

/** 当前角色可见菜单。 */
export function menuForRole(role?: string | null): MenuItem[] {
  const normalized = (role ?? '').trim();
  return MENU_ITEMS.filter((item) => !item.roles || item.roles.includes(normalized));
}

/** 是否能发起 Agent 运行（viewer 只读）。 */
export function canRunAgents(role?: string | null): boolean {
  return role === 'admin' || role === 'operator';
}

/** 是否 admin。 */
export function isAdmin(role?: string | null): boolean {
  return role === 'admin';
}

/** 运行按钮禁用原因（null = 可用）。 */
export function runDeniedReason(role?: string | null): string | null {
  return canRunAgents(role) ? null : '当前角色为只读，不能运行 Agent';
}
