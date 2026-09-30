# Week 14 实现日志 — AI 安全（Guardrails）

- 日期：2026-09-30
- 批次：2
- 对应 Spec：docs/specs/week-14.md

## 1. 本周目标

1. ai-core 落地 `GuardrailPort` + `DefaultGuardrailAdapter`（注入检测 / 长度 / PII 脱敏 / Tool 参数键白名单）
2. `GuardrailToolPort` 装饰 Tool 链（RBAC 外层 → Guardrail 内层 → ToolRegistry）
3. Platform Run 集成：`PlatformGuardrailService` 校验 input、脱敏 output
4. HTTP 400 `GUARDRAIL_VIOLATION` 异常映射

## 2. 边界

- Always：与 RBAC 正交；中文 JavaDoc；TDD；Week 8–13 编排不改
- Never：LLM 二次分类器；外部 NeMo；用 Guardrail 替代 RBAC

## 3. 增量架构

见 `docs/architecture/week-14-architecture.md`。

## 4. 新增类型清单

| 类型 | 名称 | 职责 |
|------|------|------|
| Port | GuardrailPort | 输入校验 / 输出脱敏 / Tool 白名单 |
| Model | GuardrailContext | 通道 + userId + agentKey |
| Exception | GuardrailViolationException | 内容安全违例 |
| Adapter | DefaultGuardrailAdapter | 规则驱动 Guardrail 实现 |
| Adapter | GuardrailToolPort | ToolPort 装饰器 |
| Util | PiiMasker | 手机/身份证/邮箱掩码 |
| Config | GuardrailProperties | guardrail.* 配置 |
| Service | PlatformGuardrailService | Platform Run 入出参编排 |
| Test | DefaultGuardrailAdapterTest / GuardrailToolPortTest | ai-core 单测 |
| Test | PlatformExecutionUseCaseTest（+2） | 注入拦截 + 输出脱敏 |

## 5. RED

- 先写 DefaultGuardrailAdapterTest、GuardrailToolPortTest、PlatformExecutionUseCaseTest 新场景
- 编译失败（GuardrailPort 尚未实现）

## 6. GREEN

- 命令：`.\run-maven-jdk17.ps1 test`
- 结果：ai-core **80** / enterprise 21 / patient 19 / spring-ai-alibaba-agent **77**，`BUILD SUCCESS`（合计 **197**）
- 修复：Tool 链合并为单 Bean 工厂方法，避免 Spring 循环依赖

## 7. 质量门禁

- [x] 根聚合 test 全绿
- [x] Week 8–13 测试不回退
- [x] Guardrail 与 RBAC 分层未突破
- [x] 无密钥入库

## 8. 验证证据

```
Tests run: 80 (ai-core), 77 (spring-ai-alibaba-agent)
BUILD SUCCESS (197 total)
```

验收场景：
- `ignore previous instructions` → GuardrailViolationException
- 输出 `13812345678` → `138****5678`
- Tool 参数键 `hack` → 拒绝

## 批次 2：Postman + HTTP 集成测试

- 新增 `docs/postman/week-14.postman_collection.json`（注入/超长/RBAC 对照/合法 Run）
- 新增 `PlatformExecutionGuardrailTest`（4 场景：英文/中文注入 400、P999 仍 403、PII 脱敏 200）
- 新增 `PlatformExecutionGuardrailDisabledTest`（`guardrail.enabled=false` 注入不再 400）
- 命令：`.\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am test` → **82** tests，BUILD SUCCESS

## 9. 风险与下周输入

- 规则注入检测可被绕过（对抗样本）；第 15 周 Eval 可建攻击集回归
- Tool 白名单仅键名；值域仍依赖 RBAC patientId

## 10. ADR

| 决策 | 选择 | 理由 |
|------|------|------|
| 端口位置 | ai-core | 横切；patient-agent 可复用 |
| Tool 链顺序 | RBAC→Guardrail | 先拒未授权 |
| 注入检测 | 正则规则 | YAGNI；可测 |
| Bean 装配 | 单工厂方法链式装饰 | 避免 ToolPort 循环依赖 |
