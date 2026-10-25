import { describe, expect, it } from 'vitest';
import { parseJson, truncate } from './format';

describe('format', () => {
  it('parses valid json and returns null for invalid', () => {
    expect(parseJson<{ a: number }>('{"a":1}')?.a).toBe(1);
    expect(parseJson('not-json')).toBeNull();
    expect(parseJson('')).toBeNull();
    expect(parseJson(null)).toBeNull();
  });

  it('truncates long text', () => {
    expect(truncate('abcdef', 3)).toBe('abc…');
    expect(truncate('abc', 3)).toBe('abc');
    expect(truncate(null)).toBe('');
  });
});
