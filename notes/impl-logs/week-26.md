# Week 26 实现日志 — 上线收口周（pgvector/V6、V8/V9 真库、发布验收、TD-04/05）

- 日期：2026-10-25
- 批次：1（执行周，无新业务功能）
- 对应 Spec：`docs/specs/week-26.md`
- 来源候选：`docs/specs/week-26-candidates.md`
- 运维手册：`docs/deploy/week-26-closeout.md`
- 云端日志目录：`logs/week-26/{migration,deploy,acceptance,diagnose}/`（gitignored）

## 1. 本周目标

把第 24/25 周「代码与脚本已就绪、未在真实环境执行」的六件事跑完：

1. enterprise 装 pgvector + 补跑 V6；
2. 第 24 周 V8 云端发布与验收；
3. 第 25 周发布（enterprise → 平台）+ 知识库问答；
4. V9 真库校验 + 三 Agent 回归；
5. TD-04 回滚演练；
6. TD-05 旧记忆 TTL 回填。

## 2. 边界

- **Always**：变更前备份；每步留改前/改后证据；验收脚本 FAIL=0；演练只碰 `*_drill`。
- **Never**：新功能/新表/新依赖；改已应用迁移；把令牌或密码写入仓库。
- 唯一生产代码改动：enterprise `application.yml` 的 Flyway `out-of-order` 占位符（见第 5 节）。

## 3. 变更前环境事实（2026-10-25 12:56 巡检）

| 项 | 状态 |
|----|------|
| 平台 8084 | active，jar 06:30（Week 23 代码），Flyway `1–7` |
| enterprise 8081 | active，jar 06:32，Flyway `1,2,3,4,5,7`（**跳过 6**），`RAG_VECTOR_STORE=memory` |
| pg16 容器 | `postgres:16`，卷 `data_pg_data`，`127.0.0.1:5432`，库 `aidemo/enterprise/skywalking` |
| pgvector | **未安装**（`pg_available_extensions` 无 `vector`） |
| 令牌 | 两侧 `*_INTERNAL_TOKEN` **均未配置** |
| 业务数据 | `platform_memory=5`（TTL 全 NULL）、`platform_approval=3`、`app_user=1`、`knowledge_base=0` |
| 宿主机 | 4C8G，可用 3.2GB，磁盘 28G；**没有 psql/pg_restore**（后续演练走容器） |

备份：`/opt/ai-code/backup/week26-20261025-125829/`（两库 dump + 3 个 jar + 3 个 .env + 状态快照，170MB）。

## 4. 执行过程

| 步骤 | 动作 | 结果 |
|------|------|------|
| 1 | 备份两库 + 产物 + 配置 | `platform dump 57,799B` / `enterprise 24,573B` |
| 2 | pg16 切 `pgvector/pgvector:pg16`（同卷、同网络/端口/env） | 1s 就绪；`memory=5 approval=3 app_user=1` 不变；`CREATE EXTENSION vector` → `0.8.7` |
| 3 | 排序规则修复（glibc 2.41→2.36） | 4 个库 `REFRESH COLLATION VERSION` + `REINDEX`；后续补刷 `template1` |
| 4 | 两侧令牌 + `SPRING_FLYWAY_LOCATIONS` 两目录 + `RAG_VECTOR_STORE=pgvector` | 令牌 sha256 `74c5c6cfdc81`（只记指纹） |
| 5 | 发布 enterprise（第一次） | **失败并自动回滚**：`Validate failed: Detected resolved migration not applied to database: 6`（见 9.1） |
| 6 | 补 `out-of-order` 占位符 → 重新打包发布 | `Migrating ... [out of order]` → `now at version v6`；`document_chunk` + hnsw 索引 |
| 7 | 移除 `SPRING_FLYWAY_OUT_OF_ORDER` 并重启 enterprise | `Successfully validated 7 migrations`（不重跑） |
| 8 | 发布平台（Week 25 jar） | `applied 2 migrations ... now at version v9`；`platform_followup_task`/`platform_knowledge_binding` 就绪 |
| 9 | 企业侧演示数据（SSO admin → 建库 → 入文档） | `kb=1 doc=1 chunk=2`，`vector_dims=1024` |
| 10 | `cloud-accept-week24.sh` | **PASS=23 FAIL=0**（修脚本后，见 9.2/9.3） |
| 11 | `cloud-accept-week25.sh` | **PASS=13 FAIL=0**：导入 1 个绑定、问答 `COMPLETED`、`hitCount=2`、`citations=2`、未绑定 404、viewer 403 |
| 12 | 三 Agent 回归 | `patient-analysis`(P001) / `followup`(P003) / `knowledge-qa`(kb=1) 全 COMPLETED；限流第 6 次 429 |
| 13 | TD-04 演练（容器版） | 平台库 `RTO=2s`、企业库 `RTO=2s`；生产 `platform_user=4` 前后一致 |
| 14 | TD-05 TTL 回填 | `5` 行 NULL → `created_at+90d`；`null=0 with_ttl=9` |

### 4.1 最终状态

```
服务：platform active / enterprise active / gateway active
迁移：platform 1..9；enterprise 1,2,3,4,5,7,6（V6 乱序补跑）
pgvector：vector 0.8.7
数据：memory=9（null_ttl=0）/ approval=6 / followup_task=17 / knowledge_binding=1 / execution=30
企业：kb=1 / doc=1 / chunk=2
资源：内存可用 3071MB / 磁盘可用 27G
```

## 5. 代码改动

| 文件 | 改动 | 为什么 |
|------|------|--------|
| `apps/enterprise-knowledge-agent/src/main/resources/application.yml` | 新增 `spring.flyway.out-of-order: ${SPRING_FLYWAY_OUT_OF_ORDER:false}` | 云端 V7 先于 V6 应用，补 V6 必须允许乱序；`.env` 是 properties 源、不做 relaxed binding，只能占位符（9.1） |

没有改 Java 代码、没有新增依赖；`mvn test` 全绿（924）。

## 6. 新增/修改的脚本与文档

| 文件 | 用途 |
|------|------|
| `deploy/app/cloud-week26-backup.sh` | 变更前两库 + 产物 + 配置备份（不打印密钥） |
| `deploy/app/cloud-week26-pgvector.sh` | pg16 容器换 pgvector 镜像（失败自动回滚旧镜像） |
| `deploy/app/cloud-week26-env.sh` | 两侧令牌/知识库/Flyway 配置对齐（幂等，只打印指纹） |
| `deploy/app/cloud-week26-seed-knowledge.sh` | 企业侧演示知识库 + 文档（幂等，走真实 embedding） |
| `deploy/app/cloud-week26-regression.sh` | 三 Agent + 404/429 回归 |
| `deploy/app/cloud-week26-drill.sh` | TD-04 容器版回滚演练（宿主机无 psql） |
| `deploy/app/cloud-week26-ttl-backfill.sh` | TD-05 回填 + 前后计数 |
| `deploy/app/cloud-accept-week24.sh` | 修 2 处脚本缺陷（9.2/9.3） |
| `deploy/app/cloud-accept-week25.sh` | enterprise 健康判据 + 指标抓取（9.3/9.4） |
| `apps/enterprise-knowledge-agent/src/main/resources/application.yml` | out-of-order 占位符 |

## 7. 质量门禁

- [x] 本地根聚合 **924** 用例全绿（`BUILD SUCCESS`，`logs/week-26/test/target-week26-final.log`）
- [x] JDK：未改 `JAVA_HOME`，只用 `-Djdk.17.home`
- [x] 迁移：V6/V8/V9 真库应用；重启只校验；历史表无跳过（企业库 V6 在 V7 之后，已记录）
- [x] 验收：week24 **PASS=23 FAIL=0**；week25 **PASS=13 FAIL=0**；回归 **PASS=8 FAIL=0**
- [x] 演练：两库恢复成功，生产行数不变，RTO=2s
- [x] 安全：令牌只写服务器 `.env`（600）；日志只记录 sha256 前 12 位；仓库无密钥
- [x] 分层：本周未改 Java 分层
- [x] 文档：Spec/运维手册/本日志/README/索引同步

## 8. 验证证据（摘录）

### 8.1 pgvector 与 V6

```
pgvector: vector|0.8.7（pg_available_extensions）
document_chunk: id/document_id/knowledge_base_id/chunk_index/content/embedding(vector)/created_at
索引: document_chunk_pkey, idx_document_chunk_embedding(hnsw), idx_document_chunk_knowledge_base_id

[13:09:02] Successfully validated 7 migrations
[13:09:02] Migrating schema "public" to version "6 - document chunk pgvector" [out of order]
[13:09:02] Successfully applied 1 migration to schema "public", now at version v6
[13:11:18] Successfully validated 7 migrations   ← 移除 out-of-order 后重启
```

### 8.2 V8/V9

```
[13:10:50] Migrating schema "public" to version "8 - platform followup task"
[13:10:50] Migrating schema "public" to version "9 - platform knowledge binding"
[13:10:50] Successfully applied 2 migrations to schema "public", now at version v9
```

### 8.3 week24 验收

```
PASS  运营日报 COMPLETED / reportType=DAILY / summary 字段齐全
PASS  随访计划 COMPLETED（count=2）/ 重复完成 409
PASS  P002 生成待审 → 批准成功 → 执行 COMPLETED → 写入记忆 → 生成 3 条随访任务 → 重复决策 409
PASS  approval_pending_count 已暴露（0.0）/ agent_run_count_total 仍在
=== 结果：PASS=23 FAIL=0 ===
```

### 8.4 week25 验收（知识库问答）

```
PASS  enterprise 已在监听（auth/me 401）
PASS  无令牌 403
PASS  导入 200（imported/updated 合计=1，enterpriseUserId=4）
PASS  operator 可见绑定 total=1（knowledgeBaseId=1）
PASS  问答 COMPLETED（hitCount=2 degraded=False）/ 返回 citations
PASS  未绑定知识库 404 / viewer 403
PASS  tool.call 指标存在 / knowledge-qa 指标已出现
=== 结果：PASS=13 FAIL=0 ===
```

### 8.5 三 Agent 回归

```
patient-analysis COMPLETED（degraded=False）stepCount=5
followup COMPLETED（tasks=2）
knowledge-qa COMPLETED（hits=2 citations=2 degraded=False）
未绑定知识库 404；登录第 6 次 429
=== 结果：PASS=8 FAIL=0 ===
```

### 8.6 TD-04 / TD-05

```
[drill] platform: platform_user=4 platform_role=6 execution_record=15 approval=3 memory=5
[drill] prod platform_user after=4；PASS；RTO=2s
[drill] enterprise: app_user=1；PASS；RTO=2s

[ttl] before total=9 null=5 → UPDATE 5 → after null=0 with_ttl=9
```

## 9. 真机缺陷与修复（本周价值最高的部分）

### 9.1 `.env` 里的大写下划线键不会被 relaxed binding

- **现象**：第一次发布 enterprise，启动即 `Validate failed: Detected resolved migration not applied to database: 6`，
  90s 健康检查失败后自动回滚；**回滚也起不来**（见 9.2）。
- **根因**：`.env` 由 `spring.config.import: optional:file:.env[.properties]` 作为 **properties 源**加载，
  键是字面量；Spring Boot 的 relaxed binding 只对真实环境变量生效。`SPRING_FLYWAY_LOCATIONS` 之所以一直有效，
  是因为 yml 里写的是占位符 `${SPRING_FLYWAY_LOCATIONS:...}`（占位符按名字查找），
  而 `out-of-order` 没有占位符，写 `SPRING_FLYWAY_OUT_OF_ORDER=true` 完全无效。
- **修复**：yml 加 `out-of-order: ${SPRING_FLYWAY_OUT_OF_ORDER:false}`，默认 false 行为不变。
- **教训**：凡是想从 `.env` 覆盖的 `spring.*` 属性，yml 必须写成占位符；只写环境变量风格键是自欺欺人。

### 9.2 回滚只回滚 jar，不回滚 `.env`——两次都起不来

- **现象**：deploy 失败后 `remote-deploy.sh` 切回 `app.jar.prev`，健康检查仍失败。
- **根因**：本次失败由 `.env`（Flyway locations 两目录）触发。旧 jar 里也有 `db/postgresql/V6` 文件，
  只要 locations 包含该目录，旧 jar 同样会因「V6 未应用」校验失败。**环境变量与 jar 是两个独立版本面**。
- **修复/约定**：靠 `.env` 开关才能启动的变更，回滚必须**同时回滚 `.env`**（本周手工恢复单目录后 7×2s 恢复健康）。
  运维手册已把「回滚 .env」写成显式步骤。

### 9.3 `set -o pipefail` + `grep -q` 把「指标存在」判成缺失

- **现象**：week24 验收第 5 节两条始终 FAIL，但手工 `curl | grep` 能查到指标。
- **根因**：`printf '%s' "$METRICS" | grep -q '^...'`，prometheus 响应约 275KB > 管道缓冲 64KB；
  `grep -q` 命中即退出 → printf 收到 SIGPIPE(141) → `pipefail` 让整条管道返回 141 → `&&` 判失败。
  小响应（审批输出、citations）不会触发，所以只有大响应这两条中招。
- **修复**：先落临时文件，再 `grep -q 文件`（无管道）。week25 的指标段同样修掉。
- **教训**：验收脚本里凡是「大输出 + grep -q + pipefail」的组合都是定时炸弹。

### 9.4 Python repr 二次解析（week24 随访任务数）

- **现象**：`随访任务数=` 为空 FAIL，但库里有 3 条任务、日志 `followUpTasks=3`。
- **根因**：`field` 输出的是 Python repr（单引号），又喂给 `json.load` → JSONDecodeError 被 `2>/dev/null` 吞掉。
- **修复**：直接从原始响应体解析 `len(d['data'].get('followUpTaskIds') or [])`。

### 9.5 enterprise 没有 actuator，验收却要求 `/actuator/health=200`

- **现象**：week25 脚本第 1 节必 FAIL（接口实际返回 500）。
- **修复**：与 `remote-deploy.sh` 同口径，用 `/api/v1/auth/me` 的 401 判定进程在监听。
  不为了验收脚本给 enterprise 引 actuator（YAGNI）。

### 9.6 换镜像后 glibc 版本不一致，模板库没刷新导致建库失败

- **现象**：`postgres:16`（Debian 13，glibc 2.41）→ `pgvector/pgvector:pg16`（Debian 12，glibc 2.36）后，
  每次连接告警 `collation version mismatch`；刷了 4 个业务库后，TD-04 演练 `CREATE DATABASE` 报
  `template database "template1" has a collation version mismatch`。
- **修复**：所有库 `ALTER DATABASE ... REFRESH COLLATION VERSION` + `REINDEX DATABASE`，
  **补刷 `template1`**（`template0` 无版本记录，刷新报 `invalid collation version change` 可忽略）；
  建库冒烟通过。
- **教训**：collation 是**集群级**属性，换基座镜像时业务库、模板库都要处理。

### 9.7 宿主机没有 PostgreSQL 客户端，TD-04 脚本跑不了

- **现象**：`rollback-drill.sh` 依赖宿主机 `psql/pg_restore`，`which` 全空。
- **修复**：新增 `cloud-week26-drill.sh`：`docker cp` dump 进容器 → 容器内 `pg_restore` → 容器内计数校验。
  同时修了脚本自身两个问题：`set -e` 下裸 `[ ... ] &&` 会提前退出；`to_regclass` 与缺失表同语句查询会直接报错。
- **教训**：运维脚本要么声明依赖，要么自带环境探测。

### 9.8 巡检脚本把数据库密码打进了日志（已处理）

- `docker inspect pg16` 的 env 输出未脱敏，密码一度落进 `logs/week-26/migration/target-week26-inspect.log`。
- 已在本机日志中替换为 `***REDACTED***`；该日志被 `.gitignore` 忽略、未入库。
- 后续所有巡检脚本只打印键名（`sed 's/=.*/=<set>/'`）。**建议下一个运维窗口轮换该库密码**（需同步两模块 `.env` 并重启）。

## 10. 风险与下周输入

| # | 项 | 说明 |
|---|----|------|
| 1 | 知识库绑定是快照 | 企业侧改名/删库不同步 → 第 27 周对账任务（P1 #1） |
| 2 | 租户内知识库全可见 | 科室隔离需 `platform_role_knowledge`（P1 #2） |
| 3 | 旧记忆 TTL 统一 90 天 | 历史医疗记忆将到期，需合规确认（P1 #3 之前先定策略） |
| 4 | PG 基座 Debian 12 | 后续升级沿用「同主版本 + 刷新 collation（含 template1）」流程 |
| 5 | 数据库密码曾入日志 | 建议轮换；轮换顺序：先改 `.env` → 重启两侧 → `ALTER ROLE`（或反之按手册） |
| 6 | 验收脚本与生产脚本同仓 | 本周 4 个脚本缺陷都只在真机暴露，后续新脚本先「空跑 + 大响应」自测 |

## 11. ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| pgvector 安装 | 宿主机 apt / 容器内编译 / 官方镜像 | **官方镜像** | 容器内无工具链；apt 对容器无效；同主版本换镜像最省事 |
| 排序规则 | 忽略告警 / 重建镜像 / REFRESH+REINDEX | **刷新+重建** | 官方 HINT；小库秒级；模板库一起刷 |
| V6 补跑 | 手工 SQL + 插历史行 / 允许乱序 | **允许乱序** | 走 Flyway 正常路径，checksum 与历史可信 |
| 乱序开关暴露 | 环境变量 / yml 占位符 | **yml 占位符** | `.env` 是 properties 源，不做 relaxed binding |
| 发布顺序 | 平台先 / enterprise 先 | **enterprise 先** | 平台的检索工具依赖 enterprise 内部端点；令牌先企业后平台 |
| TTL 回填 | 不回填 / 统一 90 天 / 按类型 | **统一 90 天（本周）** | 与现有默认 TTL 一致；后续按合规细化 |
| 演练方式 | 宿主机客户端 / 容器内执行 | **容器内** | 宿主机无客户端；不引入新依赖 |
