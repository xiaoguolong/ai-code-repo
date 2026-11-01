# Week 31 实现日志 — 真 SSE 流式（知识库问答 + 患者分析）

> 来源：`docs/specs/week-31.md`；架构：`docs/architecture/week-31-architecture.md`；
> 接口：`docs/api/week-31-api.md`；决策：`docs/adr/0002-sse-streaming.md`。

## 1. 本周目标

1. 两个长正文 Agent 支持模型增量输出，首字可见；
2. 新增 SSE 端点，鉴权/记录/审计/指标/审批副作用与同步端点一致且只执行一次；
3. 停止/断连有终态（`CANCELLED`），不留 RUNNING 僵尸。

## 2. 边界

**做了**：

- `ChatModelPort.chatStream(...)` 默认回退 + Spring AI `Flux` 增量适配（顺序、累加、末包 usage、取消退订）；
- `POST /api/v1/platform/agents/{key}/runs/stream`：预检同步、worker 执行、`meta/delta/final/error`、15s 心跳；
- 有界线程池 + 信号量（默认 4）：并发满在**建记录之前** 429；
- 执行记录状态机复用：COMPLETED / FAILED / PENDING_APPROVAL / **CANCELLED**；
- 流式脱敏 carry buffer（32 字符）+ 18 位身份证掩码修复；
- 前端 `fetch + ReadableStream` reader、增量渲染、停止、final 覆盖、无 meta 时回退同步；
- nginx SSE location（`proxy_buffering off`、300s）、发布/验收脚本、开关 `PLATFORM_STREAM_ENABLED`。

**没做**：

- OpenAI 兼容适配器的增量解析（生产默认 `spring-ai`；切换后退化为单 chunk）；
- `Last-Event-ID` 断线续传、多实例广播、gateway `/gw/` 直连流式；
- WebFlux 重写、TLS/域名、PDF/OCR、科室授权、容器化；
- 云端真机发布（本机无云主机凭据；脚本已就绪，见第 7 节）。

## 3. 新增/修改

| 类别 | 文件 |
|------|------|
| ai-core 契约 | `domain/model/ChatStreamListener.java`、`domain/exception/StreamCancelledException.java`、`domain/port/ChatModelPort.java` |
| ai-core 适配 | `infrastructure/springai/SpringAiChatModelAdapter.java`（`chatStream` + `buildOptions` 抽取） |
| ai-core PII | `infrastructure/security/PiiMasker.java`（18 位身份证漏掩修复） |
| 平台编排 | `platform/application/PreparedExecution.java`、`RunEventSink.java`、`StreamingTextSanitizer.java`、`PlatformChatStreamListener.java` |
| 平台编排 | `platform/application/PlatformExecutionRunner.java`（prepare/execute 拆分）、`PlatformExecutionUseCase.java`（prepareStream/executeStream）、`PlatformExecutionRecorder.java`（cancelled） |
| 平台 Web | `controller/PlatformExecutionStreamController.java`、`infrastructure/stream/PlatformStreamDispatcher.java`、`SseRunEventSink.java`、`infrastructure/config/PlatformStreamProperties.java` |
| 平台模型 | `ExecutionStatus.java`（CANCELLED）、`OperationsStatistics/OperationsSummary`（cancelled 口径）、`MonitoringMetricNames`（2 个指标） |
| 平台异常/码 | `PlatformStreamDisabledException`、`PlatformStreamBusyException`、`ApiErrorCode.STREAM_DISABLED`、`GlobalExceptionHandler` |
| Agent | `KnowledgeQaAgent/UseCase`、`PatientAnalysisAgent/UseCase`、`PlatformAgentRunner`（listener 重载） |
| 配置 | `application.yml`（stream 属性 + `stream-usage: true`）、`PlatformConfiguration` |
| 前端 | `api/stream.ts`、`api/stream.test.ts`、`views/AgentRunView.vue`、`api/http.ts`（notifyUnauthorized）、`api/types.ts`（CANCELLED） |
| 部署 | `deploy/app/nginx/aicode.conf(.example)`、`cloud-week31.sh`、`cloud-accept-week31.sh` |
| 文档 | 本文件、架构、接口、ADR-0002、README、logs-index、Postman |

## 4. RED → GREEN（本周真实踩到的 10 个问题）

### 4.1 默认流式实现把空白当增量

`ChatModelPort` 的默认回退用 `!content.isEmpty()` 判断，模型返回 `"  "` 时仍回调一次空 delta。
改为 `isBlank()`，并补用例 `doesNotEmitEmptyDeltaForBlankAnswer`。

### 4.2 Spring AI 的"默认 usage 全 0"覆盖了真实 usage

流式块里只有末包带 usage，但 `ChatResponseMetadata` 的默认 usage 是 `0/0/0`（非 null），
`usageOf` 原样返回，导致"最后一个非空 usage"被后一个 0 覆盖，记录 token 变成 0。
修复：全 0 视为"本块没有 usage"，保留上一次非空值；补用例 `keepsLastNonEmptyUsageWhenLaterChunksHaveNone`。

### 4.3 同步路径改调三参重载，37 个既有用例变红

`PlatformExecutionRunner` 最初直接调 `platformAgentRunner.run(agent, input, listener)`，
Mockito 严格桩匹配不到旧的 `run(agent,input)`，一批 `PotentialStubbingProblem`。
修复：同步路径（listener == null）仍调两参重载，流式才调三参；`KnowledgeQaUseCase` /
`PatientAnalysisUseCase` 同样分支，保证"同步路径字节级不变"。

### 4.4 18 位身份证**根本没被掩码**

写 carry buffer 用例时发现：`PiiMasker.ID_CARD` 是 `6+8+1`（15 位），
18 位号码因尾随 3 位数字过不了 `(?!\d)`，正则完全不匹配——医疗系统里这是真实 PII 泄漏。
修复为 `6+8+[1..4]`（覆盖 15/18 位，固定掩 8 位、保留前 6 后 4），补两个掩码用例。
这个问题不是流式引入的，但流式把它暴露了出来。

### 4.5 测试里的 18 位号码与旧掩码假设冲突

第 4.4 修完前，测试用 18 位样本断言失败；修完后统一用真实 18 位样本，避免"为了过测而用假数据"。

### 4.6 MockMvc 里的两个"看起来像 bug"的现象

补 SSE 主链路用例时发现：Spring 的 `SseEmitter` 实际写出的是 `event:meta`（冒号后**没有空格**），
而接口文档示例写的是 `event: meta`；前端解析器两者都支持（冒号后空格可有可无），
但测试断言必须按实际输出写。另一个是 `MockHttpServletResponse#getContentAsString()` 默认按
ISO-8859-1 解码，中文会变乱码，需要显式 `getContentAsString(UTF_8)`。
两个都不是产品缺陷，已按真实行为修正测试与文档示例。

### 4.7 `final` 信封的 traceId 是空的

`ApiResponse.success(...)` 固定 `traceId=""`，普通 JSON 响应靠 `ApiResponseTraceAdvice` 回填；
SSE 是原始字符串写出、绕过 advice，所以 `final` 的 traceId 为空，与 `meta`/日志对不上。
修复：`SseRunEventSink` 直接构造信封并显式带 `TraceIds.current()`；
SSE 主链路用例增加"final.traceId 非空"断言。

### 4.8 身份证掩码把 15 位小数改坏，工具 JSON 直接非法

云端 knowledge-qa 报 `invalid knowledge tool result: Unexpected character ('*')`：
工具结果里 `"score":0.486819416284103`（15 位小数）被身份证规则命中，替换成带 `*` 的文本，
JSON 数字变非法。根因是脱敏作用在**整段 JSON 文本**上。
修复分两层：
1. `PiiMasker` 只匹配 **15 位或 18 位**身份证（不再匹配任意 15–18 位数字）；
2. `GuardrailToolPort` 改为**结构化脱敏**：能解析 JSON 就只脱敏字符串值，数字/布尔/键原样保留，
   解析失败才退回文本脱敏。补了 15/16 位小数 + 手机号的回归用例。

### 4.9 三种 SSE 写法都"首字≈总耗时"（第 4 轮已闭环）

`SseEmitter` → `AsyncContext + flushBuffer` → 阻塞式 `OutputStream + flush`，客户端都在
响应快结束时才收到 delta（meta 却能 20–50ms 到）。服务端曾观察到首个模型 chunk 738ms，
同轮客户端 5.4s 才收到。已排除模型侧（直连 0.15–0.7s）与 SSE 引入（同步端点同样 6–12s）。
最终判断指向 Spring AI 的 WebClient 连接器对 SSE 响应的聚合；已补 `reactor-netty-http`，
但还没显式指定 `ReactorClientHttpConnector`。详见第 7 节。

### 4.10 真凶：JDK HttpClient 工厂 + 切 provider 启动失败

第 4 轮定位到 `AiCoreConfiguration.llmRestClient` 用的 `JdkClientHttpRequestFactory`
会把 SSE 响应读完整才交 InputStream；换 `SimpleClientHttpRequestFactory` 后首字 1.2s。
同时修了 `observableChatModelPort` 硬编码 `@Qualifier("springAiChatModelAdapter")`
导致 `openai-compatible` 启动失败的问题（两个适配器统一注册为 `delegateChatModelPort`）。

## 5. 质量门禁

| 门禁 | 结果 |
|------|------|
| ai-core | 90 → **108**（+18） |
| 平台 | 712 → **734**（+22） |
| enterprise / patient / gateway | **70 / 21 / 100**（不变） |
| 根聚合 | **1033** 全绿（Week 30 基线 990，+43） |
| 前端 | typecheck 0、Vitest 11 → **23**（+12）、build 成功 |
| 命令 | `.\run-maven-jdk17.ps1 test`；`tools\web-ci.ps1 -SkipInstall` |

证据：`target-acceptance/w31-aicore-test.log`、`w31-platform-test.log`、`w31-root-test.log`、
`w31-sse-http.log`、`logs/week-31/web-ci.log`（CI 复用同名脚本输出目录）。

## 6. 关键设计与取舍

- **预览 vs 权威**：`delta` 只负责早看到字，`final` 与同步端点同结构，前端覆盖；
  模型中途降级也不会把半截答案当最终结果；
- **取消不是失败**：`CANCELLED` 不计失败率；`takeWhile` 退订，最多多跑一个 chunk；
- **预检同步**：4xx 仍是 JSON 信封；只有预检通过才 200 流；
- **并发与僵尸**：信号量在落记录之前判断，429 不产生 RUNNING；
- **脱敏 carry**：32 字符（>18 位身份证），尾部 flush；final 仍整体脱敏；
- **traceId 跨线程**：预检抓 MDC，worker 恢复/清理，`agent.run` span 与 HTTP 日志同源。

## 7. 云端发布与真机验收（2026-11-01 已执行）

发布：`cloud-week31.sh` 多次（最终 jar = 同步阻塞式 SSE 版本），每次均备份到
`/opt/ai-code/backup/week31-*`；`remote-deploy.sh` 健康检查通过，`stream_401=401`、health=200。
期间做过 3 组对照实验（关 Langfuse Prompt、去 SkyWalking javaagent、加 reactor-netty），均已恢复原配置。

真机验收（`cloud-accept-week31.sh`，PASS=11 FAIL=4）：

| 检查 | 结果 |
|------|------|
| health / 登录 / 无 token 401 / 不支持流式 Agent 400 | PASS |
| knowledge-qa final `COMPLETED` 且 token > 0 | PASS（2047 tokens） |
| 走 nginx（80）能流、终态正确 | PASS |
| patient-analysis final `PENDING_APPROVAL` | PASS |
| 停止生成 → `CANCELLED` | PASS |
| 同步端点回归（medical-assistant 200） | PASS |
| 近 10 分钟无 RUNNING 僵尸 | PASS |
| 首字指标导出（2 个 series） | PASS |
| knowledge-qa 首字 < 2s | **FAIL（9.97s）** |
| nginx 首字 < 2.5s | **FAIL（9.13s）** |
| patient-analysis 首字 < 5s | **FAIL（9.37s）** |

**结论：功能闭环全部通过；"首字"指标未达标。**

已排除/已确认：

- 直连厂商（同机 curl，deepseek-v4-pro，短/长 prompt）：首 chunk **0.15–0.7s**，非模型侧；
- 平台同步端点同样 6–12s，说明延迟不是 SSE 引入；
- `SseEmitter`、`AsyncContext + flushBuffer`、阻塞式 `OutputStream + flush` 三种写法都是
  "首个 delta ≈ 总耗时（9–10s）"，deltas 189–226 个；
- 服务端一度观察到 `[llm] stream first chunk after 738ms`，同轮客户端 5.4s 才收到；
- Jar 内原本没有 reactor-netty（Spring AI 的 WebClient 退回 JDK 连接器），已补
  `reactor-netty-http`，但尚未显式指定 `ReactorClientHttpConnector`。

**下轮首项**：显式配置 `WebClient.Builder` + `ReactorClientHttpConnector`（或改用
RestClient 自解析 SSE），再跑 `cloud-accept-week31.sh`；若仍聚合，再抓 Reactor Netty wiretap
确认 chunk 到达时间。

### 7.1 第 2 轮补充实验（2026-11-01）

已新增 `WebClientConfiguration`（显式 `ReactorClientHttpConnector` + Netty），重新发布后复测：

- 首字仍为 **8.4s**（meta 33ms），与之前一致 → **换连接器无效**；
- 结论收窄到 Spring AI `OpenAiChatModel.stream` 的 chunk 窗口/合并逻辑：
  其 `windowUntil` + `concatMapIterable` 在 DeepSeek 这种"finish_reason 只在末包出现"的流上，
  可能把整段文本攒到一个窗口结束才下发（与观测到的"deltas 在末尾爆发"一致）。

### 7.2 第 3 轮：原生 SSE 读取（2026-11-01）

按 7.1 的方案，在 `OpenAiCompatibleChatModelAdapter` 实现原生 SSE：
`stream=true` + `stream_options.include_usage` → RestClient `exchange` 拿 `InputStream` →
逐行 `data:` 解析（`OpenAiChatResponseMapper.streamDelta`）→ 每块回调 listener；
云端把 `FRAMEWORK_MODEL_PROVIDER` 切到 `openai-compatible`（`.env` 备份在
`/tmp/platform.env.w31-provider`）。

效果（同机直连 8084）：

| 指标 | 切换前（Spring AI） | 切换后（原生 SSE） |
|---|---|---|
| knowledge-qa 首字 | 9.97s | **3.17s** |
| final | COMPLETED / token 2047 | COMPLETED / token 1252 |
| deltas | 末尾爆发 | 正常增量（189–226） |

真机验收 PASS=12：功能全过；首字仍未达 2s 目标（3.17s 直连、公网 nginx 轮次波动 6–17s）。
剩余差异指向 **nginx/公网链路**与模型 TTFT（直连厂商 0.2–0.7s，RAG 长上下文还会更高），
不再由 Spring AI 聚合导致。

**下一步**：核对 `nginx -T` 的 SSE location 是否命中（`proxy_buffering off`），并用原始
socket 抓一次公网到达时间；确认后再决定是否把验收阈值改为"首字 ≤ 总耗时 − 2s 且 ≤ 5s"。

### 7.3 第 4 轮：根因收口（2026-11-01，PASS=15 FAIL=0）

真凶不是 Spring AI 的窗口，而是 **ai-core 的 LLM RestClient 用了
`JdkClientHttpRequestFactory`（JDK HttpClient）**：它在 `RestClient.exchange` 里把 SSE
响应读完整才把 InputStream 交给调用方。本地单测
（`OpenAiCompatibleChatStreamTest`，用 `SimpleClientHttpRequestFactory`）证明同一适配器
能按块交付；把生产 `llmRestClient` 换成 `SimpleClientHttpRequestFactory` 后云端首字立刻正常。

同轮修掉切 provider 的启动失败：`observableChatModelPort` 硬编码
`@Qualifier("springAiChatModelAdapter")`，`provider=openai-compatible` 时该 bean 不存在
（真机上导致平台反复启动失败并触发自动回滚）。两个适配器现在统一注册为
`delegateChatModelPort`（`SpringAiAdapterConfiguration` 改名、OpenAI 适配器
`@Component("delegateChatModelPort")`），`AppConfiguration` 按该名字注入。

**最终真机验收（`cloud-accept-week31.sh`，PASS=15 FAIL=0）**：

| 检查 | 结果 |
|------|------|
| knowledge-qa 首字 | **1225ms**（<2s），COMPLETED，token 2508 |
| 走 nginx（80）首字 | **1125ms**（<2.5s） |
| patient-analysis 首字 | **1075ms**（<5s），final PENDING_APPROVAL |
| 停止生成 | CANCELLED |
| 同步回归 / 401 / 400 / 指标 / 无 RUNNING | 全部 PASS |

最终链路：`FRAMEWORK_MODEL_PROVIDER=openai-compatible`（原生 SSE reader）+
`SimpleClientHttpRequestFactory` + 阻塞式逐帧 flush。三者缺一不可：
换回 `spring-ai` 或换回 JDK HttpClient 都会回到"首字=总耗时"。

## 8. 边界与下周

| # | 待办 | 说明 |
|---|------|------|
| 1 | `Last-Event-ID` 续传 | 移动端/弱网触发；需要事件落库与重放 |
| 2 | 多实例流广播 | 第二个实例/零停机发布触发 |
| 3 | SSE 面板 | Grafana 增加首字/取消率面板（指标已导出） |
| 4 | PDF/OCR 入库 + 步骤级过程管理 | 第 32/33 周候选（prompt 原文可见 + Dify 式节点视图，需单独 Spec） |

> **上线约束（写进 ops 手册）**：`FRAMEWORK_MODEL_PROVIDER` 必须是 `openai-compatible`
> （原生 SSE），且 `llmRestClient` 必须是 `SimpleClientHttpRequestFactory`；
> 换回 `spring-ai` 或 JDK HttpClient 都会让首字退化到"整段结束后才下发"。
