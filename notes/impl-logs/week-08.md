# Week 08 实现日志 — Spring AI Alibaba 接入（框架适配器 + Graph Agent）

- 日期：2026-09-22
- 批次：1
- 对应 Spec：docs/specs/week-08.md
- 对应阅读：无（Spring AI Alibaba 为本周编码主线，独立笔记留待后续补）

## 1. 本周目标

把第 5–7 周手写的 Agent 能力接到 Spring AI / Spring AI Alibaba 框架上，且不破坏既有分层：

1. **ai-core 框架适配器**：新增无状态适配器，把领域 `ChatModelPort` / `Tool` 契约桥接到 Spring AI 1.0.0；`spring-ai-model` 声明为 `optional`，不向 app 传递。
2. **新演示模块 `apps/spring-ai-alibaba-agent`**：用 Spring AI Alibaba Graph（`StateGraph`）把「模型带工具循环」编排为 `agent ↔ tools` 图；模型经 Spring AI 的 OpenAI 兼容通道指向 DeepSeek；工具复用 ai-core `ToolRegistry`。
3. **MCP**：用 `spring-ai-starter-mcp-server-webmvc` 把 demo 工具经 MCP（SSE）暴露。
4. **适配器切换**：`framework.model-provider` 在 `openai-compatible`（第 1 周 RestClient）与 `spring-ai` 之间切换，既有 app 行为不变。

## 2. 边界

- Always：Spring AI 类型只在 `ai-core infrastructure.springai` 与 app `infrastructure`/config；领域与用例只认端口；`framework.model-provider` 缺省 `openai-compatible`；Graph 节点经端口调模型与工具；单测不打真实网络（stub Spring AI `ChatModel` + 脚本化 `ChatModelPort`）；中文 JavaDoc；无密钥入库
- Never：接 Nacos/Spring Cloud；连真实远端 MCP；改 `JAVA_HOME`；密钥入库；用户输入拼 system 提示；Controller 写 Prompt/业务规则；ai-core 依赖 app；改 patient/enterprise 既有行为

## 3. 增量架构

见 Spec `docs/specs/week-08.md` 与 `docs/architecture/week-08-architecture.md`。

## 4. 新增/变更类型清单

### ai-core（com.aicode.core.infrastructure.springai）

| 类型 | 名称 | 职责 |
|------|------|------|
| Mapper | `SpringAiMessageMapper` | 领域 `ChatMessage` ↔ Spring AI `Message`；`ChatResponse` → `ChatResult`（content/toolCalls/finishReason/usage/model） |
| Factory | `SpringAiToolCallbackFactory` | `ToolDefinition` → 仅定义 `ToolCallback`；`Tool` → 可执行 `ToolCallback`（供 MCP） |
| Adapter | `SpringAiChatModelAdapter` | 实现 `ChatModelPort`，包 Spring AI `ChatModel`；关闭内部工具执行；异常统一 `ChatModelException` |
| Config | `SpringAiAdapterConfiguration` | `@ConditionalOnClass(spring-ai)` + `@ConditionalOnProperty(framework.model-provider=spring-ai)` 装配 |

- 变更：ai-core `pom.xml` 新增 `spring-ai-model:1.0.0`（`optional`）；`OpenAiCompatibleChatModelAdapter` 增加 `@ConditionalOnProperty(framework.model-provider=openai-compatible, matchIfMissing=true)`，作为适配器开关且保持既有 app 默认生效。

### apps/spring-ai-alibaba-agent（新模块，com.aicode.framework）

| 类型 | 名称 | 职责 |
|------|------|------|
| Graph | `FrameworkAgentGraph` | 领域服务：`StateGraph` 编排 `agent ↔ tools` 循环，经端口调模型/工具 |
| UseCase | `FrameworkAgentUseCase` | 校验任务 → 调 Graph |
| Tool | `PatientLookupTool` / `HealthMetricTool` | 实现 ai-core `Tool`，演示数据 |
| MCP | `McpToolCallbackProvider`（`AppConfiguration` Bean） | 领域工具 → 可执行 `ToolCallback`，供 MCP Server |
| Config | `AppConfiguration` / `FrameworkAgentProperties` | 工具注册表、图服务、MCP Provider；`framework.agent.max-iterations` |
| Web | `FrameworkAgentController` / `GlobalExceptionHandler` | `POST /api/v1/framework/agents/runs`，异常→状态码 |
| DTO | `ApiResponse` / `FrameworkAgentRunRequest` / `FrameworkAgentRunResponse` / `FrameworkAgentStepDto` / `FrameworkUsageDto` | 协议体 |

## 5. RED

- 命令：`.\run-maven-jdk17.ps1 -pl libs/ai-core test`
- 结果：编译失败（预期）
- 原因：先写 4 个 ai-core 测试（`SpringAiMessageMapperTest` / `SpringAiToolCallbackFactoryTest` / `SpringAiChatModelAdapterTest` / `SpringAiAdapterConfigurationTest`），引用 `org.springframework.ai.chat.model` 等包，但 ai-core 尚未引入 `spring-ai-model`，报「程序包 org.springframework.ai.* 不存在 / 找不到符号 ChatModel、ChatResponse、Prompt」。

## 6. GREEN

- 命令：`.\run-maven-jdk17.ps1 test`（根聚合）
- 结果：`ai-core 69 / enterprise 21 / patient 19 / spring-ai-alibaba-agent 11` 全绿，`BUILD SUCCESS`
- 改动文件：ai-core 新增 4 类 + pom + 1 处条件注解；新模块 16 个主类 + pom + yml + prompt + 5 个测试类；根 pom 增加模块
- 关键修复：Graph 节点抛出的领域异常会被 `CompiledGraph` 包成 `CompletionException/ExecutionException`，在 `FrameworkAgentGraph.run` 增加 `unwrap` 逐层还原为 `AgentLoopExceededException` / `AgentExecutionException` / `ChatModelException` / `ToolExecutionException`。

## 7. 重构

- 抽取 `SpringAiMessageMapper` 与 `SpringAiToolCallbackFactory`，适配器只做编排，映射与工具桥接单一职责。
- 工具定义由 ai-core `ToolRegistry` 统一提供，Graph `tools` 节点只经 `ToolPort.execute`，保证工具执行唯一咽喉点。
- `SpringAiToolCallbackFactory` 同时服务两条链路（模型侧仅定义、MCP 侧可执行），避免重复工具定义。
- Graph 状态全部用 `ReplaceStrategy`（节点返回全量列表），避免 `AppendStrategy` 的 `distinct()` 合并语义带来的不确定性。

## 8. 质量门禁

- [x] 根聚合 `test` 全绿（120 个测试）
- [x] 无密钥入库（`spring.ai.openai.api-key: ${LLM_API_KEY:}` / `llm.api-key: ${LLM_API_KEY:}` 仅环境变量）
- [x] 分层未突破：ai-core 无启动类；Controller 无 SDK/SQL；模型与工具均经端口；Spring AI 类型不出 `infrastructure.springai`
- [x] public 类型中文 JavaDoc
- [x] 未改系统/用户 `JAVA_HOME`（仅 `-Djdk.17.home`）
- [x] 无 System.out/err 打印
- [x] YAGNI：未接 Nacos/Spring Cloud；未引入未发布的 `agent-framework`；未给 patient/enterprise 加框架依赖；`spring-ai-demo` 未改动

## 9. 验证证据

```
[INFO] Reactor Summary for ai-code-repo 0.1.0-SNAPSHOT:
[INFO] ai-core ............................................ SUCCESS [  9.955 s]
[INFO] enterprise-knowledge-agent ......................... SUCCESS [ 10.276 s]
[INFO] patient-agent ...................................... SUCCESS [ 11.385 s]
[INFO] spring-ai-alibaba-agent ............................ SUCCESS [ 16.448 s]
[INFO] ai-code-repo ....................................... SUCCESS [  0.000 s]
[INFO] BUILD SUCCESS
```

新增测试覆盖：

- ai-core：`SpringAiMessageMapper`（四类消息映射、tool_calls 响应映射、缺 usage 回退、空响应异常）、`SpringAiToolCallbackFactory`（仅定义回调暴露 name/description/schema、可执行回调解析参数并执行）、`SpringAiChatModelAdapter`（消息/工具/参数组装进 Prompt、关闭内部执行、异常包装）、`SpringAiAdapterConfiguration`（有属性装配、无属性不装配）
- spring-ai-alibaba-agent：`FrameworkAgentGraph`（工具轮→最终答案、超迭代、空答案）、`FrameworkAgentUseCase`（空任务拦截、委派）、`FrameworkAgentController`（200 结构、空任务 400）、`BusinessToolsTest`（工具定义/执行/缺参）、`SpringAiAlibabaAgentApplicationTest`（ChatModelPort/ToolPort/Graph/MCP ToolCallbackProvider 装配，含 MCP Server 自动配置加载）

MCP 端点（由 `McpServerProperties` 默认值确认）：SSE 握手 `GET /sse`、消息 `POST /mcp/message`。

## 10. 风险与下周输入

- **框架版本**：仅使用 Central 已发布的 `spring-ai 1.0.0` 与 `spring-ai-alibaba-graph-core 1.0.0.2`；更高层 `agent-framework` 尚未发布，本周以 Graph 编排为准。
- **MCP 仅服务端暴露**：未接远端 MCP Client，未做 SSE 协议级联调（仅装配级验证）。
- **未接记忆**：第 7 周 `MemoryPort` 未接入本周 Graph；框架 `ChatMemory` 与自研 `MemoryPort` 的映射留待后续。
- **下周（第 9 周 Workflow）**：`StateGraph` 已引入，可直接扩展为多节点患者风险分析流程（State/Node/Edge/Graph、条件分支、子图）。
- **未接 Spring Cloud/Nacos**：README 第 8 周「接入 Spring Cloud」经确认不做，避免单测依赖外部注册中心。

## 11. 决策记录 ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 框架接入形态 | 改造 patient-agent / 新增模块 + ai-core 适配器 | 新增 `spring-ai-alibaba-agent` + ai-core 适配器 | 既有 app 行为不变，符合「换框架换适配器」，单测可离线 |
| Spring AI 依赖位置 | app 直接引入 / ai-core optional | ai-core `optional` + `@ConditionalOnClass` | 规范 5.8.1：仅个别适配器用的依赖不向 app 传递 |
| 模型通道 | DashScope / DeepSeek OpenAI 兼容 | DeepSeek OpenAI 兼容 | 复用既有 `LLM_*`，无需新密钥 |
| 工具循环执行方 | 依赖 Spring AI 内部执行 / 自行执行 | 关闭内部执行，Graph 经 `ToolPort` 执行 | 工具执行是唯一咽喉点，便于权限/审计，与第 6 周一致 |
| Graph 角色 | 单节点直调 / `agent↔tools` 条件边循环 | `agent↔tools` 循环 | 真实体现 Agent 图编排，为第 9 周打底 |
| MCP 定位 | 不落地 / 最小 Server 暴露 demo 工具 | 最小 Server（webmvc/SSE） | README 学习项含 MCP，用一个 starter 即可验证，成本可控 |
| Graph 状态策略 | `AppendStrategy` / `ReplaceStrategy` | `ReplaceStrategy` | 节点返回全量列表，避免 append 的 `distinct()` 合并语义不确定 |

---

## 批次 2 — Graph 异常解包

- 日期：2026-09-22
- 背景：`FrameworkAgentGraph` 首轮测试中，`failsWhenLoopExceedsMaxIterations` / `failsWhenAnswerIsEmpty` 失败。原因：Graph 节点抛出的领域异常被 `CompiledGraph` 包装为 `java.util.concurrent.CompletionException → ExecutionException → 领域异常`，对调用方暴露为 `CompletionException`。

### 修复

- `FrameworkAgentGraph.run` 捕获 `RuntimeException` 后经 `unwrap` 逐层还原：命中 `AgentLoopExceededException` / `AgentExecutionException` / `ChatModelException` / `ToolExecutionException` 即原样抛出，否则保持原异常。

### 验证

- 命令：`.\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am "-Dtest=FrameworkAgentGraphTest" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- 结果：`FrameworkAgentGraphTest 3/3` 通过，`BUILD SUCCESS`（Graph 内部仍打印 ERROR 日志属预期，异常已被正确解包）。

---

## 批次 3 — 补齐运行配套（.env / 单模块脚本）与 base-url 修正

- 日期：2026-09-22
- 背景：新模块缺少仓库既有约定——每个 app 都有 `.env` + `.env.example`（`.env` 由 `.gitignore` 忽略、`.env.example` 提交）与单模块 `run-maven-jdk17.ps1`。同时发现：既有约定 `LLM_BASE_URL=.../v1`，若直接作为 `spring.ai.openai.base-url` 会与 Spring AI 自行拼接的 `/v1/chat/completions` 叠加成 `/v1/v1/...`。

### 修复 / 补齐

- 新增 `apps/spring-ai-alibaba-agent/.env.example`（提交）与 `.env`（本地，已确认 `git check-ignore` 命中 `.gitignore:47`）。
- 新增 `apps/spring-ai-alibaba-agent/run-maven-jdk17.ps1`（与 patient-agent 同款单模块包装）。
- `application.yml`：`spring.ai.openai.base-url` 改用独立变量 `SPRING_AI_OPENAI_BASE_URL`（默认 `https://api.deepseek.com`，不带 `/v1`）；ai-core `llm.base-url` 仍用 `LLM_BASE_URL`（含 `/v1`）。
- 同步更新 `docs/specs/week-08.md`（Commands 增加环境变量说明）与 `docs/api/week-08-api.md`（配置表）。

### 验证

- 命令：`.\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am test`
- 结果：`ai-core 69 / spring-ai-alibaba-agent 11` 全绿，`BUILD SUCCESS`

---

## 批次 4 — MCP 实跑发现并修复 tools/call（Tool context）

- 日期：2026-09-22
- 背景：用「SSE 挂起 + POST JSON-RPC」实跑 MCP：`initialize`、`tools/list` 均成功（返回 `PatientLookupTool`、`HealthMetricTool`），但 `tools/call` 返回 `{"content":[{"type":"text","text":"Tool context is not supported!"}],"isError":true}`。

### 根因

- MCP Server 调用的是 `ToolCallback.call(String, ToolContext)` **两参重载**；`SpringAiToolCallbackFactory` 的两个回调只覆盖了一参 `call(String)`，于是落到接口默认实现，抛 `UnsupportedOperationException("Tool context is not supported!")`（`javap -c` 已确认默认实现即抛此异常）。

### 修复 / 补齐

- `DefinitionToolCallback` / `ExecutableToolCallback` 均覆盖 `call(String, ToolContext)`，委托到 `call(String)`。
- `SpringAiToolCallbackFactoryTest` 增加两参调用断言（模拟 MCP 路径）。
- 新增 `apps/spring-ai-alibaba-agent/.mvn/maven.config`（规范 3.6 要求每个可构建模块都有；此前遗漏）。

### 验证

- 命令：`.\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am test` → 全绿；`.\run-maven-jdk17.ps1 test` → `ai-core 69 / enterprise 21 / patient 19 / spring-ai-alibaba-agent 11`，`BUILD SUCCESS`。
- 实跑（8085，`SERVER_PORT=8085` 起独立实例，避开用户 8084）：`initialize` / `notifications/initialized` / `tools/list` / `tools/call` 四个 POST 全 `200`；SSE 推送 `tools/call` 结果：
  ```json
  {"jsonrpc":"2.0","id":3,"result":{"content":[{"type":"text","text":"{\"gender\":\"male\",\"age\":62,\"patientId\":\"P001\",\"name\":\"张三\",\"diagnosis\":\"2 型糖尿病\"}"}],"isError":false}}
  ```
- 注意：`spring-boot:run` 单跑 app 时，ai-core 从本地仓库 `E:\workRepositoryAi` 解析。更新 ai-core 后需先执行 `.\run-maven-jdk17.ps1 -pl libs/ai-core -q -DskipTests install`（或用 IDE 的模块类路径启动），否则会 `ClassNotFoundException`。

