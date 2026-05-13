# API：接口协议

## 1. 基本约定

Base URL：

```text
/api
```

认证方式：

```text
Authorization: Bearer <access_token>
```

时间格式：

```text
ISO 8601，例如 2026-05-13T12:00:00Z
```

统一响应：

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

统一错误：

```json
{
  "success": false,
  "data": null,
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Unauthorized"
  }
}
```

## 2. Auth

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
GET  /api/me
```

### POST /api/auth/register

请求：

```json
{
  "email": "user@example.com",
  "password": "password",
  "displayName": "Alex"
}
```

响应：

```json
{
  "accessToken": "jwt",
  "refreshToken": "refresh_token",
  "user": {
    "id": "uuid",
    "email": "user@example.com",
    "displayName": "Alex"
  }
}
```

## 3. Device

```text
POST  /api/devices
PATCH /api/devices/{id}
```

用途：

- 注册移动设备。
- 保存 push token。
- 标记设备最后活跃时间。

## 4. Sync

```text
POST /api/sync/push
POST /api/sync/pull
```

### POST /api/sync/push

请求：

```json
{
  "deviceId": "device_uuid",
  "clientSeq": 123,
  "mutations": [
    {
      "mutationId": "uuid",
      "entityType": "focus_session",
      "entityId": "uuid",
      "operation": "create",
      "baseVersion": null,
      "payload": {}
    }
  ]
}
```

响应：

```json
{
  "applied": [
    {
      "mutationId": "uuid",
      "entityType": "focus_session",
      "entityId": "uuid",
      "serverVersion": 1
    }
  ],
  "rejected": [],
  "conflicts": [],
  "serverCursor": "cursor"
}
```

### POST /api/sync/pull

请求：

```json
{
  "deviceId": "device_uuid",
  "cursor": null,
  "entityTypes": [
    "focus_session",
    "habit",
    "habit_checkin",
    "privacy_setting"
  ]
}
```

响应：

```json
{
  "changes": [
    {
      "entityType": "habit",
      "entityId": "uuid",
      "serverVersion": 3,
      "deleted": false,
      "payload": {}
    }
  ],
  "nextCursor": "cursor",
  "hasMore": false
}
```

## 5. Friends

```text
GET    /api/friends
POST   /api/friends/requests
GET    /api/friends/requests
PATCH  /api/friends/requests/{id}
DELETE /api/friends/{friendUserId}
```

好友申请状态：

```text
pending
accepted
blocked
deleted
```

## 6. Privacy

```text
GET   /api/privacy-settings
PATCH /api/privacy-settings/{dataType}
```

可见性：

```text
private
friends_summary
friends_detail
```

## 7. Leaderboards

```text
GET /api/leaderboards/focus?period=today
GET /api/leaderboards/focus?period=week
GET /api/leaderboards/habits?period=today
GET /api/leaderboards/streaks
```

响应：

```json
{
  "period": "week",
  "metric": "focus_seconds",
  "entries": [
    {
      "userId": "uuid",
      "displayName": "Alex",
      "avatarUrl": null,
      "value": 7200,
      "rank": 1
    }
  ]
}
```

## 8. 错误码

| code | 含义 |
| --- | --- |
| UNAUTHORIZED | 未登录或 token 无效 |
| FORBIDDEN | 无权限 |
| VALIDATION_ERROR | 参数错误 |
| NOT_FOUND | 资源不存在 |
| CONFLICT | 数据冲突 |
| RATE_LIMITED | 请求过快 |
| INTERNAL_ERROR | 服务端错误 |
