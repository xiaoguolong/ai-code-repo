# Week 23 部署手册 — V6 迁移（记忆生命周期 + 审批表）与 HITL 验收

> 适用环境：腾讯云平台库（`platform` 模块，systemd `ai-code-platform`，8084）。
> 本文只描述**平台库**的 V6；enterprise（8081）本周无迁移。
>
> ✅ **已在云端执行（2026-10-25 02:49–02:52）**：备份 → 发布新 jar → Flyway V6 应用 →
> 重启幂等 → `cloud-accept-week23.sh` **PASS=21 FAIL=0**。执行记录见第 9 节。
> 回滚演练未执行（备份与回滚命令已就绪，见第 7 节）。

## 1. 变更内容

| 对象 | 变更 | 影响 |
|------|------|------|
| `platform_memory` | 加 `expires_at`、`embedding_model` 两列 + 两个索引 | 旧行两列为 NULL：永不过期 / 待重建，不误删 |
| `platform_approval` | 新建审批任务表 + 3 个索引 | 无旧数据，纯新增 |
| 代码 | `ExecutionStatus` 增 `PENDING_APPROVAL` / `REJECTED` | 仅枚举值，不涉及表结构 |
| 调度 | 每日 03:30 过期清理（可关） | 单实例、低负载；只删 `expires_at <= now` |

迁移脚本：`apps/spring-ai-alibaba-agent/src/main/resources/db/migration/V6__platform_memory_lifecycle_and_approval.sql`。

## 2. 前置检查（在云主机执行）

```bash
# 1) 平台进程与健康
systemctl is-active ai-code-platform
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8084/actuator/health

# 2) 当前 Flyway 版本（应为 5；若已是 6 则无需迁移）
psql "$PLATFORM_DB_URL" -c \
  "SELECT version, description, success FROM flyway_schema_history_platform ORDER BY installed_rank;"

# 3) 确认新列/新表尚不存在（避免手工改过库）
psql "$PLATFORM_DB_URL" -c \
  "SELECT column_name FROM information_schema.columns
    WHERE table_name='platform_memory' AND column_name IN ('expires_at','embedding_model');"
psql "$PLATFORM_DB_URL" -c \
  "SELECT to_regclass('public.platform_approval') AS approval_table;"

# 4) 数据量（决定 ALTER 锁表时间；平台记忆通常很小）
psql "$PLATFORM_DB_URL" -c "SELECT count(*) FROM platform_memory;"
```

> `PLATFORM_DB_URL` 从部署环境的 `.env` / 密钥管理读取，**不要把连接串写进本文或脚本**。

## 3. 备份（必须）

```bash
STAMP="$(date +%Y%m%d-%H%M%S)"
BACKUP_DIR="/opt/ai-code/backup/week23-${STAMP}"
mkdir -p "$BACKUP_DIR"

# 只备份平台库中本次会变的对象（pg_dump 自定义格式，便于单表恢复）
pg_dump "$PLATFORM_DB_URL" \
  --table=public.platform_memory \
  --table=public.flyway_schema_history_platform \
  -Fc -f "$BACKUP_DIR/platform_memory-and-history.dump"

# 代码/配置同样留一份（回滚用上一版 jar 与 .env）
cp /opt/ai-code/app/app.jar "$BACKUP_DIR/app.jar.prev"
cp /opt/ai-code/app/.env "$BACKUP_DIR/.env"
sha256sum "$BACKUP_DIR"/* > "$BACKUP_DIR/SHA256SUMS"
```

## 4. 发布

```bash
# 本机（Windows）：构建、上传、远端切换（第 22 周脚本）
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\deploy-app.ps1 -Service platform

# 云主机：确认启动日志
journalctl -u ai-code-platform -n 200 --no-pager | grep -E 'Flyway|Migrating|Successfully applied|Started'
```

期望日志（版本号与迁移数按实际）：

```text
Successfully validated 6 migrations
Migrating schema "public" to version "6 - platform memory lifecycle and approval"
Successfully applied 1 migration to schema "public", now at version v6
Started SpringAiAlibabaAgentApplication in ...
```

> Flyway 在应用启动时执行，不在部署脚本里单独跑；**失败时应用起不来**，
> `remote-deploy.sh` 的健康检查会回滚到 `app.jar.prev`（此时库可能已部分变更，见第 7 节）。

## 5. 迁移后校验（SQL）

```bash
# 5.1 历史表：v0 BASELINE + v1..v6，无跳过、无失败
psql "$PLATFORM_DB_URL" -c \
  "SELECT installed_rank, version, description, success FROM flyway_schema_history_platform ORDER BY installed_rank;"

# 5.2 新列存在
psql "$PLATFORM_DB_URL" -c \
  "SELECT column_name, data_type, is_nullable FROM information_schema.columns
    WHERE table_name='platform_memory' AND column_name IN ('expires_at','embedding_model')
    ORDER BY column_name;"

# 5.3 审批表与索引存在
psql "$PLATFORM_DB_URL" -c "\d platform_approval"

# 5.4 旧记忆未被误删/误改：行数与迁移前一致；两列为 NULL
psql "$PLATFORM_DB_URL" -c \
  "SELECT count(*) AS total,
          count(*) FILTER (WHERE expires_at IS NULL) AS never_expire,
          count(*) FILTER (WHERE embedding_model IS NULL) AS needs_rebuild
     FROM platform_memory;"

# 5.5 同库其它应用未受影响（历史表行数不变）
psql "$PLATFORM_DB_URL" -c \
  "SELECT 'enterprise' AS app, count(*) FROM flyway_schema_history
   UNION ALL SELECT 'platform', count(*) FROM flyway_schema_history_platform;"
```

## 6. 功能验收（HITL + 记忆生命周期）

```bash
# 平台真实模型 + 真实 embedding；P002 为 HIGH 风险演示患者
bash /opt/ai-code/app/cloud-accept-week23.sh
```

期望全 PASS，覆盖：

1. P002 → `PENDING_APPROVAL`，有 `approvalId`，**待审期间不写记忆**；
2. 待办列表/详情可见且 `riskLevel=HIGH`；
3. 批准 → `APPROVED` + 执行 `COMPLETED` + `memoryId`，执行记录终态；
4. 重复决策 → 409；
5. 删除记忆 → 200，列表不可见；
6. operator 重建/清理 → 403；admin → 200；
7. admin 清理返回 `deleted`，重建返回 `scanned/rebuilt/failed/currentModel`。

另外人工确认：

- 重启平台后，`PENDING` 审批仍在（审批落库，不依赖内存 checkpoint）；
- `GET /api/v1/platform/approvals?status=APPROVED` 可查到历史；
- 审计表出现 `APPROVAL_SUBMIT` / `APPROVAL_DECIDE` / `MEMORY_DELETE` / `MEMORY_PURGE` / `MEMORY_REBUILD`；
- 定时任务关闭时（`PLATFORM_MEMORY_CLEANUP_ENABLED=false`）不再装配清理 bean。

## 7. 回滚

> 回滚分“代码回滚”和“库回滚”两件事；V6 是**只加列/加表**，旧代码不会读新列/新表，
> 所以大多数情况只需回滚代码，库可以保留（更安全）。只有确认要撤销 schema 时才执行库回滚。

### 7.1 只回滚代码（推荐）

```bash
systemctl stop ai-code-platform
install -o aicode -g aicode -m 0644 "$BACKUP_DIR/app.jar.prev" /opt/ai-code/app/app.jar
systemctl start ai-code-platform
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8084/actuator/health
```

旧代码不引用 `platform_approval` 与两列，运行不受影响；新表/列留作下次发布复用。

### 7.2 代码 + 库一起回滚（会丢数据）

```bash
systemctl stop ai-code-platform

psql "$PLATFORM_DB_URL" <<'SQL'
BEGIN;
DROP TABLE IF EXISTS platform_approval;
ALTER TABLE platform_memory DROP COLUMN IF EXISTS embedding_model;
ALTER TABLE platform_memory DROP COLUMN IF EXISTS expires_at;
DELETE FROM flyway_schema_history_platform WHERE version = '6';
COMMIT;
SQL

# 恢复对象数据（如果 7.1 之前已产生新数据且需要保留）
# pg_restore --data-only --table=platform_memory -d "$PLATFORM_DB_URL" "$BACKUP_DIR/platform_memory-and-history.dump"

install -o aicode -g aicode -m 0644 "$BACKUP_DIR/app.jar.prev" /opt/ai-code/app/app.jar
systemctl start ai-code-platform
```

注意：

- 先 `DROP TABLE platform_approval`，再删列；索引 `idx_platform_memory_expires` / `idx_platform_memory_embedding_model` 会随列删除自动消失；
- 删除历史表 v6 行后，旧代码下次启动不会因 checksum/版本缺失报错；若保留新代码又删历史行，Flyway 会重跑 V6（`IF NOT EXISTS` 可重复执行，但会重建审批表）；
- **审批任务与 TTL/向量来源信息会丢失**，回滚前先确认没有待审任务。

## 8. 已知风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| `ALTER TABLE` 锁表 | 平台记忆表通常几百行，秒级；大表需窗口 | 先查 `count(*)`；必要时 `lock_timeout` |
| 旧行 `embedding_model=NULL` | 首次重建会扫描全部旧记忆 | 管理端点默认 100 条/批，分多次执行 |
| 审批写记忆失败 | 审批仍 APPROVED、记忆缺失 | 日志 WARN；可重跑？当前不支持自动补偿，需人工补记（记录在 Open Questions） |
| 每日清理误删 | 只删已过期行；`ttl-days<=0` 永不删 | 迁移后先确认 `expires_at` 语义；清理审计只在手工触发时写 |
| 调度线程 | 单实例无影响 | `@EnableScheduling` 已开启；测试关闭；多实例前改分布式锁 |

## 9. 本次执行状态（2026-10-25）

- [x] V6 在 H2(MODE=PostgreSQL) 由 Flyway 全量应用（根聚合测试通过）
- [x] 迁移脚本双库语法自查（无 `TEXT`/`JSONB`/`CREATE EXTENSION`/方言 upsert）
- [x] **真实 PostgreSQL 首次应用**：`Successfully applied 1 migration ... now at version v6`
      （备份 `/opt/ai-code/backup/week23-20261025-024942`）
- [x] **重启幂等**：`Successfully validated 6 migrations` + `Schema "public" is up to date`；
      `platform_memory` 仍 5 行、`platform_approval` 0 行（迁移后计数不变）
- [x] **云端 HITL 验收**：`cloud-accept-week23.sh` **PASS=21 FAIL=0**
- [ ] 回滚演练（备份 + 回滚命令已就绪；未实际执行，避免无谓停机）

### 9.1 执行数据

| 项 | 值 |
|----|-----|
| 备份目录 | `/opt/ai-code/backup/week23-20261025-024942`（jar 95 MB + `.env` + 21 KB dump + SHA256SUMS） |
| Flyway 历史 | `1..6` 全部 `success=true`；V6 耗时 0.034 s |
| 平台启动 | 12.99 s（首次）/ 11.09 s（重启） |
| 迁移后旧数据 | `platform_memory` 5 行不变；`expires_at` 全 NULL（不误删）；5 行 `embedding_model` 均为旧值 NULL |
| 向量重建 | admin 触发后 5 行全部变为 `openai:qwen3.7-text-embedding-flash:1024` |
| HITL 真机 | P002 HIGH → `PENDING_APPROVAL`（1731 tokens）→ 批准 `COMPLETED` + 记忆 → 删除 → 重复决策 409 |
| 审计 | `APPROVAL_SUBMIT=1`、`APPROVAL_DECIDE=1`、`MEMORY_DELETE=1`、`MEMORY_PURGE=1`、`MEMORY_REBUILD=1` |
| 资源 | 迁移后 available≈1.71 GB、swap 0 MB、disk 42% |

> 验收脚本日志：`logs/week-23/acceptance/target-week23-cloud-accept.log`；迁移前/后巡检：`logs/week-23/migration/target-week23-cloud-precheck*.log`。
