# 云端运维手册（重启 / 内存优化 / 观测开关）

> 适用主机：腾讯云 4C8G（Ubuntu 22.04，`/opt/ai-code`），2026-10-25 内存调优后的基线。
> 配套记录：[内存调优详情](cloud-memory-tuning.md) ｜ [日志索引](../logs-index.md) ｜ [Week 22 应用部署](week-22-app-deploy.md) ｜ [V6](week-23-migration.md) / [V7](week-23-batch2.md) 迁移。
>
> 原则：**业务优先**（platform / gateway / enterprise / pg16 / redis72），观测与 Langfuse 是可选负载；
> 任何内存操作先看 `free -m` + `docker stats`，再决定停谁，不要先压业务 JVM。

## 1. 组件清单与优先级

### 1.1 业务（不要先停）

| 组件 | 形态 | 端口 | 数据/配置 |
|------|------|------|-----------|
| `ai-code-platform` | systemd + jar | 8084（回环） | `/opt/ai-code/app/platform/{app.jar,.env}` |
| `ai-code-gateway` | systemd + jar | 8080（回环） | `/opt/ai-code/app/gateway/{app.jar,.env}` |
| `ai-code-enterprise` | systemd + jar | 8081（回环） | `/opt/ai-code/app/enterprise/{app.jar,.env}` |
| `pg16` | Docker | 5432（回环） | `/opt/ai-code/data`，平台库 `aidemo` |
| `redis72` | Docker | 6379（回环） | 平台/网关会话与缓存 |

### 1.2 观测（可按需停）

| 组件 | 形态 | 端口 | 说明 |
|------|------|------|------|
| `skywalking-oap` | Docker | 11800 / 12800 | 链路/指标后端；停后 agent 会重连报错，业务不受影响 |
| `skywalking-ui` | Docker | 8088 | 只影响 UI；OAP 仍可采集 |
| `prometheus` | Docker host 网络 | 19090（回环） | 指标采集，约 60 MB，建议保留 |
| `grafana` | Docker host 网络 | 3001（回环） | 仪表盘 UI，约 58 MB，可停 |
| `langfuse-web` | Docker | 3000 | LLM 观测 UI + OTLP 入口；**当前已停** |
| `langfuse-worker` | Docker | 3030（回环） | 异步消费；**当前已停** |
| `langfuse-clickhouse` | Docker | 8123/9000（回环） | Langfuse 存储，288 MB/800 MB 上限 |
| `langfuse-postgres` | Docker | 15432（回环） | Langfuse 独立库 |
| `langfuse-minio` | Docker | 9090/9091（回环） | Langfuse 对象存储 |
| `langfuse-redis` | Docker | 内部 | Langfuse 队列 |

> 应用是 **systemd**，不在 `docker stats` 里；看不到 platform/gateway/enterprise 是正常的。

## 2. 当前内存基线（2026-10-25 08:53）

```text
free -m
              total   used   free  shared  buff/cache  available
Mem:           7619   3822   1830     167        1966       3320
Swap:          4095     15   4080
```

| 组件 | 当前 | 备注 |
|------|------|------|
| `langfuse-web` / `worker` | 已停 | 数据卷保留；`LANGFUSE_ENABLED=false` |
| `skywalking-oap` | 约 1.06 GB / `-Xmx768M` | RSS 含非堆，不能按堆大小估算 |
| `skywalking-ui` | 约 185 MB / `-Xmx192M` | |
| `langfuse-clickhouse` | 288 MB / 800 MB 上限 | 未停 |
| `prometheus` / `grafana` | 59 / 58 MB | 建议保留 |
| `pg16` / `redis72` | 241 / 9 MB | 不要动 |
| platform / enterprise / gateway | 约 0.5 / 0.48 / 0.46 GB | 当前堆 768 / 640 / 512 |

### 2.1 内存水位与动作

| available | 动作 |
|-----------|------|
| ≥ 2.5 GB | 正常，不动 |
| 1.5–2.5 GB | 先看 `docker stats`；优先停 `skywalking-ui`、`grafana` |
| 1.0–1.5 GB | 停 `skywalking-oap` + `ui`（约 1.25 GB） |
| < 1.0 GB | 再停 Langfuse 存储容器（clickhouse/minio/postgres/redis）或整栈；**不要先停 pg16/redis72/业务** |

> 停 OAP 后应用仍会尝试上报，日志会有连接失败；长期停就把启动参数里的
> `-javaagent` 去掉，或接受这些日志。

## 3. 启停顺序

### 3.1 整机/全栈启动（推荐顺序）

```bash
# 1) 数据层
docker start pg16 redis72
# 等 PostgreSQL ready
docker exec pg16 pg_isready -U aidemo -d aidemo

# 2) 业务
sudo systemctl start ai-code-platform ai-code-gateway ai-code-enterprise

# 3) 观测（按需）
cd /opt/ai-code/skywalking && docker compose up -d
cd /opt/ai-code/monitoring && docker compose up -d
# Langfuse 默认不启；需要时见第 5 节
```

### 3.2 全栈停止（维护/换机）

```bash
# 先停观测，再停业务，最后数据层（数据层通常不需要停）
cd /opt/ai-code/langfuse && docker compose stop
cd /opt/ai-code/skywalking && docker compose stop
cd /opt/ai-code/monitoring && docker compose stop

sudo systemctl stop ai-code-enterprise ai-code-gateway ai-code-platform
docker stop pg16 redis72
```

> **禁止** `docker compose down -v`：会删 Langfuse/SkyWalking 数据卷。
> 只停服务用 `stop`；要删容器用 `down`（不加 `-v`）。

### 3.3 日常单服务重启

```bash
# 应用
sudo systemctl restart ai-code-platform
sudo systemctl restart ai-code-gateway
sudo systemctl restart ai-code-enterprise

# SkyWalking
cd /opt/ai-code/skywalking
docker compose restart oap ui

# 监控
cd /opt/ai-code/monitoring
docker compose restart prometheus grafana
```

重启后验证：

```bash
curl -s -o /dev/null -w 'platform=%{http_code}\n' http://127.0.0.1:8084/actuator/health
curl -s -o /dev/null -w 'gateway=%{http_code}\n'  http://127.0.0.1:8080/actuator/health
curl -s -o /dev/null -w 'enterprise=%{http_code}\n' http://127.0.0.1:8081/api/v1/auth/me   # 401 正常
curl -s -o /dev/null -w 'sw_ui=%{http_code}\n' http://127.0.0.1:8088/
```

## 4. 内存优化参数

### 4.1 已应用（2026-10-25）

| 项 | 值 | 文件 |
|----|----|------|
| Langfuse web/worker | 停止（`Exited`） | `docker compose stop` |
| platform Langfuse 导出 | `LANGFUSE_ENABLED=false` | `/opt/ai-code/app/platform/.env` |
| OAP 堆 | `-Xms256M -Xmx768M` | `/opt/ai-code/skywalking/.env` |
| UI 堆 | `-Xms64M -Xmx192M` | 同上 |
| 业务 JVM | 未改：platform 768 / enterprise 640 / gateway 512 | systemd unit |

### 4.2 可选：继续降业务 JVM（在观测已停仍不够时）

| 应用 | 当前 | 建议 | 文件 |
|------|------|------|------|
| platform | `-Xms256m -Xmx768m -XX:MaxMetaspaceSize=256m` | `-Xms128m -Xmx640m -XX:MaxMetaspaceSize=192m` | `/etc/systemd/system/ai-code-platform.service` |
| enterprise | `-Xms256m -Xmx640m -XX:MaxMetaspaceSize=256m` | `-Xms128m -Xmx512m -XX:MaxMetaspaceSize=192m` | `/etc/systemd/system/ai-code-enterprise.service` |
| gateway | `-Xms192m -Xmx512m -XX:MaxMetaspaceSize=192m` | `-Xms96m -Xmx384m -XX:MaxMetaspaceSize=128m` | `/etc/systemd/system/ai-code-gateway.service` |

修改步骤（逐台改、逐台验证，不要一次全重启）：

```bash
sudo cp /etc/systemd/system/ai-code-platform.service{,.bak.$(date +%Y%m%d-%H%M%S)}
sudo sed -i 's/-Xms256m -Xmx768m -XX:MaxMetaspaceSize=256m/-Xms128m -Xmx640m -XX:MaxMetaspaceSize=192m/' \
  /etc/systemd/system/ai-code-platform.service
sudo systemctl daemon-reload
sudo systemctl restart ai-code-platform
systemctl cat ai-code-platform | grep ExecStart
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8084/actuator/health
```

回滚：把 `.bak.*` 覆盖回 unit → `daemon-reload` → `restart`。

> 业务 JVM 不要低于 platform 512 / enterprise 384 / gateway 320；再低容易在 RAG/多 Agent 时 OOM。
> 优先停观测，而不是压业务。

### 4.3 不建议做

- 把 `langfuse-web` 的 `mem_limit` 压到 1 GB 以下：Week 18 已踩 V8 heap OOM；
- 把 `langfuse-worker` 压到 500 MB 以下；
- 用 `drop_caches` 当长期方案（只短暂释放 page cache）；
- 靠加 swap 解决（当前 4 GB swap 只用 15 MB，只会把压力变成延迟）；
- 先停 pg16 / redis72 / 业务应用。

## 5. Langfuse 开关（当前默认关闭）

### 5.1 临时查看历史 trace

```bash
cd /opt/ai-code/langfuse
# 之前只停了 web/worker、依赖还在：直接 start
docker compose start langfuse-web langfuse-worker
# 如果整栈都停过（clickhouse/pg/minio/redis 也停了）：用 up 拉起依赖
# docker compose up -d
docker compose ps
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:3000/
```

只启 web 不启 worker：UI 可看历史，但新的异步事件可能不处理；要接收新 trace 必须启 worker。

### 5.2 让 platform 重新上报

```bash
sudo sed -i 's/^LANGFUSE_ENABLED=.*/LANGFUSE_ENABLED=true/' /opt/ai-code/app/platform/.env
sudo systemctl restart ai-code-platform
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8084/actuator/health
```

### 5.3 再次关闭

```bash
sudo sed -i 's/^LANGFUSE_ENABLED=.*/LANGFUSE_ENABLED=false/' /opt/ai-code/app/platform/.env
sudo systemctl restart ai-code-platform
cd /opt/ai-code/langfuse
docker compose stop langfuse-web langfuse-worker
```

> 数据卷保留；不要 `down -v`。

## 6. SkyWalking 开关与回滚

### 6.1 只停 UI（保留链路）

```bash
cd /opt/ai-code/skywalking
docker compose stop ui
```

### 6.2 停 OAP + UI（省约 1.25 GB）

```bash
cd /opt/ai-code/skywalking
docker compose stop
```

应用仍带 `-javaagent`，会重连报错，业务不受影响；恢复用 `docker compose up -d`。

### 6.3 恢复原堆参数

修改前备份：`/opt/ai-code/skywalking/.env.bak.20261025-083800`

```bash
cd /opt/ai-code/skywalking
cp .env.bak.20261025-083800 .env
docker compose up -d oap ui
```

或手工：

```text
OAP_JAVA_OPTS=-Xms256M -Xmx1024M
UI_JAVA_OPTS=-Xms64M -Xmx256M
```

> OAP 启动约 20–40 秒；UI 的 `/healthcheck` 返回 404 是正常现象（UI 自身 8088 为 200 即可）。
> OAP `docker stats` 的 RSS 会大于 `-Xmx`，因为包含 metaspace/direct/native。

## 7. 故障排查

| 现象 | 检查 | 处理 |
|------|------|------|
| platform health 非 200 | `systemctl status ai-code-platform`、`journalctl -u ai-code-platform -n 100` | 看 Flyway/DB/端口；必要时 `systemctl restart` |
| gateway 502/503 | platform 是否 200、`ai-code-gateway` 日志 | 先恢复 platform，再重启 gateway |
| enterprise 登录 503 | platform `/auth/verify` 是否可达 | 平台恢复后自动好转；不要误判为密码错 |
| 应用 OOM/频繁重启 | `journalctl` 搜 `OutOfMemory`、`docker stats` | 回滚该应用 JVM 参数；停观测 |
| SkyWalking UI 500 | OAP 是否 running、日志 | `docker compose restart oap`；必要时恢复 1024M |
| Langfuse UI 打不开 | web/worker 是否被停 | 默认关闭；按第 5 节启动 |
| 内存又紧张 | `free -m`、`docker stats` | 按 2.1 水位表停观测；不要先停业务 |
| Flyway 启动失败 | `journalctl` 搜 `Flyway`、`Migrating` | 查数据库连通/历史表；发布失败由 `remote-deploy.sh` 回滚 jar |
| 端口冲突 | `ss -ltnp` | Langfuse 3000 / SkyWalking 8088 / Grafana 3001 已错开 |

日志查看：

```bash
journalctl -u ai-code-platform -n 120 --no-pager
journalctl -u ai-code-enterprise -n 80 --no-pager
docker logs --tail 80 skywalking-oap
docker logs --tail 80 langfuse-langfuse-web-1
```

## 8. 发布 / 回滚入口

```powershell
# 本机（Windows）发布平台/网关/enterprise
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\deploy-app.ps1 -Target platform -SkipTests
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\deploy-app.ps1 -Target gateway -SkipTests
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\deploy-app.ps1 -Target enterprise -SkipTests

# 回滚到 app.jar.prev
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\deploy-app.ps1 -Target platform -Rollback
```

已有备份：

| 场景 | 路径 |
|------|------|
| V6（记忆/审批） | `/opt/ai-code/backup/week23-20261025-024942` |
| V7（角色/执行 tenant） | `/opt/ai-code/backup/week23-b2-20261025-062917` |
| SkyWalking 原 `.env` | `/opt/ai-code/skywalking/.env.bak.20261025-083800` |
| platform 关闭 Langfuse 前 `.env` | `/opt/ai-code/app/platform/.env.bak.20261025-083912` |

## 9. 变更记录

| 日期 | 动作 | 结果 |
|------|------|------|
| 2026-10-25 02:49–02:52 | 云端 V6 迁移 + 平台发布 | HITL 验收 PASS=21 |
| 2026-10-25 06:29–06:35 | 云端 V7 + enterprise A1 发布 | 批次 2 验收 PASS=19（含 409/503） |
| 2026-10-25 08:38 | 停 Langfuse web/worker；OAP 768M、UI 192M；platform `LANGFUSE_ENABLED=false` | available 1641→3320 MB；业务健康 200/200/401 |

> 下次调优/重启先看本手册第 2 节水位表和第 3 节顺序；变更后把结果追加到第 9 节。
