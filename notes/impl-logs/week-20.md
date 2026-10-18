# Week 20 实现日志 — 完整监控体系（Prometheus + Grafana + 四后端收口）

- 日期：2026-10-18
- 批次：1
- 对应 Spec：`docs/specs/week-20.md`
- 对应阅读：`notes/week-20-prometheus-grafana.md`
- 环境：本地 Windows（应用）+ 腾讯云 4C8G / Ubuntu 22.04.5（中间件），SSH 隧道连接

## 1. 本周目标

把「已接入三个后端」推进到「**可运维的一套监控**」：

1. 自部署 Prometheus 2.55.1 + Grafana 11.3.1（Alertmanager 0.27.0 可选），补齐 README 第 20 周五个组件；
2. 新增统一监控概览 API，一次返回 OTel / Prometheus / Grafana / SkyWalking / Langfuse / 网关指标六组件状态；
3. 给网关补 `/actuator/prometheus` 与 4 个网关业务指标（第 19 周只有 `health,info`）；
4. 用契约测试锁死指标名/标签，交付 12 条告警规则 + 2 张 provisioning 仪表盘；
5. 真机跑通「本地应用 → 反向隧道 → 云端 Prometheus → Grafana」全链路。

## 2. 边界

- Always：TDD（先 RED）；中文 JavaDoc；Week 16/17/18/19 契约零回退；指标名由真实注册表锁死；
  响应/文档不含密钥；监控设施故障不影响业务；规范 6.0 交付包齐全。
- Never：业务功能；traceId/userId 进指标标签；Grafana/告警写死密钥或 webhook；
  为监控暴露应用端口；探测异常冒泡成 500；改网关鉴权/透传语义；注册中心/K8s。

## 3. 增量架构

见 `docs/architecture/week-20-architecture.md`。核心是两条独立通道：

```text
业务：本地 gateway → Feign → platform → PG/Redis/LLM
监控：本地 /actuator/prometheus → ssh -R 18084/18080 → 云端 Prometheus:19090 → Grafana:3001
```

## 4. 新增类型清单

| 类型 | 名称 | 职责 |
|------|------|------|
| Domain | `MonitoringStatus` | UP / DOWN / DISABLED / UNKNOWN 四态 |
| Domain | `MonitoringComponent` / `MonitoringOverviewView` | 单组件体检结果 / 六组件 + correlation |
| Domain | `MonitoringProbePort` / `MonitoringProbeResult` | 健康探测端口（不抛异常、有超时、可缓存） |
| Domain | `MonitoringMetricNames` | 7 个平台指标名常量（导出名映射由契约测试锁死） |
| Infra | `MonitoringProperties` | `monitoring.*` 配置 + 默认值 + 归一 |
| Infra | `HttpMonitoringProbe` | JDK HttpClient，800ms 超时 + TTL 缓存 + 异常降级 |
| Infra | `MonitoringConfiguration` | 注册探测端口 |
| UseCase | `PlatformMonitoringUseCase` | 六组件判定 + 关联块 + 管理员校验 |
| DTO | `MonitoringOverviewResponse` | 对外 JSON 契约（status 字符串、无密钥字段） |
| Controller | `PlatformMonitoringController` | `GET /monitoring-overview` |
| Gateway Domain | `GatewayMetricNames` / `GatewayMetricsPort` | 网关 4 个指标 + 端口 + NOOP |
| Gateway Infra | `MicrometerGatewayMetricsAdapter` | 唯一允许 Micrometer 的网关类 |
| Test | `PlatformMonitoringHttpTest`(3) / `PlatformMonitoringUseCaseTest`(8) / `HttpMonitoringProbeTest`(7) / `MonitoringPropertiesTest`(5) / `MonitoringMetricsContractTest`(4) / `MonitoringOverviewResponseTest`(4) | 平台 31 例 |
| Test | `MicrometerGatewayMetricsAdapterTest`(5) / `GatewayAgentUseCaseMetricsTest`(5) / `GatewaySessionUseCaseMetricsTest`(3) / `GatewayPrometheusEndpointTest`(2) | 网关 15 例 |

## 5. RED

先写测试再写实现，首次 `test-compile` 失败（缺类）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am test-compile
```

输出（节选）：

```text
[ERROR] PlatformMonitoringUseCaseTest.java:[5,48] 错误: 找不到符号
[ERROR]  符号: 类 MonitoringComponent
[ERROR] MonitoringOverviewResponseTest.java:[3,48] 错误: 找不到符号
[ERROR] HttpMonitoringProbeTest.java:[4,48] 错误: 找不到符号
[ERROR] MonitoringMetricsContractTest.java:[3,48] 错误: 找不到符号
[INFO] BUILD FAILURE
```

网关侧 RED 为编译失败（`GatewayMetricNames` / `GatewayMetricsPort` 不存在），随后补实现。

## 6. GREEN

实现后分模块跑：

```powershell
# 平台 31 例
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am test `
  "-Dtest=MonitoringPropertiesTest,HttpMonitoringProbeTest,PlatformMonitoringUseCaseTest,MonitoringOverviewResponseTest,MonitoringMetricsContractTest,PlatformMonitoringHttpTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false"
# => Tests run: 31, Failures: 0, Errors: 0

# 网关 15 例
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-maven-jdk17.ps1 -pl apps/agent-gateway -am test `
  "-Dtest=MicrometerGatewayMetricsAdapterTest,GatewayAgentUseCaseMetricsTest,GatewaySessionUseCaseMetricsTest,GatewayPrometheusEndpointTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false"
# => Tests run: 15, Failures: 0, Errors: 0
```

根聚合：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-maven-jdk17.ps1 test
# => ai-core 90 / enterprise 21 / patient 19 / platform 385 / gateway 100
# => BUILD SUCCESS（合计 615）
```

## 7. 重构

- 指标名从字符串散落改为 `MonitoringMetricNames` 常量（5 个类引用），并用真实
  `PrometheusMeterRegistry` 抓文本做契约测试；
- `GatewayMetricsPort` 保持旧构造器（默认 `NOOP`），第 19 周 85 个用例一行未改；
- 用 `HttpMonitoringProbe` 承担超时/缓存/降级，用例只做状态映射，不写 IO；
- `MonitoringOverviewResponse` 与领域视图分离，避免把枚举序列化细节暴露给前端。

## 8. 质量门禁

- [x] 编译：根聚合 `test` BUILD SUCCESS
- [x] 单测：615 全绿（新增 46）
- [x] 分层：`io.micrometer.*` 只出现在 infrastructure；网关 `application` 只依赖 `GatewayMetricsPort`
- [x] 文档：新增 public 类型均有中文 JavaDoc
- [x] 安全：无密钥入库；概览响应无密钥字段；URL userinfo 被剥离；Grafana 密码只在云端 `.env`
- [x] 数据库脚本：本周无迁移
- [x] 日志：本文件已填 RED/GREEN/真机证据
- [x] 部署物校验：云端 `docker compose config -q` 通过
- [x] 契约测试：`target/surefire-reports/` 里确认新增测试类报告存在（非静默跳过）

## 9. 验证证据

### 9.1 真机验收脚本（`tools/cloud-accept-monitoring.sh`）

```text
== containers ==
grafana      Up 45 minutes
prometheus   Up 45 minutes (healthy)

== Prometheus readiness ==
  PASS  Prometheus /-/ready 200

== Prometheus scrape targets ==
  PASS  target ai-code-platform = up
  PASS  target ai-code-gateway = up
  PASS  target prometheus = up

== Prometheus alert rules ==
  PASS  已加载 12 条 AiCode* 告警规则

== sample metrics ==
  PASS  指标 agent_run_count_total 有数据
  PASS  指标 llm_tokens_total 有数据
  PASS  指标 tool_call_count_total 有数据

== Grafana ==
  PASS  Grafana /api/health 200
  PASS  仪表盘已 provision：aicode-platform-overview / aicode-gateway-overview
  PASS  数据源 aicode-prometheus 已注册

PASS=11  FAIL=0
```

补充：12 条规则的 `/api/v1/rules` 里 `health` 全为 `ok`、`lastError` 为空
（PromQL 语法和指标名都有效；改名会让规则静默返回空，所以这一项单独核对过）。

### 9.2 真实 Agent 执行后的指标值（Prometheus `/api/v1/query`）

```text
up{job="ai-code-platform"}                                   = 1
up{job="ai-code-gateway"}                                    = 1
agent_run_count_total                                        = 3
  labels: agent_key=medical-assistant, execution_status=COMPLETED,
          application=ai-code-platform, job=ai-code-platform
llm_call_count_total                                         = 6
  labels: gen_ai_request_model=deepseek-v4-pro, llm_outcome=SUCCESS
sum by (token_type) (llm_tokens_total)                       = completion 6985 / prompt 2394
sum by (gen_ai_request_model) (llm_cost_usd_total)           = 0.008333
tool_call_count_total                                        = 1 × PatientLookupTool + 1 × HealthMetricTool
histogram_quantile(0.95, rate(agent_run_duration_seconds_bucket[10m])) = 34.2857
gateway_session_login_count_total{outcome="SUCCESS"}         = 2
gateway_catalog_lookup_count_total{cache_hit="false"}        = 1
gateway_catalog_lookup_count_total{cache_hit="true"}         = 1
gateway_agent_run_count_total{outcome="SUCCESS"}             = 1
sum(rate(http_client_requests_seconds_count{job="ai-code-gateway"}[5m])) = 0.0035
jvm_memory_used_bytes{area="heap"}                           = 102114768
```

真实执行样本：`medical-assistant`，DeepSeek `deepseek-v4-pro`，token 2830 / 3372 / 4606，
耗时约 34–40s；路径 `supervisor → report_agent → supervisor → followup_agent → FINISH`。

### 9.3 Grafana 面板查询实测（与仪表盘同数据源、同 PromQL）

用 Grafana `/api/ds/query` 直接执行面板表达式（`aicode-prometheus` 数据源）：

```text
sum(rate(agent_run_count_total[5m]))                                  -> frames 有值（vector）
histogram_quantile(0.95, sum by (le) (rate(...bucket[10m])))          -> frames 有值（34.29）
sum by (token_type) (llm_tokens_total)                                -> completion / prompt 两条
sum(rate(gateway_session_login_count_total[5m]))                      -> 窗口内无新登录时为空（预期）
```

说明：Grafana 侧「数据源能查、仪表盘 JSON 已加载、查询有数据」三件事都已验证；
本机没有无头浏览器，未做截图级验收。

### 9.4 一键自检

```powershell
# 平台登录后
GET /api/v1/platform/observability/monitoring-overview  （X-Trace-Id: week20-overview）
=> 200；components = [otel, prometheus, grafana, skywalking, langfuse, gateway-metrics]
=> allHealthy=true（skywalking/langfuse 未启用 → DISABLED，不拉低整体）
=> correlation.traceId=week20-overview / correlationTag=aicode.trace_id
```

## 10. 风险与下周输入

### 10.1 风险

- **反向隧道是外部依赖**：`tools\metrics-tunnel.ps1` 断了，Prometheus 会 target down（告警会提示），
  业务不受影响；本机重启后需要重新执行（未注册为计划任务，避免与业务隧道抢端口）；
- **低流量下 P95 会时有时无**：`rate` 需要窗口内 2 个样本；演示环境一小时只有几次执行，
  P95 面板可能短暂空白，属正常；生产有稳定流量后消失；
- **Grafana 只有本地管理员**：没有 SSO/RBAC，多用户场景需要后续接入；
- **Alertmanager 默认不启动**：规则可见但不会外发通知，通知渠道由团队定；
- **指标标签基数**：新增标签必须走评审（本周只允许 agent/model/tool/status/outcome/error_code）。

### 10.2 真机暴露并修复的缺陷（按发现顺序）

| # | 现象 | 根因 | 修法 |
|---|------|------|------|
| 1 | Prometheus 容器 `Restarting`，日志 `listen tcp 127.0.0.1:9090: bind: address already in use` | 云端 9090 被 Langfuse 的 MinIO S3 API 占用 | 先改 9091，仍冲突 → 最终改 **19090**；同步 `prometheus.yml` 自身 target、Grafana 数据源、`monitoring.prometheus.base-url`、`tunnel.ps1`、验收脚本 |
| 2 | `curl 9090` 返回 `<Error><Code>AccessDenied</Code>...` | 打到的其实是 MinIO（端口同名不同服务） | 用 `ss -lntp` + `docker port` 确认归属；这条写进部署文档「故障排查」第 1/2 条 |
| 3 | 网关启动失败：`Failed to instantiate GatewayAgentUseCase: No default constructor found` | 本周加了第二个构造器（带 `GatewayMetricsPort`），Spring 无法在多个构造器间选择 | 在完整构造器上加 `@Autowired`（`GatewaySessionUseCase` 同样处理） |
| 4 | Grafana P95 面板空白，`histogram_quantile` 返回 `NaN` | Micrometer 默认**不导出**直方图桶（只有 `_count/_sum`） | 平台/网关都配 `management.metrics.distribution.percentiles-histogram.*` |
| 5 | 配了直方图桶后 P95 仍 `NaN` | 默认桶上界 30s < 真实 Agent 耗时（34–40s），全部落 `+Inf` | 配 `maximum-expected-value`：`agent.run.duration` / `llm.call.duration` 120s |
| 6 | `MicrometerObservabilityAdapterTest` 单跑绿、全量跑红（MDC 期望值变成 32hex） | Spring 应用上下文把「OTel 作用域事件 → MDC」监听器注册到**全局** ContextStorage，上下文被缓存后污染后续纯单测 | 监控 HTTP 测试加 `@DirtiesContext(AFTER_CLASS)`；并记录为测试隔离约定 |
| 7 | `remote.ps1 -Cmd` 里 `docker ps --format '{{...}}'` 被拆成 shell 管道，报 `grafana: command not found` | Windows PowerShell 5.1 传参丢失内嵌双引号 | 复杂命令写本地 `.sh` 用 `-Script` 执行（Week 19 的教训在 `-Cmd` 上重犯） |
| 8 | `tunnel.ps1` 报大量 `Unexpected token` / 中文乱码 | 批量替换时用 `UTF8Encoding(false)` 重写了 `.ps1`，丢了 BOM | PowerShell 脚本统一 `UTF8Encoding($true)`；bash 保持无 BOM |

### 10.3 下周输入（第 21 周：医疗 SaaS 实战）

- 应用上云后：Prometheus targets 改真实地址、Grafana 换成域名 + SSO（可选）；
- 8082 / 8083 是否收敛到 8084：收敛后只需维护一份指标配置；
- 告警通知渠道落地（企业微信/邮件）+ 值班手册；
- 把 `monitoring-overview` 的探针缓存与告警联动：target down 时概览直接给出最近的 `X-Trace-Id` 样例。

## 11. 交付物清单

- `docs/specs/week-20.md`、`docs/architecture/week-20-architecture.md`、`docs/api/week-20-api.md`
- `docs/deploy/week-20-monitoring.md`、`docs/postman/week-20.postman_collection.json`
- `deploy/monitoring/`（compose、prometheus、alerts、grafana、alertmanager、`.env.example`）
- `tools/metrics-tunnel.ps1`、`tools/run-local-gateway.ps1`、`tools/cloud-accept-monitoring.sh`
- `notes/week-20-prometheus-grafana.md`、本日志
- 代码：平台 10 个新类 + 5 处指标名重构；网关 3 个新类 + 2 个用例改造

## 12. 决策记录 ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 监控栈位置 | 本地 Docker / 云端 | 云端 | 本机无 Docker；云端已有 SkyWalking/Langfuse，余量 3.1 GB 足够 |
| 容器网络 | bridge + ports / host | host | 反向隧道只绑云端回环，bridge 访问不到；host 零 sshd 改动 |
| Prometheus 端口 | 9090 / 9091 / 19090 | 19090 | 9090/9091 均被 MinIO 占用（真机实测） |
| 本地指标抓取 | 公网暴露 / 反向 SSH | 反向 SSH | 指标端点无鉴权，不能暴露公网 |
| 统一自检权限 | 登录用户 / 管理员 | 管理员 | 暴露内部地址与拓扑 |
| 探针策略 | 实时 / 缓存 | 800ms + 30s 缓存 | 避免自检把后端打爆；与 15s 抓取同量级 |
| 指标名管理 | 字符串 / 常量 + 契约测试 | 常量 + 真实注册表断言 | 改名会让仪表盘静默变空 |
| 网关指标实现 | 直接注入 MeterRegistry / Port | Port + 适配器 | 分层约束；旧构造器默认 NOOP，零回退 |
| 告警通知 | 内置 webhook / 模板 | 模板 + Alertmanager 可选 | 渠道与密钥属团队配置，不入库 |
| 直方图桶 | 默认 / 显式 | 显式（含 120s 上界） | 默认无桶 + 30s 上界会让 P95 永久 NaN |
