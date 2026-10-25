import dayjs from 'dayjs';

/** 把后端 ISO/UTC 时间格式化为本地时间；空值返回 “-”。 */
export function formatTime(value?: string | null): string {
  if (!value) {
    return '-';
  }
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : String(value);
}

/** 只显示日期。 */
export function formatDate(value?: string | null): string {
  if (!value) {
    return '-';
  }
  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD') : String(value);
}

/** 安全解析后端返回的 JSON 字符串（非法时返回 null，不抛）。 */
export function parseJson<T>(value?: string | null): T | null {
  if (!value || !value.trim()) {
    return null;
  }
  try {
    return JSON.parse(value) as T;
  } catch {
    return null;
  }
}

/** 截断长文本，保留尾部省略号。 */
export function truncate(text: string | null | undefined, max = 80): string {
  const value = text ?? '';
  return value.length > max ? `${value.slice(0, max)}…` : value;
}

/** token 用量展示。 */
export function tokenText(usage?: { totalTokens?: number } | null): string {
  return usage?.totalTokens ? String(usage.totalTokens) : '0';
}
