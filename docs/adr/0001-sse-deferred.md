# ADR-0001：SSE 流式回答顺延（第 30 周）

- **状态**：已接受（2026-10-25）
- **决策人**：Week 30 实现
- **相关**：`docs/specs/week-30.md`、`libs/ai-core/.../ChatModelPort.java`

## 背景

前端希望知识库问答/患者分析能“边生成边显示”。当前平台执行链路是同步的：

```java
public interface ChatModelPort {
    ChatResult chat(List<ChatMessage> messages, ChatOptions options, List<ToolDefinition> tools);
}
```

没有 `Flux`、回调或增量 token 接口；`PlatformExecutionUseCase` 也是“跑完写执行记录再返回 200”。
第 29 周前端因此按同步 200 + loading 实现，180s 超时。

## 决策

**第 30 周不做 SSE**，优先企业知识库页与前端 CI；SSE 顺延到第 31 周或出现明确产品要求时。

## 理由

1. 只在前端加 `EventSource` 没有后端流，做出来是“假流式”（最后一次性返回），没有收益；
2. 真流式至少需要：
   - `ChatModelPort` 新增流式契约（`Flux<ChatChunk>` 或 `Consumer<ChatChunk>`）；
   - Spring AI/OpenAI 适配器实现增量解析；
   - 平台 SSE 端点：鉴权、Guardrail、执行记录落库、取消/断连；
   - 前端 reader、错误/重连、审批/随访等副作用在流结束后的收敛；
3. 这些改动跨 ai-core/platform/gateway，测试面大；本周 3 天排期不足以可靠交付；
4. 当前同步 200 已能满足“可用”：知识库问答 1–2s，患者分析 20–60s，页面有 loading 与超时提示。

## 后果

- 长任务仍是“转圈等结果”，首字延迟不可见；
- 执行记录/审批/随访语义保持简单（一次完成再落库）；
- 前端无需处理流式断连、半截 JSON、重复事件。

## 触发条件（满足任一再启动）

- 产品明确要求“首字 < 2s”或对话式多轮；
- 患者分析 P95 超过 60s，用户等待不可接受；
- 需要“停止生成”/中途取消。

## 实施清单（未来）

1. `ChatModelPort` 增加流式方法（保留同步默认实现，避免破坏现有适配器）；
2. OpenAI/Spring AI 适配器实现流式；
3. 平台新增 `POST /agents/{key}/runs/stream`（SSE），鉴权 + 执行记录 + 取消；
4. Guardrail 在流结束后统一校验，审批/随访副作用只执行一次；
5. 前端 `fetch` + `ReadableStream` reader，替换 AgentRun 的同步调用；
6. 契约测试 + 断连/取消/错误用例；灰度开关。
