# ADR-0002：SSE 流式落地（第 31 周）

- **状态**：已接受（2026-11-01）
- **取代**：[ADR-0001](0001-sse-deferred.md)（第 30 周"顺延"，本文件落地其 6 步清单）
- **相关**：`docs/specs/week-31.md`、`docs/architecture/week-31-architecture.md`、`docs/api/week-31-api.md`

## 背景

第 30 周把 SSE 顺延，理由是 `ChatModelPort` 只有同步 `chat(...)`，真流式需要
"端口契约 + 适配器 + SSE 端点 + 前端 reader"四段，且患者分析实测 32s、等待不可接受。
第 31 周这四段全部打通，并把"取消/断连"补成显式状态。

## 决策

### 1. Servlet 阻塞式写 + 有界并发，不引入 WebFlux / 不用 SseEmitter

平台（8084）是 Servlet + Sa-Token 过滤链 + OTel + 990 用例；网关（8080）本来才是 WebFlux。
为流式重写平台等于把控制器/过滤器/观测全部动一遍，风险远大于收益。

真机验证后**放弃** `SseEmitter` 与 `AsyncContext`：两者写入后不保证及时 flush，
Tomcat 8KB 响应缓冲会把短答案攒到结束才发（首字=总耗时；meta 因触发首次 flush 反而最早到）。
最终实现是**在请求线程阻塞式执行 + 直接写 `OutputStream` 并逐帧 flush**，
并发由信号量限制（默认 4，满了在落执行记录之前 429）。代价是每条流占一个请求线程。

### 1.1 模型通道：原生 SSE + Simple HTTP 工厂

Spring AI `ChatModel.stream` 在当前版本对 SSE 有聚合行为，且 ai-core 的 LLM RestClient
用了 `JdkClientHttpRequestFactory`（读完整响应才交 InputStream）。最终生产链路：
`FRAMEWORK_MODEL_PROVIDER=openai-compatible`（`OpenAiCompatibleChatModelAdapter` 原生
`data:` 逐行解析）+ `SimpleClientHttpRequestFactory` + 逐帧 flush。
换回 `spring-ai` 或 JDK HttpClient 都会退化（真机 1.2s → 8–10s）。

### 2. "预览"与"权威结果"分离

`delta` 只是早看到字；`final` 携带与同步端点**完全相同**的 `ExecutionRecordResponse`，
前端收到后覆盖预览。理由：
- 模型中途失败会降级为原文摘录，预览可能"半截"，最终值必须以用例结果为准；
- 执行记录/审批/记忆副作用只在用例里发生一次，流式不新增第二条写库链路。

### 3. 预检同步、执行异步

鉴权/RBAC/Guardrail/Agent 校验在请求线程完成，失败仍是 401/403/404/400/429/503 + JSON 信封；
只有预检通过才 `200 text/event-stream`。避免"HTTP 200 里塞一个 403"。

### 4. 断连 = `CANCELLED`，不是 `FAILED`

用户点停止、浏览器关闭、SSE 超时，语义都是"这次执行被中止"，不是系统故障：
- 落 `CANCELLED`，不计入失败率（`agent.run.count{status=CANCELLED}` 单独可见）；
- 模型层用 `takeWhile(isCancelled)` 退订上游，最多多跑一个 chunk；
- 已 COMPLETED 之后不再改写成 CANCELLED（任务确实完成了，只是 final 没送达）。

### 5. 流式脱敏用 32 字符 carry buffer

逐 delta 掩码会漏掉"被切成两段的手机号/身份证"：前一段末尾数字还没凑够 11 位。
所以尾部保留 32 字符（> 身份证 18 位）不发，只发安全前缀；结束时 flush。
代价是可见文本比模型晚约 32 字符，首字仍是毫秒级。
残留风险：邮箱 local part >29 字符且正好跨块时，前缀 2 字符可能漏掩——已在接口文档登记。

### 6. 只对两个"长正文"Agent 开 token 级流式

`knowledge-qa`、`patient-analysis` 都只有一次"生成正文"的模型调用，接入成本低、收益直接。
其它 Agent 走同步端点；流式端点对它们返回 400，而不是回一条"一次性 delta"冒充流式。

### 7. 流式 usage：原生适配器显式 `include_usage`

OpenAI 兼容协议默认流式末包不带 usage。原生 SSE 适配器固定发送
`stream_options.include_usage=true`，从末包解析 prompt/completion/total，
并写入执行记录与 `llm.tokens` 指标。验收要求"流式执行 token > 0"；厂商不返回时为 0
（已在接口文档登记）。

### 8. 开关优先于回滚

`PLATFORM_STREAM_ENABLED=false` 时端点 503 `STREAM_DISABLED`，前端自动回退同步端点；
分钟级回滚，不换 jar、不动数据库（`status` 是 `VARCHAR(24)`，加枚举不需要迁移）。

## 后果

- 首字可见：患者分析从"转圈 30s"变为"先出报告正文"；
- 取消有据可查：`CANCELLED` 记录 + 审计 + 指标；
- 复杂度增加：多一个 worker 池、心跳、carry buffer、并发上限（默认 4）；
- 单实例局限：流绑定在某个实例上，多实例需 Redis 广播/粘性会话（未做）；
- 无断线续传：刷新/掉线后只能重新运行（`Last-Event-ID` 未实现）。

## 触发条件（满足任一再做）

- 移动端/弱网要求"断线续看" → 事件落库 + `Last-Event-ID` 重放；
- 平台上第二个实例/零停机发布 → 流事件广播或粘性会话；
- 切换 `FRAMEWORK_MODEL_PROVIDER=openai-compatible` 且仍要 token 级流式 → 该适配器补增量解析。
