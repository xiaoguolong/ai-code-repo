# Week 20 阅读提纲 — Prometheus / Grafana / 告警与基数

> 配合 `docs/specs/week-20.md`。目标不是背 API，而是能回答四个问题：
> **指标怎么建模、怎么查、怎么告警、怎么不把 Prometheus 打爆**。

---

## 1. Prometheus 数据模型（30 分钟）

- **时间序列 = 指标名 + 标签集**：`agent_run_count_total{application="ai-code-platform",agent_key="medical-assistant",execution_status="COMPLETED"}`
  是**一条**独立序列；标签值每多一种组合就多一条序列（这就是「基数」）。
- 四种指标类型：Counter / Gauge / Histogram / Summary。
  - Counter 只增（重启归零），查询用 `rate()` / `increase()`；
  - Gauge 可增可减（JVM 堆、活跃会话）；
  - Histogram 产出 `_bucket`（累积计数，带 `le`）、`_sum`、`_count` —— **P95 只能从 `_bucket` 算**。
- 抓取模型是 **pull**：Prometheus 每 15s 打一次 `/actuator/prometheus`，不是应用 push。
  所以「应用在本地、Prometheus 在云上」必须有反向隧道（本周 `tools\metrics-tunnel.ps1`）。

自检问题：

1. 为什么 `llm.tokens.total` 导出成 `llm_tokens_total` 而不是 `llm_tokens_total_total`？
2. `_count` 和 `_bucket{le="+Inf"}` 的关系是什么？
3. 哪些标签绝对不能加？（回答：`traceId`、`userId`、`executionId`、Prompt 正文）

---

## 2. PromQL（60 分钟，重点）

必须掌握的六个函数/模式：

| 模式 | 例子 | 用途 |
|------|------|------|
| `rate(counter[5m])` | `sum(rate(agent_run_count_total[5m]))` | 每秒速率（计数器必须配 rate） |
| `sum by (label)` | `sum by (gen_ai_request_model) (rate(llm_call_count_total[5m]))` | 按维度聚合 |
| 比例 | `sum(rate(x{status=~"5.."}[5m])) / clamp_min(sum(rate(x[5m])), 0.001)` | 错误率；`clamp_min` 防除零 |
| `histogram_quantile` | `histogram_quantile(0.95, sum by (le) (rate(agent_run_duration_seconds_bucket[5m])))` | P95 |
| `increase` | `sum(increase(llm_cost_usd_total[24h]))` | 区间增量（成本） |
| `up` | `up{job="ai-code-platform"} == 0` | 抓取目标存活 |

**坑（本周真机踩过）**：

- `histogram_quantile` 返回 **NaN** 的两种常见原因：
  ① 没有 `_bucket`（Micrometer 默认不导出直方图桶，必须 `percentiles-histogram=true`）；
  ② 所有观测都超过最大桶上界（默认 30s），只有 `+Inf` 有值 —— Agent/LLM 调用常见 30–60s，
  必须配 `maximum-expected-value`。本周把 Agent/LLM 上界设为 **120s**。
- `rate()` 窗口内至少要 2 个样本；窗口小于 2×scrape_interval 会时有时无。

自检问题：给定一个「Agent 失败率 > 20% 持续 10 分钟」的告警，写出 expr 并解释每个函数。

---

## 3. 告警设计（30 分钟）

- 告警四要素：**expr + for + labels(severity/component) + annotations(summary/description)**。
- `for` 用来抗抖动；`severity` 用来分级（critical/warning/info）。
- 抑制规则（inhibit_rules）：目标 down 时抑制同组件的细粒度告警，避免「一次故障刷几十条」。
- 告警要**可行动**：description 里写「先看哪个面板、用哪个 ID 查」，
  本周的 description 直接给出 SkyWalking / Langfuse / traceId 的排查路径。
- 不要为「可能有用」建告警：每条告警都要有人负责响应，否则会变成告警疲劳。

自检问题：为什么 `AiCodePlatformTargetDown` 是 critical，而 `AiCodeGatewayTargetDown` 是 warning？
（网关不可用时平台仍可直连。）

---

## 4. Grafana provisioning（30 分钟）

- **Provisioning 而不是手工点**：数据源 `provisioning/datasources/*.yml`、仪表盘
  `provisioning/dashboards/*.yml` + JSON 目录，容器启动自动加载，重建环境零手工。
- 仪表盘 JSON 的 `uid` 必须与代码/文档一致（本周 `aicode-platform-overview` / `aicode-gateway-overview`），
  否则深链 404。
- 变量（textbox `skywalking_url` / `langfuse_url`）让同一份仪表盘适配不同环境，不写死地址。
- 面板设计按 **RED / USE**：
  - RED：Rate、Errors、Duration（服务视角）；
  - USE：Utilization、Saturation、Errors（资源视角：JVM 堆/GC）。

---

## 5. 基数与成本（20 分钟）

- 一个标签值 = 一条序列；`userId` 有 10 万用户 → 10 万条序列，Prometheus 内存与查询都会崩。
- 规则：**指标标签只放低基数维度**（agentKey、model、tool、status），
  高基数标识（traceId、executionId、userId）只放 **span 属性 / 日志**。
- 本周指标标签清单在 `docs/architecture/week-20-architecture.md` 第 4 节，可作为 code review 检查表。

---

## 6. 与 OTel / SkyWalking / Langfuse 的分工（20 分钟）

| 问题 | 用哪个后端 |
|------|------------|
| 「最近一小时 Agent 失败率多少？」 | Prometheus + Grafana |
| 「这次请求为什么慢？慢在哪一段？」 | SkyWalking 拓扑 + span 树 |
| 「这次 LLM 调用用了哪个 Prompt 版本、多少 token、多少钱？」 | Langfuse generation |
| 「日志里这条 traceId 的完整故事」 | 控制台/日志 + `audit_log.trace_id` |
| 「同一条请求在四个系统里怎么对上？」 | `GET /monitoring-overview` 的 `correlation` |

**关键认知**：指标是**聚合**视图，天然不含单条请求 ID；从指标跳到单条 trace 靠
「时间窗 + 组件 + 业务 traceId」，不靠给指标加 traceId 标签（那会打爆基数）。

---

## 7. 延伸阅读

- Prometheus 官方：*Data model*、*Querying basics*、*Alerting rules*、*Histograms and summaries*
- Google SRE Book：Chapter 6 *Monitoring Distributed Systems*（四个黄金信号）
- Grafana 官方：*Provisioning*、*Dashboard JSON model*
- OpenTelemetry：*Semantic Conventions for GenAI*（本周指标标签 `gen_ai.*` 的来源）
- Micrometer 官方：*Prometheus naming convention*、*Histograms and percentiles*
