# Spec: Week 19 — SkyWalking（网关 / Spring Cloud 服务接入 + HTTP/Feign/MySQL/Redis/JVM 监控）

## Objective

本周把系统从「单进程可观测」推进到**服务拓扑可观测**：新增 **Spring Cloud Gateway 统一入口**（路由 + 鉴权 +
跨进程链路续接），补上**Feign 服务间调用**这条此前完全不存在的链路，并接入**自部署 Apache SkyWalking 10.2.0**
（Java Agent 自动埋点 + Toolkit 手动埋点），让一次请求在 SkyWalking 上表现为
`网关 → 平台服务 → 数据库 / 缓存 / 大模型 / 工具` 的**拓扑 + 全链路 span 树 + JVM 指标**。

**红线不变**：Week 16 的 `X-Trace-Id` 回显契约、Week 17 的 traceId 单源与 Prometheus 指标出口、
Week 18 的 Langfuse 观测全部不回退；SkyWalking 是**第三个后端**，与 OTel/Langfuse 并存而非替代。

## Tech Stack

| 项 | 本周启用 |
|----|----------|
| JDK 17、Spring Boot 3.4.5、Maven、`libs/ai-core`、Sa-Token、Redis、PostgreSQL/H2、Flyway、Actuator、Micrometer/OTel | 复用，契约不改 |
| **Spring Cloud 2024.0.2**：`spring-cloud-starter-gateway`（响应式网关） | 本周新增 |
| **Spring Cloud OpenFeign**：`spring-cloud-starter-openfeign` + `feign-micrometer` | 本周新增 |
| **Apache SkyWalking 10.2.0**：OAP + UI（Docker Compose）+ Java Agent（`-javaagent`） | 本周新增（部署物 + 文档） |
| `org.apache.skywalking:apm-toolkit-trace:9.7.0`（Maven Central 最新可发布版；纯 facade，`provided` 作用域） | 本周新增（手动埋点） |
| Spring `StringRedisTemplate` / `ReactiveStringRedisTemplate` | 本周新增用法（网关会话与缓存） |
| `com.sun.net.httpserver.HttpServer`（JDK 自带） | 复用 Week 18 的测试夹具思路（断言 Feign/网关出站请求头） |

明确不做：SkyWalking 日志采集（LAL）、告警规则、Profiling/eBPF、服务网格与 Satellite；
Prometheus/Grafana 服务端（第 20 周）；Spring Cloud 注册中心（Nacos/Eureka，见 ADR）；
SkyWalking 官方 `apm-toolkit-logback-1.x`（日志与链路已由 Week 16/17 的 MDC 单源保证，再引一份是重复）。

## Commands

- 不改 `JAVA_HOME`；JDK17：`-Djdk.17.home=E:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`
- Maven：`-s D:\soft\maven-3.9.4\conf\settings-ailocal.xml`（本地仓库 `E:\workRepositoryAi`，`.mvn/maven.config` 已配）
- 根聚合测试：`.\run-maven-jdk17.ps1 test`
- 单模块测试：`.\run-maven-jdk17.ps1 -pl apps/agent-gateway -am test`
- 启动网关（8080，**带 SkyWalking Agent**）：

```powershell
# 方式一（脚本，推荐）：run-maven-jdk17.ps1 已读取 $env:SKYWALKING_AGENT_PATH
$env:SKYWALKING_AGENT_PATH="E:\skywalking-agent\skywalking-agent.jar"
$env:SW_AGENT_NAME="ai-code-gateway"
$env:SW_AGENT_COLLECTOR_BACKEND_SERVICES="192.168.132.128:11800"
.\run-maven-jdk17.ps1 spring-boot:run

# 方式二（jar，参数形式，便于运维复制）
java -javaagent:/path/skywalking-agent.jar \
     -Dskywalking.agent.service_name=ai-code-gateway \
     -Dskywalking.collector.backend_service=192.168.132.128:11800 \
     -Dskywalking.agent.instance_name=gateway-1 \
     -jar target/agent-gateway-0.1.0-SNAPSHOT.jar
```

- 启动平台（8084，带 Agent）：同上，`-Dskywalking.agent.service_name=ai-code-platform`
- 部署 SkyWalking：见 `docs/deploy/week-19-skywalking.md`（`deploy/skywalking/docker-compose.yml`）；
  `docker compose up -d` → OAP `11800/12800`、UI `http://<sw-host>:8080`
- 应用侧关键环境变量：`SKYWALKING_AGENT_PATH`、`SW_AGENT_NAME`、`SW_AGENT_COLLECTOR_BACKEND_SERVICES`、
  `SW_AGENT_INSTANCE_NAME`、`GATEWAY_ROUTE_PLATFORM_URI`、`GATEWAY_TOKEN_TTL_SECONDS`
- Postman：导入 `docs/postman/week-19.postman_collection.json`

## 增量架构

见 `docs/architecture/week-19-architecture.md`（只画本周新增 / 改动节点）。

## 一、新增 Spring Cloud 网关（`apps/agent-gateway`，8080）

本周第一次出现**跨进程入口**。职责严格限定为三件事，**不放任何业务规则**：

| 职责 | 做法 | 不做什么 |
|------|------|----------|
| 统一入口与路由 | Spring Cloud Gateway（响应式）把 `/gw/**` 路由到平台 8084 | 不聚合业务数据、不做结果改写 |
| 统一鉴权（Week 13 遗留「Gateway 层统一鉴权 + 路由」） | `GlobalFilter` 校验网关联会话；登录改为经 Feign 调平台 `POST /api/v1/platform/auth/login` 换取平台 token，再在网关侧登记会话（Redis，TTL 可配） | 不在网关复制 RBAC 规则；不做 SQL |
| 链路续接与透传 | 透传 `Authorization` / `X-Trace-Id` / W3C `traceparent`，响应回写 `X-Trace-Id` 与 `X-Gateway-Session` | 不改 Week 16 的信封与状态码语义 |

**为什么用「网关联会话」而不是「网关自己签 token」**：网关没有用户库，也不应为了鉴权把 RBAC 数据复制一份。
登录仍由平台裁决（唯一事实源），网关只保存「会话 → 平台 token」映射；平台侧 RBAC 一行未改，
因此 Week 13/16 的权限用例零回退。

**网关也承载 Feign（重要取舍，已预登记）**：`agent-ops` 型下游只做一件事——
经 Feign 读平台 Agent 目录（`GET /api/v1/platform/agents`）。原因：本机是单机 lab，
再起一个只转发一层的 HTTP 服务会白占 ~300 MB 内存（VM 仅剩 ~1.1 GB），而它提供的可观测事实
（Feign 调用、Redis 缓存、跨进程链路）在网关进程内**完全等价**。拓扑上它在 SkyWalking 里表现为
第二个服务实例（同一个 JVM 由 `-Dskywalking.agent.service_name` 之外的第二个 instance 承载不了两个服务名，
故 Feign 的客户端与目标端**分别**出现在网关与平台两个服务下，这是真实的可观测事实）。

## 二、SkyWalking 接入方式（Agent 自动 + Toolkit 手动）

两条腿，各自负责不同层次，**不重复埋点**：

| 层次 | 手段 | 产出 |
|------|------|------|
| HTTP 入口 / 出口、Feign、JDBC、Redis（Lettuce/Jedis）、JVM | **Java Agent 自动埋点**（零代码改动） | 服务拓扑、端点指标、`Feign` 调用链、慢 SQL / 慢缓存命令、JVM 面板 |
| 业务语义（Agent 执行 / 大模型调用 / 工具调用） | **Toolkit 手动埋点**（`apm-toolkit-trace`） | `agent.run` / `llm.chat` / `tool.call` span + 业务 tag |

- 自动埋点的开关落在**启动参数**（不是 `application.yml`）：`-javaagent:<path>` 与 `-Dskywalking.*`。
  Agent 未挂载时应用行为与 Week 18 完全一致（Toolkit facade 是 no-op），因此**单测不需要 Agent**。
- Toolkit 只在 `observability.skywalking.enabled=true` 时装配真实适配器；默认 false → 空实现、零开销。

## 三、链路 ID 口径：三后端关联（本周核心决策）

**事实**：SkyWalking 的 traceId（Base64 编码的 segmentId）与 W3C traceparent 的 32 位 hex
**不可能逐位相等**；强行统一就得改 Week 16 验收过的 `X-Trace-Id` 回显契约（破坏性变更，已否决）。

于是本周把口径写成三条可验收的规则：

1. **同源派生**：网关与平台都以请求头 `X-Trace-Id` 为准（缺失时由 OTel/W3C 生成），
   平台侧 span 上写业务 tag `aicode.trace_id` = 该值；
2. **一次请求三处可见**：响应头 `X-Trace-Id` + SkyWalking span tag `aicode.trace_id` + Langfuse/OTel traceId
   在同一请求上给出**同一个业务 ID**，跨后端检索用这个 ID（不是用 SkyWalking 的 traceId）；
3. **不覆盖**：SkyWalking 的 `sw8` 头传播归 Agent，W3C `traceparent` 传播归 OTel，
   应用不改写任何一方（只做透传与记录）。

**诚实边界**：入口 server span 由 Agent 在 Filter 之前建立，其 tag 由 `TraceIdFilter` 在链路上补齐；
若 SkyWalking 版本不支持把 W3C 上下文并入 `sw8`，则 SkyWalking 侧看不到与 OTel 的**父子**关系，
只能靠 `aicode.trace_id` 做**同请求关联**——这一点在部署文档与实现日志中写明，不假装已打通。

## 四、监控面（README 五类，逐条落到可验收事实）

| README 要求 | 本周怎么给 | 验收证据 |
|-------------|-----------|----------|
| HTTP | Agent 自动埋点网关入口与平台入口；网关自检端点 `GET /api/v1/gateway/status` 返回链路上下文 | SkyWalking UI 服务页出现端点指标；网关响应体/头能对照 traceId |
| Feign | 网关 Feign 客户端调平台 Agent 目录（`feign-micrometer` 提供指标）；Agent 自动建立 Feign 客户端 span | SkyWalking 拓扑出现网关 → 平台边；span 名含 `Feign` |
| MySQL | 平台执行 Agent 时的 JDBC 语句（Agent 自动埋点）；网关侧**不**直连数据库（避免为监控造第二条数据通道） | SkyWalking span 里出现 `select/insert` 语句与耗时 |
| Redis | 网关会话（`ReactiveStringRedisTemplate`）与幂等键（`StringRedisTemplate` SETNX + TTL）；平台侧既有 Redis memory | SkyWalking span 出现 `Redis` / `SET`/`GET` 命令与耗时 |
| JVM | Agent 自带 JVM 指标（堆、GC、线程、类加载） | UI「Instance → JVM」面板有数据；`/actuator/prometheus` 的既有 JVM 指标不回退 |

## 五、网关 API（详见 `docs/api/week-19-api.md`）

| 方法 | 路径 | 说明 | 鉴权 |
|------|------|------|------|
| POST | `/api/v1/gateway/sessions` | 登录（经 Feign 调平台）→ 返回网关联会话 token | 免登录 |
| DELETE | `/api/v1/gateway/sessions/current` | 登出（删网关会话） | 需会话 |
| GET | `/api/v1/gateway/status` | 网关接入自检（路由目标、链路上下文、SkyWalking 开关；**不含密钥**） | 需会话 |
| GET | `/api/v1/gateway/agents` | 经 Feign 读平台 Agent 目录（Redis 缓存 + 幂等键演示） | 需会话 |
| POST | `/api/v1/gateway/agents/{agentKey}/runs` | 转发到平台执行（透传链路头） | 需会话 |

平台侧新增：`GET /api/v1/platform/observability/skywalking-status`（管理员/登录）返回 SkyWalking 服务名、
OAP 后端地址、当前 traceId 与 `aicode.trace_id`、以及 Toolkit 是否可用。

## 六、开关、降级与单测隔离

```yaml
observability:
  enabled: ${OBSERVABILITY_ENABLED:true}      # Week 17 既有：OTel span + 指标
  skywalking:                                  # 本周新增
    enabled: ${SKYWALKING_ENABLED:false}       # 默认关：零 Toolkit 依赖、零开销
    service-name: ${SW_AGENT_NAME:}
    backend-service: ${SW_AGENT_COLLECTOR_BACKEND_SERVICES:}

gateway:
  routes:
    platform-uri: ${GATEWAY_ROUTE_PLATFORM_URI:http://localhost:8084}
  session:
    token-ttl-seconds: ${GATEWAY_TOKEN_TTL_SECONDS:1800}
  catalog:
    cache-ttl-seconds: ${GATEWAY_CATALOG_CACHE_TTL_SECONDS:30}
```

- 未挂 Agent 时：Toolkit facade 全 no-op，`skywalking-status` 返回 `agentAttached=false`，
  既有 450 个用例行为与耗时不变；
- SkyWalking 后端不可达时：Agent 缓冲/丢弃上报，**业务请求不受影响**（与 Week 17 collector、
  Week 18 Langfuse 同口径，真机复验）；
- 网关上游不可达：返回 502 + 统一信封 `GATEWAY_UPSTREAM_ERROR`，不泄漏上游报文。

## Boundaries

- **Always**：TDD（先 RED）；中文 JavaDoc；Week 16/17/18 契约零回退；密钥只从环境变量读；
  链路 ID 三条规则写进文档与单测；规范 6.0 交付包齐全
- **Ask first**：改动 `libs/ai-core` Port 契约；给 8083/8082 挂 Agent；把可观测下沉到 `libs`；
  引入注册中心（Nacos/Eureka）；用 SkyWalking Cloud/官方 Demo 环境
- **Never**：本周引 Prometheus/Grafana 服务端与告警；改网关去写业务 SQL；
  把 `org.apache.skywalking.*` 类型放进 `domain` / `application` / `controller`；
  让网关直连业务库来「制造」MySQL 监控数据；把 `X-Trace-Id` 改成 SkyWalking traceId；
  引 Spring Cloud Config/Bus/Nacos 等本周用不到的组件

## Success Criteria

- [ ] `spring-cloud-starter-gateway` 路由生效：`POST /api/v1/gateway/sessions` → 200；未带会话访问业务端点 → 401；
      上游不可达 → 502 `GATEWAY_UPSTREAM_ERROR`（含统一信封与 traceId）
- [ ] 网关会话落 Redis（`SETEX`，TTL 可配）且登出即失效；单测断言 TTL 与键名规则
- [ ] Feign 调用成立：`GET /api/v1/gateway/agents` 经 Feign 拿平台目录，命中 Redis 缓存时**不再打平台**；
      用 JDK `HttpServer` 桩断言 Feign 请求带 `Authorization` 与 `X-Trace-Id`
- [ ] `observability.skywalking.enabled=false`（默认）时零 Toolkit 装配、既有用例全绿；
      `=true` 时装配 `SkyWalkingObservabilityAdapter`，`openSpan` 产出 `agent.run`/`llm.chat`/`tool.call` span 与 tag
- [ ] `SkyWalkingObservabilityAdapter` 单测（假 Toolkit facade）：`aicode.trace_id`、`agent.key`、
      `gen_ai.request.model`、token 数、`tool.name` 等 tag 写入正确；异常不抛出；关闭时全 no-op
- [ ] `GET /api/v1/platform/observability/skywalking-status` 返回服务名 / OAP 地址 / 当前 traceId /
      `aicode.traceId` / `agentAttached`，**不含**任何密钥
- [ ] 网关 `GET /api/v1/gateway/status` 返回路由目标、会话数、链路上下文与 SkyWalking 开关
- [ ] 根聚合 `test` 全绿，Week 8–18 用例不回退（新增模块用例 ≥ 40）
- [ ] **真实端到端**（192.168.132.128）：SkyWalking OAP + UI 起来（容器 healthy，JVM 堆显式调小），
      网关与平台挂 Agent 后跑一次真实请求，SkyWalking UI/GraphQL 可验证：
      ① 拓扑出现网关 → 平台；② 链路含 HTTP + Feign + MySQL(`select`/`insert`) + Redis(`SET`/`GET`) span；
      ③ JVM 指标有数据；④ span 上 `aicode.trace_id` 与响应头一致；⑤ 关掉 SkyWalking 业务仍 200
- [ ] 交付物齐全：Spec / 架构 / 接口 / 部署文档 / 部署物 / Postman / 实现日志

## 与计划的偏差（预登记）

| 计划（README） | 实际做法 | 原因 |
|----------------|----------|------|
| 「接入 Gateway」 | **新建** `apps/agent-gateway`（Spring Cloud Gateway），不是改造既有模块 | 仓库此前无任何网关/Spring Cloud 模块，Week 13 遗留项也要求本周补上 |
| 「接入 Spring Cloud 服务」 | 网关进程内承载 Feign（下游=平台 8084） | 单机 lab 内存只剩 ~1.1 GB；新增的独立下游服务只转发一层，可观测事实在网关进程内完全等价（见一、ADR） |
| 计划隐含「注册中心 + 服务发现」 | **不引入注册中心**，路由与 Feign 都用配置化 URL | 单机双进程，注册中心对学习目标无增量却引入一个常驻组件与一套新故障模式（YAGNI，规范 3.4） |
| 「MySQL 监控」 | 复用平台既有 JDBC 语句（Agent 自动埋点） | 网关不直连数据库；为监控造第二条数据通道违反规范 5.2（Controller 不调 Repository） |
| README 第 19 周未提 traceId 口径 | 明确「三后端不同 traceId、用 `aicode.trace_id` 关联」 | SkyWalking traceId 与 W3C traceparent 不可比；强行统一会破坏 Week 16 契约 |
| 原计划用 H2 作为 OAP 存储 | **改为复用 VM 上既有 PostgreSQL 16**（独立 `skywalking` 库 + BanyanDB 之外的 SQL 存储） | SkyWalking 10.x 已**移除 H2 存储**（官方仅剩 BanyanDB / MySQL / PostgreSQL / ES）；独立库与 Langfuse 的 pg 分离，不污染业务库 |

## Open Questions

- 是否在 `libs/ai-core` 提供统一的「网关鉴权」能力供后续 SaaS 复用 → 第 21 周 SSO 整合时决定
- SkyWalking 与 otel-collector 并存时的采样率与数据保留策略 → 第 20 周完整监控体系统一规划
- 网关限流（Redis RateLimiter）与熔断（Resilience4j）是否本周做 → 本周不做，第 20 周按容量决定
- 8082/8083 是否也挂 Agent → 第 21 周收敛到 8084 时统一

## ADR（本周）

见 `docs/architecture/week-19-architecture.md` 第 5 节与实现日志第 12 节（含四选项对照）。
