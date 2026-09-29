# Week 09 接口文档 — 患者风险分析 Workflow（spring-ai-alibaba-agent）

> Base URL：`http://localhost:8084`
> 响应信封：`{ "code": "...", "message": "...", "data": ... }`
> 成功时 `code=SUCCESS`，`data` 为业务数据；失败时 `data=null`
> 与第 8 周 `POST /api/v1/framework/agents/runs` 并存，本端点新增于同模块。

---

## 1. 运行患者风险分析 Workflow

### POST /api/v1/workflows/patient-risk/runs

提交 `patientId` 后，由 Spring AI Alibaba Graph 按**固定顺序**执行多节点流程：

```
query_patient（查患者）→ query_metrics（查指标）→ judge_risk（规则风险判断）
   →(条件边) HIGH → escalate（加急标记）
   → generate_report（LLM 生成报告）→ END
```

节点直接经 `ToolPort` 调度工具（不经过模型选工具）；风险判断为确定性规则（`PatientRiskAssessor`）；报告由 `ChatModelPort` 生成。

### 请求参数

| 字段 | 类型 | 必填 | 限制 | 说明 |
|------|------|------|------|------|
| patientId | string | **是** | 非空白，最大 100 字符 | 患者编号，如 `P001` |

### 请求示例

```bash
curl -X POST http://localhost:8084/api/v1/workflows/patient-risk/runs \
  -H "Content-Type: application/json" \
  -d '{ "patientId": "P001" }'
```

### 成功响应 200

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "workflowId": "8f2a1b3c-4d5e-4f6a-9b0c-1d2e3f4a5b6c",
    "patientId": "P001",
    "patient": {
      "patientId": "P001",
      "name": "张三",
      "age": 62,
      "gender": "male",
      "diagnosis": "2 型糖尿病"
    },
    "metrics": {
      "systolic": 148,
      "diastolic": 92,
      "fastingGlucose": 8.6,
      "hba1c": 7.9
    },
    "riskLevel": "MEDIUM",
    "riskLabel": "中风险",
    "justification": "收缩压 148 达到 140 阈值；舒张压 92 达到 90 阈值；空腹血糖 8.6 达到 7.0 阈值；糖化血红蛋白 7.9 达到 7.0 阈值",
    "escalated": false,
    "report": "患者张三（P001）血压 148/92、空腹血糖 8.6、糖化血红蛋白 7.9，均未达标，属中风险，建议调整降压与降糖方案并门诊随访。",
    "model": "deepseek-chat",
    "usage": {
      "promptTokens": 310,
      "completionTokens": 180,
      "totalTokens": 490
    }
  }
}
```

### 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| workflowId | string | 本次流程标识（服务端生成 UUID） |
| patientId | string | 请求携带的患者编号 |
| patient | object | 患者基础信息（patientId/name/age/gender/diagnosis） |
| metrics | object | 健康指标（systolic/diastolic/fastingGlucose/hba1c） |
| riskLevel | string | `LOW` / `MEDIUM` / `HIGH` |
| riskLabel | string | 风险等级中文标签（低风险/中风险/高风险） |
| justification | string | 风险判断依据（命中项说明） |
| escalated | boolean | 是否加急（`riskLevel==HIGH` 时为 true） |
| report | string | 报告文本（由模型生成） |
| model | string | 报告生成所用模型名 |
| usage | object | 汇总 Token（promptTokens/completionTokens/totalTokens） |

### 失败响应

**400 - 参数校验失败**

```json
{ "code": "VALIDATION_ERROR", "message": "patientId 不能为空", "data": null }
```

**502 - 工具执行失败（未知工具/参数非法）**

```json
{ "code": "TOOL_EXECUTION_ERROR", "message": "工具执行失败", "data": null }
```

**502 - 模型调用失败（报告生成）**

```json
{ "code": "CHAT_MODEL_ERROR", "message": "模型调用失败", "data": null }
```

**500 - 未预期错误**

```json
{ "code": "INTERNAL_ERROR", "message": "系统繁忙，请稍后重试", "data": null }
```

---

## 2. 与第 8 周 Graph Agent 的关系

| 维度 | `POST /api/v1/framework/agents/runs`（第 8 周） | `POST /api/v1/workflows/patient-risk/runs`（第 9 周） |
|------|-------------------------------------------------|---------------------------------------------------------|
| 编排 | `agent ↔ tools` 模型带工具循环（ReAct） | 固定顺序多节点（Workflow） |
| 入参 | `task`（自由任务，模型决定调工具） | `patientId`（固定流程） |
| 工具调度 | 模型返回 toolCalls 后由 `tools` 节点执行 | 节点直接经 `ToolPort` 调度 |
| 分支 | 单一循环 | 条件边（`urgent`/`routine`） |
| 规则 | 无确定性业务规则 | `PatientRiskAssessor` 确定性风险判断 |

两套端点共享同一 `ToolPort`（`PatientLookupTool` / `HealthMetricTool`）与同一模型通道，互不影响。

---

## 3. 模型通道与配置

沿用第 8 周模块配置，不新增配置项：

| 配置 | 说明 | 默认 |
|------|------|------|
| `SPRING_AI_OPENAI_BASE_URL` / `spring.ai.openai.base-url` | Spring AI OpenAI 兼容端点（只写主机名） | `https://api.deepseek.com` |
| `spring.ai.openai.api-key` | 密钥，仅环境变量 `LLM_API_KEY` | 空 |
| `spring.ai.openai.chat.options.model` | 模型名，环境变量 `LLM_MODEL` | `deepseek-chat` |
| `prompt.version` | 报告提示词版本（`prompts/patient-risk-report-{version}.txt`） | `v1` |

> 报告提示词文件：`apps/spring-ai-alibaba-agent/src/main/resources/prompts/patient-risk-report-v1.txt`。

---

## 4. HTTP 状态码汇总

| 状态码 | 含义 | 触发场景 |
|--------|------|----------|
| 200 | 成功 | 流程正常收敛，返回患者/指标/风险/报告 |
| 400 | 请求参数错误 | patientId 空白、长度超限 |
| 500 | 服务器内部错误 | 未预期异常 |
| 502 | 上游服务错误 | 工具执行失败、模型调用失败 |
