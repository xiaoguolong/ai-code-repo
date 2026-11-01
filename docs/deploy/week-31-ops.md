# Week 31 运维手册 — SSE 流式发布与回滚

> 面向云主机（已按 Week 29 部署 nginx + 平台 systemd）。所有命令在云主机 root 下执行。

## 1. 发布前置

| 文件 | 说明 |
|------|------|
| `/tmp/app.jar.new` | 平台新 jar（含 Week 31） |
| `/tmp/web.tar.gz` | 前端 `dist` 内容 |
| `/tmp/aicode.conf` | 含 SSE location 的 nginx 站点配置 |

上传后先做一次备份（脚本第 1 步会自动做，也可手动）：

```bash
cp -a /opt/ai-code/app/platform/.env /opt/ai-code/backup/platform.env.$(date +%F)
```

## 2. 发布

```bash
sudo bash deploy/app/cloud-week31.sh
```

脚本顺序：备份（web/jar/.env）→ `remote-deploy.sh platform`（切换 jar、90s 健康检查、
失败自动回滚）→ 静态包（保留 `web.prev`）→ 安装 nginx 配置并 reload → 本机入口检查。

> `cloud-week31.sh` 依赖同目录的 `remote-deploy.sh`，两者一起上传。

## 2.1 模型通道（重要）

流式依赖 `FRAMEWORK_MODEL_PROVIDER=openai-compatible`（ai-core 原生 SSE reader）。
切回 `spring-ai` 会回到"整段响应到达后才下发 chunk"的行为（首字=总耗时），不要仅为省事切回。
`.env` 里该键由 Week 31 发布时写入，备份在 `/tmp/platform.env.w31-provider`（临时）。

## 3. 验收

```bash
sudo bash deploy/app/cloud-accept-week31.sh
```

硬指标：

| # | 检查 | 期望 |
|---|------|------|
| 1 | knowledge-qa `meta` | < 300ms |
| 2 | knowledge-qa 首字 | < 2000ms |
| 3 | knowledge-qa `final` | COMPLETED 且 token > 0 |
| 4 | patient-analysis 首字 | < 5000ms，final PENDING_APPROVAL |
| 5 | 走 nginx 首字 | < 2500ms（未被缓冲） |
| 6 | 停止生成 | 记录 `CANCELLED` |
| 7 | 近 10 分钟 RUNNING | 0 |
| 8 | `agent_stream_first_delta_seconds_count` | 已导出 |
| 9 | 同步端点 | medical-assistant 200（回归） |

手动抽查：

```bash
curl -N -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream, application/json' \
  -d '{"input":{"knowledgeBaseId":1,"question":"糖尿病患者的随访频率是多少？"}}' \
  http://127.0.0.1/api/v1/platform/agents/knowledge-qa/runs/stream
```

## 4. 回滚

| 方式 | 操作 | 生效时间 |
|------|------|----------|
| **只关流式（首选）** | `.env` 加 `PLATFORM_STREAM_ENABLED=false` → `systemctl restart ai-code-platform` | 分钟级；前端自动回退同步 |
| 回滚 jar | `sudo bash deploy/app/remote-deploy.sh platform --rollback` | 分钟级 |
| 回滚静态 | `rm -rf /opt/ai-code/web && mv /opt/ai-code/web.prev /opt/ai-code/web` + reload nginx | 秒级 |
| 回滚 nginx | 从备份取上一版 `aicode.conf`，`nginx -t && systemctl reload nginx` | 秒级 |

无数据库迁移：`CANCELLED` 只是枚举值，`execution_record.status` 为 `VARCHAR(24)`。

## 5. 排障

| 现象 | 排查 |
|------|------|
| 首字≈总耗时 | nginx 是否命中 SSE location？`grep -n 'runs/stream' /etc/nginx/sites-enabled/aicode`；响应头是否有 `X-Accel-Buffering: no` |
| 首字出现但无 final | 客户端是否提前断开；看平台日志 `sse client gone` / `execution cancelled` |
| 429 | 并发满（默认 4）：等或调 `PLATFORM_STREAM_MAX_CONCURRENT` |
| 503 STREAM_DISABLED | `.env` 开关被关；前端应已回退同步 |
| token 为 0 | 厂商未返回末包 usage：确认 `spring.ai.openai.chat.options.stream-usage=true`，看请求体是否带 `stream_options.include_usage` |
| 记录 CANCELLED 但用户说没点停止 | 检查是否代理/网关超时（300s）或心跳被掐；看 `agent.stream.count{stream.result="cancelled"}` |
| 日志 traceId 对不上 | worker 是否恢复了 MDC；`agent.run` span 的 traceId 应与 `meta.traceId` 一致 |
