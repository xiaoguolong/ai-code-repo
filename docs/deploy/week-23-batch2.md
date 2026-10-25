# Week 23 批次 2 部署手册 — V7（角色租户 + 执行记录租户）+ enterprise 运维令牌

> 适用：平台（8084，V7 迁移）与 enterprise（8081，A1 运维令牌）。
>
> ✅ **已于 2026-10-25 06:29–06:35 在云端执行**：备份 → 平台 V7 → enterprise 发布/令牌设置 →
> 批次 2 验收 **PASS=19 FAIL=0**（含 409/503 真机）。回滚演练未执行，步骤与备份见第 7 节。

## 1. 变更内容

| 对象 | 变更 |
|------|------|
| `platform_role` / `platform_role_agent` / `platform_role_tool` / `platform_role_patient` | 重建为 `(tenant_key, role_key[, value])` 主键；旧全局角色/授权复制给每个租户 |
| `execution_record` | 加 `tenant_key`（默认 default）+ 索引；按 `user_id` 从 `platform_user` 回填 |
| enterprise | 新增 `ENTERPRISE_ADMIN_TOKEN` + `/api/v1/admin/sso-links` 运维端点（无 schema 变更） |

迁移脚本：`apps/spring-ai-alibaba-agent/src/main/resources/db/migration/V7__platform_role_tenant_and_execution_tenant.sql`。

## 2. 前置检查

```bash
# 平台当前 Flyway 版本应为 6；V7 对象尚不存在
psql "$PLATFORM_DB_URL" -c \
  "SELECT version, description, success FROM flyway_schema_history_platform ORDER BY installed_rank;"
psql "$PLATFORM_DB_URL" -c "\d platform_role"
psql "$PLATFORM_DB_URL" -c \
  "SELECT column_name FROM information_schema.columns
    WHERE table_name='execution_record' AND column_name='tenant_key';"

# 租户与用户（决定 V7 复制范围）
psql "$PLATFORM_DB_URL" -c "SELECT tenant_key FROM platform_tenant ORDER BY 1;"
psql "$PLATFORM_DB_URL" -c "SELECT tenant_key, role_key, count(*) FROM platform_user GROUP BY 1,2;"
```

## 3. 备份（必须）

```bash
STAMP="$(date +%Y%m%d-%H%M%S)"
DIR="/opt/ai-code/backup/week23-b2-${STAMP}"
sudo mkdir -p "$DIR"
# 角色四表 + 执行记录 + 用户 + 历史表
docker exec -e PGPASSWORD="$DBPASS" pg16 pg_dump -h 127.0.0.1 -U "$DBUSER" -d "$DB" \
  -Fc -t public.platform_role -t public.platform_role_agent -t public.platform_role_tool \
  -t public.platform_role_patient -t public.execution_record -t public.platform_user \
  -t public.flyway_schema_history_platform > /tmp/w23-b2.dump
sudo install -m 600 /tmp/w23-b2.dump "$DIR/platform-role-execution.dump"
sudo install -m 640 /opt/ai-code/app/platform/app.jar "$DIR/app.jar.prev"
sudo bash -c "cd '$DIR' && sha256sum * > SHA256SUMS"
echo "BACKUP_DIR=$DIR"
```

## 4. 发布平台（V7）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\deploy-app.ps1 -Target platform -SkipTests
```

期望日志：

```text
Successfully validated 7 migrations
Migrating schema "public" to version "7 - platform role tenant and execution tenant"
Successfully applied 1 migration ... now at version v7
```

## 5. 迁移后校验

```sql
-- 5.1 历史表 v1..v7 全部 success
SELECT version, description, success FROM flyway_schema_history_platform ORDER BY installed_rank;

-- 5.2 角色已按租户复制（每个租户 3 个角色）
SELECT tenant_key, count(*) FROM platform_role GROUP BY 1 ORDER BY 1;
SELECT tenant_key, role_key, count(*) FROM platform_role_agent GROUP BY 1,2 ORDER BY 1,2;

-- 5.3 执行记录 tenant 回填正确
SELECT tenant_key, count(*) FROM execution_record GROUP BY 1 ORDER BY 1;
SELECT count(*) FROM execution_record e JOIN platform_user u ON u.id = e.user_id
 WHERE e.tenant_key <> u.tenant_key;   -- 应为 0

-- 5.4 用户/角色仍可登录（抽查）
SELECT username, tenant_key, role_key FROM platform_user ORDER BY id;
```

功能验收：

```bash
# 角色按租户查（operator 只能看到自己有权限的接口；admin 可看全部）
curl -s "http://127.0.0.1:8084/api/v1/platform/roles?tenantKey=hospital-b" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq '.data'

# 执行记录带 tenantKey
curl -s "http://127.0.0.1:8084/api/v1/platform/executions" \
  -H "Authorization: Bearer $TOKEN" | jq '.data[0] | {executionId, tenantKey, status}'
```

## 6. enterprise 运维令牌

1. 在 `/opt/ai-code/app/enterprise/.env` 增加（随机长字符串，勿与其它密钥复用）：

```text
ENTERPRISE_ADMIN_TOKEN=<random-32+-chars>
```

2. 重启 enterprise：

```bash
sudo systemctl restart ai-code-enterprise
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8081/api/v1/admin/sso-links
# 期望 403（没有令牌）；不是 401
```

3. 带令牌验证：

```bash
TOKEN=<ENTERPRISE_ADMIN_TOKEN>
curl -s "http://127.0.0.1:8081/api/v1/admin/sso-links?limit=50" \
  -H "X-Admin-Token: $TOKEN" | jq '.data'
```

4. **409/503 真机**（批次 2 #11）：

```bash
# 409：造一个本地同名用户（无 sso_user_link），再用 SSO 登录
curl -s -X POST http://127.0.0.1:8081/api/v1/auth/register \
  -H 'Content-Type: application/json' -d '{"username":"conflict_probe","password":"Probe123!"}'
curl -s -o /tmp/conflict.json -w '%{http_code}\n' -X POST http://127.0.0.1:8081/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"conflict_probe","password":"Probe123!"}'
# 期望 409（前提：平台存在同名用户；否则先建平台用户）

# 503：短暂停平台 → SSO 登录 → 启动平台
sudo systemctl stop ai-code-platform
curl -s -o /tmp/unavailable.json -w '%{http_code}\n' -X POST http://127.0.0.1:8081/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"operator","password":"operator123"}'
# 期望 503 + AUTH_SSO_UNAVAILABLE
sudo systemctl start ai-code-platform
```

> 409/503 的行为由 Week 21 代码决定，批次 2 未改；真机跑完把输出写入实现日志。
> 造出的 `conflict_probe` 本地用户需清理（enterprise 当前无删除用户 API，可走 DB：
> `DELETE FROM app_user WHERE username='conflict_probe';`，执行前确认无关联数据）。

## 7. 回滚

### 7.1 代码 + 库一起回滚（推荐）

V7 已把 `platform_role` 主键改为复合；旧代码 `WHERE role_key=?` 会命中多行。因此**不能只回滚代码**，
必须库与代码一起回滚，且角色表要用备份恢复（不要手工重建，否则丢租户副本/授权）：

```bash
sudo systemctl stop ai-code-platform

# 1) 从备份恢复四张角色表 + 执行记录（--clean 先删后建）
pg_restore --clean --if-exists --no-owner \
  --table=platform_role --table=platform_role_agent \
  --table=platform_role_tool --table=platform_role_patient \
  --table=execution_record \
  -d "$PLATFORM_DB_URL" "$BACKUP_DIR/platform-role-execution.dump"

# 2) 删除 V7 历史记录（否则新代码不会重跑，旧代码也可能因 checksum 报错）
psql "$PLATFORM_DB_URL" -c "DELETE FROM flyway_schema_history_platform WHERE version = '7';"

# 3) 换回旧 jar
sudo install -o aicode -g aicode -m 640 "$BACKUP_DIR/app.jar.prev" /opt/ai-code/app/platform/app.jar
sudo systemctl start ai-code-platform
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8084/actuator/health
```

> `pg_restore` 的表名/连接串按实际备份文件与 `.env` 调整；恢复前确认 dump 内确实包含这 5 张表。

### 7.2 更安全的回滚策略

- 发布前保留 `app.jar.prev` 与角色四表 dump；
- 失败时先停平台，用 `pg_restore` 恢复四表 + `execution_record`，再 `DELETE` 历史表 v7 行，
  换回 `app.jar.prev` 启动；
- 回滚后跑 `cloud-accept-app.sh`（Week 22）确认平台/记忆/限流仍正常。

## 8. 本次执行状态（2026-10-25 06:29–06:35）

- [x] 代码/测试完成（H2：V7 迁移 + 租户角色隔离 + 执行记录 tenant + enterprise 运维端点）
- [x] 平台 V7 **已执行**：`Successfully applied 1 migration ... now at version v7`；角色 default 3 / hospital-b 3；
      `execution_record` 13 条全部 default，tenant 与 user 不一致数 = 0
- [x] enterprise 批次 2 **已发布**；`ENTERPRISE_ADMIN_TOKEN` 64 位已写入 `.env`（不打印）
- [x] 批次 2 验收 **PASS=19 FAIL=0**：令牌 403/200、角色租户、执行 tenant、
      建链/解绑删本地用户、**409 同名未建链**、**503 平台不可达**、恢复后登录 200
- [x] V7 后回归批次 1：`cloud-accept-week23.sh` **PASS=21 FAIL=0**（P002 1439 tokens，重建 scanned=0）
- 备份：`/opt/ai-code/backup/week23-b2-20261025-062917`（角色四表/执行/用户/历史 dump + 两个 jar + enterprise .env）
- 资源：available≈1.7 GB、disk 43%
- 回滚演练：未执行（`pg_restore` + 旧 jar 步骤已写，见第 7 节）
