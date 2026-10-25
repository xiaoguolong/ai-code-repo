# Week 24 运维手册：V8 上线、TD-01/02/03 验证、TD-04/TD-05 演练

> 目标：把第 24 周的两类变更安全落到云端——**代码/迁移（V8 随访任务表）** 与
> **观测口径（失败率分母、待办积压）**；并给出第 23 周遗留债 TD-04（回滚演练）、
> TD-05（旧记忆 TTL 回填）的可执行步骤。
>
> 约定：所有命令都在云端 `aicode` 用户下执行；库名/账号用占位符 `<db-name>` 表示，
> 真实值来自服务器 `.env`，**本文不写任何真实地址/密钥**。

## 1. 发布前检查

| 检查 | 期望 |
|------|------|
| 本地根聚合测试 | `BUILD SUCCESS`，用例数 ≥ 798 |
| 迁移脚本 | `V8__platform_followup_task.sql` 只含 `CREATE TABLE/INDEX IF NOT EXISTS`；无 JSONB/CLOB/TEXT |
| 配置 | `.env` 未出现真实密钥；新增键全部可选、有默认值 |
| 备份 | `/opt/ai-code/backup/week24-<时间戳>`（jar + `.env` + 库备份） |

## 2. 部署顺序（平台 → 网关）

```bash
# 1) 备份（沿用第 22/23 周脚本）
bash /opt/ai-code/app/backup-before-deploy.sh week24

# 2) 切 jar（脚本内做健康检查，失败自动回滚到上一版）
bash /opt/ai-code/app/remote-deploy.sh platform

# 3) 观察首次启动日志：V8 必须被应用一次
journalctl -u aicode-platform -n 200 | grep -E "Migrating|Successfully applied|now at version"
#   期望：Successfully applied 1 migration ... now at version v8

# 4) 重启一次，确认只校验不重跑
systemctl restart aicode-platform
journalctl -u aicode-platform -n 100 | grep -E "Successfully validated|up to date"
```

> 网关本周无代码变更，可不动；若一起发布，按第 22 周顺序先平台后网关。

## 3. V8 落库验收（规范 3.7.4）

```sql
-- 历史表无跳过版本（本模块专属历史表，见 application-jdbc.yml）
SELECT version, description, success FROM flyway_schema_history_platform ORDER BY installed_rank;
-- 新表存在且为空
SELECT COUNT(*) FROM platform_followup_task;
-- 既有表未受影响
SELECT COUNT(*) FROM platform_approval;
```

期望：历史表包含 `v8`；`platform_followup_task` 可查（0 行）；`platform_approval` 行数不变。

## 4. TD-01：Grafana 失败率口径

1. 把 `deploy/monitoring/grafana/dashboards/aicode-platform-overview.json` 同步到云端
   Grafana provisioning 目录，等待 30s 自动重载（或 `docker restart aicode-grafana`）；
2. 打开面板确认：
   - 顶部「Agent 成功率 / 失败率」显示两个值；
   - 触发一次 HIGH 风险患者分析（`PENDING_APPROVAL`）后，**失败率不升高**；
   - PromQL 自查：
     ```promql
     sum(rate(agent_run_count_total{execution_status="FAILED"}[5m]))
       / clamp_min(sum(rate(agent_run_count_total{execution_status=~"COMPLETED|FAILED"}[5m])), 0.0001)
     ```
3. 口径说明写进面板 description（已在 JSON 里），避免下次又被改回。

## 5. TD-03：待办积压指标与告警

1. 应用启动约 20s 后，`curl -s 127.0.0.1:8084/actuator/prometheus | grep approval_pending_count`，
   应看到 Gauge 值；
2. Prometheus 目标页确认 `approval_pending_count` 已入库；
3. 告警规则 `AiCodePendingApprovalBacklog`（`> 20` 持续 30m）加载成功；
4. 关闭方式：`PLATFORM_APPROVAL_PENDING_GAUGE_ENABLED=false`（只影响采样，不影响审批）。

## 6. TD-02：审批一致性对账

- 应用默认每 10 分钟扫描一次「审批已终态、执行仍 `PENDING_APPROVAL`」的记录并补写执行终态；
- 手工核查 SQL：

```sql
SELECT a.approval_id, a.status AS approval_status, e.status AS execution_status
FROM platform_approval a
JOIN execution_record e ON e.execution_id = a.execution_id
WHERE a.status IN ('APPROVED','REJECTED') AND e.status = 'PENDING_APPROVAL';
```

期望：**0 行**。若对账后仍有残留，看 `journalctl -u aicode-platform | grep "approval reconciled"`；
对账只补执行状态，不重新决策、不写记忆、不生成任务。

- 关闭方式：`PLATFORM_APPROVAL_RECONCILIATION_ENABLED=false`。

## 7. TD-05：旧记忆 TTL 回填（一次性，P1）

第 23 周 V6 之前写入的 5 条记忆 `expires_at IS NULL`（永不过期）。若合规要求统一 TTL：

```bash
# 1) 先备份
pg_dump -h 127.0.0.1 -U <db-user> -d <db-name> -t platform_memory \
  > /opt/ai-code/backup/week24-platform_memory-$(date +%Y%m%d-%H%M%S).sql

# 2) 执行幂等回填（只补 NULL）
psql -h 127.0.0.1 -U <db-user> -d <db-name> -f /opt/ai-code/app/ttl-backfill-week24.sql

# 3) 校验
psql -h 127.0.0.1 -U <db-user> -d <db-name> -c \
  "SELECT COUNT(*) FILTER (WHERE expires_at IS NULL) AS still_null,
          COUNT(*) FILTER (WHERE expires_at IS NOT NULL) AS with_ttl
   FROM platform_memory;"
```

期望：`still_null = 0`（或只剩运维明确要永久保留的记录）；列表/语义检索不受影响。

## 8. TD-04：回滚演练（临时库，不碰生产库）

> 本周只交付脚本与步骤；**演练需要在维护窗口执行并回填记录**（本文第 8.3 节留空表格）。
> 脚本 `deploy/app/rollback-drill.sh` 的设计原则：**只写临时库 `*_drill`**，生产库名不匹配直接退出。

```bash
# 1) 在云端准备一个临时库（示例名，勿用生产库名）
createdb -h 127.0.0.1 -U <db-user> aicode_drill

# 2) 用最近一次备份做恢复演练
DRILL_DB=aicode_drill \
BACKUP_FILE=/opt/ai-code/backup/week23-b2-20261025-062917/db.sql \
bash deploy/app/rollback-drill.sh

# 3) 旧 jar 回滚演练：只切 jar、不动库（V8 是新增表，旧 jar 不读它，向前兼容）
bash /opt/ai-code/app/remote-deploy.sh platform --jar /opt/ai-code/backup/week24-<ts>/platform.jar
curl -fsS 127.0.0.1:8084/actuator/health
```

### 8.1 演练验收

| 项 | 期望 |
|----|------|
| `pg_restore` 退出码 | 0 |
| 临时库关键表行数 | 与生产库备份时点一致（± 备份期间新增） |
| 旧 jar 启动 | `Started ... in <N>s`，`/actuator/health` = `UP` |
| V8 表在旧 jar 下 | 存在但为空，旧代码不读取（无启动错误） |
| 回滚后恢复新 jar | 健康检查 200，随访任务可查 |

### 8.2 RTO 记录

| 步骤 | 实测耗时 |
|------|----------|
| 停止服务 | |
| 替换 jar | |
| 启动 + 健康检查 | |
| **合计 RTO** | |

### 8.3 演练记录（执行后填写）

| 日期 | 执行人 | 备份文件 | 临时库 | 结果 | 备注 |
|------|--------|----------|--------|------|------|
| （待维护窗口） | | | | | 脚本与步骤已就绪 |

## 9. 回滚决策（本周）

| 场景 | 动作 |
|------|------|
| V8 迁移失败（脚本报错） | 服务不会启动成功；修脚本后重跑。V8 全是 `IF NOT EXISTS`，可重复执行 |
| 新 jar 启动失败 | `remote-deploy.sh` 自动回滚到上一版 jar；V8 表残留不影响旧代码 |
| 运营日报数据不对 | 先核对 `windowDays` 与租户可见性（admin 整租户 / operator 只看自己），再查 `execution_record` |
| 随访任务异常 | `PLATFORM_APPROVAL_RECONCILIATION_ENABLED=false` 只停对账；任务本身可用 `cancel` 收敛 |
| 待办积压指标干扰 | `PLATFORM_APPROVAL_PENDING_GAUGE_ENABLED=false` |
