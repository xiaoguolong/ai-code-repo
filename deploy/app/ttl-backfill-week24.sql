-- Week 24 / TD-05：旧记忆 TTL 一次性回填（幂等，只补 NULL）
-- 背景：第 23 周 V6 之前的记录 expires_at 为 NULL（视为永不过期）。
-- 口径：expires_at = created_at + 默认 TTL（90 天）；已过期的记录由每日清理任务处理。
-- 执行前必须备份 platform_memory（见 docs/deploy/week-24-ops.md 第 7 节）。
UPDATE platform_memory
SET expires_at = created_at + INTERVAL '90' DAY
WHERE expires_at IS NULL;

-- 校验（执行后应为 still_null = 0）
SELECT COUNT(*) FILTER (WHERE expires_at IS NULL) AS still_null,
       COUNT(*) FILTER (WHERE expires_at IS NOT NULL) AS with_ttl
FROM platform_memory;
