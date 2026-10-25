# Week 26 运维手册：上线收口（pgvector / V6 / V8 / V9 / 演练 / TTL）

> 适用环境：腾讯云 4C8G Ubuntu 22.04，`/opt/ai-code`，容器 `pg16`，systemd 三个单元。
> 本手册按「**可重复执行**」写：每一步都有前置、命令、判据、回滚；所有脚本不打印密钥。
>
> 相关：Spec `docs/specs/week-26.md` ｜ 实现日志 `notes/impl-logs/week-26.md` ｜
> 第 24 周运维 `docs/deploy/week-24-ops.md` ｜ 第 25 周部署 `docs/deploy/week-25-knowledge.md`

## 0. 顺序总览

```text
备份 → 换 pgvector 镜像 → 刷 collation（含 template1）→ 建扩展
     → 对齐 .env（令牌/Flyway/向量存储）→ 发 enterprise（V6 乱序）→ 发平台（V8+V9）
     → 演示数据 → 两套验收 → 三 Agent 回归 → TD-04 演练 → TD-05 TTL 回填
```

## 1. 备份（不可跳过）

```bash
bash /opt/ai-code/app/cloud-week26-backup.sh
# 产出：/opt/ai-code/backup/week26-<ts>/
#   platform-aidemo.dump  enterprise-enterprise.dump
#   {platform,enterprise,gateway}-app.jar + .env
#   state-before.txt（flyway 历史、行数、pg16 镜像与 env 键名）
```

判据：两个 dump 均非空；`state-before.txt` 含 flyway 历史。

## 2. pg16 换 pgvector 镜像（保留数据卷）

官方 `postgres:16` 不含 `vector`，容器内也没有编译工具链；换官方 `pgvector/pgvector:pg16`。

```bash
bash /opt/ai-code/app/cloud-week26-pgvector.sh
```

脚本行为：

1. `docker inspect` 记录旧容器形态；
2. 从旧容器 `Config.Env` 提取 `POSTGRES_*` 到 `/root/week26-pg16.env`（600，不打印）；
3. `stop/rm` 旧容器（**卷 `data_pg_data` 保留**）→ 用**同名、同网络、同端口、同 env** 起新镜像；
4. 60s 内未就绪或启动失败 → **自动回滚到 `postgres:16`**；
5. 校验行数（`platform_memory` / `platform_approval` / `app_user`）→ `CREATE EXTENSION vector`。

判据：`pg_available_extensions` 有 `vector`；`pg_extension` 显示版本；旧数据行数不变。

### 2.1 排序规则刷新（换基座镜像必做）

旧镜像基于 Debian 13（glibc 2.41），pgvector 镜像基于 Debian 12（glibc 2.36）。数据目录记录的是 2.41，
运行库是 2.36 → 每次连接告警，且**建库会失败**（模板库同样不匹配）。

```bash
# 业务库 + postgres
for db in postgres aidemo enterprise skywalking; do
  docker exec pg16 psql -U aidemo -d postgres -c "ALTER DATABASE \"$db\" REFRESH COLLATION VERSION;"
done
for db in aidemo enterprise skywalking; do
  docker exec pg16 psql -U aidemo -d "$db" -c "REINDEX DATABASE \"$db\";"
done

# 模板库（本周漏过一次，TD-04 建演练库时暴露）
docker exec pg16 psql -U aidemo -d postgres -c "ALTER DATABASE template1 REFRESH COLLATION VERSION;"
docker exec pg16 psql -U aidemo -d template1 -c "REINDEX DATABASE template1;"
# template0 无 datcollversion，刷新会报 invalid collation version change，可忽略

# 冒烟：能建库即通过
docker exec pg16 psql -U aidemo -d postgres -c "CREATE DATABASE collation_smoke;"
docker exec pg16 psql -U aidemo -d postgres -c "DROP DATABASE collation_smoke;"
```

判据：`pg_database.datcollversion` 全为 `2.36`（`template0` 为 null）；冒烟建库成功。

## 3. 配置对齐（幂等）

```bash
bash /opt/ai-code/app/cloud-week26-env.sh
```

写入：

| 文件 | 键 | 值 |
|------|----|----|
| enterprise `.env` | `ENTERPRISE_INTERNAL_TOKEN` | 随机 32 字节 hex（与平台一致） |
| enterprise `.env` | `SPRING_FLYWAY_LOCATIONS` | `classpath:db/migration,classpath:db/postgresql` |
| enterprise `.env` | `SPRING_FLYWAY_OUT_OF_ORDER` | `true`（**仅补 V6 时**；补完删除） |
| enterprise `.env` | `RAG_VECTOR_STORE` | `pgvector` |
| platform `.env` | `PLATFORM_KNOWLEDGE_INTERNAL_TOKEN` | 同上 |
| platform `.env` | `PLATFORM_KNOWLEDGE_BASE_URL` | `http://127.0.0.1:8081` |
| platform `.env` | `PLATFORM_KNOWLEDGE_ENABLED` | `true` |

判据：脚本打印 `token aligned: yes (sha256=xxxxxxxxxxxx)`；两侧 `.env` 权限 600。

> 为什么 `out-of-order` 必须写在 yml 占位符里：`.env` 由 `config.import` 作为 properties 源加载，
> **不做 relaxed binding**，`SPRING_FLYWAY_OUT_OF_ORDER` 这个键不会被绑定到
> `spring.flyway.out-of-order`。详见实现日志 9.1。

## 4. 发布（enterprise → platform）

```bash
# 本地
.\tools\deploy-app.ps1 -Target enterprise -SkipTests
.\tools\deploy-app.ps1 -Target platform -SkipTests
```

或手工（先上传到 `/tmp`，再 `sudo install` + `sudo bash /opt/ai-code/app/remote-deploy.sh <target>`）。

判据：

- enterprise 日志：`Migrating ... to version "6 - document chunk pgvector" [out of order]` →
  `Successfully applied 1 migration ... now at version v6`；
  `/api/v1/auth/me` 401。
- 平台日志：`Successfully applied 2 migrations ... now at version v9`；`/actuator/health` 200。
- 补完 V6 后**立刻**删除 `SPRING_FLYWAY_OUT_OF_ORDER` 并重启 enterprise：
  `Successfully validated 7 migrations`（不重跑）。

## 5. 企业侧演示数据（幂等）

```bash
bash /opt/ai-code/app/cloud-week26-seed-knowledge.sh
```

- 以 admin 走 SSO 登录 enterprise → 建「医疗随访规范库」→ 入《糖尿病随访管理规范》；
- 判据：`kb=1 doc=1 chunk>=2`，`vector_dims(embedding)=1024`。

## 6. 验收

```bash
bash /opt/ai-code/app/cloud-accept-week24.sh   # 期望 PASS=23 FAIL=0
bash /opt/ai-code/app/cloud-accept-week25.sh   # 期望 PASS=13 FAIL=0
bash /opt/ai-code/app/cloud-week26-regression.sh # 期望 PASS=8 FAIL=0
```

2026-10-25 实测：`23/0`、`13/0`、`8/0`。

## 7. TD-04 回滚演练（容器版）

宿主机没有 `psql/pg_restore`，用容器版脚本（只碰 `*_drill` 库）：

```bash
cd /opt/ai-code/app
DRILL_DB=aicode_week26_drill \
  BACKUP_FILE=/opt/ai-code/backup/week26-20261025-125829/platform-aidemo.dump \
  bash cloud-week26-drill.sh

DRILL_DB=enterprise_week26_drill \
  BACKUP_FILE=/opt/ai-code/backup/week26-20261025-125829/enterprise-enterprise.dump \
  bash cloud-week26-drill.sh
```

### 7.1 演练记录

| 项 | 平台库 | 企业库 |
|----|--------|--------|
| 日期 | 2026-10-25 | 2026-10-25 |
| 执行人 | 平台运维（自动化脚本） | 同上 |
| 备份 | `week26-20261025-125829/platform-aidemo.dump` | `.../enterprise-enterprise.dump` |
| 临时库 | `aicode_week26_drill` | `enterprise_week26_drill` |
| 恢复结果 | PASS | PASS |
| 关键计数 | `platform_user=4` / `platform_role=6` / `execution_record=15` / `approval=3` / `memory=5` | `app_user=1`（备份时企业还没有知识库） |
| 生产库 | `platform_user` 前后均 4，未被修改 | 同 |
| **RTO** | **2s**（重建库 + 恢复 + 校验） | **2s** |
| 备注 | 该备份是**变更前**快照，所以不含 V8/V9 表（预期） | 不含 V6 的 `document_chunk`（预期） |

> 结论：小体量（<100MB）库的恢复 RTO 在秒级；真实体量需按备份大小线性外推，并在下次演练记录。

## 8. TD-05 旧记忆 TTL 回填

```bash
bash /opt/ai-code/app/cloud-week26-ttl-backfill.sh
```

- 口径：`expires_at = created_at + 90 天`，只补 `NULL`，幂等；
- 判据：`null=0`，`with_ttl=总数`；
- 2026-10-25 实测：`5` 行 NULL → `UPDATE 5`，`total=9 / null=0`；
- 回滚：从 `week26-*/platform-aidemo.dump` 恢复 `platform_memory`（会丢失之后的审批/执行记录，需评估）。

## 9. 回滚矩阵

| 场景 | 动作 | 已验证 |
|------|------|--------|
| 平台新 jar 异常 | `tools\deploy-app.ps1 -Target platform -Rollback` | 脚本自动回滚 |
| enterprise 新 jar 异常 | `-Target enterprise -Rollback` | 是（本周第一次发布） |
| **`.env` 变更导致旧 jar 也起不来** | **先回滚 `.env`**（Flyway locations/令牌），再重启；只回滚 jar 无效 | 是（9.2） |
| pgvector 镜像异常 | `cloud-week26-pgvector.sh` 内自动回退 `postgres:16` | 脚本内置 |
| 数据集损坏 | 停服务 → 从 `week26-*/` dump 恢复到新库 → 改 `.env` → 起服务 | TD-04 演练验证 |
| 关闭知识库 | 平台 `.env` 设 `PLATFORM_KNOWLEDGE_ENABLED=false` → 重启（工具 502，其它 Agent 正常） | 第 25 周设计 |
| 令牌轮换 | 先 enterprise 后平台（顺序反了有 502 窗口） | 设计约定 |

## 10. 故障排查

| 现象 | 最可能原因 | 处理 |
|------|------------|------|
| 启动报 `Detected resolved migration not applied to database: 6` | 没开 `out-of-order`，或 `.env` 的键没被绑定 | 检查 yml 占位符 + `.env`；见 9.1 |
| 换镜像后每次连接告警 collation | 没刷新/没重建索引 | 第 2.1 节（含 `template1`） |
| `CREATE DATABASE` 报 template collation | `template1` 未刷新 | 第 2.1 节 |
| 导入知识库 502 | 两侧令牌不一致 / 管理员未建 SSO 映射 | `cloud-week26-env.sh` 重跑；先 enterprise 登录一次 |
| 问答 200 但 `hitCount=0` | 文档未入库/切片为空 | 查 `document_chunk` 行数；重跑 seed |
| 验收指标假 FAIL | `pipefail` + `grep -q` 大响应 SIGPIPE | 用落文件再 grep（脚本已修） |
| 演练脚本退出码 1 但无 FAIL | `set -e` 下裸 `[ ] &&` | 用 `if`（脚本已修） |
| 巡检日志出现密码 | `docker inspect` env 未脱敏 | 只打印键名；已泄露的日志要脱敏并考虑轮换 |
