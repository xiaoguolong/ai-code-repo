# Week 28 接口文档 — 平台用户停用/恢复

> 通用信封/错误码见 `docs/api/week-16-api.md`。本周**不新增错误码**：
> 未登录 401、越权 403、不存在 404、非法参数 400（停用自己）。

## 1. 用户列表（仅 admin）

`GET /api/v1/platform/users`

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "total": 3,
    "items": [
      {
        "userId": 1,
        "username": "admin",
        "roleKey": "admin",
        "tenantKey": "default",
        "disabled": false,
        "disabledAt": null
      },
      {
        "userId": 2,
        "username": "operator",
        "roleKey": "operator",
        "tenantKey": "default",
        "disabled": true,
        "disabledAt": "2026-10-25T07:30:00Z"
      }
    ]
  },
  "traceId": "..."
}
```

| 情况 | 状态码 |
|------|--------|
| admin | 200 |
| operator / viewer | 403 `FORBIDDEN` |
| 未登录 | 401 `UNAUTHORIZED` |

> 不返回 `passwordHash`；不做分页（当前用户量个位数；第 29 周前端需要时再加 `limit/offset`）。

## 2. 停用用户（仅 admin）

`POST /api/v1/platform/users/{userId}/disable`

无请求体。成功：

```json
{
  "code": "SUCCESS",
  "message": "OK",
  "data": {
    "userId": 2,
    "username": "operator",
    "roleKey": "operator",
    "tenantKey": "default",
    "disabled": true,
    "disabledAt": "2026-10-25T07:30:00Z"
  },
  "traceId": "..."
}
```

语义：

- 写 `platform_user.disabled_at`；调用 `StpUtil.logout(userId)` 踢出该用户全部平台会话；
- 审计 `USER_DISABLE`（`resource=user:{userId}`，detail 只带 userId/username）；
- **幂等**：已停用再调用仍 200，返回原 `disabledAt`，不重复审计；
- **停用自己 400** `VALIDATION_ERROR`（防止唯一 admin 自锁）。

| 情况 | 状态码 |
|------|--------|
| 非 admin | 403 `FORBIDDEN` |
| 目标不存在 | 404 `PLATFORM_NOT_FOUND` |
| 停用自己 | 400 `VALIDATION_ERROR` |

## 3. 恢复用户（仅 admin）

`POST /api/v1/platform/users/{userId}/enable`

无请求体。响应同上，`disabled=false`、`disabledAt=null`；审计 `USER_ENABLE`；幂等。

> 恢复后用户可以重新登录；已有会话不自动恢复（需要重新登录），这是有意的。

## 4. 停用对认证的影响

| 接口 | 停用用户 |
|------|----------|
| `POST /auth/login` | 401 `UNAUTHORIZED`，`message` 与密码错误一致 |
| `POST /auth/verify`（SSO） | 401 同上（enterprise 登录随之失败） |
| `GET /auth/me`（旧 token） | 401（会话被踢出；即使未踢出也按 disabled 拒绝） |
| 历史执行/审批/随访/记忆 | 管理员仍可读；不因停用删除 |

审计只多一个字段，不改变对外响应：

```text
action=LOGIN_FAILED detail="username=operator disabled=true"
```

## 5. 审计事件

| action | 触发 | resource | detail |
|--------|------|----------|--------|
| `USER_DISABLE` | admin 停用用户 | `user:{userId}` | `userId=2 username=operator` |
| `USER_ENABLE` | admin 恢复用户 | `user:{userId}` | `userId=2 username=operator` |

> `docs/api/week-16-api.md` 的审计事件表已同步；detail 不含密码/哈希/令牌。

## 6. 与 Week 29 的关系

第 29 周前端在「用户管理」页调用这三个接口；权限按钮按 `admin` 收敛。
停用/恢复只做平台账号，不联动 enterprise 本地用户与 `sso_user_link`。
