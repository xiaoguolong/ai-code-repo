# Week 32 运维：企业侧 PDF/OCR 入库发布与回滚

## 1. 发布（企业服务 8081）

```powershell
# 1) 构建
.\run-maven-jdk17.ps1 -pl apps/enterprise-knowledge-agent -am -DskipTests package
# 2) 上传
.\remote.ps1 -Put "apps\enterprise-knowledge-agent\target\enterprise-knowledge-agent-0.1.0-SNAPSHOT.jar /tmp/enterprise.jar.new"
# 3) 部署（备份 app.jar.prev → 重启 → 等 /auth/me 401）
.\remote.ps1 -Script .\deploy\app\cloud-week32.sh
```

脚本会打印 Flyway 迁移行；首次发布应看到：

```
Migrating schema "public" to version "8 - ingest job"
Successfully applied 1 migration to schema "public", now at version v8
```

## 1.5 前端发布（企业页新 UI）

```powershell
# 1) 构建（web-ci 会顺带 typecheck/test/build）
.\tools\web-ci.ps1
# 2) 打包 dist（内容根目录即 index.html）
Push-Location apps\web\dist; tar -czf "$env:TEMP\web-w32.tar.gz" .; Pop-Location
# 3) 上传 + 只替换 /opt/ai-code/web（不动 nginx 配置）
.\remote.ps1 -Put "$env:TEMP\web-w32.tar.gz /tmp/web.tar.gz"
.\remote.ps1 -Script .\deploy\app\cloud-week32-web.sh
```

校验：`curl http://118.195.208.210/assets/EnterpriseKnowledgeView-<hash>.js` 返回 200，
且 bundle 内含 "PDF/OCR 入库作业"。用户端需 **Ctrl+F5** 强刷（旧 chunk 有缓存）。

回滚：`rm -rf /opt/ai-code/web && mv /opt/ai-code/web.prev /opt/ai-code/web`。

## 2. 回滚

```bash
# 云主机上
sudo bash /tmp/remote-deploy.sh enterprise --rollback
```

`--rollback` 切回 `app.jar.prev` 并重启；**数据库不回滚**（V8 只新增两张表，
旧版本忽略它们，回滚安全；若要彻底清理见 §5）。

## 3. 真机验收

```powershell
# 前置：20 页 PDF 上传到 /tmp/w32-guide-20p.pdf
.\remote.ps1 -Put "$env:TEMP\w32-guide-20p.pdf /tmp/w32-guide-20p.pdf"
.\remote.ps1 -Script .\deploy\app\cloud-accept-week32.sh
```

预期输出：`status= SUCCEEDED pages= 10 chunks= 40`、6 个 step 全 SUCCEEDED、
clean 有 `promptLen=219 ptok=3948 ctok=7120`、`duplicate=True same=True`、`RESULT PASS`。

> 千帆单次 PDF 解析上限 **10 页**；20 页文件当前以 10 页入库（分批 OCR 列 P1）。
> `ingest.max-pages=200` 只做上限保护，不改变厂商行为。

## 4. 运行配置（`.env`，改完重启）

| 键 | 默认 | 说明 |
|----|------|------|
| `INGEST_CLEAN_ENABLED` | `true` | `false` 跳过 LLM 清洗（OCR 原文直接切片，省 token） |
| `INGEST_MAX_PAGES` | `200` | OCR 页数上限 |
| `INGEST_WORKER_COUNT` | `2` | 异步线程数 |
| `INGEST_QUEUE_CAPACITY` | `64` | 排队上限，满返回 429 |
| `CHAT_MAX_TOKENS` | `1024` | clean 分段 1500 字符即按此上限控制，勿随意下调 |

## 5. 数据与清理

- 表：`ingest_job`、`ingest_job_step`（`prompt_text` 永久保留）；
- 查最近作业：`SELECT job_id,status,page_count,chunk_count,created_at FROM ingest_job ORDER BY created_at DESC LIMIT 10;`
- 彻底清理某次入库（文档/向量需另按 documentId 删）：

```sql
DELETE FROM ingest_job WHERE job_id = '<job-id>';  -- step 级联删除
```

## 6. 已知限制

1. 千帆单次 10 页（分批 OCR P1）；
2. 进程重启遗留 `RUNNING` 不会自动转 FAILED（P1）；
3. 企业侧无 actuator，节点耗时只在 DB/页面，无 Prometheus 指标（W33）；
4. prompt 原文含患者信息：仅 owner/admin 可见，页面默认折叠，不写日志。
