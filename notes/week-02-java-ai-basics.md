# 第2周精读提纲：Java AI 开发基础

> 对应书籍：《Applied AI for Enterprise Java Development》  
> 对应 Spec：`docs/specs/week-02.md`  
> 本周代码目标：Prompt 模板、Structured Output、Redis 上下文、三张表

## 0. 本周要建立的心智模型

| 概念 | 一句话 | 落到代码 |
|------|--------|----------|
| AI Service | 把 LLM 当接口，而不是散落的 HTTP 调用 | UseCase 调 Port，不调 SDK |
| Prompt 模板 | Prompt 当代码：命名、版本、变量 | `prompts/{name}-{version}.txt` |
| Structured Output | 约束模型输出可解析结构 | `responseFormat=json` + 解析失败 422 |
| 短期记忆 | 多轮对话必须进 context window | Redis + 滑动窗口 |

## 1. 与第1周的衔接

第1周打通应用层 → 模型层。本周只加编排层，不换模型适配器。

```
Prompt Engineering（本周模板化） → RAG / Agent（第3、5周） → Fine-tuning（不做）
```

## 2. 实践对照

- 用户输入永远是 `user` role，模板变量不得注入用户原文到 system。
- Redis 存热上下文；PostgreSQL 存会话/消息/Token，重启可回填。
- JSON Mode 仍可能夹 markdown 围栏，解析前剥离，失败则 422。
