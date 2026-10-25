import axios, { type AxiosError, type AxiosInstance } from 'axios';
import type { ApiEnvelope } from './types';

const TOKEN_KEY = 'aicode_platform_token';

/** 业务错误（信封 code/message 或 HTTP 状态）。 */
export class ApiError extends Error {
  readonly status?: number;
  readonly code?: string;
  readonly traceId?: string;

  constructor(message: string, status?: number, code?: string, traceId?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.traceId = traceId;
  }
}

/** 从统一信封取 data；非 SUCCESS 一律抛 ApiError。 */
export function unwrapEnvelope<T>(body: ApiEnvelope<T> | undefined): T {
  if (!body || typeof body !== 'object') {
    throw new ApiError('服务返回为空');
  }
  if (body.code !== 'SUCCESS') {
    throw new ApiError(body.message || '请求失败', undefined, body.code, body.traceId);
  }
  return body.data;
}

const STATUS_MESSAGES: Record<number, string> = {
  400: '请求参数不合法',
  401: '登录已失效，请重新登录',
  403: '没有权限执行该操作',
  404: '资源不存在',
  409: '资源状态已变化，请刷新后重试',
  429: '尝试过于频繁，请稍后再试',
  500: '服务内部错误',
  502: '上游服务不可用，请稍后重试'
};

/** 把 axios/未知异常规整成 ApiError（优先用后端 message）。 */
export function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error;
  }
  const axiosError = error as AxiosError<ApiEnvelope<unknown>>;
  const status = axiosError.response?.status;
  const body = axiosError.response?.data;
  const message =
    body?.message ||
    (status ? STATUS_MESSAGES[status] : undefined) ||
    axiosError.message ||
    '网络异常，请检查连接';
  return new ApiError(message, status, body?.code, body?.traceId);
}

let unauthorizedHandler: (() => void) | null = null;

/** 由 main.ts 注册：401 时清会话并回登录页。 */
export function setUnauthorizedHandler(handler: () => void): void {
  unauthorizedHandler = handler;
}

export const http: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 180_000,
  headers: { 'Content-Type': 'application/json' }
});

http.interceptors.request.use((config) => {
  const token = sessionStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
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

export function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return http.get(url, { params }) as unknown as Promise<T>;
}

export function post<T>(url: string, data?: unknown): Promise<T> {
  return http.post(url, data) as unknown as Promise<T>;
}

export function del<T>(url: string): Promise<T> {
  return http.delete(url) as unknown as Promise<T>;
}

export { TOKEN_KEY };
