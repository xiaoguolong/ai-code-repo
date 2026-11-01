import { ApiError, notifyUnauthorized, TOKEN_KEY } from './http';
import type { ApiEnvelope, ExecutionRecord } from './types';

/**
 * 平台 SSE 流式执行客户端（Week 31）。
 *
 * <p>用 {@code fetch + ReadableStream} 而不是 {@code EventSource}：EventSource 不能发 POST、
 * 不能带 {@code Authorization} 头（令牌只能塞 query，会进访问日志）。</p>
 *
 * <p>服务端事件：{@code meta → delta* → final | error}；{@code final} 是权威结果，
 * 与同步端点同结构，调用方应拿它覆盖预览文本。</p>
 */

export interface RunStreamMeta {
  executionId: string;
  agentKey: string;
  startedAt: string;
  traceId?: string;
}

export interface RunStreamError {
  code: string;
  message: string;
  traceId?: string;
  executionId?: string;
}

export interface RunStreamHandlers {
  onMeta?: (meta: RunStreamMeta) => void;
  onDelta?: (text: string) => void;
  onFinal?: (record: ExecutionRecord) => void;
  onError?: (error: RunStreamError) => void;
}

interface ParsedSseEvent {
  event: string;
  data: string;
}

/**
 * 增量 SSE 解析器：只按"空行分帧"切，不假设一次 read 正好是一帧。
 *
 * <p>注释行（{@code : ping}）与未知字段被忽略；多行 {@code data:} 按 SSE 规范用换行拼接。</p>
 */
export class SseParser {
  private buffer = '';

  push(chunk: string): ParsedSseEvent[] {
    this.buffer = (this.buffer + chunk).replace(/\r\n/g, '\n');
    const events: ParsedSseEvent[] = [];
    let index = this.buffer.indexOf('\n\n');
    while (index >= 0) {
      const block = this.buffer.slice(0, index);
      this.buffer = this.buffer.slice(index + 2);
      const parsed = parseBlock(block);
      if (parsed) {
        events.push(parsed);
      }
      index = this.buffer.indexOf('\n\n');
    }
    return events;
  }

  /** 丢弃半帧缓冲（取消/重连时用）。 */
  reset(): void {
    this.buffer = '';
  }
}

function parseBlock(block: string): ParsedSseEvent | null {
  let event = 'message';
  const data: string[] = [];
  for (const line of block.split('\n')) {
    if (!line || line.startsWith(':')) {
      continue;
    }
    const colon = line.indexOf(':');
    const field = colon >= 0 ? line.slice(0, colon) : line;
    let value = colon >= 0 ? line.slice(colon + 1) : '';
    if (value.startsWith(' ')) {
      value = value.slice(1);
    }
    if (field === 'event') {
      event = value;
    } else if (field === 'data') {
      data.push(value);
    }
  }
  if (data.length === 0) {
    return null;
  }
  return { event, data: data.join('\n') };
}

function parseJson<T>(text: string): T {
  try {
    return JSON.parse(text) as T;
  } catch {
    throw new ApiError('流式响应解析失败');
  }
}

/** 把一帧事件分发给调用方；final 里仍是标准信封。 */
export function dispatchStreamEvent(event: ParsedSseEvent, handlers: RunStreamHandlers): void {
  if (event.event === 'meta') {
    handlers.onMeta?.(parseJson<RunStreamMeta>(event.data));
    return;
  }
  if (event.event === 'delta') {
    const payload = parseJson<{ text?: string }>(event.data);
    if (payload.text) {
      handlers.onDelta?.(payload.text);
    }
    return;
  }
  if (event.event === 'final') {
    const envelope = parseJson<ApiEnvelope<ExecutionRecord>>(event.data);
    if (envelope.code === 'SUCCESS') {
      handlers.onFinal?.(envelope.data);
    } else {
      handlers.onError?.({
        code: envelope.code,
        message: envelope.message,
        traceId: envelope.traceId
      });
    }
    return;
  }
  if (event.event === 'error') {
    handlers.onError?.(parseJson<RunStreamError>(event.data));
  }
}

/**
 * 发起一次流式执行。
 *
 * <p>非 2xx 会在建立流之前抛出 {@link ApiError}（带 status/code），调用方据此决定是否回退同步端点；
 * 2xx 之后的错误走 {@code onError} 事件，HTTP 状态已经无法再改。</p>
 */
export async function runAgentStream(
  agentKey: string,
  input: Record<string, unknown>,
  handlers: RunStreamHandlers,
  signal?: AbortSignal
): Promise<void> {
  const token = sessionStorage.getItem(TOKEN_KEY);
  const response = await fetch(
    `/api/v1/platform/agents/${encodeURIComponent(agentKey)}/runs/stream`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream, application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify({ input }),
      signal
    }
  );

  if (!response.ok) {
    const body = await readErrorEnvelope(response);
    if (response.status === 401) {
      notifyUnauthorized();
    }
    throw new ApiError(
      body?.message || `请求失败（${response.status}）`,
      response.status,
      body?.code,
      body?.traceId
    );
  }
  if (!response.body) {
    throw new ApiError('当前浏览器不支持流式响应');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  const parser = new SseParser();
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      const text = decoder.decode(value, { stream: true });
      for (const event of parser.push(text)) {
        dispatchStreamEvent(event, handlers);
      }
    }
  } finally {
    reader.releaseLock();
  }
}

async function readErrorEnvelope(
  response: Response
): Promise<{ code?: string; message?: string; traceId?: string } | null> {
  try {
    const text = await response.text();
    if (!text) {
      return null;
    }
    return JSON.parse(text) as { code?: string; message?: string; traceId?: string };
  } catch {
    return null;
  }
}
