# Week 27 运维手册：知识库对账 + 审批超时提醒（V10）云端发布

> 2026-10-25 已在腾讯云 4C8G（`VM-0-11-ubuntu`，Ubuntu 22.04.5，PG16 + pgvector）执行完成。
> 结果：**PASS=29 FAIL=0**；备份 `/opt/ai-code/backup/week27-20261025-145732`。
> 相关脚本：`deploy/app/cloud-week27-{precheck,backup}.sh`、`cloud-accept-week27.sh`、
> `cloud-week27-postcheck.sh`；架构/接口见 `docs/specs/week-27.md`。

## 1. 前置

- `remote.ps1 -Test` 通过；`server.conf` 的 `AUTH_MODE=key`；
- 本地根聚合 971 用例全绿（`.\run-maven-jdk17.ps1 test`）；
- 两侧 `ENTERPRISE_INTERNAL_TOKEN` / `PLATFORM_KNOWLEDGE_INTERNAL_TOKEN` 一致（只比 sha256）。

## 2. 只读巡检（不改任何东西）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -Script .\deploy\app\cloud-week27-precheck.sh
```

关注：平台 Flyway 到 `v9`、`sync_status`/提醒表不存在（预期）、绑定 1 条、`approval` 无 PENDING、
令牌指纹一致、内存/磁盘余量。

## 3. 备份（不可跳过）

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -Script .\deploy\app\cloud-week27-backup.sh
```

产出 `/opt/ai-code/backup/week27-<ts>/`：两库 dump（非空校验）、三个旧 jar、`.env`、
`state-before.txt`（Flyway 历史、行数、jar sha256、pg16 镜像、env 键名）。

## 4. 发布（enterprise → platform）

```powershell
# 本地构建（测试已跑过）
.\run-maven-jdk17.ps1 -pl apps/enterprise-knowledge-agent,apps/spring-ai-alibaba-agent -am -DskipTests package

# enterprise
powershell -File .\remote.ps1 -Put "<enterprise-jar> /tmp/app-enterprise.jar.new"
powershell -File .\remote.ps1 -Cmd "install -m 640 -o aicode -g aicode /tmp/app-enterprise.jar.new /opt/ai-code/app/enterprise/app.jar.new && bash /opt/ai-code/app/remote-deploy.sh enterprise"

# platform
powershell -File .\remote.ps1 -Put "<platform-jar> /tmp/app-platform.jar.new"
powershell -File .\remote.ps1 -Cmd "install -m 640 -o aicode -g aicode /tmp/app-platform.jar.new /opt/ai-code/app/platform/app.jar.new && bash /opt/ai-code/app/remote-deploy.sh platform"
```

`remote-deploy.sh` 自带 90s 健康检查与失败回滚（`app.jar.prev`）。
enterprise 健康判据是 `/api/v1/auth/me` 401（无 actuator）；platform 是 `/actuator/health` 200。

## 5. V10 验证

```bash
docker exec -e PGPASSWORD=... pg16 psql -U <user> -d aidemo -c \
  "select version from flyway_schema_history_platform order by installed_rank desc limit 1;"
# 期望 10
```

再查 `information_schema.columns` 的 `sync_status/last_synced_at`、
`information_schema.tables` 的 `platform_approval_reminder`、以及
`idx_platform_approval_status_created`。启动日志应出现：

```text
Successfully applied 1 migration to schema "public", now at version v10
```

## 6. 验收

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -Script .\deploy\app\cloud-accept-week27.sh
powershell -NoProfile -ExecutionPolicy Bypass -File .\remote.ps1 -Script .\deploy\app\cloud-week27-postcheck.sh
```

主验收做的事：

1. 服务健康 + V10 对象；
2. admin 登录 → `POST /knowledge-bindings/reconcile`（期望 `failed=0`）→ 绑定 `ACTIVE`；
3. enterprise `GET /internal/knowledge/bases?enterpriseUserId=<id>` 200；
4. operator 跑一次 HIGH 患者分析 → 把该审批 `created_at` 临时回填 30h
   （原值写 `/tmp/w27-approval.txt`）→ `GET /approvals/reminders` 能看到、`overdueHours>=29`、审批仍 PENDING；
5. `knowledge-qa` 真实问答（期望 `COMPLETED / hitCount>=1 / citations>=1`）；
6. 抓 `/actuator/prometheus` 的 4 个知识库指标。

提醒调度默认每小时 `:05`。若窗口内等不到，可临时把 cron 调成每分钟验证，**但不要把它裸写进 `.env`**
（见第 7 节），验证后立即恢复。

## 7. 两个真机坑（本次踩过）

1. **`.env` 不是 shell**：`.env` 由 Spring `config.import` 当 properties 读取，但运维脚本会
   `source` 它。写入 `PLATFORM_APPROVAL_REMINDER_CRON=0 */1 * * * *` 后，脚本把 `*/1` 当命令执行
   （`No such file or directory`），`set -e` 下直接中断。临时改 cron 用 systemd drop-in/环境变量，
   或先剥离该行；改完用 `cmp` 校验 `.env` 与备份一致。
2. **`pipefail` + `grep -q` 的 SIGPIPE**：对 130KB+ 的 scrape 用
   `echo "$PROM" | grep -q`，grep 命中即退出会让上游收到 SIGPIPE，管道返回 141，
   断言随机失败（Week 26 同款）。用 bash 模式匹配或直接 `grep 文件`，并给 scrape 加超时/重试。

## 8. 回滚

| 场景 | 操作 |
|------|------|
| 平台新 jar 异常 | `.\tools\deploy-app.ps1 -Target platform -Rollback`（切回 `app.jar.prev`） |
| enterprise 异常 | `.\tools\deploy-app.ps1 -Target enterprise -Rollback` |
| 需要旧 jar | 从 `/opt/ai-code/backup/week27-<ts>/` 取 `platform-app.jar` / `enterprise-app.jar` |
| V10 只增列/增表 | 旧代码可继续跑（忽略新列/表）；如需彻底回退：`DROP TABLE platform_approval_reminder; ALTER TABLE platform_knowledge_binding DROP COLUMN sync_status, DROP COLUMN last_synced_at;`（先备份） |
| 库级回滚 | 用备份 dump 恢复到 `*_drill` 先演练（`deploy/app/rollback-drill.sh`） |

## 9. 证据

| 日志 | 证明 |
|------|------|
| `logs/week-27/deploy/target-week27-precheck.log` | 变更前 v9 / 无 V10 对象 / 数据与令牌 |
| `logs/week-27/deploy/target-week27-backup.log` | 171MB 备份、两库 dump 非空 |
| `logs/week-27/deploy/target-week27-deploy-{enterprise,platform}.log` | 两次发布与健康检查 |
| `logs/week-27/acceptance/target-week27-accept.log` | 对账/提醒/问答/指标（PASS=28，1 处脚本断言误判） |
| `logs/week-27/acceptance/target-week27-reminder-metrics-check.log` | 15:04 提醒记账/审计/指标 |
| `logs/week-27/acceptance/target-week27-cleanup-restore.log` | `.env` 与 `created_at` 恢复 |
| `logs/week-27/acceptance/target-week27-postcheck.log` | 终检 **PASS=29 FAIL=0** |
