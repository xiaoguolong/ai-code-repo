# Java 转企业级 AI Agent 工程师学习计划

> 周期：6个月  
> 目标：从 Java / SpringCloud 后端工程师转型为企业级 AI Agent 应用架构师

## 最终目标

能够独立完成：

- [ ] 企业级 AI Agent 架构设计
- [ ] Spring AI Alibaba 开发
- [ ] LangChain4j 开发
- [ ] RAG 知识库建设
- [ ] Agent Workflow 设计
- [ ] Multi-Agent 系统设计
- [ ] Tool Calling 业务集成
- [ ] AI 应用权限、安全治理
- [ ] OpenTelemetry / Langfuse 可观测体系
- [ ] Docker / Kubernetes 部署


---

# 第一阶段：AI应用基础（第1个月）

目标：

理解 LLM 应用开发基础，完成第一个 AI 应用。


---

# 第1周：LLM基础

## 阅读

书籍：

- [ ] 《AI工程：大模型应用开发实战》

学习：

- [ ] Foundation Model 基础
- [ ] LLM 工作原理
- [ ] Token
- [ ] Context Window
- [ ] Prompt Engineering
- [ ] 模型调用流程


## 实践

环境准备：

- [ ] 安装 JDK17+
- [ ] 安装 Docker
- [ ] 安装 PostgreSQL
- [ ] 安装 Redis
- [ ] 创建 AI 学习代码仓库


项目：

spring-ai-demo


完成：

- [x] Spring Boot 创建项目
- [x] 调用 DeepSeek/Qwen/OpenAI API
- [x] 实现聊天接口
- [x] 保存请求日志
- [x] 统计 Token


---

# 第2周：Java AI开发基础


## 阅读

- [ ] 《Applied AI for Enterprise Java Development》

学习：

- [ ] Java 集成 LLM
- [ ] AI Service设计
- [ ] Prompt模板
- [ ] Structured Output


## 实践


技术：

- [ ] Spring Boot
- [ ] LangChain4j


完成：

- [ ] Chat接口封装
- [ ] Prompt模板管理
- [ ] 返回JSON结构
- [ ] Redis保存上下文


数据库：

创建：

- [ ] chat_session表
- [ ] chat_message表
- [ ] token_record表


---

# 第3周：RAG知识库


## 阅读

学习：

- [ ] RAG原理
- [ ] Embedding
- [ ] Vector Database
- [ ] Chunk切片
- [ ] Similarity Search


## 实践


搭建：

企业知识库


流程：

文件

↓

解析

↓

切片

↓

Embedding

↓

向量数据库

↓

检索

↓

LLM回答


完成：

- [ ] PostgreSQL安装
- [ ] pgvector安装
- [ ] 文档上传
- [ ] 文档解析
- [ ] 向量生成
- [ ] 相似度搜索
- [ ] RAG问答


---

# 第4周：企业知识库Agent V1


项目：

enterprise-knowledge-agent


完成：

- [ ] 用户登录
- [ ] 文档管理
- [ ] 知识库管理
- [ ] AI问答
- [ ] 历史记录
- [ ] Token统计
- [ ] Docker部署


阶段成果：

- [ ] 第一个企业AI应用完成


---

# 第二阶段：Agent开发（第2个月）

目标：

从 ChatBot 转向 Agent。


---

# 第5周：Agent基础


## 学习

- [ ] Agent概念
- [ ] ReAct
- [ ] Reason
- [ ] Action
- [ ] Observation
- [ ] Planning
- [ ] Memory


## 实践


项目：

patient-agent


实现：

- [ ] 用户提出任务
- [ ] Agent分析任务
- [ ] Agent自动执行步骤
- [ ] 返回结果


---

# 第6周：Tool Calling


## 学习

- [ ] Function Calling
- [ ] Tool设计
- [ ] Tool参数
- [ ] Tool权限


## 实践


开发Tools：


- [ ] PatientTool
- [ ] ReportTool
- [ ] HealthDataTool
- [ ] OrderTool


调用链：

用户

↓

Agent

↓

Tool

↓

业务服务

↓

数据库


完成：

- [ ] Agent查询患者信息
- [ ] Agent调用业务接口
- [ ] Agent组合多个数据源


---

# 第7周：Agent Memory


学习：

- [ ] 短期记忆
- [ ] 长期记忆
- [ ] Redis Memory
- [ ] Vector Memory


实践：

完成：

- [ ] 保存聊天上下文
- [ ] 保存历史任务
- [ ] 历史信息检索


---

# 第8周：Spring AI Alibaba


学习：

- [ ] Spring AI Alibaba架构
- [ ] Agent API
- [ ] Workflow
- [ ] Tool
- [ ] MCP


实践：

- [ ] 创建Spring AI Alibaba项目
- [ ] 接入Spring Cloud
- [ ] 实现Agent服务


---

# 第三阶段：Agent Workflow（第3个月）


目标：

掌握企业复杂流程Agent。


---

# 第9周：Workflow


学习：

- [x] State
- [x] Node
- [x] Edge
- [x] Graph


实践：

实现：

患者风险分析流程


流程：

- [x] 查询患者
- [x] 查询指标
- [x] 风险判断
- [x] 生成报告


---

# 第10周：Human In The Loop


学习：

- [ ] 人工审批
- [ ] Agent暂停
- [ ] Agent恢复


实践：

完成：

- [ ] 高风险任务人工审核
- [ ] 审核后继续执行


---

# 第11周：Multi Agent


学习：

- [ ] Supervisor Agent
- [ ] Agent通信
- [ ] Agent角色设计


实践：


设计：

医疗助手Agent


- [ ] 数据Agent
- [ ] 分析Agent
- [ ] 报告Agent
- [ ] 随访Agent


---

# 第12周：Agent平台V1


完成：

- [ ] Agent注册
- [ ] Agent配置
- [ ] Tool管理
- [ ] Workflow管理
- [ ] 执行记录


---

# 第四阶段：企业生产化（第4个月）


目标：

让Agent具备上线能力。


---

# 第13周：权限体系


学习：

- [ ] RBAC
- [ ] 数据权限
- [ ] Agent权限
- [ ] Tool权限


实践：

实现：


用户

↓

角色

↓

Agent

↓

Tool

↓

数据


---

# 第14周：AI安全


学习：

- [ ] Prompt Injection
- [ ] 数据泄露
- [ ] Guardrails


实践：

增加：

- [ ] Prompt过滤
- [ ] 输入校验
- [ ] 输出脱敏
- [ ] Tool白名单


---

# 第15周：Agent Evaluation


学习：

- [ ] Agent评估
- [ ] RAG评估
- [ ] Prompt评估


实践：

建立：

- [ ] 测试问题集
- [ ] 标准答案
- [ ] 自动评分


---

# 第16周：企业规范


完成：

- [x] API规范（统一响应信封 + traceId + 错误码枚举 + 契约测试）
- [x] 日志规范（traceId 全链路、MDC、统一访问日志、脱敏禁令）
- [x] 审计规范（AuditLogPort + audit_log 落库 + 4 类事件 + 只读查询 API）
- [x] 异常处理规范（错误码唯一来源、HTTP 语义、不泄漏堆栈/SQL/密钥）
- [x] 遗留补项：RBAC + ExecutionRecord 由内存改为 PostgreSQL 落库（Flyway V1–V3）


---

# 第五阶段：AI可观测性（第5个月）


目标：

解决Agent上线后的监控问题。


---

# 第17周：OpenTelemetry


学习：

- [x] Trace
- [x] Span
- [x] Metric
- [x] Context


实践：

接入：

- [x] Spring Boot
- [x] Agent调用链
- [x] LLM调用链

交付：Spec `docs/specs/week-17.md` ｜ 架构 `docs/architecture/week-17-architecture.md` ｜
接口 `docs/api/week-17-api.md` ｜ 实现日志 `notes/impl-logs/week-17.md` ｜
阅读提纲 `notes/week-17-opentelemetry.md` ｜ Postman `docs/postman/week-17.postman_collection.json`

实测：真实 OTLP collector 收到 span 树 `http post → agent.run → llm.chat / tool.call`，
日志 / 审计 / 响应体 / 链路后端四处 traceId 单源一致；`/actuator/prometheus` 暴露
`agent_run_count_total`、`llm_call_count_total`、`tool_call_count_total`、`llm_tokens_total`。


---

# 第18周：Langfuse


学习：

- [x] LLM Trace
- [x] Prompt管理
- [x] Token统计
- [x] 成本分析


实践：

部署：

- [x] Langfuse（v4 六服务：web / worker / ClickHouse / PostgreSQL / Redis / MinIO，headless 初始化免手工建 Key）
- [x] PostgreSQL
- [x] ClickHouse


接入：

- [x] Prompt记录（generation 上关联 `prompt.name` + `prompt.version`）
- [x] Token记录（真实 usage → `usage_details` JSON + `llm.tokens.total` 指标）
- [x] Agent执行链（trace → agent / generation / tool 观测树，traceId 与 `traceparent` 同源）

交付：Spec `docs/specs/week-18.md` ｜ 架构 `docs/architecture/week-18-architecture.md` ｜
接口 `docs/api/week-18-api.md` ｜ 部署 `docs/deploy/week-18-langfuse.md` ｜
部署物 `deploy/langfuse/`（compose + .env.example）｜ 实现日志 `notes/impl-logs/week-18.md` ｜
Postman `docs/postman/week-18.postman_collection.json`

实测：**在 192.168.132.128 上自部署 Langfuse v4.50.0 真机验收通过**（六容器 healthy，headless 初始化自动建好项目与 API Key）。

> 📌 **环境已迁移（2026-10-18）**：现网为腾讯云 4C8G 上的 **Langfuse 4.63.0**，与 SkyWalking 同时在线，
> 详见 [`docs/deploy/week-19-cloud-env.md`](docs/deploy/week-19-cloud-env.md)。下文 `192.168.132.128` 的数值为当时记录。
`langfuse.enabled=true` 时容器内**同时**存在 Boot 的 collector 导出器与自定义 Langfuse 导出器
（`SpanExporters` 收集到 2 个，一次埋点两路导出）；真实 LLM Agent Run 的 span 全部落进 v4 事件库：
`agent.run`(AGENT) / `llm.chat`(GENERATION)×2 / `tool.call`(TOOL)×2 + 框架自动埋点 span，
且每个业务与框架 span 都带 `trace_name=agent:medical-assistant` / `user_id=1` / `session_id=P001` /
`tags=[MEDICAL_ASSISTANT,medical-assistant]` / `environment` / `release`，traceId 与响应头 `traceparent` 一致；
generation 带真实 usage（如 `{'input':261,'output':2048,'total':2309}`）与成本（客户端上报与服务端推断口径一致），
并关联到 Langfuse 托管 Prompt（`medical-report@1` / `medical-followup@1`）；
正文默认不上报（`capture-content=false` 时正文长度全为 0），开启后经 Guardrail 脱敏 + 截断；
**Langfuse 停机时业务仍 200 COMPLETED**（只有 OTel SDK 自己记录导出 timeout）。
根聚合 **450** 用例全绿（8084 由 251 → 320，Langfuse 新增 63）。
真机验收还抓出并修复了 3 个只在真实环境暴露的缺陷（Node 堆上限、Langfuse 单价单位是「每 token」、
JDK HttpClient 明文 HTTP 的 h2c 升级），详见 `notes/impl-logs/week-18.md` 第 10.2 节。


---

# 第19周：SkyWalking


实践：

接入：

- [x] Gateway（新增 `apps/agent-gateway`：Spring Cloud Gateway 统一入口 + 统一鉴权 + Feign 出站）
- [x] Agent服务（8084 挂 Java Agent + Toolkit 手动埋点）
- [x] Spring Cloud服务（Spring Cloud 2024.0.2 / OpenFeign，网关进程内承载 Feign 调用）

监控：

- [x] HTTP（网关与平台端点指标、服务拓扑）
- [x] Feign（网关 → 平台调用链，组件 ID 11 = Spring Cloud Feign）
- [x] MySQL（平台 JDBC 语句耗时，复用既有业务库查询）
- [x] Redis（网关会话/缓存 Lettuce 调用命令与耗时）
- [x] JVM（实例堆/GC/线程/类加载 + 实例元数据）

交付：Spec `docs/specs/week-19.md` ｜ 架构 `docs/architecture/week-19-architecture.md` ｜
接口 `docs/api/week-19-api.md` ｜ 部署 `docs/deploy/week-19-skywalking.md` +
`docs/deploy/week-19-cloud-env.md` ｜
部署物 `deploy/skywalking/`、`deploy/data/`（compose + .env.example）｜
实现日志 `notes/impl-logs/week-19.md` + `notes/impl-logs/week-19-cloud.md` ｜
运维工具 `remote.ps1`、`provision.sh`、`tools/`（隧道/计划任务/镜像拉取/验收） ｜
Postman `docs/postman/week-19.postman_collection.json`

实测：**在 192.168.132.128 上自部署 SkyWalking 10.2.0（OAP + UI，存储用既有 PostgreSQL 独立库 `skywalking`）
真机验收通过**，网关（8080 / `gateway-1`）与平台（8084 / `platform-1`）各挂 Java Agent 9.7.0；
一次真实 Agent 执行（真实 DeepSeek 模型、真实 token 3473）在 OAP 侧可查到：

- **服务拓扑**：`ai-code-gateway → ai-code-platform`（服务端调用关系 15 次）；
- **中间件**：平台 → PostgreSQL(JDBC) 43 次、网关 → Redis 30 次、网关 → 平台 Feign 14 次、
  平台 → `api.deepseek.com` 13 次、平台 → Langfuse OTLP 39 次；
- **HTTP 端点清单**：网关 `Lettuce/Reactive/*`、平台各业务端点与 `Async/execute`；
- **实例元数据**：两实例的 JVM 参数 / OS / 主机 / 进程号 / jar 依赖清单齐全（UI 的 JVM 面板同源）；
- **三后端关联**：`bridgeAvailable=true`、`correlated=true`，span tag `aicode.trace_id` = 响应头 `X-Trace-Id`
  （SkyWalking 的 traceId 是 Base64 segmentId，与 W3C traceId 不可比，故用业务 tag 关联，不假装打通父子关系）；
- 根聚合 **569** 用例全绿（8084 由 320 → 354，新增网关模块 85）。
- 真机验收抓出并修复了 6 个只在真实环境暴露的缺陷（PowerShell 解析 `-D`、Agent 目录 `scp -r` 损坏、
  **WebFlux 下 Feign 缺 `HttpMessageConverters`**、**sa-token 头与前缀语义**、代理路由响应二次写出、
  多构造器缺 `@Autowired` 导致上下文起不来），详见 `notes/impl-logs/week-19.md` 第 10.2 节。

### 第 19 周 · 环境批次（2026-10-18）：搬到云上并解除「二选一」

原 VMware 节点只有 3.8 G，Langfuse（1.75 G）与 SkyWalking（1.03 G）**无法同时在线**。
本批次把两套观测后端 + PG16 + Redis7.2 迁到**腾讯云 4C8G / Ubuntu 22.04.5**，共 **10 个容器**并存：

| 组件 | 版本 | 入口 |
|------|------|------|
| PostgreSQL 16（`aidemo` + `skywalking` 两库） | 16.15 | `127.0.0.1:5432` |
| Redis | 7.2.16 | `127.0.0.1:6379` |
| SkyWalking OAP / UI | 10.2.0 | `:11800` / `:8088` |
| Langfuse | 4.63.0 | `:3000` |

> 第 20 周追加（同一台云主机，host 网络，只监听回环）：
> **Prometheus 2.55.1** `127.0.0.1:19090`（3 目标 / 12 告警 / 15d-2GB）、
> **Grafana 11.3.1** `127.0.0.1:3001`（2 张 provisioning 仪表盘）、
> 可选 **Alertmanager 0.27.0** `127.0.0.1:9093`。端口 19090 而非 9090，是因为
> 9090/9091 已被 Langfuse 的 MinIO 占用（详见 `docs/deploy/week-20-monitoring.md`）。

应用仍跑在**本地 IDEA**，通过 **SSH 隧道**（`tools/tunnel.ps1`，已注册 Windows 计划任务登录自启）
访问云端中间件 —— 数据库与 Redis 只绑回环，公网零暴露。实测结果：

- 本地应用 **7/7** 全链路通过（health / 登录 / trace-context / skywalking-status /
  agent 目录 / 执行记录 / auth-me）；
- 数据确实落云：OAP `listServices` 出现 `ai-code-platform`、今日 **72** 个 segment、
  实例名 **`platform-local`**；业务库 8 表 / 3 个种子用户 / Flyway V1–V3 全 OK；
- 网络实测：Docker Hub 不可达；**显式 mirror 主机名拉取 3 s，走 `docker.io` 名字 121 s**（差 40 倍），
  故采用「预拉 + retag」策略；
- 抓出并修复 **15 个真实缺陷**，其中三条的共性是「**报错信息指向的位置是错的**」：
  用户名拼错会同时伪装成「密码错误」与「密钥未注册」、配置读取的编码问题伪装成「未填」、
  `git check-ignore` 判错（看输出而非退出码）导致含密钥的备份差点入库；
- 迁移成本已压到 **改 4 处**（`server.conf` 的 HOST/私钥、安全组、镜像源、Docker apt 源），
  详见 `docs/deploy/week-19-cloud-env.md` 第 8 节。

> 详细过程：`docs/deploy/week-19-cloud-env.md`（部署与迁移手册）、
> `notes/impl-logs/week-19-cloud.md`（探针式 RED、15 条缺陷表、10 条 ADR）。


---

# 第20周：完整监控体系


完成：

- [x] SkyWalking（复用第 19 周：10.2.0 + Java Agent + Toolkit 业务 span + 服务拓扑）
- [x] OpenTelemetry（复用第 17 周：span 树 + `/actuator/prometheus` 指标出口）
- [x] Langfuse（复用第 18 周：LLM trace / token / 成本 / Prompt 管理）
- [x] Prometheus（2.55.1 自部署：3 个抓取目标 + 12 条告警规则 + 15d/2GB 保留）
- [x] Grafana（11.3.1 自部署：数据源与 2 张仪表盘 provisioning 自动加载）
- [x] 四后端统一自检（`GET /api/v1/platform/observability/monitoring-overview`，仅管理员）
- [x] 网关指标出口（第 19 周只有 `health,info`；本周 `/actuator/prometheus` + 4 个网关业务指标）
- [x] 指标契约（7 个平台指标 + 5 个网关指标的名字/标签用真实 Prometheus 注册表锁死）

交付：Spec `docs/specs/week-20.md` ｜ 架构 `docs/architecture/week-20-architecture.md` ｜
接口 `docs/api/week-20-api.md` ｜ 部署 `docs/deploy/week-20-monitoring.md` ｜
部署物 `deploy/monitoring/`（compose + Prometheus 抓取/告警 + Grafana provisioning + 2 张仪表盘
+ 可选 Alertmanager + `.env.example`）｜ 运维脚本 `tools/metrics-tunnel.ps1`、
`tools/run-local-gateway.ps1`、`tools/cloud-accept-monitoring.sh` ｜
实现日志 `notes/impl-logs/week-20.md` ｜ 阅读提纲 `notes/week-20-prometheus-grafana.md` ｜
Postman `docs/postman/week-20.postman_collection.json`

实测：**在腾讯云 4C8G 上自部署 Prometheus 2.55.1 + Grafana 11.3.1 真机验收通过**，
与 SkyWalking / Langfuse 共 12 个容器并存（内存余量约 3.1 GB）：

- **抓取链路**：本地平台 8084 / 网关 8080 → `tools\metrics-tunnel.ps1` 反向 SSH
  （云端 `127.0.0.1:18084/18080`）→ Prometheus 每 15s 抓取，三个 target 全部 `up`；
- **真实业务指标**：跑真实 DeepSeek Agent 后，Prometheus 查询到
  `agent_run_count_total{execution_status="COMPLETED"}`、`llm_call_count_total`、
  `llm_tokens_total{token_type=prompt|completion}`、`llm_cost_usd_total=0.008333`、
  `tool_call_count_total{PatientLookupTool,HealthMetricTool}`、`jvm_memory_used_bytes`；
  **P95 实算 34.29s**（`histogram_quantile` + 120s 桶上界）；
- **网关指标**：`gateway_session_login_count_total{outcome="SUCCESS"}`、
  `gateway_catalog_lookup_count_total{cache_hit=true/false}`、
  `gateway_agent_run_count_total{outcome="SUCCESS"}`、Feign `http_client_requests_*` 均有数据；
- **告警与仪表盘**：12 条 `AiCode*` 规则加载；Grafana 数据源 `aicode-prometheus` 与
  `aicode-platform-overview` / `aicode-gateway-overview` 两张仪表盘 provision 成功；
- 根聚合 **615** 用例全绿（平台 354 → 385，网关 85 → 100，本周新增 46）。
- 真机验收抓出并修复 5 个只在真实环境暴露的缺陷（**9090/9091 被 Langfuse MinIO 占用**、
  多构造器缺 `@Autowired` 导致网关起不来、**Micrometer 默认不导出直方图桶 → P95 永久 NaN**、
  **默认 30s 桶上界 < Agent 实际耗时 → P95 仍 NaN**、Spring 测试全局 OTel 监听器污染
  纯单测的 MDC 断言），详见 `notes/impl-logs/week-20.md` 第 10.2 节。


---

# 第21周：医疗 SaaS 起步（多租户 + 统一身份 SSO + Agent 1）


完成：

- [x] 多租户（`platform_tenant` + `platform_user.tenant_key`；`default` / `hospital-b` 两个租户）
- [x] 统一身份 SSO（平台 `POST /api/v1/platform/auth/verify` 为唯一身份源，**只校验不建会话**；
      enterprise 由 `sso.enabled` 开关式委托 + `sso_user_link` 首次登录自动开户，同名未建链拒绝合并）
- [x] Agent 1 患者分析 Agent（`patient-analysis`：查患者 → 查检查报告 → 查健康指标 → 规则风险 → 报告 → 写长期记忆）
- [x] 长期记忆按 租户 + 用户 隔离（`platform_memory`，隔离谓词写死在 SQL；admin 才可换 scope）
- [x] patient-agent（**8083**）标注 deprecated（`Deprecation` / `Sunset` / `Link` 三个响应头 + 启动告警，业务零改动）
- [x] 端口文档纠错：enterprise 实际 **8081**、patient-agent 实际 **8083**（规范 7.4 原写 8083/8082，已改正）

交付：Spec `docs/specs/week-21.md` ｜ 架构 `docs/architecture/week-21-architecture.md` ｜
接口 `docs/api/week-21-api.md` ｜ 实现日志 `notes/impl-logs/week-21.md` ｜
阅读提纲 `notes/week-21-saas-multitenancy.md` ｜ Postman `docs/postman/week-21.postman_collection.json`

实测：本周新增 **86** 个用例全绿，根聚合 **701** 用例零回退（ai-core 90 / enterprise 37 / patient 21 /
platform 453 / gateway 100）。关键验证：

- **真机（腾讯云 PostgreSQL 16 + 真实 DeepSeek + 真实 embedding）**：
  `V4/V5` 首次启动 `Successfully applied 2 migrations`，重启只校验不重跑、seed 只补缺；
  一次真实患者分析 `COMPLETED`、`degraded=false`、**1587 tokens**、5 步轨迹、写入记忆；
  语义检索命中 `score=0.6703`；`doctor_b`（hospital-b）查同一患者 **0 条**（跨租户隔离生效）；
- **租户**：停用租户登录与 `/verify` 均 401（不暴露「租户被停用」）；`GET /tenants` 仅管理员，运营 403；
- **SSO**：`/verify` 免登录、不返回 token、不建会话（成功写 `LOGIN` 审计，detail 带 `sso=true`）；
  平台不可达 → **503 `AUTH_SSO_UNAVAILABLE`**（不伪装成 401）；`sso.enabled=false` 时行为与第 4 周完全一致；
- **Agent 1**：模型不可用时 `degraded=true` 且执行 `COMPLETED`，报告明确标注「规则模板生成」；
- **记忆隔离**：同租户不同用户、同用户不同租户都查不到对方数据（H2 真实 SQL + 真机双重验证）；
  响应不含 embedding；非管理员带 `userId` 查他人 → 403；
- 真机抓出并修复 **3 个只在真实装配/初始化里出现**的缺陷（verify 漏配 Sa-Token 白名单、
  seed 以「表为空」为整体开关导致新权限/新用户补不上、平台 yml 只映射 embedding.provider 导致
  `EMBEDDING_API_KEY` 读不到、向量为空），详见 `notes/impl-logs/week-21.md` 第 10.2 节。

> 遗留（已转第 22 周处理，见下节）：其中「enterprise `.env` 环境迁移 + SSO 跨进程真机」已在第 22 周完成；
> 其余（记忆生命周期、`sso_user_link` 解绑、HITL、检查报告数据源）的**目标周次见
> [`docs/specs/week-22.md`](docs/specs/week-22.md) 的「未实现项与目标阶段」表**。

---

# 第22周：登录限流 + 应用上云（jar + systemd）+ enterprise/SSO 真机


完成（2026-10-24，**批次 1 代码/资产 + 批次 2 云端执行**）：

- [x] 登录 / 身份校验限流：`/auth/login` 与 `/auth/verify` 共用 `PlatformAuthenticationFacade`，
      按 `IP + 用户名` 滑动窗口计数，默认 5 次失败封 5 分钟；**被限流时不查库、不比对密码**
- [x] 429 语义独立：新增 `ApiErrorCode.RATE_LIMITED`（与 401 严格区分，避免客户端把「试太频」当「密码错」重试）
- [x] 客户端 IP：默认只信 `remoteAddr`；只有显式 `trust-forwarded-for=true`（前面有可信反代）才取 `X-Forwarded-For`
- [x] 应用上云：JDK 17 + `aicode` 用户 + systemd 两个单元，平台 8084 / 网关 8080 **只监听回环**；
      平台 11.5 s、网关 13 s 启动；Flyway 只校验（V4/V5 第 21 周已应用）
- [x] 部署资产：`deploy/app/`（systemd 单元、`remote-deploy.sh` 备份/切换/健康检查/失败自动回滚、
      Prometheus 目标切换脚本、云端验收脚本、nginx 模板）+ `tools/deploy-app.ps1` 一键部署
- [x] 反向隧道退役：Prometheus 目标改为 `127.0.0.1:8084/8080`，三个 target 全 up（第 20 周那条脆弱依赖消失）
- [x] **enterprise 上云 + SSO 真机**：独立库 `enterprise`（跳过 V6，云端 PG 无 pgvector）+ systemd 8081；
      平台账号首次登录自动开户并建链（platform 2 → enterprise 1），二次登录复用，本地会话有效

交付：Spec `docs/specs/week-22.md` ｜ 部署手册 `docs/deploy/week-22-app-deploy.md` ｜ 实现日志 `notes/impl-logs/week-22.md`

实测：根聚合 **726** 用例全绿；云端验收两份脚本均全通过：

- `cloud-accept-app.sh` **PASS=18 FAIL=0**（两次）：真实患者分析 **1713 / 2479 tokens**、`degraded=false`、
  5 步、写入记忆；operator 3 条记忆、doctor_b 跨租户 0 条；限流 `401 401 401 401 401 429`；
  `monitoring-overview` **allHealthy=true**（otel DISABLED 不参与判定）；
- `cloud-accept-enterprise.sh` **PASS=11 FAIL=0**：SSO 首次登录开户 + 建链（`2->1`）、二次登录复用、
  本地会话有效、平台登录不受影响；enterprise Flyway 为 `1,2,3,4,5,7`（V6 跳过）；
- 资源：平台 542 MB / 网关 442 MB / enterprise 436 MB，主机 available ≈ 1.6 GB、swap 12 MB；
- 真机抓出并修复 7 个只在真实运行环境暴露的缺陷（监控探针 **h2c 升级**导致 Langfuse 误报 DOWN、
  `chown` 后 `scp` 静默失败、`-Cmd` 带 `[` 被 PowerShell 拆坏、`flyway.locations` 字面量不可覆盖、
  生成 `.env` 重复键、验收脚本「空串相等」假绿、PowerShell 改写 UTF-8 脚本损坏），
  详见实现日志第 13/14 节。

> **未实现项**（HITL、记忆生命周期、SSO 解绑、租户级角色、云端 RAG、真实报告源、前端/CI、Agent 2–4）
> 已逐条标注目标周次与阻塞条件，见 [`docs/specs/week-22.md`](docs/specs/week-22.md)「未实现项与目标阶段」表。
> 其中 HITL 与记忆生命周期已在第 23 周批次 1 完成；SSO 解绑/租户级角色已在批次 2（A1+B2）完成并上线。

---

# 第23周：患者分析 HITL + 长期记忆生命周期（批次 1）+ SSO 解绑/租户角色（批次 2）


完成（2026-10-25，批次 1 + 批次 2 代码/测试）：

- [x] **HIGH 风险人工闸门**：`patient-analysis` 判定 HIGH 后停在 `PENDING_APPROVAL`，
      生成 `platform_approval` 待办并**不写随访记忆**；报告已生成，审核人可据报告决策
- [x] **跨进程审批**：`GET /approvals` 待办/历史、`GET /approvals/{id}` 详情、
      `POST /approvals/{id}/decision` 批准/拒绝；批准 → 写记忆 + `COMPLETED`，
      拒绝 → 不写记忆 + `REJECTED`；**决策不调用模型**
- [x] **原子决策**：`UPDATE ... WHERE status='PENDING'`，并发/重复决策只有一个人成功，另一个 409
- [x] **审批归属**：本人或 admin 可决策；他人 403、不存在 404；审计 `APPROVAL_SUBMIT` / `APPROVAL_DECIDE`
- [x] **记忆 TTL**：保存写 `expires_at`（默认 90 天，`<=0` 永不过期）；列表/语义检索都过滤过期；
      每日 03:30 清理（可关），admin 可手工 `POST /memories/purge-expired`
- [x] **按范围删除**：`DELETE /memories/{memoryId}`，谓词带 `tenant_key + user_id`，删不到 404；
      审计 `MEMORY_DELETE`
- [x] **向量重建**：保存写 `embedding_model=provider:model:dimension`；换模型后
      `POST /memories/rebuild-embeddings`（admin，100 条/批）重建旧/空向量，单条失败不中断；
      审计 `MEMORY_REBUILD`
- [x] **V6 迁移**：`platform_memory` 加两列 + 索引，新建 `platform_approval` + 3 索引；
      旧行 `expires_at=NULL`（不误删）/ `embedding_model=NULL`（待重建）
- [x] **演示数据**：`P002` 作为 HIGH 风险患者（168/104、HbA1c 9.6），供真机 HITL 验收
- [x] **批次 2 · A1**：enterprise `/api/v1/admin/sso-links` 运维端点（`X-Admin-Token`，fail closed）；
      支持列表/建链/重建/解绑，`deleteLocalUser=true` 解绑并删本地用户，避免 409 死结
- [x] **批次 2 · B2**：V7 把角色四表改为 `(tenant_key, role_key)` 并按租户复制；
      `execution_record.tenant_key` + 按 user 回填；权限按用户租户取角色，旧库回退 default

交付：Spec `docs/specs/week-23.md` ｜ 架构 `docs/architecture/week-23-architecture.md` ｜
接口 `docs/api/week-23-api.md` ｜ 迁移手册 `docs/deploy/week-23-migration.md` ｜
云端验收脚本 `deploy/app/cloud-accept-week23.sh` ｜ 实现日志 `notes/impl-logs/week-23.md` ｜
日志归档索引 `docs/logs-index.md` ｜ 内存调优记录 `docs/deploy/cloud-memory-tuning.md` ｜
云端运维手册 `docs/deploy/cloud-ops-runbook.md` ｜
Postman `docs/postman/week-23.postman_collection.json`

实测：根聚合 **777** 用例全绿（ai-core 90 / enterprise 37 / patient 21 / platform 529 / gateway 100；
Week 22 基线 726 → 777，新增 51）；V6 在 H2（PostgreSQL 兼容模式）首次启动
`Successfully applied 6 migrations ... now at version v6`，后续上下文只校验不重跑。
**真实 PostgreSQL 已于 2026-10-25 02:51 应用 V6**（`Successfully applied 1 migration ... now at version v6`，
重启幂等、5 条旧记忆不丢，备份 `/opt/ai-code/backup/week23-20261025-024942`）；
云端 `cloud-accept-week23.sh` **PASS=21 FAIL=0**：P002 HIGH **1731 tokens** → 待审 → 批准写记忆 →
重复决策 409 → 删除 → 5 条旧向量重建为 `openai:qwen3.7-text-embedding-flash:1024`。

> **批次 2 已完成并上线（2026-10-25，A1 + B2）**：
> enterprise 新增 `ENTERPRISE_ADMIN_TOKEN` + `/api/v1/admin/sso-links`（列表/建链/重建/解绑，
> `deleteLocalUser=true` 可避免 409 死结）；平台 V7 把角色四表改为 `(tenant_key, role_key)`
> 并按租户复制，`execution_record` 加 `tenant_key`；根聚合 **798** 用例
> （enterprise 55 / platform 532 / ai-core 90 / patient 21 / gateway 100）。
> 云端 V7 已应用、enterprise 已发布，批次 2 验收 **PASS=19 FAIL=0**（含 409/503 真机）；
> 备份 `/opt/ai-code/backup/week23-b2-20261025-062917`，部署/回滚见 `docs/deploy/week-23-batch2.md`。

---

# 第六阶段：企业项目实战（第6个月）


项目：

医疗 SaaS AI Agent 平台


---

# Agent 1：患者分析Agent


完成（Week 21 交付，平台 `patient-analysis`）：

- [x] 查询患者（`PatientLookupTool`）
- [x] 查询检查报告（`ReportLookupTool`，Week 21 新增）
- [x] 查询健康指标（`HealthMetricTool`）
- [x] 风险分析（复用 Week 9 `PatientRiskAssessor` 规则，可解释、可单测）
- [x] 自动生成报告（模型生成；模型不可用时降级为规则模板并显式标记 `degraded`）
- [x] **HITL（Week 23）**：HIGH 风险报告人工确认后才写随访记忆；批准/拒绝可跨进程、可重启
- [x] **记忆生命周期（Week 23）**：TTL / 按范围删除 / 换 embedding 模型后的向量重建
- [x] 附加：长期记忆落库（租户 + 用户隔离）、执行记录 / 审计 / RBAC / 四后端观测全链路


---

# Agent 2：运营分析Agent


完成：

- [ ] 数据分析
- [ ] 自动生成日报
- [ ] 异常发现


---

# Agent 3：知识库Agent


完成：

- [ ] 医疗文档管理
- [ ] RAG问答
- [ ] 权限控制


---

# Agent 4：随访Agent


完成：

- [ ] 生成随访计划
- [ ] 创建业务任务
- [ ] 消息提醒
- [ ] 人工审核


---

# 最终能力检查


## AI基础

- [ ] LLM
- [ ] Prompt
- [ ] Embedding
- [ ] RAG


## Agent能力

- [x] Tool Calling（Week 6：Patient/Report/HealthMetric/Order 工具 + RBAC/Guardrail/观测装饰器）
- [x] Memory（Week 7 会话记忆 + Week 21 租户/用户隔离长期记忆 + Week 23 TTL/删除/向量重建）
- [x] Workflow（Week 9–10：State/Node/Edge/Graph + 高风险暂停/恢复）
- [x] Multi Agent（Week 11：Supervisor + 数据/分析/报告/随访四个角色）
- [x] Human In The Loop（Week 10 workflow 暂停/恢复 + Week 23 平台级审批任务，跨进程可重启）


## Java能力

- [ ] Spring AI Alibaba
- [ ] LangChain4j
- [ ] Spring Boot
- [ ] Spring Cloud


## 企业能力

- [x] 权限（Week 13 RBAC：用户 → 角色 → Agent → Tool → 数据域，Week 16 落库；
      Week 23 B2：角色四表按 `tenant_key` 隔离，每个租户一份副本）
- [x] 安全（Week 14 Guardrail：注入拦截、输入校验、输出脱敏、Tool 参数白名单；
      Week 22：登录/身份校验按 IP+用户名 限流，超阈值 429，默认 5 次/5 分钟；
      Week 23 A1：enterprise SSO 运维端点用 `ENTERPRISE_ADMIN_TOKEN`，未配置即 403）
- [x] 审计（Week 16：AuditLogPort + audit_log 落库 + traceId 可追溯；
      Week 23：审批提交/决策、记忆删除/清理/重建新增 5 类事件）
- [x] 日志（Week 16：traceId 全链路 + 统一访问日志 + 结构化 key=value）
- [x] 多租户（Week 21：`platform_tenant` + `platform_user.tenant_key`，租户停用即拒绝登录；
      记忆按 租户+用户 隔离，隔离谓词在 SQL；enterprise ↔ platform 统一身份由平台 `/auth/verify` 提供；
      Week 23：记忆 TTL 过期可见性 + 按范围删除 + 向量来源标记/重建；
      角色按租户复制 + 执行记录 `tenant_key` + SSO 映射运维解绑/重建）
- [x] 可观测（Week 17：OTel span 树 + 调用/token 指标 + Prometheus 出口，日志与链路 traceId 单源；
      Week 18：Langfuse LLM 观测树 + 真实 token/成本 + Prompt 版本关联与托管，正文默认不出站；
      Week 19：SkyWalking 拓扑 + Java Agent 自动埋点 + 三后端关联；
      Week 20：Prometheus + Grafana 统一纳管，四后端一张体检表，12 条告警 + 2 张仪表盘）
- [ ] 规范落地度：响应信封与规范 5.6 草案仍有偏离（见 docs/specs/week-16.md 收尾审计）


## 运维能力

- [ ] Docker
- [ ] Kubernetes
- [ ] SkyWalking
- [x] OpenTelemetry（Week 17：Trace/Span/Metric/Context + OTLP 导出 + Prometheus 指标出口）
- [x] Langfuse（Week 18：v4 自部署 + OTLP 双导出 + trace 维度传播 + generation/tool/agent 观测 +
      Token/成本上报与成本查询；正文可选、脱敏后出站）
- [x] SkyWalking（Week 19：10.2.0 自部署（OAP+UI，PostgreSQL 独立库）+ Java Agent 自动埋点
      （HTTP/Feign/JDBC/Redis/JVM）+ Toolkit 业务 span + 服务拓扑；未挂 Agent 时业务不受影响）
- [x] Prometheus（Week 20：2.55.1 自部署（host 网络 / 只监听回环 / 端口 19090），
      平台 + 网关 + 自身三目标，12 条告警规则，反向 SSH 抓本地指标）
- [x] Grafana（Week 20：11.3.1 自部署，数据源与两张仪表盘 provisioning，
      Platform 业务/LLM 与 Gateway 入口两张盘，含跨后端深链）


---

# 最终目标

Java AI Agent 工程师

↓

企业级 AI 应用架构师