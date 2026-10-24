# Spec: Week 22 — 批次 1：登录限流 + 应用上云部署资产（不碰云端）

## Objective

第 21 周把医疗 SaaS 的「身份 + 租户 + 第一个 Agent」做完后，平台已经具备上云条件，
但有两件事必须先补：

1. **登录/身份校验端点没有限流**。第 21 周新增的 `POST /api/v1/platform/auth/verify`
   是**免登录**的密码校验端点，一旦随应用上云暴露给任何非本机来源，就是现成的撞库靶子；
   同时每次失败都会写一条审计，等于顺带打审计表。
2. **部署形态还停留在「本机进程 + 云上中间件」**。需要一个可回滚、可观测、
   配置不漂移的 jar 部署形态，否则「上云」只能靠手工 nohup。

本批次**只做这两件事里的代码与资产**，**不执行任何云端变更**（装包、systemd、开端口都留到获批后）。

## Tech Stack

| 项 | 本批次 |
|----|--------|
| Spring Boot 3.4.5 / JDK 17 / `libs/ai-core` / Sa-Token / Flyway | 复用，契约不改 |
| `ConcurrentHashMap` + 滑动窗口计数（无新依赖） | 本周新增（内存版限流器） |
| Spring `OncePerRequestFilter`？**不用**：限流在应用层做，控制器只解析客户端 IP | 设计选择 |
| JDK 17 + systemd + scp（部署资产，仅文件不执行） | 本周新增 |

明确不做：Redis 版分布式限流（单实例先够用，端口已隔离）、验证码、账号锁定表、
镜像构建、CI 平台接入、云端执行、网关进程上云、前端与 nginx 实际部署。

## Boundaries

- **Always**：TDD（先 RED）；限流在**凭据校验之前**；成功登录清零计数；
  429 走统一错误码枚举；配置项可关闭（`enabled=false` 回到第 21 周行为）；
  部署资产只写文件、不执行
- **Ask first**：安装 `openjdk-17-jre-headless`、写 `/etc/systemd/system`、开安全组、
  改 Prometheus 抓取目标、把 8084/8080 绑定到非回环地址
- **Never**：本批次动云端、把限流做成 `static` 全局状态、用 `X-Forwarded-For` 默认取信、
  在部署脚本里写死密钥、把 `.env` 打进产物

## 一、限流：为什么要按「IP + 用户名」而不是只按 IP

| 方案 | 问题 |
|------|------|
| 只按 IP | 同一出口 NAT 下的多个正常用户会互相拖累；攻击者换 IP 就绕过 |
| 只按用户名 | 攻击者可换用户名枚举；且能用来「锁死别人的账号」 |
| **IP + 用户名（采用）** | 单账号爆破和单 IP 横扫都要受限；误伤面最小 |

另外两条：

- **默认不信 `X-Forwarded-For`**：只有显式 `trust-forwarded-for=true`（即确认前面有可信反代）才取第一跳，
  否则攻击者伪造该头即可绕过限流。上云时 8084 只绑回环、由 nginx 反代，届时再打开；
- **只在应用层做**：控制器只负责取客户端 IP，限流判断放在 `PlatformAuthenticationFacade`，
  这样不依赖 Servlet 过滤器顺序，也能被纯单测覆盖（用固定 `Clock` 验证窗口与封禁到期）。

## 二、限流语义（写死并单测）

| 事件 | 行为 |
|------|------|
| `isBlocked(key)` | 窗口内失败次数 ≥ `max-attempts` 且距最后一次失败 < `block-seconds` → 拦截 |
| 进入登录/校验前 | 先查 `isBlocked`，命中直接 429（**不查数据库、不比对密码**） |
| 凭据错误 | 记一次失败（时间戳入队），继续抛 401 |
| 登录/校验成功 | 清零该 key |
| 窗口过期 | 超过 `window-seconds` 的失败不再计数（滑动窗口，天然解锁） |
| `enabled=false` | 全部放行（回到第 21 周行为，便于本地联调） |

默认：`max-attempts=5`、`window-seconds=300`、`block-seconds=300`。

## 三、429 契约

新增错误码 `RATE_LIMITED`（HTTP **429**，中文文案「尝试过于频繁，请稍后再试」）：

```json
{ "code": "RATE_LIMITED", "message": "尝试过于频繁，请稍后再试", "data": null, "traceId": "..." }
```

`ApiErrorCodeContractTest` 的状态白名单同步加入 `HttpStatus.TOO_MANY_REQUESTS`，
并把 `RATE_LIMITED` 加入「API 依赖的错误码」清单——错误码只有一个来源的约定不破。

## 四、部署资产（只写文件，不执行）

```text
deploy/app/
├── systemd/ai-code-platform.service      ← 单元模板（User / WorkingDirectory / -Xmx / -javaagent）
├── systemd/ai-code-gateway.service
├── remote-deploy.sh                      ← 服务端执行：备份 → 切换 → 重启 → 健康检查 → 失败自动回滚
└── nginx/aicode.conf.example             ← 前端/反代到位时用（/api → 8084，静态站点占位）
tools/deploy-app.ps1                      ← 本机一键：构建 → 上传 → 远程切换 → 验证（支持 -DryRun / -Rollback）
docs/deploy/week-22-app-deploy.md         ← 运行手册（前置条件、内存预算、验收、回滚、待办）
```

三条硬约束（写进运行手册，违反则不做）：

1. **`WorkingDirectory` 必须是模块目录**：`spring.config.import: optional:file:.env` 按进程工作目录解析；
2. **显式堆**：`-Xms256m -Xmx768m -XX:MaxMetaspaceSize=256m`（4C8G 已跑 12 个容器，默认按物理内存 1/4 取堆会打到 swap）；
3. **只绑回环 + 安全组不开 8084/8080**；健康检查通过再切换（失败自动回滚 `app.jar.prev`）。

## Success Criteria

- [x] 限流单测：达到阈值后 `isBlocked=true`；窗口过期自动解除；成功登录清零；不同 key 互不影响
      （`InMemoryLoginAttemptAdapterTest` 7 例）
- [x] 用例：被拦截时**不调用**认证（不查库）；失败计数；成功清零；`verify` 同样受限；开关关闭后完全放行
      （`PlatformAuthenticationFacadeTest` 6 例）
- [x] 客户端 IP：默认取 `remoteAddr`；`trust-forwarded-for=false` 时**忽略** `X-Forwarded-For`；
      开启时取第一跳并 trim（`ClientIpResolverTest` 4 例）
- [x] HTTP：连续失败超阈值 → 429 + `RATE_LIMITED` + traceId；正常登录不受影响
      （`LoginRateLimitHttpTest` 4 例）；错误码契约的状态白名单同步加入 429（`ApiErrorCodeContractTest` 48 例）
- [x] 配置：`platform.security.rate-limit.*` 可配、可关；`.env.example` 列全 5 个可选键
- [x] 部署资产：`deploy/app/` 三件 + `tools/deploy-app.ps1`（`-DryRun` 实测不发任何远程命令、exit 0）；
      运行手册含前置条件、内存预算、验收步骤（含限流自检）、回滚步骤与「未执行云端」的显式说明
- [x] 根聚合 `test` 全绿：**725** 用例（ai-core 90 / enterprise 37 / patient 21 / platform 477 / gateway 100），
      本批次新增 24，Week 4–21 用例零回退（期间修掉 1 处切片测试装配回归，见实现日志 5 节）

## 本批次自己踩的坑（事后登记）

| # | 现象 | 根因 | 修法 |
|---|------|------|------|
| 1 | `ApiStandardsContractTest` 5 例全错，context 起不来 | 控制器改注入 `PlatformAuthenticationFacade` 后，切片测试只 mock 了旧用例，少一个 bean；且桩打在旧用例上失效 | 补 `@MockitoBean`，桩改到门面；全量跑才发现，说明只跑新增用例不足以证明无回归 |
| 2 | `tools/deploy-app.ps1` 被 PowerShell 5.1 解析报「字符串缺少终止符」 | 新写的 `.ps1` 是无 BOM UTF-8，PS 5.1 按 ANSI 读，中文注释被解成乱码 | 加 UTF-8 BOM（与既有 `tools/*.ps1` 一致；注意 `edit` 工具会丢 BOM，每次改完要补） |

## Open Questions

- 是否上 Redis 版分布式限流 → 单实例阶段不需要；多实例时按 `LoginAttemptPort` 换适配器；
- 429 是否要给 `Retry-After` 头 → 需要暴露「剩余封禁秒数」，本批次只给文案，下一批次按需加；
- 是否把审计写入也纳入限流（避免失败审计刷表）→ 本批次只限制认证尝试，审计量级由限流间接收敛；
- 部署是否最终走镜像 → 本批次是过渡形态，`deploy-app.ps1` 的上传/切换步骤到时替换为镜像推送+compose。
