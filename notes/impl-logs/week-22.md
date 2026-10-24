# Week 22 实现日志 — 批次 1：登录限流 + 应用上云部署资产

- 日期：2026-10-24
- 批次：1（安全加固 + 部署形态；**不执行任何云端变更**）
- 对应 Spec：`docs/specs/week-22.md`
- 对应部署手册：`docs/deploy/week-22-app-deploy.md`

## 1. 本批次目标

第 21 周把「身份 + 租户 + 患者分析 Agent」做完后，出现两个必须先补的缺口：

1. `POST /api/v1/platform/auth/verify` 是**免登录**的密码校验端点，没有限流；
2. 部署仍是「本机进程 + 云上中间件」，指标抓取依赖反向 SSH 隧道。

本批次只做**代码与资产**，不装包、不写 systemd、不开端口。

## 2. 边界

- Always：限流在凭据校验**之前**；成功清零；429 走枚举；可配置关闭；部署资产只写文件。
- Never：本批次动云端；限流做成静态全局状态；默认信任 `X-Forwarded-For`；把 `.env` 打进产物。

## 3. 新增类型

| 类型 | 名称 | 职责 |
|------|------|------|
| Port | `LoginAttemptPort` | 失败计数（isBlocked / recordFailure / clear） |
| Adapter | `InMemoryLoginAttemptAdapter` | 滑动窗口 + 封禁窗口，按 key 加锁 |
| UseCase | `PlatformAuthenticationFacade` | 先限流后认证、失败计数、成功清零 |
| 异常 | `PlatformRateLimitExceededException` | 429 语义，与 401 严格区分 |
| 工具 | `ClientIpResolver` | 默认只信 remoteAddr，显式开关才信 XFF |
| 配置 | `PlatformRateLimitProperties` | `platform.security.rate-limit.*`（非法值回退） |
| 契约 | `ApiErrorCode.RATE_LIMITED` | HTTP 429（契约测试的状态白名单同步扩展） |
| 部署资产 | `deploy/app/{systemd,nginx,remote-deploy.sh}` + `tools/deploy-app.ps1` + 部署手册 | jar + systemd 过渡形态 |

## 4. RED（如实说明）

本批次**没有单独采集 RED 证据**。原因：限流与部署资产是同一次连续实现完成，
测试写完直接进入实现，`test-compile` 中间态没有留档——这与规范 4.1「先红灯再实现」有偏离。

补偿措施（不是等价物，只是把风险压住）：

- 每个断言都对应一条可观察行为（阈值、封禁到期、窗口过期、成功清零、key 隔离、429 契约、
  默认不信任 XFF），没有「为了覆盖率写的空断言」；
- 中途真实红过一次并修掉：`LoginRateLimitHttpTest` 起不来，根因是控制器改注入
  `PlatformAuthenticationFacade` 后，测试仍只 mock 了 `PlatformAuthUseCase`（少一个 bean）；
  补齐 mock 后 22 例全绿。

> 下一批次恢复「先跑红灯并留档」的做法；这条已写进本批次风险，不再重复。

## 5. GREEN

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-maven-jdk17.ps1 -pl apps/spring-ai-alibaba-agent -am test `
  "-Dtest=InMemoryLoginAttemptAdapterTest,PlatformAuthenticationFacadeTest,ClientIpResolverTest,LoginRateLimitHttpTest,PlatformAuthControllerTest,ApiErrorCodeContractTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false"
```

```text
Tests run: 7, Failures: 0 -- InMemoryLoginAttemptAdapterTest
Tests run: 6, Failures: 0 -- PlatformAuthenticationFacadeTest
Tests run: 4, Failures: 0 -- ClientIpResolverTest
Tests run: 4, Failures: 0 -- LoginRateLimitHttpTest
Tests run: 1, Failures: 0 -- PlatformAuthControllerTest
Tests run: 48, Failures: 0 -- ApiErrorCodeContractTest（新增 RATE_LIMITED 后 15×3+3）
=> 合计 70，全部通过
```

全量跑时又抓出一处**真实的连带影响**（不是新逻辑错，是旧测试的装配假设变了）：

```text
[ERROR] ApiStandardsContractTest Tests run: 5, Errors: 5
java.lang.IllegalStateException: Failed to load ApplicationContext ...
（根因：`@WebMvcTest(PlatformAuthController.class)` 只 mock 了 PlatformAuthUseCase）
```

控制器改为注入 `PlatformAuthenticationFacade` 后，该切片测试少一个 bean；
且 `authenticationFailureUsesEnumCodeAndHidesCredentials` 的桩打在旧用例上，改走门面后不再生效。
修法：补 `@MockitoBean PlatformAuthenticationFacade`，把桩改到门面。

> 这条和第 21 周「新增构造依赖必须同步所有切片测试」是同一类问题；
> 这次是全量跑才暴露，说明**只跑新增用例不足以证明没回归**。

根聚合结果见第 9 节。

### 5.1 本批次自己踩的第二个坑（写死在这，别再犯）

| # | 现象 | 根因 | 修法 |
|---|------|------|------|
| 1 | `tools/deploy-app.ps1` 用 PowerShell 5.1 的 `Parser::ParseFile` 解析直接报「字符串缺少终止符 / 意外的标记」 | `write` 写出的是**无 BOM 的 UTF-8**，PS 5.1 按 ANSI(GBK) 读，中文注释被解成乱码并把引号/括号带歪 | 统一加 UTF-8 BOM（与 `tools/*.ps1` 既有约定一致，第 20 周缺陷 #8 同类）；bash 脚本保持无 BOM |
| 2 | `-DryRun` 在尚未打包时会直接抛「未找到产物」 | DryRun 跳过了打包步骤，却仍强制要求 jar 存在 | DryRun 下允许无产物，只打印提示 |

## 6. 设计要点（为什么这么做）

1. **按 `ip|username` 计数**：只按 IP 会误伤同一出口 NAT 的正常用户，只按用户名又给「锁别人账号」留口子；
2. **限流放在应用层而不是过滤器**：过滤器读请求体要做缓存包装（容易把 JSON 消费掉），
   放应用层还能用固定 `Clock` 纯单测窗口与封禁到期，不必起 Web 环境；
3. **被限流时不查库、不比对密码**：否则限流只是把攻击成本从「一次哈希」变成「两次查询」；
4. **默认不信 `X-Forwarded-For`**：只有显式开启才取第一跳，否则伪造头即可绕过（这比不做更糟）；
5. **429 单独错误码**：混用 401 会让客户端以为「密码错」而继续重试，等于帮攻击者打满限流。

## 7. 部署资产（未执行）

- `deploy/app/systemd/*.service`：`WorkingDirectory` 强制模块目录（`.env` 按工作目录解析）、
  显式堆（平台 768m / 网关 512m）、SkyWalking agent 参数、`Restart=always`；
- `deploy/app/remote-deploy.sh`：校验 → 备份 `app.jar.prev` → 切换 → 重启 → 90s 健康检查 → 失败自动回滚；
  回滚模式 `--rollback`；
- `tools/deploy-app.ps1`：构建 →（默认跑全量测试）→ 上传 jar 与远端脚本 → 远端切换；
  `-DryRun` 只打印，`-SkipTests`、`-Rollback` 可选；远端命令只用简单参数（避开第 20 周 `-Cmd` 引号坑）；
- `deploy/app/nginx/aicode.conf.example`：前端到位后启用，`/api` → 8084、`/gw` → 8080、
  `/actuator` 返回 404、`proxy_read_timeout 120s`（真实 Agent 实测 32s）。

## 8. 质量门禁

- [x] 编译：`test-compile` 通过（JDK17 参数方式，未改 `JAVA_HOME`）
- [x] 单测：本批次新增 24 例（21 个用例 + 新枚举带来的 3 个参数化契约用例）全绿；根聚合见第 9 节
- [x] 分层：`LoginAttemptPort` 在 domain，限流实现只在 infrastructure；
      `PlatformAuthenticationFacade` 不感知 Servlet（IP 由控制器传入）
- [x] 安全：默认不信任 `X-Forwarded-For`；限流 key 归一化（大小写/空白）；429 不含内部细节
- [x] YAGNI：未引入 Redis 限流、验证码、账号锁定表；未做镜像与 CI
- [x] 云端：**未做任何变更**（无 apt、无 systemd、无端口、无 Prometheus 改动）

## 9. 根聚合测试

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run-maven-jdk17.ps1 test
```

```text
ai-core 90 / enterprise 37 / patient 21 / platform 477 / gateway 100
=> 合计 725，Failures 0，Errors 0，BUILD SUCCESS
```

（Week 21 基线 701 → 725，新增 24；enterprise / patient / gateway 零变化。）

## 10. 风险与下一步

- **限流是内存版**：重启清零、多实例不共享；单实例阶段够用，换 `LoginAttemptPort` 适配器即可；
- **没有 `Retry-After`**：客户端只能从文案知道「稍后再试」；需要精确剩余秒数时再加；
- **429 与审计的关系**：被限流的请求不会写审计（未进入认证逻辑），这是有意的，避免攻击流量刷审计表；
- **部署资产未在云端执行**：三个前置（装 JDK、建用户、安全组）未确认前不执行，执行步骤见部署手册第 2 节；
- 下一步：确认前置 → 平台上云 → 内存基线 → enterprise 拉起与 SSO 真机 → 记忆生命周期。

## 11. 交付物

- `docs/specs/week-22.md`、`docs/deploy/week-22-app-deploy.md`、本日志
- 代码：6 个新类 + 1 个枚举值 + 控制器/异常处理改造
- 部署资产：`deploy/app/`（2 个 systemd 单元、`remote-deploy.sh`、nginx 模板）、`tools/deploy-app.ps1`
- 配置：`application.yml`（`platform.security.rate-limit.*`）、`.env.example`（5 个可选键）

## 12. ADR

| 决策 | 选项 | 选择 | 理由 |
|------|------|------|------|
| 限流存储 | 内存 / Redis | 内存 | 单实例；Redis 会引入新依赖与序列化问题，端口已隔离 |
| 计数维度 | IP / 用户名 / IP+用户名 | IP+用户名 | 兼顾误伤与「锁他人账号」风险 |
| 限流位置 | Servlet 过滤器 / 应用层门面 | 应用层 | 避免消费请求体；可用固定时钟纯单测 |
| XFF 信任 | 默认信 / 默认不信 | 默认不信 | 默认信等于给攻击者一个绕过开关 |
| 429 vs 401 | 复用 401 / 独立 429 | 独立 429 | 语义不同，混用会导致客户端重试放大攻击 |
| 部署形态 | 镜像 / jar+systemd | jar+systemd | 单机单环境，镜像收益低；先消灭隧道依赖 |
| 健康检查位置 | 本地轮询 / 远端脚本内 | 远端脚本内 | 失败可在一次 ssh 会话内自动回滚，减少人工介入 |
