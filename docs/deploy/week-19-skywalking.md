# 部署：Week 19 — Apache SkyWalking 10.2.0 自托管（OAP + UI + Java Agent）

> 目标：在一台 Linux 主机上用 Docker Compose 起一套 **SkyWalking OAP + UI**，
> 让**应用侧零代码改动**获得 HTTP / Feign / JDBC / Redis / JVM 五类监控与**服务拓扑**；
> 业务语义（`agent.run` / `llm.chat` / `tool.call`）由 `apm-toolkit-trace` 手动埋点补上。
>
> 交付物：`deploy/skywalking/docker-compose.yml`、`deploy/skywalking/.env.example`、本文档。
> 代码侧接入见 `docs/specs/week-19.md`、`docs/architecture/week-19-architecture.md`、`docs/api/week-19-api.md`。

---

## 1. 组件与版本水位

| 组件 | 镜像 / 包 | 作用 | 版本要求 |
|------|-----------|------|----------|
| OAP（后端） | `apache/skywalking-oap-server:10.2.0` | 接收上报、聚合、查询（gRPC 11800 / HTTP 12800） | 10.x（与 Agent 9.7.0 官方兼容） |
| UI | `apache/skywalking-ui:10.2.0` | 拓扑 / 链路 / 指标 / JVM 面板 | 与 OAP 同 tag |
| Java Agent | `apache-skywalking-java-agent-9.7.0.tgz` | 应用侧自动埋点（`-javaagent`） | **9.7.0**（官方最稳定版；10.x 主线 Agent 尚未发布到 CDN） |
| 存储 | 宿主既有 **PostgreSQL 16**（独立库 `skywalking`） | trace / metrics / topology 落库 | PostgreSQL ≥ 12 |
| 手动埋点依赖 | `org.apache.skywalking:apm-toolkit-trace:9.7.0` | 业务 span（`provided` 作用域） | Maven Central 最新可发布版 |

### 1.1 为什么存储用 PostgreSQL 而不是默认的 BanyanDB

SkyWalking 10.x 的默认存储是 **BanyanDB**，官方文档里的存储选项只剩
**BanyanDB / MySQL / PostgreSQL / Elasticsearch**——**H2 已被移除**（9.x 时代的选择）。
本机（192.168.132.128，3.8 GB 内存）同时跑着 SkyWalking 与 Week 18 的 Langfuse 六容器，
再常驻一个存储引擎会直接压垮节点。因此选择：

- **复用宿主已有的 PostgreSQL 16 容器**（`pg16`），但**新建独立库 `skywalking`**，
  与业务库 `aidemo`、Langfuse 自带库完全隔离；
- OAP 只负责**建表**（自动建 69 张按天分表的表），**不建库**——建库需要超级用户权限，
  见 2.2 的实测说明；
- JVM 堆显式限制（见 `.env.example`），避免容器内 JVM 按宿主内存的 1/4 取上限。

> 生产环境若数据量大，按官方建议改用 BanyanDB 或 Elasticsearch，并把 OAP 部署成集群
> （`SW_CLUSTER`），本文的单机 + PostgreSQL 是**实验室规模**的取舍。

---

## 2. 前置条件与准备

| 项 | 要求 | 检查命令 |
|----|------|----------|
| Docker Engine | 24+ | `docker version` |
| Docker Compose | v2（`docker compose`） | `docker compose version` |
| 资源 | OAP ≈ 900 MB、UI ≈ 150 MB（JVM 堆 768 M / 256 M） | `free -m`、`docker stats --no-stream` |
| 端口 11800 / 12800 | 需对应用所在主机开放（Agent 上报与 UI 查询） | `ss -lntp \| grep -E '11800\|12800'` |
| 端口 8088 | UI 对外端口（避开业务网关 8080） | `ss -lntp \| grep 8088` |
| 时间同步 | 容器与宿主机 UTC（按天分表依赖日期） | `timedatectl` |

### 2.1 上传部署物料

```bash
mkdir -p ~/skywalking/deploy && cd ~/skywalking/deploy
# 从仓库拷 deploy/skywalking/{docker-compose.yml,.env.example}
cp .env.example .env && vi .env
```

### 2.2 预先建库（**必须**，OAP 不建库）

```bash
# aidemo 用户没有 CREATEDB 权限（实测报 permission denied to create database），
# 因此建库要用超级用户 postgres；建完把 owner 交给业务用户，OAP 用业务用户连库。
docker exec pg16 psql -U postgres -d postgres -c 'CREATE DATABASE skywalking OWNER aidemo'

# 验证业务用户在 skywalking 库里有建表权限（OAP 启动时会建 69 张表）
docker exec pg16 psql -U aidemo -d skywalking -c 'create table if not exists _probe(id int); drop table _probe;'
```

### 2.3 启动

```bash
docker compose up -d
docker compose ps          # 期望：skywalking-oap / skywalking-ui 都是 Up
docker compose logs oap | tail -30
```

启动成功的判据（本项目实测输出）：

```text
module.storage.provider                    |   postgresql
module.cluster.provider                    |   standalone
oap.external.grpc.port                     |   11800
oap.external.http.port                     |   12800
```

> **注意**：`GET /internal/l7check` 在本版本返回 **404**（Armeria 只把它当普通路径），
> 这不是「没起来」。判据请用 `docker compose ps` + 日志里的 `module.*.provider` 行，
> 以及 `POST /graphql` 返回 200。

### 2.4 连通性自检

```bash
# UI（宿主 8088）应为 200
curl -s -o /dev/null -w 'ui:%{http_code}\n' http://localhost:8088/

# OAP GraphQL 应为 200
curl -s -o /dev/null -w 'graphql:%{http_code}\n' -X POST http://127.0.0.1:12800/graphql \
  -H 'Content-Type: application/json' -d '{"query":"{version}"}'

# 表已建好（应输出 69）
docker exec pg16 psql -U aidemo -d skywalking -tAc \
  "select count(*) from information_schema.tables where table_schema='public'"
```

---

## 3. 应用侧接入（Java Agent + Toolkit）

### 3.1 准备 Agent

```bash
# 官方 CDN（9.7.0 是 java-agent 目录下唯一版本；10.x 的 Agent 尚未发布到该目录）
curl -fLO https://dlcdn.apache.org/skywalking/java-agent/9.7.0/apache-skywalking-java-agent-9.7.0.tgz
tar xzf apache-skywalking-java-agent-9.7.0.tgz     # 得到 skywalking-agent/
```

> **跨平台拷贝要用压缩包，别用 `scp -r`**：实测从 Linux VM 用 `scp -r` 把 agent 目录拷到
> Windows 时，`plugins/*.jar` 大面积损坏（解压报 `zip END header not found`），
> Agent 加载插件全部失败。正确做法：`tar --exclude='._*' -czf agent.tgz skywalking-agent`
> 后在目标机解压，并校验 `plugins` 里小于 10 KB 的文件数（正常应为 0–2 个）。

### 3.2 启动参数（两种等价写法）

```powershell
# 网关（8080）
java -javaagent:/path/skywalking-agent/skywalking-agent.jar `
     -Dskywalking.agent.service_name=ai-code-gateway `
     -Dskywalking.agent.instance_name=gateway-1 `
     -Dskywalking.collector.backend_service=<oap-host>:11800 `
     -jar target/agent-gateway-0.1.0-SNAPSHOT.jar

# 平台（8084）
java -javaagent:/path/skywalking-agent/skywalking-agent.jar `
     -Dskywalking.agent.service_name=ai-code-platform `
     -Dskywalking.agent.instance_name=platform-1 `
     -Dskywalking.collector.backend_service=<oap-host>:11800 `
     -jar target/spring-ai-alibaba-agent-0.1.0-SNAPSHOT.jar
```

也可以用环境变量（Agent 的 `config/agent.config` 支持 `${SW_AGENT_*}` 占位）：

| 环境变量 | 含义 | 对应系统属性 |
|----------|------|--------------|
| `SW_AGENT_NAME` | 服务名（拓扑上按它分组） | `skywalking.agent.service_name` |
| `SW_AGENT_INSTANCE_NAME` | 实例名（同服务多实例区分） | `skywalking.agent.instance_name` |
| `SW_AGENT_COLLECTOR_BACKEND_SERVICES` | OAP 地址 `<host>:11800` | `skywalking.collector.backend_service` |

> **PowerShell 用户注意**：命令行里直接写 `-Dskywalking.x=y` 会被 PowerShell 当成
> 「驱动器限定路径」解析，Java 收到的是 `/agent/service_name=...` 并报
> `ClassNotFoundException`。请在脚本里给每个 `-D` 参数加引号（或改用 `SW_AGENT_*` 环境变量）。
> 这是本项目实测踩到的第一个坑（见实现日志 10.2 ①）。

### 3.3 应用配置（`observability.skywalking.*`）

```yaml
observability:
  skywalking:
    enabled: ${SKYWALKING_ENABLED:false}   # 是否叠加我们自己的业务 span（agent.run / llm.chat / tool.call）
    service-name: ${SW_AGENT_NAME:}        # 仅用于自检展示
    backend-service: ${SW_AGENT_COLLECTOR_BACKEND_SERVICES:}  # 仅用于自检展示
    tag-prefix: aicode                     # 业务标签前缀（禁魔法字符串）
```

- **`enabled=true` 要求启动参数已挂 Agent**：Toolkit 类由 Agent 提供。
  未挂 Agent 时装饰器自动降级为空实现并在启动日志打 WARN，业务不受影响。
- 自检接口 `GET /api/v1/platform/observability/skywalking-status` 会明确回
  `bridgeAvailable=false`，避免「配置开了但链路上什么都没有」的迷惑现象。
- **Java Agent 的开关不在这份配置里**：它由 `-javaagent` 启动参数决定，
  应用配置无法在类加载前改变它（文档如此写，是为了避免误导）。

#### 3.3.1 从 IDEA 启动（含网关）

| 应用 | 端口 | 关键点 |
|------|------|--------|
| `spring-ai-alibaba-agent` | 8084 | 默认 profile=`jdbc`；`.env` 由 `application-jdbc.yml` 的 `config.import` 读；VM options 放 Agent 参数 |
| `agent-gateway` | 8080 | 无 profile 差异；`application.yml` 直接声明 `config.import: optional:file:.env[.properties]`；**必须二选一**：EnvFile 插件指向模块 `.env`，或把 Working directory 设为模块目录 |

（其余两种模块的 `.env` 都由 EnvFile 插件注入，网关照抄即可）

**网关启动失败 `Host must not be empty` 的定位与修复**（实测，排查过程容易跑偏）：

```text
日志关键行 1：  started by <user> in E:\workNew\ai-code-repo      ← 工作目录是仓库根
日志关键行 2：  Caused by: java.lang.IllegalArgumentException: Host must not be empty
                at RedisConnectionDetails$Standalone.of(...)
```

含义：`spring.data.redis.host=${SPRING_REDIS_HOST:}` 展开成**空串**，Lettuce 建连接工厂时直接失败。
**这是刻意的 fail-fast**（占位符默认留空 = 不允许把环境地址写进版本控制）；网关与平台不同，
平台没有 redis 依赖（只用 `StringRedisTemplate` 时才需要），所以平台上不会触发这个报错，容易造成
「平台没配也能起，网关为什么不行」的错觉。

> 注意变量名：本仓库的既有约定是 `SPRING_REDIS_HOST/PORT/PASSWORD`（`.env` 里就是这个），
> 而 Spring Boot 的属性路径是 `spring.data.redis.*`。两者是靠 **application.yml 里的占位符**连接的：

| 配置项 | 说明 |
|--------|------|
| `.env` 或环境变量 | `SPRING_REDIS_HOST` / `SPRING_REDIS_PORT` / `SPRING_REDIS_PASSWORD` |
| `application.yml` | `spring.data.redis.host: ${SPRING_REDIS_HOST:}`（**必须先声明**，否则 Boot 3.x 不会把 `SPRING_REDIS_HOST` 当属性） |
| 平台的情况 | 只有 `SPRING_REDIS_*` 三个变量，没有 redis starter，因此不参与自动配置 |

修复步骤（任选其一，已实测场景 2 可用）：

1. **Run Configuration → EnvFile 插件 → 指定 `$PROJECT_DIR$/apps/agent-gateway/.env`**（与其它模块同口径，推荐）；
2. **Run Configuration → Working directory 改成 `$PROJECT_DIR$/apps/agent-gateway`**（或 `$MODULE_WORKING_DIR$`），
   让 `spring.config.import` 读到模块目录下的 `.env`。

顺带提醒：如果用的是 IDEA **自动生成的 temporary 配置**（名称带灰底、旁边没有「Save」），
它对 `envFilePaths` 的修改可能在下次运行被重置 —— 建议「Save configuration」，或手工新建一条常驻配置。

`.env` 与 VM options 的分工（两者都会生效，环境变量优先）：

```text
.env（或环境变量）  →  SPRING_REDIS_HOST / SPRING_REDIS_PORT / SPRING_REDIS_PASSWORD
                       GATEWAY_ROUTE_PLATFORM_URI
                       SW_AGENT_NAME / SKYWALKING_ENABLED
VM options          →  -javaagent:<skywalking-agent.jar>
                       -Dskywalking.agent.service_name / .instance_name / collector.backend_service
```

> **PowerShell 用户注意**：命令行里直接写 `-Dskywalking.x=y` 会被当作「驱动器限定路径」，
> Java 收到 `/agent/service_name=...` 并报 `ClassNotFoundException`；
> IDEA 的 VM options 文本框没有这个问题。详见实现日志 10.2 ②。


### 3.4 与 Week 17/18 的关系（三个后端并存）

| 后端 | 定位 | traceId 形态 | 关联方式 |
|------|------|--------------|----------|
| 自研 / 日志 / 审计 | 业务排查的单源 ID | `X-Trace-Id`（客户端可指定） | 事实源 |
| OTel / Langfuse | LLM 观测树、token/成本 | W3C traceparent（32 位 hex） | 与自研**逐位相等**（Week 17/18 已验收） |
| SkyWalking | 服务拓扑、中间件、JVM | Base64 segmentId（Agent 生成） | span tag `aicode.trace_id` = 业务 ID |

- **不要把 `X-Trace-Id` 改成 SkyWalking traceId**：那会破坏 Week 16 的「原样回显」契约，
  且客户端将无法指定链路 ID。
- SkyWalking 与 OTel 各自传播自己的头（`sw8` / `traceparent`），应用只做透传，不互相改写。
- 两个 Agent 同时挂载**不冲突**：它们是两套独立 SDK，只是各自多一条链路数据。

---

## 4. UI 使用入口

```text
SkyWalking UI : http://<oap-host>:8088
  拓扑图      : Topology → 选择服务（ai-code-gateway / ai-code-platform）
  链路        : Trace → 按 Service / Endpoint / Trace ID 检索
  端点指标    : Service → Endpoint（p99 / 错误率 / 慢端点）
  中间件      : Topology 上的 Database / Cache 节点，或 Trace 详情的 JDBC / Redis span
  JVM         : Instance → JVM（堆 / GC / 线程 / 类加载）
```

> 本版本 UI 是**纯前端 + OAP GraphQL**：如果 UI 打开空白，先确认 `SW_OAP_ADDRESS`
> 指向 OAP 的 12800（compose 里已配 `http://oap:12800`），而不是 11800（那是 gRPC）。

---

## 5. 运维与容量

| 项 | 做法 |
|----|------|
| 内存 | 显式给 `OAP_JAVA_OPTS=-Xmx768M`、`UI_JAVA_OPTS=-Xmx256M`；实测 OAP 常驻 ≈ 890 MB、UI ≈ 140 MB |
| 采样 | 默认全采样（实验室）。生产按容量用 `agent-analyzer.default.sampleRate` 降到 0.1 量级 |
| 数据保留 | PostgreSQL 存储按天分表（`*_YYYYMMDD`），用官方 TTL 配置或定期清理分区；本周不设保留策略 |
| 升级 | 换镜像 tag + `docker compose up -d`；PostgreSQL 存储的表结构由 OAP 自动迁移 |
| 备份 | 备份 `skywalking` 库即可（`pg_dump -d skywalking`）；UI 无状态 |

### 5.1 与其他观测组件共存的内存账（本机实测）

| 组合 | 内存占用 | 结论 |
|------|----------|------|
| Langfuse 六容器 | ≈ 1.75 GB | Week 18 常态 |
| SkyWalking OAP + UI | ≈ 1.03 GB | 本周新增 |
| 两者同时在线 | ≈ 2.8 GB + 业务进程 | 3.8 GB 节点**不可行**，需 8 GB 以上或分机部署 |

因此本机验收时的做法是：`docker compose stop`（**保留数据卷**）暂停 Langfuse，
验收完 `docker compose start` 恢复。生产环境应把两套后端放在不同节点或扩容。

---

## 6. 常见故障

| 现象 | 根因 | 处置 |
|------|------|------|
| OAP 启动即退出，日志 `permission denied to create database` / `database "skywalking" does not exist` | OAP 不建库，且业务用户无 CREATEDB 权限 | 按 2.2 用超级用户建库并授权 |
| `docker compose up` 报 `dependency cycle detected: oap -> ui -> oap` | oap 与 ui 互相 `depends_on` | 只保留 ui → oap 的单向依赖（compose 已修） |
| Agent 启动报大量 `jar file can't be resolved: zip END header not found` | Agent 目录被 `scp -r` 拷坏 | 用 tar 包重新分发（见 3.1） |
| 应用报 `ClassNotFoundException: /agent/service_name=...` | PowerShell 把 `-D...` 当路径解析 | 给 `-D` 参数加引号，或改用 `SW_AGENT_*` 环境变量 |
| 应用启动正常但拓扑里没有服务 | Agent 未挂 / OAP 地址写错端口（写了 12800 而 Agent 需要 11800） | 核对 `-javaagent` 与 `backend_service`；看应用日志里 Agent 的「SkyWalking agent started」 |
| 自检接口 `bridgeAvailable=false` | Toolkit 类不可用或未挂 Agent | 确认 `-javaagent` 生效；本项目要求 Agent 与 Toolkit 版本兼容（9.7.0） |
| 有 span 但没有 `aicode.trace_id` | `observability.skywalking.enabled=false` | 置 true 并重启（该开关不是热加载） |
| UI 打开空白 | `SW_OAP_ADDRESS` 指向了 11800 或写错主机 | 指向 `http://<oap-host>:12800` |
| 网关会话全部 500 / NOAUTH | Redis 设了 `requirepass` 但应用未配密码 | 配 `SPRING_REDIS_PASSWORD`（本项目实测踩坑） |

---

## 7. 降级与回滚

- **OAP 停掉**：Agent 侧上报失败只影响链路数据，业务请求正常返回（与 Week 17 collector、
  Week 18 Langfuse 同口径；验收含此场景）。应用日志里会出现 Agent 的 gRPC 连接告警，
  属预期，不影响 `X-Trace-Id` / 审计 / 指标。
- **不挂 Agent**：启动参数去掉 `-javaagent` 即可，代码与配置无需改动；
  自检接口会如实回 `bridgeAvailable=false`。
- **完全回滚**：`docker compose down`（默认保留数据卷）→ 应用启动参数去掉 Agent 相关项 →
  把 `observability.skywalking.enabled` 置回 `false`。**Week 17/18 的 OTel 与 Langfuse 不受任何影响。**

---

## 8. 验收清单（真机实测）

- [ ] `docker compose ps`：OAP、UI 均 Up；日志出现 `module.storage.provider | postgresql`
- [ ] `skywalking` 库有 69 张表（按天分表）
- [ ] UI `http://<host>:8088` 返回 200；`POST /graphql` 返回 200
- [ ] 网关与平台以 `-javaagent` 启动，应用日志出现 Agent 启动信息
- [ ] 经网关跑一次真实 Agent 执行（登录 → 目录 → 执行），HTTP 200 / COMPLETED
- [ ] SkyWalking 拓扑出现 `ai-code-gateway` → `ai-code-platform`
- [ ] 链路含 HTTP + Feign + JDBC（`select`/`insert`）+ Redis（`SET`/`GET`）span
- [ ] 业务 span 出现 `agent.run` / `llm.chat` / `tool.call`，并带 `aicode.trace_id`
- [ ] 实例 JVM 面板有堆 / GC / 线程数据
- [ ] `aicode.trace_id` 与响应头 `X-Trace-Id` 一致
- [ ] 停掉 OAP 后业务仍 200（降级验证）
