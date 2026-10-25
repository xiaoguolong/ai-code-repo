import { del, get, post } from './http';
import type {
  AgentDefinition,
  Approval,
  ApprovalDecision,
  ApprovalList,
  ApprovalReminder,
  CurrentUser,
  ExecutionRecord,
  FollowUpReminder,
  FollowUpStatus,
  FollowUpTask,
  FollowUpTaskList,
  KnowledgeBindingList,
  KnowledgeImportResult,
  KnowledgeReconcileResult,
  LoginResponse,
  PlatformUserItem,
  PlatformUserList
} from './types';

const P = '/v1/platform';

export function login(username: string, password: string): Promise<LoginResponse> {
  return post<LoginResponse>(`${P}/auth/login`, { username, password });
}

export function logout(): Promise<void> {
  return post<void>(`${P}/auth/logout`);
}

export function me(): Promise<CurrentUser> {
  return get<CurrentUser>(`${P}/auth/me`);
}

export function listAgents(): Promise<AgentDefinition[]> {
  return get<AgentDefinition[]>(`${P}/agents`);
}

export function runAgent(
  agentKey: string,
  input: Record<string, unknown>
): Promise<ExecutionRecord> {
  return post<ExecutionRecord>(`${P}/agents/${agentKey}/runs`, { input });
}

export function listExecutions(): Promise<ExecutionRecord[]> {
  return get<ExecutionRecord[]>(`${P}/executions`);
}

export function getExecution(executionId: string): Promise<ExecutionRecord> {
  return get<ExecutionRecord>(`${P}/executions/${executionId}`);
}

export function listApprovals(status = 'PENDING', limit = 100): Promise<ApprovalList> {
  return get<ApprovalList>(`${P}/approvals`, { status, limit });
}

export function getApproval(approvalId: string): Promise<Approval> {
  return get<Approval>(`${P}/approvals/${approvalId}`);
}

export function decideApproval(
  approvalId: string,
  approved: boolean,
  comment: string
): Promise<ApprovalDecision> {
  return post<ApprovalDecision>(`${P}/approvals/${approvalId}/decision`, { approved, comment });
}

export function approvalReminders(limit = 20): Promise<ApprovalReminder> {
  return get<ApprovalReminder>(`${P}/approvals/reminders`, { limit });
}

export function listFollowUps(
  status: FollowUpStatus = 'PENDING',
  patientId?: string,
  limit = 100
): Promise<FollowUpTaskList> {
  const params: Record<string, unknown> = { status, limit };
  if (patientId) {
    params.patientId = patientId;
  }
  return get<FollowUpTaskList>(`${P}/followup-tasks`, params);
}

export function completeFollowUp(taskId: string, note: string): Promise<FollowUpTask> {
  return post<FollowUpTask>(`${P}/followup-tasks/${taskId}/complete`, { note });
}

export function getFollowUp(taskId: string): Promise<FollowUpTask> {
  return get<FollowUpTask>(`${P}/followup-tasks/${taskId}`);
}

export function cancelFollowUp(taskId: string, reason: string): Promise<FollowUpTask> {
  return post<FollowUpTask>(`${P}/followup-tasks/${taskId}/cancel`, { reason });
}

export function followUpReminders(days = 7): Promise<FollowUpReminder> {
  return get<FollowUpReminder>(`${P}/followup-tasks/reminders`, { days });
}

export function listKnowledgeBindings(): Promise<KnowledgeBindingList> {
  return get<KnowledgeBindingList>(`${P}/knowledge-bindings`);
}

export function importKnowledgeBindings(): Promise<KnowledgeImportResult> {
  return post<KnowledgeImportResult>(`${P}/knowledge-bindings/import`);
}

export function reconcileKnowledgeBindings(): Promise<KnowledgeReconcileResult> {
  return post<KnowledgeReconcileResult>(`${P}/knowledge-bindings/reconcile`);
}

export function deleteKnowledgeBinding(bindingId: string): Promise<void> {
  return del<void>(`${P}/knowledge-bindings/${bindingId}`);
}

export function listUsers(): Promise<PlatformUserList> {
  return get<PlatformUserList>(`${P}/users`);
}

export function disableUser(userId: number): Promise<PlatformUserItem> {
  return post<PlatformUserItem>(`${P}/users/${userId}/disable`);
}

export function enableUser(userId: number): Promise<PlatformUserItem> {
  return post<PlatformUserItem>(`${P}/users/${userId}/enable`);
}

export type { Approval, ExecutionRecord, FollowUpTask };
