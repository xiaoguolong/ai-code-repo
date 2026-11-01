import { describe, expect, it, vi } from 'vitest';
import { SseParser, dispatchStreamEvent } from './stream';
import type { ExecutionRecord } from './types';

describe('SseParser', () => {
  it('parses a single event', () => {
    const parser = new SseParser();
    expect(parser.push('event: meta\ndata: {"executionId":"e1"}\n\n')).toEqual([
      { event: 'meta', data: '{"executionId":"e1"}' }
    ]);
  });

  it('reassembles an event split across chunks', () => {
    const parser = new SseParser();
    expect(parser.push('event: del')).toEqual([]);
    expect(parser.push('ta\ndata: {"text":"你')).toEqual([]);
    expect(parser.push('好"}\n\n')).toEqual([{ event: 'delta', data: '{"text":"你好"}' }]);
  });

  it('normalizes CRLF frames', () => {
    const parser = new SseParser();
    expect(parser.push('event: delta\r\ndata: {"text":"a"}\r\n\r\n')).toEqual([
      { event: 'delta', data: '{"text":"a"}' }
    ]);
  });

  it('joins multiple data lines', () => {
    const parser = new SseParser();
    expect(parser.push('event: error\ndata: line1\ndata: line2\n\n')).toEqual([
      { event: 'error', data: 'line1\nline2' }
    ]);
  });

  it('ignores heartbeat comments and unknown fields', () => {
    const parser = new SseParser();
    expect(parser.push(': ping\nid: 7\nretry: 3000\n\n')).toEqual([]);
    expect(parser.push(': ping\nevent: delta\ndata: {"text":"x"}\n\n')).toEqual([
      { event: 'delta', data: '{"text":"x"}' }
    ]);
  });

  it('emits multiple events from one chunk', () => {
    const parser = new SseParser();
    const events = parser.push(
      'event: delta\ndata: {"text":"a"}\n\nevent: delta\ndata: {"text":"b"}\n\n'
    );
    expect(events.map((event) => event.data)).toEqual(['{"text":"a"}', '{"text":"b"}']);
  });

  it('does not emit an incomplete frame', () => {
    const parser = new SseParser();
    expect(parser.push('event: delta\ndata: {"text":"a"}\n')).toEqual([]);
    expect(parser.push('\n')).toEqual([{ event: 'delta', data: '{"text":"a"}' }]);
  });
});

describe('dispatchStreamEvent', () => {
  it('dispatches meta', () => {
    const onMeta = vi.fn();
    dispatchStreamEvent(
      { event: 'meta', data: '{"executionId":"e1","agentKey":"knowledge-qa","startedAt":"t"}' },
      { onMeta }
    );
    expect(onMeta).toHaveBeenCalledWith({
      executionId: 'e1',
      agentKey: 'knowledge-qa',
      startedAt: 't'
    });
  });

  it('dispatches delta and skips empty text', () => {
    const onDelta = vi.fn();
    dispatchStreamEvent({ event: 'delta', data: '{"text":"你好"}' }, { onDelta });
    dispatchStreamEvent({ event: 'delta', data: '{"text":""}' }, { onDelta });
    expect(onDelta).toHaveBeenCalledTimes(1);
    expect(onDelta).toHaveBeenCalledWith('你好');
  });

  it('unwraps final envelope', () => {
    const onFinal = vi.fn();
    const record = { executionId: 'e1', status: 'COMPLETED' } as ExecutionRecord;
    dispatchStreamEvent(
      { event: 'final', data: JSON.stringify({ code: 'SUCCESS', message: 'OK', data: record }) },
      { onFinal }
    );
    expect(onFinal).toHaveBeenCalledWith(record);
  });

  it('turns a non-success final into an error', () => {
    const onError = vi.fn();
    dispatchStreamEvent(
      {
        event: 'final',
        data: JSON.stringify({ code: 'FAILED', message: '模型失败', data: null, traceId: 't1' })
      },
      { onError }
    );
    expect(onError).toHaveBeenCalledWith({ code: 'FAILED', message: '模型失败', traceId: 't1' });
  });

  it('dispatches error events', () => {
    const onError = vi.fn();
    dispatchStreamEvent(
      { event: 'error', data: '{"code":"CHAT_MODEL_ERROR","message":"模型调用失败","executionId":"e1"}' },
      { onError }
    );
    expect(onError).toHaveBeenCalledWith({
      code: 'CHAT_MODEL_ERROR',
      message: '模型调用失败',
      executionId: 'e1'
    });
  });
});
