# 云端内存调优记录（2026-10-25）

> 背景：4C8G 云主机同时跑业务 + Langfuse + SkyWalking + Prometheus/Grafana，
> 可用内存一度降到 1.0–1.7 GB。本次按「业务不动、先停非业务观测」原则处理。

## 1. 调整前

`free -m`：

```text
total 7619 MB / used 5504 MB / free 174 MB / buff/cache 1940 MB / available 1641 MB
swap 4095 MB / used 18 MB
```

主要占用：

| 组件 | 内存 |
|------|------|
| `langfuse-web` | 1045 MiB |
| `skywalking-oap` | 1061 MiB（`-Xmx1024M`，无 cgroup 上限） |
| `langfuse-worker` | 532 MiB |
| platform / enterprise / gateway | 约 519 / 478 / 462 MiB |
| `clickhouse`（Langfuse） | 288 MiB / 800 MiB 上限 |
| `skywalking-ui` | 277 MiB |
| `pg16` / `redis72` | 237 / 9 MiB |

## 2. 本次动作

| # | 动作 | 说明 |
|---|------|------|
| 1 | 停 `langfuse-web` + `langfuse-worker` | 数据卷保留；`docker compose start` 可恢复；ClickHouse/PG/MinIO/Redis 仍在 |
| 2 | OAP `-Xmx1024M → 768M`，UI `-Xmx256M → 192M` | 改 `/opt/ai-code/skywalking/.env` 后 `docker compose up -d oap ui` |
| 3 | platform `LANGFUSE_ENABLED=false` 并重启 | Langfuse web 已停，避免 OTLP 导出每 8–15 秒重试报错 |
| 4 | 业务 JVM 参数未动 | platform / gateway / enterprise 保持原堆；pg16/redis72 未动 |

脚本：

- `deploy/app/cloud-memory-tune.sh`（停 Langfuse + 降 OAP/UI，失败自动回滚）
- `deploy/app/cloud-memory-tune-langfuse-off.sh`（关 platform Langfuse 导出并重启）

## 3. 调整后

`free -m`（platform 重启稳定后）：

```text
total 7619 MB / used 3792 MB / free 1863 MB / buff/cache 1963 MB / available 3349 MB
swap 4095 MB / used 15 MB
```

| 指标 | 调整前 | 调整后 |
|------|--------|--------|
| available | 1641 MB | **3349 MB** |
| used | 5504 MB | 3792 MB |
| `langfuse-web` | 1045 MiB | 已停（Exited 143） |
| `langfuse-worker` | 532 MiB | 已停（Exited 137） |
| `skywalking-oap` | 1061 MiB / 无上限 | 1045 MiB / `-Xmx768M`（RSS 含非堆，仍约 1 GB） |
| `skywalking-ui` | 277 MiB | 185 MiB / `-Xmx192M` |

业务健康：

```text
ai-code-platform active / platform health 200
ai-code-gateway active / gateway health 200
ai-code-enterprise active / enterprise /auth/me 401（正常）
skywalking-ui 200
```

> 说明：OAP 的 cgroup `MemUsage` 仍约 1.0 GB，因为 JVM 非堆（metaspace/code cache/direct/native）
> 也计入；`-Xmx768M` 限制的是堆上限，主要收益在峰值不再按 1 GB 堆继续增长。
> 若还要再省，下一步是停 OAP/UI（约 1.2 GB）或把 OAP 降到 512M（需压测，可能影响查询）。

## 4. 如何恢复 Langfuse

```bash
# 1) 恢复 platform 导出开关
sudo sed -i 's/^LANGFUSE_ENABLED=.*/LANGFUSE_ENABLED=true/' /opt/ai-code/app/platform/.env
sudo systemctl restart ai-code-platform

# 2) 启动 Langfuse web/worker（数据卷不变）
cd /opt/ai-code/langfuse
docker compose start langfuse-web langfuse-worker
docker compose ps
```

> 如果只想临时看历史 trace，不必启 worker；但新的 OTLP 导入/异步处理需要 web + worker。
> 长期不启用时保持 `LANGFUSE_ENABLED=false`，避免无效重试。

## 5. 如何恢复 SkyWalking 原堆参数

本次修改前的 `.env` 备份：

```text
/opt/ai-code/skywalking/.env.bak.20261025-083800
```

恢复：

```bash
cd /opt/ai-code/skywalking
cp .env.bak.20261025-083800 .env
docker compose up -d oap ui
```

或手工改回：

```text
OAP_JAVA_OPTS=-Xms256M -Xmx1024M
UI_JAVA_OPTS=-Xms64M -Xmx256M
```

## 6. 后续建议

1. 日常保持当前配置：业务 + SkyWalking + Prometheus/Grafana，Langfuse 按需启动；
2. 若还要再释放内存，优先停 `skywalking-oap` + `skywalking-ui`（约 1.2 GB），而不是继续压业务 JVM；
3. 如果必须同时保留 Langfuse，建议把 OAP 降到 512M 并压测；不要把 `langfuse-web` 压到 1G 以下（Week 18 已踩 V8 OOM）；
4. 变更后用 `free -m` + `docker stats --no-stream` 复测，并确认 business health 200。
