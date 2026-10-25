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

> **现状提醒（2026-10-25）**：云端 enterprise 库在 Week 22 **跳过了 V6**（当时 PG 无 pgvector），
> 所以第 3、4 项现在大概率不满足。按第 2 节装扩展后必须**补跑 V6**，否则检索会在执行 SQL 时报
> `type "vector" does not exist`，平台侧表现为 502。

## 2. 启用 pgvector（一次性）

```bash
# 1) 安装扩展（Ubuntu 22.04 + PostgreSQL 16 示例；以实际镜像/包名为准）
sudo apt-get update
sudo apt-get install -y postgresql-16-pgvector
# 若 apt 源没有该包，使用官方 pgvector 编译安装（需要 postgresql-server-dev-16）

# 2) 在 enterprise 库启用扩展
psql -h 127.0.0.1 -U <db-user> -d <enterprise-db> -c "CREATE EXTENSION IF NOT EXISTS vector;"

# 3) 补跑 V6（把 Flyway 位置恢复成默认两目录后重启 enterprise）
#    .env: 删除或注释 SPRING_FLYWAY_LOCATIONS（默认 classpath:db/migration,classpath:db/postgresql）
systemctl restart aicode-enterprise
journalctl -u aicode-enterprise -n 100 | grep -E "Migrating|Successfully applied"

# 4) 校验
psql -h 127.0.0.1 -U <db-user> -d <enterprise-db> -c "\d document_chunk"
```

**回滚**：V6 只新增表与索引，回滚方式是停服务 → `DROP TABLE document_chunk` → 恢复
`SPRING_FLYWAY_LOCATIONS=classpath:db/migration` → 重启；同时 Flyway 历史表中删除 V6 行
（仅在明确要重跑时）。生产环境请先备份。

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
bash /opt/ai-code/app/backup-before-deploy.sh week25

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
| 问答返回 502 | enterprise 不可用、超时（默认 5s）、pgvector 缺失 | `journalctl -u aicode-enterprise` 看 SQL 错误；装扩展补 V6 |
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
