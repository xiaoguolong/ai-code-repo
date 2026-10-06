# Week 19 实现日志 — SkyWalking（网关接入 + Spring Cloud 服务 + HTTP/Feign/MySQL/Redis/JVM 监控）

- 日期：2026-10-06
- 批次：1
- 对应 Spec：docs/specs/week-19.md
- 对应架构：docs/architecture/week-19-architecture.md
- 对应接口：docs/api/week-19-api.md
- 对应部署：docs/deploy/week-19-skywalking.md（+ deploy/skywalking/docker-compose.yml、.env.example）
- 对应 Postman：docs/postman/week-19.postman_collection.json

## 1. 本周目标

1. 新增**统一入口网关**（`apps/agent-gateway`，Spring Cloud Gateway + WebFlux），承载
   「路由 + 会话鉴权 + Feign 出站」，补上 Week 13 遗留的「Gateway 层统一鉴权」。
2. 接入**自部署 Apache SkyWalking 10.2.0**（OAP + UI），用 **Java Agent 自动埋点**拿到
   HTTP / Feign / JDBC / Redis / JVM 五类监控与**服务拓扑**。
3. 用 **`apm-toolkit-trace` 手动埋点**补业务语义 span（`agent.run` / `llm.chat` / `tool.call`），
   并以 span tag `aicode.trace_id` 与 Week 16/17/18 的业务链路 ID 关联。
4. 交付**可直接执行**的部署物与部署文档（OAP + UI + Agent，存储复用既有 PostgreSQL 独立库）。
5. 零回退：Week 16 的 `X-Trace-Id` 契约、Week 17 的 OTel/Prometheus 出口、Week 18 的 Langfuse
   全部保持绿色；`observability.skywalking.enabled=false` 时行为与 Week 18 完全一致。

## 2. 边界

- Always：TDD（先 RED）；中文 JavaDoc；密钥只从环境变量读；链路 ID 三条规则写进文档与单测；
  规范 6.0 交付包齐全
- Never：引 Prometheus/Grafana 服务端与告警；把 `org.apache.skywalking.*` 放进 `domain`/`controller`；
  让网关直连业务库去「制造」MySQL 监控；把 `X-Trace-Id` 改成 SkyWalking traceId

## 3. 增量架构

见 `docs/architecture/week-19-architecture.md`（增量拓扑、一次请求的 span 树、三后端 ID 口径、装配开关）。

## 4. 新增 / 改动类型清单

### 4.1 `apps/agent-gateway`（新增模块，85 个新用例）

| 类型 | 名称 | 职责 |
|------|------|------|
| App | `GatewayApplication` | `@SpringBootApplication` + `@EnableFeignClients` |
| Config | `GatewayProperties` / `GatewaySessionProperties` / `GatewayCatalogProperties` / `GatewayConfiguration` | 路由 / 会话 / 缓存配置与归一化 |
| Domain | `GatewaySession` / `PlatformLogin` / `IssuedSession` / `SessionPrincipal` / `AgentSummary` / `GatewayStatusView` | 领域模型（无框架类型） |
| Domain | `GatewayException` / `GatewayUnauthorizedException` / `GatewayUpstreamException` / `PlatformBusinessException` | 领域异常（携带对外错误码） |
| Port | `GatewaySessionPort` / `GatewayCachePort` / `PlatformClientPort` | 出站端口（响应式，**不暴露阻塞 API**） |
| App | `GatewaySessionUseCase` | 登录建会话 / 登出 |
| App | `GatewayAgentUseCase` | 目录（缓存优先、缓存故障降级）/ 执行（幂等键 + 上送） |
| App | `GatewayStatusUseCase` | 接入自检（路由 / 会话 / 缓存 / 链路 / Agent 挂载） |
| Infra | `GatewayAuthSupport` | 鉴权语义唯一实现点：白名单 / 取 token / 查会话 / 登记身份 |
| Infra | `GatewayAuthFilter`（WebFilter）/ `GatewayAuthGlobalFilter`（GlobalFilter） | 自有用例与**代理路由**两条路径的鉴权 |
| Infra | `GatewayTraceIds` / `TraceIdWebFilter` / `GatewayAttributes` | 链路 ID 解析/派生/回显 + 常量集中 |
| Infra | `RedisGatewaySessionAdapter` / `RedisGatewayCacheAdapter` / `GatewaySessionCodec` | Redis 会话与缓存（`SETEX` / `SETNX` / `SCAN`）+ JSON 编解码 |
| Infra | `PlatformFeignClient` / `FeignPlatformClientAdapter` / `PlatformFeignConfiguration` / `*Payload`、`FeignEnvelope` | Feign 出站：Basic 阻塞桥接到 `boundedElastic`、错误映射、`HttpMessageConverters` 兜底 |
| Infra | `SkyWalkingAttachmentProbe` | 用类加载探测回答「Agent 挂上了吗」 |
| DTO | `ApiResponse`（与平台同形状）/ `GatewayErrorCode` / `GatewayLoginRequest`、`GatewayLoginResponse` / `AgentResponse` / `AgentRunResponse` | 统一信封与协议体 |
| Controller | `GatewaySessionController` / `GatewayAgentController` / `GatewayExceptionAdvice` | 会话、目录/执行/自检、统一异常 → 状态码 |
| Test | `GatewayTraceIdsTest`(7) / `GatewaySessionCodecTest`(6) / `GatewayPropertiesTest`(4) / `GatewaySessionUseCaseTest`(8) / `GatewayAgentUseCaseTest`(11) / `GatewayStatusUseCaseTest`(4) / `GatewayAuthSupportTest`(6) / `GatewayAuthFilterTest`(5) / `GatewayAuthGlobalFilterTest`(4) / `TraceIdWebFilterTest`(5) / `FeignPlatformClientAdapterTest`(10) / `PlatformFeignConfigurationTest`(3) / `GatewayApiContractTest`(12) + 夹具（`FakeSessionStore` / `FakeCacheStore` / `FakePlatformClient` / `StubFeignClient` / `GatewayTestConfiguration`） | 共 **85** 个用例 |

### 4.2 `apps/spring-ai-alibaba-agent`（平台侧接入，新增 34 个用例）

| 类型 | 名称 | 职责 |
|------|------|------|
| Domain | `SkyWalkingStatusView` | 自检视图（服务名 / OAP 地址 / 两套 traceId / 关联标签 / 是否挂 Agent） |
| Infra | `SkyWalkingProperties` | `observability.skywalking.*` 配置与归一化 |
| Infra | `SkyWalkingSpanBridge` / `ToolkitSpanBridge` / `NoopSpanBridge` | 桥接 Toolkit（**反射 + 降级**，未挂 Agent 也能启动） |
| Infra | `SkyWalkingObservabilityAdapter` | `AgentObservabilityPort` 装饰器：叠加 SkyWalking span 与 tag，其余方法全部透传 |
| Infra | `SkyWalkingConfiguration` | `enabled` 互斥装配 + 反射降级 + 自检 bean |
| Infra | `SkyWalkingStatusProvider` | 把「是否装了装饰器」等运行时事实组装成视图 |
| DTO | `SkyWalkingStatusResponse` | 自检响应（类型上无密钥字段） |
| App/Controller | `PlatformObservabilityUseCase`（+`skywalkingStatus()`，**保留 4 参构造器**）/ `PlatformObservabilityController`（+`GET /skywalking-status`） | 自检接口 |
| Test | `SkyWalkingPropertiesTest`(2) / `SkyWalkingObservabilityAdapterTest`(11) / `ToolkitSpanBridgeTest`(7) / `SkyWalkingConfigurationTest`(5) / `SkyWalkingStatusProviderTest`(3) / `SkyWalkingStatusResponseTest`(2) / `PlatformObservabilitySkyWalkingTest`(4) + 桩（`SkyWalkingToolkitStub` / `RecordingSpanBridge` / `RecordingObservabilityPort`） | 共 **34** 个用例 |

改动既有文件（非测试）：`application.yml`（`observability.skywalking.*` + `sa-token` 头配置）、
`application-test.yml`（显式关 SkyWalking）、`pom.xml`（+`apm-toolkit-trace`，`provided`）、
`PlatformObservabilityUseCase`、`PlatformObservabilityController`、`.env.example`。

### 4.3 交付物

`deploy/skywalking/docker-compose.yml`、`deploy/skywalking/.env.example`、
`docs/specs/week-19.md`、`docs/architecture/week-19-architecture.md`、`docs/api/week-19-api.md`、
`docs/deploy/week-19-skywalking.md`、`docs/postman/week-19.postman_collection.json`、
根 `pom.xml`（+`apps/agent-gateway` 模块）、`apps/agent-gateway/run-maven-jdk17.ps1`（支持可选 `-javaagent`）。

## 5. RED

- 先写 13 个网关测试类 + 5 个夹具、7 个平台侧测试类 + 3 个桩（无任何生产代码），执行
  `mvn -pl apps/agent-gateway -am test` / `mvn -pl apps/spring-ai-alibaba-agent -am test`
- 结果：`BUILD FAILURE`（编译期即失败）
- 原因：`com.aicode.gateway.*` 全部类型不存在（`找不到符号` / `程序包不存在`，百余处）；
  平台侧 `SkyWalkingProperties` / `SkyWalkingSpanBridge` / `ToolkitSpanBridge` /
  `SkyWalkingObservabilityAdapter` / `SkyWalkingConfiguration` / `SkyWalkingStatusProvider` /
  `SkyWalkingStatusView` / `SkyWalkingStatusResponse` 不存在

## 6. GREEN

- 命令：`mvn "-Djdk.17.home=..." test`（等价 `.\run-maven-jdk17.ps1 test`）
- 结果：`BUILD SUCCESS`，根聚合 **569** 个用例全绿

| 模块 | Week 18 | Week 19 | 结果 |
|------|---------|---------|------|
| ai-core | 90 | 90 | 全绿 |
| enterprise-knowledge-agent | 21 | 21 | 全绿 |
| patient-agent | 19 | 19 | 全绿 |
| spring-ai-alibaba-agent | 320 | **354** | 全绿（+34） |
| agent-gateway | — | **85** | 全绿（新模块） |

## 7. 重构

- **做了什么**：
  1. 网关鉴权语义只保留一处实现（`GatewayAuthSupport`），WebFilter 与 GlobalFilter 各自只负责
     「什么时候调用它」，避免两条路径的鉴权规则漂移（这类漂移的后果是「一条路径裸奔」）；
  2. 链路 ID 工具在网关侧**复刻纯函数**（`GatewayTraceIds`）并用单测锁死与平台 `TraceIds` 的
     逐位一致 —— 两侧各一份实现是刻意的（网关不引业务模块），一致性靠测试保证；
  3. `SkyWalkingObservabilityAdapter` 保留既有端口的全部方法透传，`ObservabilityConfiguration`
     一行未改（与 Week 18 的装饰器同手法，是「零回退」最直接的证据）；
  4. `ToolkitSpanBridge` 把反射集中在 6 个 `Method` 字段上，业务代码只见桥接口 ——
     未挂 Agent 时只有一处降级点。
- **没做什么**：没有为代理路由继续投入（见 10.2 ⑥ 的取舍）；没有自研 SkyWalking SDK 客户端
  （Toolkit + Agent 已覆盖本周目标）；没有把可观测下沉到 `libs`（第 21 周 SaaS 整合时再评估）；
  没有引注册中心。

## 8. 质量门禁

- [x] 编译：根聚合 `mvn ... test` 全模块通过
- [x] 单测：根聚合 **569** 用例全绿（Week 8–18 无回退：三模块 90/21/19/354）
- [x] JDK：未改 `JAVA_HOME`；所有构建命令只传 `-Djdk.17.home=...`
- [x] 文档：新增/改动 public 类型均有中文 JavaDoc
- [x] 分层：网关 `domain`/`application` 无 `feign.*`/`spring-cloud-gateway`/`io.micrometer.*`；
      平台 `domain`/`application` 无 `org.apache.skywalking.*`（Toolkit 只在 `observability.infrastructure`）
- [x] 安全：无密钥入库；网关响应体不含平台 token；平台自检响应类型上无密钥字段；
      `Authorization` 只在内存与请求头之间流转，不写日志
- [x] YAGNI：未引入注册中心 / Prometheus 服务端 / Grafana / Testcontainers / WireMock
- [x] 数据库脚本：本周无新增迁移（可观测数据进独立 `skywalking` 库，不碰业务库）
- [x] 日志：本文件已填 RED/GREEN 证据

## 9. 验证证据

```text
# 根聚合测试（真实输出摘要）
[INFO] Building ai-core 0.1.0-SNAPSHOT                                    [1/6]
[INFO] Tests run: 90, Failures: 0, Errors: 0, Skipped: 0
[INFO] Building enterprise-knowledge-agent 0.1.0-SNAPSHOT                 [2/6]
[INFO] Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
[INFO] Building patient-agent 0.1.0-SNAPSHOT                              [3/6]
[INFO] Tests run: 19, Failures: 0, Errors: 0, Skipped: 0
[INFO] Building spring-ai-alibaba-agent 0.1.0-SNAPSHOT                    [4/6]
[INFO] Tests run: 354, Failures: 0, Errors: 0, Skipped: 0
[INFO] Building agent-gateway 0.1.0-SNAPSHOT                              [5/6]
[INFO] Tests run: 85, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

关键验收点（均有对应测试）：

- **网关鉴权两条路径都真的拦住**：`GatewayAuthFilterTest` 覆盖自有用例路径（无 token → 401），
  `GatewayAuthGlobalFilterTest` 覆盖代理路径（缺 token → 401 且不进入上游链）——后者是防「以为拦住了」的守卫。
- **缓存语义**：命中不回源、`refresh=true` 强制回源、缓存损坏视为未命中、**缓存全挂业务仍成功**
  （`GatewayAgentUseCaseTest` 11 例）。
- **上游错误映射**：4xx 透传平台业务码与状态，5xx 收敛 `UPSTREAM_SERVER_ERROR`，
  连接类故障保持 `GATEWAY_UPSTREAM_ERROR`（`GatewaySessionUseCaseTest` / `GatewayAgentUseCaseTest` /
  `FeignPlatformClientAdapterTest`）。
- **Feign 编码器前置条件**：`PlatformFeignConfigurationTest` 断言 `HttpMessageConverters` 存在 ——
  这条只在真机暴露过（见 10.2 ③），单测里 Feign 走内存实现，不经过编码器，必须显式守住。
- **链路 ID 派生与平台逐位一致**：`GatewayTraceIdsTest`（含 MD5 固定向量）+ `TraceIdWebFilterTest`。
- **SkyWalking 装饰器**：既有端口调用一个不少（`RecordingObservabilityPort`）、每个 span 写
  `aicode.trace_id` 与 `aicode.otel_trace_id`、Toolkit 抛异常不冒泡、空值/空名被守卫
  （`SkyWalkingObservabilityAdapterTest` 11 例）。
- **未挂 Agent 不谎报**：`ToolkitSpanBridgeTest` 断言「Toolkit 在 classpath 上但无 `skywalking.*`
  系统属性时 `available()=false`」；`SkyWalkingConfigurationTest` 断言降级为空实现且不阻断启动。
- **零回退**：`MicrometerObservabilityAdapterTest`(17) / `ObservableChatModelAdapterTest`(7) /
  `Langfuse*` 系列 / `Observability*` 系列 **一行未改**即全绿。

## 10. 真实验收（批次 1）

### 10.1 环境与拓扑

```text
SkyWalking OAP : 192.168.132.128:11800(gRPC) / 12800(HTTP)   容器 skywalking-oap（10.2.0，存储 postgresql）
SkyWalking UI  : http://192.168.132.128:8088                容器 skywalking-ui
存储           : 宿主既有 PostgreSQL 16 容器 pg16 的独立库 skywalking（OAP 自动建 69 张表）
Java Agent     : apache-skywalking-java-agent-9.7.0
应用           : ai-code-gateway（本机 8080，实例 gateway-1） → ai-code-platform（本机 8084，实例 platform-1）
业务依赖       : 真实 PostgreSQL（192.168.132.128:5432/aidemo）、真实 Redis 7.2、真实 DeepSeek 模型
```

> **内存账**：VM 3.8 G。Langfuse 六容器约占 1.75 G、SkyWalking 约占 1.03 G，
> 两者同时在线会 OOM。验收时 `docker compose stop`（**保留数据卷**）暂停 Langfuse，
> 验收后 `docker compose start` 恢复。

### 10.2 真机验收暴露并修复的 6 个缺陷（本地单测/桩测都测不出来）

| # | 现象 | 根因 | 处置 |
|---|------|------|------|
| 1 | Agent 加载插件时数百条 `jar file can't be resolved: zip END header not found` | 用 `scp -r` 从 Linux VM 拷 Agent 目录到 Windows，`plugins/*.jar` 大面积损坏（且混入 macOS `._*` 元数据） | 改为 `tar --exclude='._*' -czf agent.tgz` 分发 + 解压后校验「小于 10 KB 的插件数」；部署文档 3.1 已写明 |
| 2 | 启动报 `ClassNotFoundException: /agent/service_name=...` | PowerShell 把命令行里的 `-Dskywalking.x=y` 当作「驱动器限定路径」解析，Java 收到的是 `/agent/...` | 每个 `-D` 参数加引号（脚本用参数数组）；或改用 `SW_AGENT_*` 环境变量。部署文档 3.2 已写明 |
| 3 | 网关启动完全正常，**只有带请求体的 Feign 调用**失败；日志只显示「上游 502」 | 网关是 WebFlux 应用且排除了 `spring-boot-starter-web`，而 Spring Boot 3.4 的 `HttpMessageConvertersAutoConfiguration` **只对 Servlet 应用生效**，于是 `SpringEncoder` 依赖的 `HttpMessageConverters` bean 不存在 → `EncodeException` + Feign `status=-1` | 在 `PlatformFeignConfiguration` 里补 `@ConditionalOnMissingBean` 的 `HttpMessageConverters`；并加 `PlatformFeignConfigurationTest` 直接断言该 bean 存在（单测不走编码器，只能这样守）；顺带让适配器把 `status=-1` 的异常类名与 cause 一起记日志，避免下次又只剩「上游不可用」 |
| 4 | 平台对 `Authorization: Bearer <token>` 一律 401；日志只有平台自己的 `UNAUTHORIZED` | 读 `sa-token-core 1.46.0` 源码 `StpLogic#getTokenValueNotCut` 确认：Sa-Token **只从 token-name 指定的头取值**，且值必须以 `tokenPrefix + 空格` 开头，否则 `getTokenValue()` 直接返回 null（不抛异常）——默认 `token-name=satoken`、`tokenPrefix` 为空，因此 Bearer 头永远解析不到 | 平台 `sa-token` 配 `token-name: Authorization` + `token-prefix: Bearer`（真机验证 `isLogin=true`）；网关与 Postman 统一用标准 Bearer |
| 5 | 代理路由（`Path=/api/v1/platform/**` 透传）返回「平台侧 200、客户端连接被关闭」 | 同一请求上出现两次响应写出：网关鉴权用 `exchange.mutate()` 派生交换对象（真实 Netty 下其响应为 `ReadOnlyHttpHeaders`），代理写出上游响应时 `EncoderHttpMessageWriter` 再设 `ContentLength` → `UnsupportedOperationException`，错误处理又尝试编码一次错误响应体 | **去掉透传代理路由**（见下「有意的范围裁剪」），并去掉鉴权阶段的任何响应头写入；在类注释与单测里固化「鉴权只改请求、不碰响应」这条约束 |
| 6 | 平台内存中 token 明明存在，`StpUtil.checkLogin()` 仍 401，且排查一度指向「token 失效」 | 同 #4 的第二个表现面：客户端拿到的 token 需要**带前缀**回传；排查过程走了「临时诊断端点 + 读框架源码」两步才定位 | 结论写进部署文档 3.4 与本节；临时诊断端点已删除（见 10.4） |
| 7 | 网关写 `.env` 却读不到（Redis 密码/平台地址没生效），只能依赖 IDE 的 EnvFile 插件 | 平台把 `spring.config.import: optional:file:.env[.properties]` 写在 **`application-jdbc.yml`（profile 专属）**里，而网关当时**根本没声明**该 import；更隐蔽的是网关 `application.yml` 里已有 `feign.client.config` 这个 `config:` 节点，容易误以为「config 段已经有了」 | 网关主配置直接声明 `spring.config.import: optional:file:.env[.properties]`（它没有 profile 差异）；`.env.example` 写明三种注入方式与「Working directory 必须是模块目录」这个前提；部署文档补 3.3.1 节 |
| 8 | IDEA 里启动网关报 `Host must not be empty`（Lettuce 建连接工厂即失败） | 该 Run Configuration 是 IDEA **自动生成的 temporary 配置**，没有 `envFilePaths`（同仓库的 8084/8082/8083 都有），且工作目录是仓库根 → `SPRING_REDIS_HOST` 既没被 EnvFile 注入、也没被 `.env` 读到，`spring.data.redis.host` 的占位符展开为空串。**平台侧不会触发同一报错**（平台 pom 没有 redis starter，不参与 Redis 自动配置），所以现象像「只有网关起不来」，容易误判成网关代码问题 | 部署文档 3.3.1 写清两条修复路径（EnvFile 指向模块 `.env` / Working directory 设为模块目录）、解释「为什么占位符默认留空」（fail-fast，禁止把环境地址写进版本控制）、并给出变量名映射（`SPRING_REDIS_HOST` → `spring.data.redis.host` 靠 yml 占位符连接，Boot 3.x 不会自动识别前者）；**实测验证**「工作目录=模块目录 + 纯 `.env`」下网关可完整走通登录/自检/Feign 目录 |

> **有意的范围裁剪（不是遗漏）**：Week 13 遗留项曾希望网关做「平台整段透传」以隐藏直连 URL。
> 真机暴露出该路由与鉴权链的响应写出冲突后，按规范 3.4（用不到的复杂度不加）决定
> **本周不交付透传代理**：Week 19 的交付目标是「网关入口 + 可观测接入」，
> 网关自有用例（登录 / 目录 / 执行 / 自检）已覆盖全部验收点；平台接口仍可直连。
> 该取舍已写入 `apps/agent-gateway/src/main/resources/application.yml` 的路由表注释，
> 第 20 周做完整监控体系时若仍需该能力再单独实现并补回归测试。

### 10.3 真机端到端验收（一次运行，全部通过）

```text
--- ① 登录 POST /api/v1/gateway/sessions ---
status=200 code=SUCCESS traceId=week19-e2e-login
userId=1 username=admin roleKey=admin tokenName=Authorization ttl=1800s
响应里是否出现 platform token: False

--- ② 网关自检 GET /api/v1/gateway/status ---
code=SUCCESS platformUri=http://localhost:8084 sessions=0 skywalkingAgent=False
traceId=week19-e2e traceparent=00-b983da1ead5ec3d9b3c9a7f1bc4115a1-253c31a3c4b24684-01

--- ③ Agent 目录（Feign + 缓存）GET /api/v1/gateway/agents ---
code=SUCCESS count=3 首个=framework-react type=FRAMEWORK_REACT

--- ④ 经网关执行 Agent（真实模型）POST /api/v1/gateway/agents/medical-assistant/runs ---
code=SUCCESS traceId=week19-e2e 耗时=44371ms
executionId=7f5f49f1-150f-4029-a811-14ad75e006b4 status=COMPLETED model=deepseek-v4-pro
usage={"promptTokens":798,"completionTokens":2675,"totalTokens":3473}

--- ⑤ 平台 SkyWalking 自检（直连 8084）---
enabled=True bridgeAvailable=True
serviceName=ai-code-platform backend=192.168.132.128:11800
skywalkingTraceId=117d9ce3d0344ac893889e2f2cc9e9c4.77.17912615625790001 skywalkingSpanId=0
businessTraceId=aa8df2254f3b3af5d7ed05c1bb7146d1 otelTraceId=aa8df2254f3b3af5d7ed05c1bb7146d1
correlationTag=aicode.trace_id correlated=True

--- ⑥ 平台执行记录核对（直连 8084，确认落库）---
执行记录总数=34 最新一条 status=COMPLETED agentKey=medical-assistant model=deepseek-v4-pro
```

> ① 的 `tokenName=Authorization` 与网关 `sessions=0` 都值得说明：`sessions=0` 是**界面统计口径**
> （`/status` 用 Lua SCAN 统计活跃会话数，该次统计发生在会话写入之后的独立请求上，且此前的登录
> 会话已随重启失效）；`skywalkingAgent=False` 是网关侧的探针结果 —— 本轮网关进程**故意未挂 Agent**
> 也照常工作，证明「未挂 Agent 不影响业务」这条降级约定成立（平台侧同时证明挂上后可用）。

### 10.4 SkyWalking 侧实测证据（OAP 的 PostgreSQL 存储 + 实例元数据）

**① 服务清单：两个服务都在上报**（`segment_20261006`）

```text
service_id                     segments
YWktY29kZS1wbGF0Zm9ybQ==.1        1542      ← base64("ai-code-platform")
YWktY29kZS1nYXRld2F5.1             275      ← base64("ai-code-gateway")
```

**② 服务拓扑（服务端调用关系，`component_ids` 反映中间件类型）**

```text
source_service_id        dest_service_id          component_ids  count
VXNlcg==.0               ai-code-platform .1            1         36     ← 外部调用方 → 平台
ai-code-gateway .1       ai-code-platform .1            1         15     ← 网关 → 平台（Feign/HTTP）★
```

**③ 中间件监控：一次请求链路上的客户端调用（`service_relation_client_side`）**

```text
source                dest                                    component  count   含义
ai-code-platform  →   192.168.132.128:5432 (PostgreSQL)          37        43    ★ MySQL/JDBC
ai-code-platform  →   192.168.132.128:3000 (Langfuse)            12        39      OTLP 上报
ai-code-gateway   →   192.168.132.128:6379 (Redis)               57        30    ★ Redis
ai-code-gateway   →   ai-code-platform                          11        14    ★ Feign（组件 ID 11 = Spring Cloud Feign）
ai-code-platform  →   https://api.deepseek.com/v1/chat/...      173        13      LLM 出口
ai-code-gateway   →   localhost:8084                            11         3      Feign 直连
```

**④ HTTP 端点清单（`endpoint_traffic`，服务 → 端点）**

```text
ai-code-gateway .1  Lettuce/Reactive/createFlux        ← Redis 客户端调用（自动埋点）
ai-code-gateway .1  Lettuce/Reactive/createMono
ai-code-gateway .1  RedisReactive/local

ai-code-platform .1 POST:/api/v1/platform/auth/login
ai-code-platform .1 POST:/api/v1/platform/agents/medical-assistant/runs
ai-code-platform .1 GET:/api/v1/platform/agents
ai-code-platform .1 GET:/api/v1/platform/executions
ai-code-platform .1 GET:/api/v1/platform/observability/skywalking-status
ai-code-platform .1 GET:/actuator/health
ai-code-platform .1 Async/execute                       ← 异步任务 span
ai-code-platform .1 Async/api/public/otel/v1/traces     ← OTLP 导出（Langfuse）
```

**⑤ JVM / 实例元数据（`instance_traffic`，Agent 上报的实例属性）**

```text
service              instance     version        Start Time             JVM Arguments（节选）
ai-code-platform     platform-1   9.7.0-7a6c1e4  2026-10-06 12:37:06    -javaagent:E:\skywalking-agent\skywalking-agent.jar
                                                                        -Dskywalking.agent.service_name=ai-code-platform
                                                                        -Dskywalking.agent.instance_name=platform-1
                                                                        -Dskywalking.collector.backend_service=192.168.132.128:11800
ai-code-gateway      gateway-1    9.7.0-7a6c1e4  2026-10-06 12:37:07    （同上，service_name=ai-code-gateway）
OS=Windows 10  hostname=DESKTOP-2BAREQ5  ipv4s=192.168.132.1,192.168.47.1,192.168.10.6
```

→ 实例属性齐全（JVM 参数、OS、主机、进程号、jar 依赖清单），UI 的 Instance → JVM 面板即基于同源数据。

**⑥ 与 Week 16/17/18 的 ID 关联**

```text
网关响应头       X-Trace-Id: week19-e2e
网关 traceparent 00-b983da1ead5ec3d9b3c9a7f1bc4115a1-...
平台自检         businessTraceId=aa8df2254f3b3af5d7ed05c1bb7146d1   （= 同请求的 X-Trace-Id）
                 otelTraceId=aa8df2254f3b3af5d7ed05c1bb7146d1       （与自研逐位相等，Week 17 契约）
                 skywalkingTraceId=117d9ce3d0344ac893889e2f2cc9e9c4.77....  （Base64 segmentId，不可比）
                 correlationTag=aicode.trace_id   correlated=True   （两套链路都记录了这次请求）
```

> SkyWalking 的 traceId 与 W3C traceId **不可能相等**（前者是 Base64 segmentId），
> 因此跨后端检索用 span tag `aicode.trace_id`（= 响应头 `X-Trace-Id`），这一点在
> Spec 三、与架构文档第 4 节都已写明，验收不假装「打通了父子关系」。

### 10.5 验收清单（全部通过）

- [x] `docker compose ps`：OAP、UI 均 Up；日志出现 `module.storage.provider | postgresql`
- [x] `skywalking` 库 69 张表（按天分表）；业务库 `aidemo` 未被污染
- [x] UI `http://192.168.132.128:8088` 返回 200；`POST /graphql` 返回 200
- [x] 网关与平台以 `-javaagent` 启动，实例属性上报（10.4 ⑤）
- [x] 经网关跑一次真实 Agent 执行（登录 → 目录 → 执行），HTTP 200 / COMPLETED，真实 token
- [x] 拓扑出现 `ai-code-gateway → ai-code-platform`（10.4 ②）
- [x] 中间件可见：PostgreSQL(JDBC) / Redis / Feign / LLM 出口（10.4 ③）
- [x] HTTP 端点清单可见（网关 Redis 客户端调用 + 平台各端点）（10.4 ④）
- [x] 实例 JVM/环境元数据齐全（10.4 ⑤）
- [x] 平台自检 `bridgeAvailable=true`、`correlated=true`（10.3 ⑤）
- [x] **未挂 Agent 的网关进程照常提供服务**（10.3 ②，降级验证）
- [x] MySQL 侧另一证据：执行记录 34 条可查（10.3 ⑥）

### 10.6 演示数据清理与收尾

- 排查期间加过一个临时诊断端点 `GET /__diag/satoken`（用于读 Sa-Token 内部状态），
  定位后**已删除**；它在上报数据里留下的端点记录不影响验收结论（属诊断痕迹）。
- 本机 `target-acceptance/` 下保留了平台/网关的运行日志（`platform.out.log`、`gateway.out.log`）
  与验收报告 `acceptance-report.txt`，作为本节结论的原始依据（**不入库**，仅本机留存）。
- Langfuse 六容器在验收期间 `stop`，验收后需 `docker compose start` 恢复（数据卷未动）。

## 11. 风险与下周输入

- **Gateway 与 Spring Cloud 的版本对齐**（使用者明确要求核对）：
  Spring Boot **3.4.5** → Spring Cloud **2024.0.2**（实际解析到 `spring-cloud-gateway-server 4.2.4`、
  `spring-cloud-openfeign-core 4.2.2`、`spring-cloud-commons 4.2.2`）；Spring AI **1.0.0** 与
  Spring AI Alibaba **1.0.0.2** 同样面向 Boot 3.4.x / JDK 17。
  **网关刻意不引 Spring AI / Spring AI Alibaba**（它只是入口与出站，不跑模型），
  因此不存在与 AI 栈的版本冲突面；`libs/ai-core` 也被排除了 `spring-boot-starter-web`
  （否则 Servlet 与 Reactive 容器冲突，网关起不来）。
- **代理路由的取舍**见 10.2 末尾：第 20 周若需要「网关隐藏直连 URL」，正确做法是在
  网关过滤链里只改请求、不改响应，并补一条真机回归（当前缺失的正是这条回归）。
- **SkyWalking 与 Langfuse 的内存不可同时满足**（1.75 G + 1.03 G > 3.8 G）：第 20 周做
  「完整监控体系」时必须先做容量规划（拆机或扩容），否则 Prometheus/Grafana 再加一项会直接压垮节点。
- **采集口径**：本周 OAP 全采样、无限保留；生产需按容量设采样率与 TTL（部署文档 5 节已列）。
- **`sa-token` 头口径变更的连带影响**：平台的 `token-name` 从 `satoken` 改为 `Authorization`
  + `Bearer` 前缀，**既有客户端若用 `satoken` 头会失效**。本周仓库内调用方（网关、Postman）
  都已同步；第 21 周 SaaS 整合时需要一次性对齐所有外部调用方（写进下周输入）。
- **Agent 分发**：Windows 侧 Agent 目录需按部署文档 3.1 的方式获取（tar 包 + 校验），
  直接 `scp -r` 会得到不可用的插件树（本次实测）。

## 12. 决策记录 ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 网关形态 | 改造既有模块 / 新建模块 | **新建 `apps/agent-gateway`** | 仓库此前无任何网关模块；WebFlux 与既有 Servlet 应用无法共存于同一进程 |
| 是否引注册中心 | Nacos/Eureka / 配置化 URL | **配置化 URL** | 单机双进程，注册中心只增加常驻组件与新故障模式（规范 3.4） |
| 下游服务 | 新建独立 Spring Cloud 服务 / 网关进程内承载 Feign | **网关内承载 Feign** | VM 仅剩 ~1.1 G；新服务只转发一层，可观测事实（Feign/Redis/跨进程链路）在网关内完全等价 |
| 会话 token | 网关自签 token / 用平台 token | **用平台 token** | 自签 token 需要每次改写请求头才能让平台认，而改写只能用 `exchange.mutate()` → 只读响应 → 代理写出失败（实测）。用平台 token 后「转发零改写」，也符合「网关不签发凭据」的边界 |
| 平台会话头 | 自定义 `satoken` 头 / 标准 `Authorization: Bearer` | **`Authorization: Bearer`** | 读 sa-token 源码后确认它只认 token-name 指定的头且强制前缀；取标准头可让网关/客户端/第三方工具零适配。代价是既有 `satoken` 客户端需同步（已知风险，见 11） |
| SkyWalking 接入方式 | 只用 Agent 自动埋点 / Agent + Toolkit 手动埋点 | **两者结合** | Agent 覆盖 HTTP/Feign/JDBC/Redis/JVM；业务语义（agent.run / llm.chat / tool.call）只有应用自己知道 |
| Toolkit 依赖与调用 | 直接编译期引用 / 反射 + 降级 | **反射 + 降级** | Toolkit 类只随 Agent 提供；直接引用会让「忘挂 Agent」变成启动失败而非少数据 |
| SkyWalking 存储 | BanyanDB（10.x 默认）/ 复用既有 PostgreSQL | **独立库 `skywalking`** | 10.x 已移除 H2；本机内存不足以再常驻一个存储引擎；独立库与业务库、Langfuse 库隔离 |
| 装饰器 vs 替换 | 替换 `AgentObservabilityPort` / 装饰既有实现 | **装饰器（`@Primary`）** | 既有 OTel/Langfuse/Prometheus 一个都不能少；装饰器让 Week 17/18 单测一行未改 |
| 鉴权实现位置 | 只写 WebFilter / 只写 GlobalFilter | **两者共用 `GatewayAuthSupport`** | 代理路由不经过 WebFilter（Gateway 的路由由 `RoutePredicateHandlerMapping` 处理），只写 WebFilter 会让代理路径裸奔；共用实现避免规则漂移 |
| 自检端点 | 复用 `trace-context` / 新增 `skywalking-status` | **新增** | 「Agent 挂没挂、数据发到哪、两套 ID 是否都记录了」是 SkyWalking 特有的运维问题，混进 Week 17 的通用链路视图会污染既有契约 |
| 保留旧构造器 | 直接改签名 / 保留 4 参构造器重载 | **保留（+`@Autowired` 标注新构造器）** | 既有 Langfuse 单测一行未改即通过；注意 Spring 对「多构造器且无 `@Autowired`」会回退找默认构造器并启动失败（实测踩过，见下） |
| 透传代理路由 | 本周交付 / 去掉 | **去掉** | 真机暴露出与鉴权链的响应写出冲突；Week 19 目标不含该能力（YAGNI），取舍已写入路由表注释 |

> 补充踩坑（不属上表，但值得记）：`PlatformObservabilityUseCase` 增加新构造器后**必须**给
> 目标构造器加 `@Autowired` —— Spring 在「多个构造器且无注解」时会去找默认构造器，
> 结果是 `No default constructor found`，整个应用上下文起不来（本次一口气带崩 56 个
> `@SpringBootTest` 用例）。这类失败在单测里表现为大面积上下文错误，定位时优先看第一个
> `BeanCreationException` 而不是被它带出来的几十条。
