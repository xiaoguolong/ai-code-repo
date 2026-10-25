/** 后端统一响应信封（docs/api/week-16-api.md） */
export interface ApiEnvelope<T> {
  code: string;
  message: string;
  data: T;
  traceId?: string;
}

export interface LoginResponse {
  token: string;
  userId: number;
  username: string;
  roleKey: string;
  tenantKey: string;
}

export interface CurrentUser {
  userId: number;
  username: string;
  roleKey: string;
  tenantKey: string;
  tenantName: string;
}

export interface AgentDefinition {
  agentKey: string;
  name: string;
  description: string;
  agentType: string;
  enabled: boolean;
  maxIterations?: number;
  temperature?: number;
  model?: string;
  registeredAt?: string;
}

export interface Usage {
  promptTokens: number;
  completionTokens: number;
  totalTokens: number;
}

export type ExecutionStatus =
  | 'RUNNING'
  | 'COMPLETED'
  | 'FAILED'
  | 'PENDING_APPROVAL'
  | 'REJECTED';

export interface ExecutionRecord {
  executionId: string;
  userId: number;
  tenantKey: string;
  agentKey: string;
  agentType: string;
  status: ExecutionStatus;
  inputJson: string;
  outputJson: string;
  model: string;
  usage: Usage;
  errorMessage: string;
  startedAt: string;
  finishedAt: string | null;
}

export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface Approval {
  approvalId: string;
  executionId: string;
  agentKey: string;
  patientId: string;
  riskLevel: string;
  task: string;
  status: ApprovalStatus;
  comment: string;
  createdAt: string;
  decidedAt: string | null;
}

export interface ApprovalList {
  total: number;
  items: Approval[];
}

export interface ApprovalDecision {
  approvalId: string;
  executionId: string;
  status: ApprovalStatus;
  decidedBy: number | null;
  comment: string;
  decidedAt: string | null;
  executionStatus: string;
  memoryId: string | null;
  followUpTaskIds: string[];
}

export interface ApprovalReminderItem {
  approvalId: string;
  executionId: string;
  agentKey: string;
  patientId: string;
  riskLevel: string;
  createdAt: string;
  overdueHours: number;
}

export interface ApprovalReminder {
  thresholdHours: number;
  total: number;
  items: ApprovalReminderItem[];
}

export type FollowUpStatus = 'PENDING' | 'COMPLETED' | 'CANCELLED';

export interface FollowUpTask {
  taskId: string;
  tenantKey: string;
  userId: number;
  patientId: string;
  source: string;
  approvalId: string | null;
  executionId: string | null;
  title: string;
  detail: string;
  priority: string;
  status: FollowUpStatus;
  dueAt: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
  note: string;
  overdue: boolean;
}

export interface FollowUpTaskList {
  total: number;
  tasks: FollowUpTask[];
}

export interface FollowUpReminderItem {
  taskId: string;
  patientId: string;
  title: string;
  priority: string;
  dueAt: string | null;
  overdueDays: number;
}

export interface FollowUpReminder {
  days: number;
  total: number;
  overdue: FollowUpReminderItem[];
  dueSoon: FollowUpReminderItem[];
}

export type KnowledgeSyncStatus = 'UNKNOWN' | 'ACTIVE' | 'MISSING';

export interface KnowledgeBinding {
  bindingId: string;
  knowledgeBaseId: number;
  name: string;
  description: string;
  enabled: boolean;
  syncStatus: KnowledgeSyncStatus;
  lastSyncedAt: string | null;
  createdAt: string;
}

export interface KnowledgeBindingList {
  total: number;
  items: KnowledgeBinding[];
}

export interface KnowledgeImportResult {
  tenantKey: string;
  enterpriseUserId: number;
  imported: number;
  updated: number;
  items: KnowledgeBinding[];
}

export interface KnowledgeReconcileResult {
  tenantKey: string;
  reconciledAt: string;
  groups: number;
  checked: number;
  updated: number;
  missing: number;
  unchanged: number;
  failed: number;
  unbound: number;
}

export interface KnowledgeCitation {
  documentId: string;
  documentName: string;
  chunkIndex: number;
  content: string;
  score: number;
}

export interface KnowledgeAnswer {
  knowledgeBaseId: number;
  knowledgeBaseName: string;
  question: string;
  answer: string;
  citations: KnowledgeCitation[];
  hitCount: number;
  degraded: boolean;
  totalSteps?: number;
}

export interface PlatformUserItem {
  userId: number;
  username: string;
  roleKey: string;
  tenantKey: string;
  disabled: boolean;
  disabledAt: string | null;
}

export interface PlatformUserList {
  total: number;
  items: PlatformUserItem[];
}
