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

- [ ] LLM Trace
- [ ] Prompt管理
- [ ] Token统计
- [ ] 成本分析


实践：

部署：

- [ ] Langfuse
- [ ] PostgreSQL
- [ ] ClickHouse


接入：

- [ ] Prompt记录
- [ ] Token记录
- [ ] Agent执行链


---

# 第19周：SkyWalking


实践：

接入：

- [ ] Gateway
- [ ] Agent服务
- [ ] Spring Cloud服务


监控：

- [ ] HTTP
- [ ] Feign
- [ ] MySQL
- [ ] Redis
- [ ] JVM


---

# 第20周：完整监控体系


完成：

- [ ] SkyWalking
- [ ] OpenTelemetry
- [ ] Langfuse
- [ ] Prometheus
- [ ] Grafana


---

# 第六阶段：企业项目实战（第6个月）


项目：

医疗 SaaS AI Agent 平台


---

# Agent 1：患者分析Agent


完成：

- [ ] 查询患者
- [ ] 查询检查报告
- [ ] 查询健康指标
- [ ] 风险分析
- [ ] 自动生成报告


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

- [ ] Tool Calling
- [ ] Memory
- [ ] Workflow
- [ ] Multi Agent


## Java能力

- [ ] Spring AI Alibaba
- [ ] LangChain4j
- [ ] Spring Boot
- [ ] Spring Cloud


## 企业能力

- [x] 权限（Week 13 RBAC：用户 → 角色 → Agent → Tool → 数据域，Week 16 落库）
- [x] 安全（Week 14 Guardrail：注入拦截、输入校验、输出脱敏、Tool 参数白名单）
- [x] 审计（Week 16：AuditLogPort + audit_log 落库，4 类事件 + traceId 可追溯）
- [x] 日志（Week 16：traceId 全链路 + 统一访问日志 + 结构化 key=value）
- [x] 可观测（Week 17：OTel span 树 + 调用/token 指标 + Prometheus 出口，日志与链路 traceId 单源）
- [ ] 规范落地度：响应信封与规范 5.6 草案仍有偏离（见 docs/specs/week-16.md 收尾审计）


## 运维能力

- [ ] Docker
- [ ] Kubernetes
- [ ] SkyWalking
- [x] OpenTelemetry（Week 17：Trace/Span/Metric/Context + OTLP 导出 + Prometheus 指标出口）
- [ ] Langfuse
- [ ] Prometheus
- [ ] Grafana


---

# 最终目标

Java AI Agent 工程师

↓

企业级 AI 应用架构师