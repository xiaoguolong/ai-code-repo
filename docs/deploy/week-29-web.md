# Week 29 运维手册：前端同域部署（nginx）

> 2026-10-25 已在腾讯云执行：nginx 1.18.0 安装、静态站点 `/opt/ai-code/web`、
> `/api` 反代 8084、`trust-forwarded-for=true`；云端与公网均 200。
> 相关脚本：`deploy/app/cloud-week29-web.sh`、`deploy/app/nginx/aicode.conf`。

## 1. 部署形态

```text
浏览器 → http://HOST:80 → nginx
                         ├── /        /opt/ai-code/web（SPA，try_files 回 index.html）
                         ├── /api/    → 127.0.0.1:8084（保留前缀）
                         └── /ent/    → 127.0.0.1:8081（去前缀，第 30 周用）
```

- 静态产物来自 `apps/web/dist`；上一版保留 `/opt/ai-code/web.prev`。
- 平台 `.env` 增加 `PLATFORM_RATE_LIMIT_TRUST_FORWARDED_FOR=true`，限流按 nginx 传来的真实 IP。
- 安全组需放行 80；当前公网 `http://HOST/` 已实测 200。

## 2. 首次安装（已执行）

```bash
apt-get update -qq
apt-get install -y -qq nginx
systemctl enable --now nginx
install -m 644 /tmp/aicode.conf /etc/nginx/sites-available/aicode
ln -sfn /etc/nginx/sites-available/aicode /etc/nginx/sites-enabled/aicode
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx
```

## 3. 发布（本地构建 → 上传 → 脚本）

```powershell
# 1) 构建
cd apps/web
node <dsh>/dependencies/node/bin/node.exe <dsh>/dependencies/pnpm/bin/pnpm.mjs run build
tar -czf ..\..\logs\week-29\web.tar.gz -C dist .

# 2) 上传
powershell -File ..\..\remote.ps1 -Put "..\..\logs\week-29\web.tar.gz /tmp/web.tar.gz"
powershell -File ..\..\remote.ps1 -Put "..\..\deploy\app\nginx\aicode.conf /tmp/aicode.conf"

# 3) 部署（备份 → 解压 → reload → trust-forwarded-for → 平台重启）
powershell -File ..\..\remote.ps1 -Script ..\..\deploy\app\cloud-week29-web.sh
```

脚本判据：

- `nginx -t` 成功、`systemctl is-active nginx=active`；
- `index_http=200`、`api_login_http=200`；
- platform health 200；
- 备份目录 `/opt/ai-code/backup/week29-<ts>/`（web + platform.env）。

## 4. 本地/云端验收

```powershell
# 云端本机
curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1/
curl -s -X POST -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' \
  http://127.0.0.1/api/v1/platform/auth/login

# 浏览器冒烟（本地 dev 或云端隧道）
$env:WEB_BASE='http://127.0.0.1:18080'
$env:LOG_DIR='logs/week-29/deployed'
$env:SMOKE_SKIP_AGENT='1'
node apps/web/scripts/smoke.mjs
```

冒烟覆盖：admin 登录/工作台、审批、知识库、用户管理、viewer 菜单与路由拦截。
真实 Agent 运行另跑一次（会创建 P002 待审数据，需在审批页处理）。

## 5. 回滚

| 场景 | 操作 |
|------|------|
| 静态包异常 | `rm -rf /opt/ai-code/web && mv /opt/ai-code/web.prev /opt/ai-code/web`；`systemctl reload nginx` |
| 配置异常 | 从 `/opt/ai-code/backup/week29-<ts>/` 恢复，或 `git` 取上一版 `aicode.conf` |
| 平台不可用 | `systemctl restart ai-code-platform`；health 200 |
| 需要撤销 trust-forwarded-for | 从备份恢复 `.env` 并重启平台（直连调试时 false） |

## 6. 边界

- **仅 HTTP**：未上 TLS/域名；正式对外需加证书与 301。
- 未做前端 CI：手工 build + tar + scp；CI 触发条件（多环境/多人）另排。
- `/ent` 已配好但第 29 周页面不使用，第 30 周企业知识库页直接复用。
- nginx 1.18（Ubuntu 22.04 源）；静态 assets 缓存 7 天，index no-store。
