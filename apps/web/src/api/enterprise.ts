import axios, { type AxiosError, type AxiosInstance } from 'axios';
import { ApiError, toApiError, unwrapEnvelope } from './http';
import type {
  ApiEnvelope,
  EnterpriseDocument,
  EnterpriseIngestResult,
  EnterpriseKnowledgeBase,
  EnterpriseLoginResponse,
  EnterpriseUser
} from './types';

const TOKEN_KEY = 'aicode_enterprise_token';
const SATOKEN_HEADER = 'satoken';

let unauthorizedHandler: (() => void) | null = null;

/** 企业 401 处理：只清企业会话，不影响平台登录。 */
export function setEnterpriseUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler;
}

export const entHttp: AxiosInstance = axios.create({
  baseURL: '/ent',
  timeout: 60_000,
  headers: { 'Content-Type': 'application/json' }
});

entHttp.interceptors.request.use((config) => {
  const token = sessionStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers[SATOKEN_HEADER] = token;
  }
  return config;
});

entHttp.interceptors.response.use(
  (response) =>
    unwrapEnvelope(response.data as ApiEnvelope<unknown>) as unknown as typeof response,
  (error: unknown) => {
    const apiError = toApiError(error);
    if (apiError.status === 401) {
      sessionStorage.removeItem(TOKEN_KEY);
      unauthorizedHandler?.();
    }
    return Promise.reject(apiError);
  }
);

function get<T>(url: string): Promise<T> {
  return entHttp.get(url) as unknown as Promise<T>;
}

function post<T>(url: string, data?: unknown): Promise<T> {
  return entHttp.post(url, data) as unknown as Promise<T>;
}

function del<T>(url: string): Promise<T> {
  return entHttp.delete(url) as unknown as Promise<T>;
}

export function login(username: string, password: string): Promise<EnterpriseLoginResponse> {
  return post<EnterpriseLoginResponse>('/api/v1/auth/login', { username, password });
}

export function logout(): Promise<void> {
  return post<void>('/api/v1/auth/logout');
}

export function me(): Promise<EnterpriseUser> {
  return get<EnterpriseUser>('/api/v1/auth/me');
}

export function listBases(): Promise<EnterpriseKnowledgeBase[]> {
  return get<EnterpriseKnowledgeBase[]>('/api/v1/knowledge-bases');
}

export function createBase(name: string, description: string): Promise<EnterpriseKnowledgeBase> {
  return post<EnterpriseKnowledgeBase>('/api/v1/knowledge-bases', { name, description });
}

export function deleteBase(id: number): Promise<void> {
  return del<void>(`/api/v1/knowledge-bases/${id}`);
}

export function listDocuments(baseId: number): Promise<EnterpriseDocument[]> {
  return get<EnterpriseDocument[]>(`/api/v1/knowledge-bases/${baseId}/documents`);
}

export function ingestDocument(
  baseId: number,
  name: string,
  content: string
): Promise<EnterpriseIngestResult> {
  return post<EnterpriseIngestResult>(`/api/v1/knowledge-bases/${baseId}/documents`, {
    name,
    content
  });
}

export function deleteDocument(baseId: number, documentId: string): Promise<void> {
  return del<void>(`/api/v1/knowledge-bases/${baseId}/documents/${documentId}`);
}

export { TOKEN_KEY as ENTERPRISE_TOKEN_KEY };

export type EnterpriseApiError = AxiosError<ApiEnvelope<unknown>>;

export { ApiError };
