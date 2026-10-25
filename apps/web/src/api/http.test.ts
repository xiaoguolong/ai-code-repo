import { describe, expect, it } from 'vitest';
import { ApiError, toApiError, unwrapEnvelope } from './http';
import type { ApiEnvelope } from './types';

describe('unwrapEnvelope', () => {
  it('returns data for SUCCESS', () => {
    const body: ApiEnvelope<{ token: string }> = {
      code: 'SUCCESS',
      message: 'OK',
      data: { token: 'abc' },
      traceId: 't1'
    };
    expect(unwrapEnvelope(body).token).toBe('abc');
  });

  it('throws ApiError with code for business failure', () => {
    const body: ApiEnvelope<null> = {
      code: 'FORBIDDEN',
      message: '没有权限',
      data: null,
      traceId: 't2'
    };
    try {
      unwrapEnvelope(body);
      throw new Error('should not reach');
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError);
      const apiError = error as ApiError;
      expect(apiError.code).toBe('FORBIDDEN');
      expect(apiError.message).toBe('没有权限');
      expect(apiError.traceId).toBe('t2');
    }
  });
});

describe('toApiError', () => {
  it('prefers backend message', () => {
    const error = toApiError({
      response: {
        status: 409,
        data: { code: 'PLATFORM_CONFLICT', message: '审批已被他人处理', data: null }
      }
    });
    expect(error.status).toBe(409);
    expect(error.code).toBe('PLATFORM_CONFLICT');
    expect(error.message).toBe('审批已被他人处理');
  });

  it('falls back to status message for 429', () => {
    const error = toApiError({ response: { status: 429, data: undefined } });
    expect(error.status).toBe(429);
    expect(error.message).toContain('频繁');
  });

  it('keeps ApiError unchanged', () => {
    const original = new ApiError('boom', 403, 'FORBIDDEN');
    expect(toApiError(original)).toBe(original);
  });
});
