# API: Week 13 — 权限体系

Base URL：`http://localhost:8084`  
鉴权 Header：`satoken: <token>`（Sa-Token 默认 token 名）

## Auth

### POST /api/v1/platform/auth/login

```json
{ "username": "operator", "password": "operator123" }
```

响应：

```json
{
  "code": "SUCCESS",
  "data": {
    "token": "...",
    "userId": 2,
    "username": "operator",
    "roleKey": "operator"
  }
}
```

### GET /api/v1/platform/auth/me

需登录。返回当前用户与角色。

### POST /api/v1/platform/auth/logout

需登录。

## Role

### GET /api/v1/platform/roles

需登录。列出预置角色及授权摘要。

### GET /api/v1/platform/roles/{roleKey}

需登录。角色详情（allowedAgentKeys / allowedToolKeys / allowedPatientIds）。

## Platform Run（强 gate）

### POST /api/v1/platform/agents/{agentKey}/runs

需登录 + Agent 权限 + input 中 patientId 数据域校验。

```bash
curl -X POST http://localhost:8084/api/v1/platform/agents/medical-assistant/runs \
  -H "Content-Type: application/json" \
  -H "satoken: <token>" \
  -d '{"input":{"patientId":"P001"}}'
```

| 场景 | HTTP |
|------|------|
| 未登录 | 401 |
| viewer Run | 403 |
| operator 越权 Agent | 403 |
| operator patientId=P999 | 403（Tool 或 Run 前校验） |

## Executions（按用户隔离）

### GET /api/v1/platform/executions

- admin：全部记录
- 其他：仅 `userId` 匹配的记录

### GET /api/v1/platform/executions/{executionId}

非 owner 且非 admin → 403。

## 配置

```yaml
platform:
  security:
    enforce-direct-runs: false   # true 时直连 Run API 也需登录+Agent 权限

auth:
  password-salt: ${AUTH_PASSWORD_SALT:platform-dev-salt}
```

## Seed 账号

| username | password | role |
|----------|----------|------|
| admin | admin123 | admin |
| operator | operator123 | operator |
| viewer | viewer123 | viewer |

## Postman

导入 `docs/postman/week-13.postman_collection.json`，先跑文件夹 **「0. 环境准备（登录）」** 写入 token 变量，再按序跑 RBAC 场景（含自动 Tests 断言）。
