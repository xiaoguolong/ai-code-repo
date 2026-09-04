# Week 04 实现日志 — 企业知识库 Agent V1（登录 / 知识库 / 文档 / 文件 / 问答 / 历史 / Token / Docker）

- 日期：2026-09-04
- 批次：1
- 对应 Spec：docs/specs/week-04.md
- 对应阅读：无（本周以工程交付为主，Agent 概念留待第 5 周）

## 1. 本周目标

把第 3 周单用户 RAG 升级为多用户多租户企业知识库应用，独立模块 `apps/enterprise-knowledge-agent`：

1. Sa-Token 登录鉴权（注册/登录/注销/当前用户，密码加盐 md5）。
2. 用户-知识库-文档三级多租户隔离（越权 403）。
3. 文档切片向量化入库（pgvector 生产落库，memory 测试）。
4. 限定知识库范围的 RAG 问答（带 sources + 会话历史 + Token 统计落库）。
5. 通用文件上传服务（本地落地 + OSS/OBS 接口预留）+ OCR 图片占位符替换为访问路径。
6. Docker 一键部署（Dockerfile + docker-compose，pgvector 镜像）。

## 2. 边界

- Always：Sa-Token 登录/鉴权、三级多租户隔离、文档切片向量化入库、限定 kb 问答、会话历史、Token 统计、文件上传、OCR 占位符替换、Docker 部署、中文 JavaDoc、单测不打真实网络、无密钥入库
- Never：改 JAVA_HOME、密码/密钥明文入库、用户输入拼系统提示、Spring Security 全家桶、Tika/POI、抽共享库模块、OSS/OBS 落地

## 3. 增量架构

见 Spec `docs/specs/week-04.md` 的「增量架构」三张图（分层 / 登录时序 / 问答时序 / OCR 清理时序）。

## 4. 新增类型清单

| 类型 | 名称 | 职责 |
|------|------|------|
| Port | UserPort / KnowledgeBasePort / AuthTokenPort | 用户、知识库、登录令牌 |
| Port | FileStoragePort / ImageDownloadPort | 文件存储、远程图片下载 |
| Port | DocumentPort / VectorStorePort / EmbeddingModelPort / ConversationPort / AuditPort / ChatModelPort / PromptTemplatePort / DocumentOcrPort | 沿用第 2/3 周，签名扩展多租户 |
| Domain | PasswordHasher / FileUrlAssembler / OcrMarkdownImageProcessor | 密码哈希抽象、路径组装、占位符清理 |
| UseCase | Register/Login/Logout/GetCurrentUser、Create/List/Get/Update/DeleteKnowledgeBase、Ingest/List/DeleteDocument、AskKnowledge、ListChatHistory、TokenStats、Upload/GetFile、RecognizeOcr | 共 19 个用例 |
| Adapter | MyBatisUser/KnowledgeBase/Document/Conversation/AuditAdapter、LocalFileStorageAdapter、HttpImageDownloadAdapter、SaTokenAuthTokenAdapter、SaTokenMd5PasswordHasher | 仓储/存储/下载/鉴权/哈希 |
| Adapter | Hashing/OpenAi Embedding、InMemory/PgVector、OpenAiChatModel、ClasspathPrompt、PaddleOcrVl | 沿用第 2/3 周 |
| Config | AppConfiguration、SaTokenConfig、7 个 Properties | 装配 + Sa-Token 拦截器 |
| Entity | AppUser/KnowledgeBase/Document/ChatSession/ChatMessage/TokenRecord/FileRecord | 7 张表映射 |
| Migration | V1~V5 + db/postgresql/V6 | 建表 + pgvector 扩展 |
| Docker | Dockerfile + docker-compose.yml | 多阶段构建 + pgvector 镜像编排 |

## 5. RED

- 命令：`.\run-maven-jdk17.ps1 test`
- 结果：`Tests run: 45, Failures: 0, Errors: 2, Skipped: 0`（2 个 H2 仓储测试报错）
- 原因：`MyBatisUserAdapter`/`MyBatisKnowledgeBaseAdapter` 误用 `insertWithPk`（该 API 要求实体主键非空，用于手动指定主键），自增主键应走 `insert`。报 `FluentMybatisException: The pk of insert entity can't be null`。
- 说明：本周大部分领域逻辑为第 3 周已验证逻辑的移植+多租户适配，新逻辑（FileUrlAssembler / OcrMarkdownImageProcessor / SaTokenMd5PasswordHasher / 多租户检索过滤）在实现后以测试锁定；上述 insertWithPk 的 RED→GREEN 是真实的缺陷复现循环。

## 6. GREEN

- 改动文件：新建模块 `apps/enterprise-knowledge-agent` 全量（domain 19 模型 + 12 端口 + 5 领域服务 + 8 异常、infrastructure 7 实体 + 13 适配器 + 7 配置、application 19 用例 + 12 command/outcome、controller 7 个 + GlobalExceptionHandler、dto 18 个、7 个迁移 + 2 prompt、application*.yml、pom、Dockerfile、docker-compose）
- 命令：`.\run-maven-jdk17.ps1 test`
- 结果：`Tests run: 45, Failures: 0, Errors: 0, Skipped: 0`，`BUILD SUCCESS`
- 打包：`.\run-maven-jdk17.ps1 -q -DskipTests package` 成功，产出 `enterprise-knowledge-agent-0.1.0-SNAPSHOT.jar`（约 30 MB）

## 7. 重构

- 列表/删除文档用例的归属校验收敛为注入 `GetKnowledgeBaseUseCase`，消除类内 `new`。
- 会话归属校验新增 `ConversationPort.sessionOwner`，供历史查询做多租户隔离。
- 密码哈希抽象为 `PasswordHasher` 接口，默认 `SaTokenMd5PasswordHasher`，留 BCrypt/Argon2 替换点。

## 8. 质量门禁

- [x] 编译（`-DskipTests compile` 成功）
- [x] 单测 45/45
- [x] 无密钥入库（`EMBEDDING_API_KEY`/`LLM_API_KEY`/`OCR_API_KEY` 仅环境变量；源码无 sk- / 硬编码密钥）
- [x] 分层未突破（Controller 无 SDK/SQL；业务层无 SQL；domain 无 Spring 注解）
- [x] public 类型中文 JavaDoc（含 entity 字段注释）
- [x] 未改系统/用户 JAVA_HOME；沿用 `run-maven-jdk17.ps1`
- [x] Flyway H2 迁移通过（V1~V5）；V6 仅 PostgreSQL 目录
- [x] 无 System.out/err 打印

## 9. 验证证据

```
Tests run: 45, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

新增测试覆盖：

- domain：TextChunker / RagContextAssembler / FileUrlAssembler / OcrMarkdownImageProcessor（含下载失败保留占位符）
- application：Register（重名 409）/ Login（错误密码 401）/ GetKnowledgeBase（越权 403）/ IngestDocument（切片向量化、越权）/ AskKnowledge（历史+Token 落库、越权）/ ListChatHistory（越权）
- infrastructure：SaTokenMd5PasswordHasher（确定性、非明文、匹配）/ HashingEmbedding（维度、确定性）/ InMemoryVectorStore（按 kb 过滤、删除）/ PaddleOcrResponseMapper（markdown+images）/ MyBatisUser / MyBatisKnowledgeBase（H2 仓储、自增回填）

## 10. 风险与下周输入

- 密码为加盐 md5（快速哈希），演示可接受，生产应换 BCrypt/Argon2（`PasswordHasher` 已留替换点）。
- Sa-Token 会话默认内存，多实例需迁 Redis（`sa-token-redis-jackson`），列 Ask first。
- pgvector 生产落库需在真实 PostgreSQL 验证 `CREATE EXTENSION` 与向量检索（docker-compose 用 `pgvector/pgvector:pg16`）。
- 语义向量化需 `EMBEDDING_PROVIDER=openai`；默认 hashing 仅跑通链路。
- OCR 图片下载为同步串行，量大时需改异步/限流（第 3 周遗留的批量控 QPS 仍待处理）。
- 意图路由 / Tool Calling / Agent 属第 5 周。

## 11. 决策记录 ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 鉴权框架 | Spring Security / Sa-Token / 手写 | Sa-Token | 轻量、开箱即用、spring-boot3-starter 原生支持 |
| 密码哈希 | BCrypt / Sa-Token SaSecureUtil | SaSecureUtil.md5BySalt（盐=username+全局盐） | 用户指定零额外依赖；PasswordHasher 抽象留替换点；风险已在风险节标注 |
| 令牌形态 | JWT / 默认随机令牌 | 默认随机令牌 | 简单够用，JWT 按需后续切 |
| 模块归属 | 扩 spring-ai-demo / 新模块 | 新模块 enterprise-knowledge-agent | 规范 5.1；多用户企业与单机 demo 边界不同 |
| RAG 管线复用 | 抽共享库 / 模块内自含 | 模块内自含（从第 3 周适配） | 仅一个复用者，抽库过早；规则三后再抽 |
| pgvector | 手动装扩展 / pgvector 镜像 | 已装好直接落库；新环境用 pgvector/pgvector:pg16 | 现有容器已可用，镜像仅作新环境默认 |
| 文件存储 | 本地 / OSS / OBS | 本地落地 + OSS/OBS 接口预留 | 演示零密钥可跑通；多后端用端口隔离 |
