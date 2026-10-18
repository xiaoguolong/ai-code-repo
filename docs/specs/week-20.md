# Spec: Week 20 — 完整监控体系（Prometheus + Grafana 收口 + 四后端统一自检）

## Objective

第 17–19 周分别接入了 OTel、Langfuse、SkyWalking，但它们是**三套各自为政的后端**：
每个后端要单独调一个自检接口才知道活没活，指标体系只覆盖平台 8084，网关 8080 在 Prometheus 侧
完全不可见，也没有任何仪表盘与告警。本周把监控从「接入了」推进到「**可运维**」：

1. 部署 **Prometheus 2.55.1 + Grafana 11.3.1**（告警通知 Alertmanager 可选），补齐 README 第 20 周的五个组件；
2. 给**网关补上指标出口**（第 19 周只暴露 `health,info`，HTTP/Feign/JVM 指标只在 SkyWalking 里）；
3. 新增**统一监控概览 API**：一次返回六个组件（OTel / Prometheus / Grafana / SkyWalking / Langfuse / 网关指标）
   的开关、状态、端点与控制台入口，并给出「同一条请求在四个系统里怎么查」的关联信息；
4. 指标名、抓取目标、告警规则、仪表盘四者**用契约测试锁死**，避免改名后仪表盘静默变空。

**红线不变**：Week 16 的 `X-Trace-Id` 回显契约、Week 17 的 traceId 单源与 `/actuator/prometheus`、
Week 18 的 Langfuse 观测、Week 19 的网关鉴权与 SkyWalking 三后端关联，全部**零回退**。

## Tech Stack

| 项 | 本周启用 |
|----|----------|
| JDK 17、Spring Boot 3.4.5、Maven、`libs/ai-core`、Sa-Token、Redis、PostgreSQL/H2、Flyway | 复用，契约不改 |
| **Prometheus 2.55.1**（云上 Docker，host 网络，只监听 `127.0.0.1:19090`） | 本周新增（部署物 + 抓取/告警配置） |
| **Grafana 11.3.1**（云上 Docker，`127.0.0.1:3001`，provisioning 数据源与仪表盘） | 本周新增（部署物 + 2 张仪表盘 JSON） |
| **Alertmanager 0.27.0**（可选 profile `alerts`） | 本周新增（默认不启动） |
| `io.micrometer:micrometer-registry-prometheus`（网关模块） | 本周新增（网关指标出口） |
| JDK `HttpClient`（无新依赖） | 本周新增用法（监控探针） |
| `prometheus-metrics`（经 micrometer-registry-prometheus 传递，测试里抓文本格式） | 复用现有测试依赖 |

明确不做：Loki/ELK 日志集中、Prometheus 服务端告警通知渠道落地（只提供模板）、Grafana SSO、
应用上云、注册中心、Kubernetes 部署、trace exemplars（指标里嵌 traceId）、业务功能改动。

## Commands

- 不改 `JAVA_HOME`；JDK17：`-Djdk.17.home=E:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot`
- Maven：`-s D:\soft\maven-3.9.4\conf\settings-ailocal.xml`（本地仓库 `E:\workRepositoryAi`）
- 根聚合测试：`powershell -NoProfile -ExecutionPolicy Bypass -File .\run-maven-jdk17.ps1 test`
- 平台监控用例：`... -pl apps/spring-ai-alibaba-agent -am test "-Dtest=Monitoring*Test,HttpMonitoringProbeTest,PlatformMonitoring*Test"`
- 网关指标用例：`... -pl apps/agent-gateway -am test "-Dtest=*GatewayMetrics*,GatewayPrometheusEndpointTest"`
- 启动应用（8084 / 8080）：模块目录下 `.\run-maven-jdk17.ps1 spring-boot:run`（详见各自 `.env.example`）
- 指标反向隧道（云端 Prometheus 抓本地）：`.\tools\metrics-tunnel.ps1`（`-Status` / `-Stop`）
- 业务/UI 隧道：`.\tools\tunnel.ps1`（新增本地 `19090` / `3001` 转发）
- 部署监控栈：见 `docs/deploy/week-20-monitoring.md`（`deploy/monitoring/docker-compose.yml`）
- 云端验收：`.\remote.ps1 -Script .\tools\cloud-accept-monitoring.sh`
- 自检：`GET /api/v1/platform/observability/monitoring-overview`（管理员）
- 指标：`http://localhost:8084/actuator/prometheus`、`http://localhost:8080/actuator/prometheus`
- 仪表盘：`http://127.0.0.1:3001/d/aicode-platform-overview`、`/d/aicode-gateway-overview`
- Postman：导入 `docs/postman/week-20.postman_collection.json`

## 增量架构

见 `docs/architecture/week-20-architecture.md`（只画本周新增 / 改动节点）。

## 一、本周的核心问题：四个后端，零个统一视图

| 已有能力（Week 17–19） | 本周之前的状态 | 本周怎么补 |
|------------------------|----------------|------------|
| OTel span + `/actuator/prometheus` | 只有平台 8084 有指标出口；网关没有 | 网关加 `micrometer-registry-prometheus` + 暴露 `prometheus` 端点 |
| Langfuse LLM 观测 | 只能调 `/langfuse-status` 单查 | 收进统一概览；启用时探 `/api/public/health` |
| SkyWalking 拓扑/链路/JVM | 只能调 `/skywalking-status` 单查 | 收进统一概览；区分「配置关了」与「Agent 没挂」 |
| Prometheus / Grafana | **不存在**（第 17 周明确推迟到本周） | 部署 + 抓取 + 仪表盘 + 告警规则 |
| 跨后端定位 | 靠文档里的三条规则人工记 | 概览里直接给 traceId / otelTraceId / 关联标签 / 三个控制台深链 / 检索提示 |

## 二、统一监控概览 API（本周核心代码）

`GET /api/v1/platform/observability/monitoring-overview`（**仅管理员**）一次返回六个组件：

| key | 名称 | 判定规则 | 端点 / 深链 |
|-----|------|----------|-------------|
| `otel` | OpenTelemetry | `observability.enabled && otlp 导出开` → UP，否则 DISABLED | OTLP/HTTP 端点（只展示，不探测） |
| `prometheus` | Prometheus | 未配置/总开关关 → DISABLED；否则探 `/-/ready` | `base/graph` |
| `grafana` | Grafana | 未配置/总开关关 → DISABLED；否则探 `/api/health` | `base/d/<dashboardUid>` |
| `skywalking` | SkyWalking | 配置关 → DISABLED；开了但 Agent 没挂 → **DOWN**；否则 UP | SkyWalking UI |
| `langfuse` | Langfuse | 关 → DISABLED；开 → 探 `/api/public/health` | Langfuse Web |
| `gateway-metrics` | Gateway Metrics | 未配置 → DISABLED；否则探网关 `/actuator/prometheus` | — |

**状态语义（写死并单测）**：

- `DISABLED` 表示「没启用」，**不参与** `allHealthy` 判定 —— 把「没开」报成「坏了」会制造假故障；
- `DOWN` 表示「启用了但探不通」，必须让 `allHealthy=false`；
- 没有任何组件时 `allHealthy=false`（「什么都没配」不算健康）；
- 探测统一 800ms 超时 + 30s 结果缓存（可配），失败只回落 `DOWN`，**绝不抛异常**；
- 端点地址做**凭据剥离**（`http://user:pass@host` → `http://host`），响应里不出现账号密码；
- 只有管理员可看（会暴露内部地址与拓扑），非管理员 403。

关联块（`correlation`）同时给两套 ID 与五个检索提示：

| 字段 | 用途 |
|------|------|
| `traceId` | 业务 traceId，与响应头 `X-Trace-Id`、`audit_log.trace_id` 一致 |
| `otelTraceId` | OTel / Langfuse 的 W3C traceId（由业务 traceId 确定性派生） |
| `correlationTag` | 跨后端标签名（`aicode.trace_id`） |
| `langfuseTraceUrl` / `grafanaDashboardUrl` / `skywalkingUiUrl` | 三个控制台深链 |
| `queryHints` | `logs` / `audit` / `skywalking` / `langfuse` / `prometheus` 各怎么查 |

## 三、网关指标出口（补第 19 周的缺口）

第 19 周的网关只有 `management.endpoints.web.exposure.include: health,info`：
HTTP/Feign/JVM 指标只在 SkyWalking 的自动埋点里，Prometheus 抓不到，因此「平台 + 网关」无法在同一张
仪表盘上对比。本周：

1. 网关 `pom.xml` 增加 `micrometer-registry-prometheus`；`application.yml` 暴露 `prometheus`；
2. 两个应用统一加公共标签 `management.metrics.tags.application`（`ai-code-platform` / `agent-gateway`），
   避免 `jvm_*` / `http_server_requests_*` 同名序列互相覆盖；
3. 网关补四个**只有网关知道**的指标（经 `GatewayMetricsPort`，领域层不依赖 Micrometer）：

| 指标（Micrometer） | Prometheus 导出名 | 标签 | 埋点 |
|--------------------|-------------------|------|------|
| `gateway.session.login.count` | `gateway_session_login_count_total` | `outcome` | `GatewaySessionUseCase.login` |
| `gateway.catalog.lookup.count` | `gateway_catalog_lookup_count_total` | `cache_hit` | `GatewayAgentUseCase.listAgents` |
| `gateway.agent.run.count` / `.duration` | `gateway_agent_run_count_total` / `_seconds_*` | `outcome` | `GatewayAgentUseCase.runAgent` |
| `gateway.upstream.error.count` | `gateway_upstream_error_count_total` | `error_code` | 同上（异常分支） |

**为什么用 Port**：`io.micrometer.*` 不得进入 `application` 层（规范 3.3）；旧构造器默认 `NOOP`，
第 19 周的用例与测试一行未改。

## 四、抓取拓扑：应用在本地，Prometheus 在云上

这是本周唯一的「非代码」难点。现状（Week 19 环境批次）：应用跑本地 IDEA，中间件在腾讯云 4C8G，
通过 `ssh -L` 隧道访问。Prometheus 要抓本地 `/actuator/prometheus`，只有两条路：

| 方案 | 做法 | 问题 |
|------|------|------|
| A. 把应用端口暴露到公网 | 安全组开 8084/8080 | 零鉴权指标端点直接暴露，不可接受 |
| B. 反向 SSH 隧道（**采用**） | 本地 `ssh -R 127.0.0.1:18084:127.0.0.1:8084` | 需要多一个隧道脚本；但零公网暴露、零 sshd 改动 |

配套决策：Prometheus/Grafana 容器用 **host 网络**。原因：sshd 默认把 `-R` 绑在**云端回环**，
桥接网络的容器看不到宿主回环；host 网络直接抓 `127.0.0.1:18084`，且不需要改 `GatewayPorts`
（改 sshd 会把端口暴露到 LAN）。代价是不再用 `ports` 映射，改为「只监听 127.0.0.1 + 安全组不开
19090/3001/9093」，UI 通过 `tools\tunnel.ps1` 的 `-L 19090/3001` 访问。

> **端口不是默认 9090**：真机部署时发现云端 9090/9091 已被 Langfuse 的 MinIO（S3 API + 控制台）占用
> （`curl 9090` 返回 S3 `AccessDenied` XML，Prometheus 因绑定失败反复重启）。本周把 Prometheus
> 统一改用 **19090**，并把这个「看起来能用、其实被别的服务占着」的坑写进部署文档与实现日志。

```text
本地 IDEA                  SSH 反向隧道                 云端 4C8G
┌──────────────┐        ┌──────────────────┐        ┌─────────────────────┐
│ platform 8084│◀───────│ -R 18084:...     │───────▶│ Prometheus :19090    │
│ gateway  8080│◀───────│ -R 18080:...     │        │  └ scrape 127.0.0.1 │
└──────────────┘        └──────────────────┘        │ Grafana    :3001    │
                                                    │  └ datasource       │
                                                    │ Alertmanager :9093  │
                                                    └─────────────────────┘
```

## 五、仪表盘与告警（可运维的最小集）

**两张仪表盘**（provisioning 自动加载，不手工点）：

| 仪表盘 | UID | 内容 |
|--------|-----|------|
| Platform 业务与 LLM 概览 | `aicode-platform-overview` | Agent 速率/失败率/P95、LLM 调用与失败、Token、24h 成本、Tool、HTTP RPS/P95、JVM 堆 |
| Gateway 入口概览 | `aicode-gateway-overview` | 入站 RPS/5xx/P95、登录成功率、目录缓存命中率、经网关的执行、上游错误、Feign 出站、JVM |

**告警规则**（`deploy/monitoring/prometheus/alerts.yml`，12 条）：

- 可用性：平台/网关抓取目标 down（3m）；
- SLO：平台 5xx > 5%、P95 > 5s、Agent 失败率 > 20%、LLM 失败率 > 10%、Tool 失败率 > 20%；
- 成本：Token 速率 > 5000/s（10m）；
- 网关：5xx > 5%、上游失败 > 0.1/s、登录失败突增；
- 监控自身：规则评估失败。

Alertmanager **默认不启动**（`--profile alerts`）：本周只交付可用的规则与模板，通知渠道属于团队配置，
不写死任何 webhook/密钥。

## 六、采样率与保留策略（收口第 17/19 周的 Open Questions）

| 项 | 本周取值 | 依据 |
|----|----------|------|
| OTel 采样率 | 演示/验收 `1.0`（环境变量可降） | 需要看到全部 span；生产用 `OTEL_TRACES_SAMPLER_PROBABILITY=0.1` |
| Prometheus 保留 | `15d` / `2GB` 双上限 | 50 GB 盘；与 SkyWalking 按天分表的保留量级对齐 |
| SkyWalking / Langfuse | 不改（各自 TTL） | 本周目标是「统一入口」，不是改各后端保留策略 |
| 指标标签基数 | 禁止 `user.id` / `execution.id` / `traceId` 进指标 | 高基数会打爆 Prometheus；关联靠业务 traceId + 时间窗 |
| 直方图桶上界 | Agent / LLM `120s`，HTTP `30s`，网关转发 `120s` | 默认 30s 小于一次真实 Agent 执行（实测 34–40s），P95 会变成 NaN；必须显式 `maximum-expected-value` |

## 七、降级与安全

- **Prometheus/Grafana 挂了**：应用照常返回 200（指标只写在本地注册表，导出失败不影响业务）；
  统一概览把对应组件报 `DOWN`，不抛异常；
- **指标隧道没开**：Prometheus target down → `AiCodePlatformTargetDown` 告警；业务不受影响；
- **探针目标不可达**：`DOWN` + 一行摘要（异常短名），不返回堆栈、不返回响应正文；
- **端点/仪表盘**：只监听 `127.0.0.1`；安全组**不开** 19090/3001/9093；Grafana 匿名访问关闭；
- **密钥**：Grafana 管理员密码只在 `/opt/ai-code/monitoring/.env`（600）；仓库只有 `.env.example`；
- **统一概览**：仅管理员；端点地址剥离 userinfo。

## Boundaries

- **Always**：TDD（先 RED）；中文 JavaDoc；Week 16/17/18/19 契约零回退；
  指标名/标签由契约测试锁死；响应与文档不含密钥；监控设施故障不影响业务；规范 6.0 交付包齐全
- **Ask first**：改动 `libs/ai-core` Port 契约；引入 Loki/ELK；给 8082/8083 补指标出口；
  改 sshd `GatewayPorts`；把应用部署到云端
- **Never**：本周做业务功能；把 traceId/用户 ID 写成指标标签；在 Grafana/告警里写死密钥或 webhook；
  为监控把应用端口暴露公网；让探测异常冒泡成 500；改第 19 周网关鉴权/透传语义；
  引入注册中心或 K8s

## Success Criteria

- [x] `GET /api/v1/platform/observability/monitoring-overview`：未登录 401、非管理员 403、管理员 200；
      六个组件顺序固定（`otel` / `prometheus` / `grafana` / `skywalking` / `langfuse` / `gateway-metrics`）；
      响应含 `allHealthy` 与 `correlation.queryHints`，且**不含**任何密钥字段（`PlatformMonitoringHttpTest` 3 例）
- [x] 状态语义单测：关闭 → `DISABLED` 且不探测；启用不可达 → `DOWN` 且 `allHealthy=false`；
      SkyWalking 配置开但 Agent 未挂 → `DOWN`；URL 中的账号密码被剥离（`PlatformMonitoringUseCaseTest` 8 例）
- [x] 探针单测：2xx/3xx → UP，5xx/连接失败/非法 URL → DOWN（不抛异常）；TTL 内命中缓存、到期重探
      （`HttpMonitoringProbeTest` 7 例）
- [x] 指标名契约：真实 `PrometheusMeterRegistry` 抓文本，断言 `agent_run_count_total` /
      `agent_run_duration_seconds_*` / `llm_call_count_total` / `llm_tokens_total` / `llm_cost_usd_total` /
      `tool_call_count_total` 与标签名（`MonitoringMetricsContractTest` 4 例）
- [x] 网关：`/actuator/prometheus` 200 且含 `jvm_memory_used_bytes` 与 `application="agent-gateway"`；
      登录后出现 `gateway_session_login_count_total{outcome="SUCCESS"}`；四个网关指标单测全绿
      （`GatewayPrometheusEndpointTest` 2 例 + 3 个单测类共 13 例）
- [x] 部署物：`deploy/monitoring/`（compose + prometheus.yml + alerts.yml + Grafana provisioning + 2 张仪表盘
      + Alertmanager 模板 + `.env.example`）；`docker compose config -q` 在云端通过
- [x] 根聚合 `test` 全绿：**615** 用例（ai-core 90 + enterprise 21 + patient 19 + platform 385 + gateway 100），
      Week 8–19 用例零回退（platform 354 → 385，gateway 85 → 100，本周新增 46）
- [x] **真机端到端**（腾讯云）：Prometheus 2.55.1 / Grafana 11.3.1 容器 Up、`/-/ready` 200、
      三个 target（平台/网关/自身）全部 up、12 条告警规则加载、Grafana 两张仪表盘 provision 成功、
      数据源 `aicode-prometheus` 注册（`tools/cloud-accept-monitoring.sh`：**PASS=11 FAIL=0**）；
      跑真实 DeepSeek Agent（4 次，token 2830–4606）后，Prometheus 查到
      `agent_run_count_total` / `llm_tokens_total{prompt,completion}` / `llm_cost_usd_total=0.008333` /
      `tool_call_count_total` / `jvm_memory_used_bytes`，**P95 = 34.29s**；
      网关侧 `gateway_session_login_count_total` / `gateway_catalog_lookup_count_total{cache_hit}` /
      `gateway_agent_run_count_total` / Feign `http_client_requests_*` 均有数据
- [x] 交付物齐全：Spec / 架构 / 接口 / 部署文档 / 部署物 / 运维脚本 / Postman / 实现日志 / 阅读提纲

## 与计划的偏差（预登记）

| 计划（README） | 实际做法 | 原因 |
|----------------|----------|------|
| 「完成：SkyWalking / OpenTelemetry / Langfuse」 | 三项均为第 17–19 周已交付，本周只做**统一入口与关联**，不重复接入 | 再装一遍等于重复建设；本周真正的缺口是「没有统一视图、没有 Prometheus/Grafana、网关没有指标出口」 |
| 「Prometheus」「Grafana」 | 部署在**云端**（与 SkyWalking/Langfuse 同机），应用仍在本地 | 本机没有 Docker；云端常驻且已有 10 个容器，新增 2 个（+可选 1）在 4C8G 余量内 |
| 计划未提抓取路径 | 反向 SSH 隧道 + host 网络 | 唯一能「不暴露应用端口」的方案；细节见四、与 ADR |
| 计划未提告警 | 交付 12 条 Prometheus 规则 + 可选 Alertmanager | 没有告警的仪表盘只能「事后看图」；通知渠道留给团队配置，不写死密钥 |
| 计划未提网关指标 | 本周补网关指标出口 + 4 个业务指标 | 第 19 周网关只有 `health,info`，平台/网关无法同盘对比 |

## 真机暴露并修复的缺陷（事后补登记）

> 前 5 条是「只在真实环境 / 全量执行」才暴露的问题；另 3 条（`remote.ps1 -Cmd` 引号、
> PowerShell 脚本 BOM、Linux 端口归属排查）属工具链问题，完整 8 条见实现日志 10.2。

| # | 现象 | 根因 | 修法 |
|---|------|------|------|
| 1 | Prometheus 反复重启，`curl 9090` 返回 S3 `AccessDenied` XML | 云端 9090/9091 被 Langfuse 的 MinIO（S3 API + 控制台）占用 | 改用 **19090**，同步抓取自身 target / Grafana 数据源 / `monitoring.prometheus.base-url` / 隧道端口 |
| 2 | 网关启动失败：`No default constructor found` | 本周给用例加了带指标的**第二个构造器**，Spring 不知道选哪个 | 在完整构造器上加 `@Autowired`（Week 19 已踩过同类问题，这次是新增构造器重犯） |
| 3 | Grafana P95 面板为空，`histogram_quantile` 返回 NaN | Micrometer 默认**不导出**直方图桶，只有 `_count/_sum` | 配 `management.metrics.distribution.percentiles-histogram.*` |
| 4 | 加了直方图桶后 P95 仍是 NaN | 默认桶上界 30s < 真实 Agent 执行耗时（34–40s），只有 `+Inf` 有值 | 配 `maximum-expected-value`：Agent/LLM 120s、HTTP 30s |
| 5 | 单跑 `MicrometerObservabilityAdapterTest` 绿，和 Spring 测试同 JVM 跑红 | 应用上下文把「作用域事件 → MDC」监听器注册到**全局** OTel ContextStorage，缓存的上下文污染后续纯单测 | 监控 HTTP 测试加 `@DirtiesContext(AFTER_CLASS)`，并记录为测试隔离约定 |

## Open Questions

- 应用上云后（第 21+ 周）抓取目标改为应用真实地址，是否引入服务发现 → 暂用静态 target，规模不够再议
- Grafana 登录是否接企业 SSO（OAuth/LDAP）→ 本周只做本地管理员，等有真实多用户需求
- Alertmanager 通知渠道（企业微信/邮件）→ 模板已给，落地取决于团队
- 指标是否引入 exemplars 把 traceId 嵌进直方图 → Prometheus 需 OpenMetrics + 客户端支持，
  本周用「业务 traceId + 时间窗」关联已够用（YAGNI）
- 8082 patient-agent / 8083 enterprise 是否补指标出口 → 第 21 周收敛到 8084 时统一

## ADR（本周）

见 `docs/architecture/week-20-architecture.md` 第 6 节与实现日志第 12 节（含选项对照）。
