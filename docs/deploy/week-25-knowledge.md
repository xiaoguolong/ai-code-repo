# Week 25 部署手册：知识库 Agent 3（包装 enterprise RAG）

> 两件事必须一起做才算上线：**内部令牌对齐**（平台 ↔ enterprise）与 **pgvector 就绪**。
> 任一件没做，知识库问答都会显式失败（502），不会静默降级成「没有相关内容」——这是刻意设计。

## 1. 前置检查

| 项 | 命令 / 判断 | 期望 |
|----|-------------|------|
| enterprise 在线 | `curl -fsS 127.0.0.1:8081/actuator/health` | `UP` |
| 平台在线 | `curl -fsS 127.0.0.1:8084/actuator/health` | `UP` |
| pgvector 扩展 | `psql -d <enterprise-db> -c "SELECT extname FROM pg_extension WHERE extname='vector';"` | 有 `vector` 行 |
| `document_chunk` | `psql -d <enterprise-db> -c "\d document_chunk"` | 表存在（含 hnsw 索引） |
| enterprise Flyway | `SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank;` | 含 `6`（或历史表名以实际配置为准） |

> **现状（2026-10-25，第 26 周收口后）**：上面 5 项均已满足——pgvector 0.8.7 已启用、V6 已乱序补跑、
> `document_chunk` 与 hnsw 索引存在、enterprise Flyway 为 `1,2,3,4,5,7,6`、两侧令牌已对齐。
> 历史背景：Week 22 因 PG 没有 pgvector 跳过 V6，当时检索会显式 502；处理过程见
> [`week-26-closeout.md`](week-26-closeout.md)。

## 2. 启用 pgvector（一次性）

> **2026-10-25 已在云端执行**，实际做法与本节的 apt 示例不同：容器里的 `postgres:16` 没有 pgvector 文件，
> 也没有编译工具链，因此改用**官方 pgvector 镜像 + 同一数据卷**。完整步骤与坑见
> [`week-26-closeout.md`](week-26-closeout.md) 第 2 节。要点：

```bash
# 1) 拉官方镜像（同 PG16 主版本，数据目录兼容）
docker pull pgvector/pgvector:pg16

# 2) 记录旧容器形态 → 提取 env 到 /root（600）→ 删容器（保留卷 data_pg_data）
#    再以同名/同网络/同端口/同 env 起新镜像：见 deploy/app/cloud-week26-pgvector.sh

# 3) 换基座后刷新排序规则（旧镜像 Debian 13 / 新镜像 Debian 12，glibc 不一致）
#    业务库 + postgres + template1 都要刷，业务库再 REINDEX；最后建库冒烟
#    漏刷 template1 会让 CREATE DATABASE 直接失败（第 26 周真机踩过）

# 4) 启用扩展（企业库）
docker exec pg16 psql -U <db-user> -d <enterprise-db> -c "CREATE EXTENSION IF NOT EXISTS vector;"
docker exec pg16 psql -U <db-user> -d <enterprise-db> -tAc \
  "select extname||' '||extversion from pg_extension where extname='vector';"   # 期望 vector 0.8.7

# 5) 恢复 Flyway 两目录并允许乱序补 V6（V7 已先行应用）
#    enterprise .env:
#      SPRING_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/postgresql
#      SPRING_FLYWAY_OUT_OF_ORDER=true
#    注意：yml 必须写 out-of-order: ${SPRING_FLYWAY_OUT_OF_ORDER:false}，
#    .env 是 properties 源，不做 relaxed binding（第 26 周真机缺陷）
systemctl restart ai-code-enterprise
journalctl -u ai-code-enterprise -n 100 | grep -E "Migrating|Successfully applied"
# 期望：Migrating ... to version "6 - document chunk pgvector" [out of order] → now at version v6

# 6) 补完立即删除 SPRING_FLYWAY_OUT_OF_ORDER 并重启，确认只 validated 7 migrations
```

**回滚**：停服务 → `DROP TABLE document_chunk` → 恢复 `SPRING_FLYWAY_LOCATIONS=classpath:db/migration`
→ 重启；同时 Flyway 历史表中删除 V6 行（仅在明确要重跑时）。生产环境请先备份。
容器基座回滚：`docker run` 回 `postgres:16`（同卷），见 `cloud-week26-pgvector.sh` 的内置回退。

**过渡方案（仅演示）**：不装扩展时把 enterprise 的 `RAG_VECTOR_STORE` 设为 `memory`。
内存向量**重启即丢**、不可用于生产；它只用于「先联调平台链路」。

## 3. 内部令牌（平台 ↔ enterprise）

两侧必须是**同一个随机串**，且只写入服务器 `.env`（不入库、不进日志）：

```bash
openssl rand -hex 32        # 生成一次
# enterprise .env
ENTERPRISE_INTERNAL_TOKEN=<同一个值>
# 平台 .env
PLATFORM_KNOWLEDGE_INTERNAL_TOKEN=<同一个值>
PLATFORM_KNOWLEDGE_BASE_URL=http://127.0.0.1:8081
```

轮换流程：先在 enterprise 更新并重启 → 再更新平台并重启（顺序反了会有窗口期 502）。

## 4. 发布顺序

```bash
# 1) 备份（沿用第 24 周脚本）
bash /opt/ai-code/app/cloud-week26-backup.sh

# 2) enterprise（V6 若已补跑，这里只切 jar）
bash /opt/ai-code/app/remote-deploy.sh enterprise

# 3) 平台（V9 自动迁移）
bash /opt/ai-code/app/remote-deploy.sh platform

# 4) 迁移校验
journalctl -u aicode-platform -n 120 | grep -E "Migrating|now at version v9|Successfully validated"
```

## 5. 导入知识库并验收

```bash
# 1) 管理员登录拿 token（示例）
TOKEN=$(curl -s -X POST 127.0.0.1:8084/api/v1/platform/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['data']['token'])")

# 2) 导入（需要管理员已建 SSO 映射，且企业侧有知识库）
curl -s -X POST 127.0.0.1:8084/api/v1/platform/knowledge-bindings/import \
  -H "Authorization: Bearer $TOKEN"

# 3) 看绑定
curl -s 127.0.0.1:8084/api/v1/platform/knowledge-bindings \
  -H "Authorization: Bearer $TOKEN"

# 4) 问答（用上一步返回的 knowledgeBaseId）
curl -s -X POST 127.0.0.1:8084/api/v1/platform/agents/knowledge-qa/runs \
  -H "Authorization: Bearer $OPERATOR_TOKEN" -H 'Content-Type: application/json' \
  -d '{"input":{"knowledgeBaseId":11,"question":"糖尿病随访频率？"}}'
```

一键验收：`bash /opt/ai-code/app/cloud-accept-week25.sh`（失败项逐条打印 PASS/FAIL）。

## 6. 故障排查

| 现象 | 最可能原因 | 处理 |
|------|------------|------|
| 导入返回 502 `TOOL_EXECUTION_ERROR` | enterprise 令牌未配置/不一致；或管理员未建 SSO 映射 | 对齐 `*_INTERNAL_TOKEN`；先完成 SSO 建链 |
| 问答返回 502 | enterprise 不可用、超时（默认 5s）、pgvector 缺失 | `journalctl -u ai-code-enterprise` 看 SQL 错误；装扩展补 V6（见 `week-26-closeout.md`） |
| 问答 404 | 绑定被删/停用，或 `knowledgeBaseId` 不是本租户的 | `GET /knowledge-bindings` 核对 |
| 问答 200 但 `hitCount=0` | 文档未入库/切片为空/问题与文档无关 | 企业侧确认 `document_chunk` 行数；换问法 |
| 问答 200 且 `degraded=true` | 模型不可用（平台 LLM Key/网络） | 查平台 `llm` 配置与模型健康 |
| 导入返回 200 但 `bases=[]` | 该管理员企业账号下没有知识库 | 企业侧先建库/传文档 |

## 7. 回滚

| 场景 | 动作 |
|------|------|
| 平台新版本异常 | `remote-deploy.sh platform` 自动回滚 jar；V9 表保留（旧代码不读） |
| 令牌配错导致全量 502 | 回滚 `.env` 到上一版并重启两侧 |
| pgvector 安装导致 enterprise 起不来 | 恢复 `SPRING_FLYWAY_LOCATIONS=classpath:db/migration`，停用知识库（`PLATFORM_KNOWLEDGE_ENABLED=false`） |
| 需要暂时关闭知识库 | 平台 `.env` 设 `PLATFORM_KNOWLEDGE_ENABLED=false` 并重启：工具调用直接 502，其它 Agent 不受影响 |
