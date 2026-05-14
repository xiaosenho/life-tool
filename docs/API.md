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

## 8. Focus Preferences

专注偏好用于保存用户默认专注时长。单次专注记录仍通过同步模型中的 `focus_session` 上传。

```text
GET   /api/focus/preferences
PATCH /api/focus/preferences
```

### GET /api/focus/preferences

响应：

```json
{
  "defaultFocusMinutes": 25,
  "shortBreakMinutes": 5,
  "longBreakMinutes": 15,
  "autoStartBreak": false,
  "updatedAt": "2026-05-13T12:00:00Z"
}
```

### PATCH /api/focus/preferences

请求：

```json
{
  "defaultFocusMinutes": 45,
  "shortBreakMinutes": 5,
  "longBreakMinutes": 15,
  "autoStartBreak": false
}
```

校验规则：

- `defaultFocusMinutes` 范围：1 到 180。
- `shortBreakMinutes`、`longBreakMinutes` 范围：0 到 60。
- 客户端可离线保存偏好，联网后通过同步队列或该接口上传。

## 9. Ledger

```text
GET    /api/ledger/transactions?month=2026-05
POST   /api/ledger/transactions
PATCH  /api/ledger/transactions/{id}
DELETE /api/ledger/transactions/{id}
GET    /api/ledger/summary?month=2026-05
GET    /api/ledger/budgets?month=2026-05
PUT    /api/ledger/budgets/{month}
```

### POST /api/ledger/transactions

请求：

```json
{
  "type": "expense",
  "amount": 36.5,
  "currency": "CNY",
  "category": "餐饮",
  "account": "微信",
  "occurredAt": "2026-05-13T12:00:00Z",
  "note": "午餐",
  "mediaAssetId": "uuid"
}
```

响应（201）：

```json
{
  "id": "uuid",
  "type": "expense",
  "amount": 36.5,
  "currency": "CNY",
  "category": "餐饮",
  "account": "微信",
  "occurredAt": "2026-05-13T12:00:00Z",
  "note": "午餐",
  "mediaAssetId": "uuid",
  "createdAt": "2026-05-13T12:00:00Z"
}
```

流水类型：

```text
income
expense
transfer
```

### GET /api/ledger/summary

响应：

```json
{
  "month": "2026-05",
  "income": 12000,
  "expense": 3200.5,
  "balance": 8799.5,
  "budget": 5000,
  "categoryExpenses": [
    {
      "category": "餐饮",
      "amount": 980.5
    }
  ]
}
```

### PUT /api/ledger/budgets/{month}

请求：

```json
{
  "amount": 5000,
  "currency": "CNY",
  "category": null
}
```

说明：

- `category` 为 `null` 表示整月总预算。
- 指定 `category` 表示分类预算。
- 记账数据默认私密，不进入好友汇总。

## 10. Anniversaries And Events

```text
GET    /api/events?from=2026-05-01&to=2026-06-30
POST   /api/events
PATCH  /api/events/{id}
DELETE /api/events/{id}
GET    /api/events/upcoming?days=30
```

### POST /api/events

请求：

```json
{
  "type": "anniversary",
  "title": "结婚纪念日",
  "eventDate": "2026-10-01",
  "repeatRule": "yearly",
  "remindDaysBefore": [30, 7, 1],
  "note": "提前准备礼物",
  "mediaAssetId": "uuid"
}
```

响应（201）：

```json
{
  "id": "uuid",
  "type": "anniversary",
  "title": "结婚纪念日",
  "eventDate": "2026-10-01",
  "repeatRule": "yearly",
  "remindDaysBefore": [30, 7, 1],
  "daysUntil": 141,
  "nextOccurrenceDate": "2026-10-01",
  "note": "提前准备礼物",
  "mediaAssetId": "uuid"
}
```

事件类型：

```text
anniversary
birthday
important_day
todo_reminder
```

重复规则：

```text
none
yearly
monthly
weekly
```

说明：

- 纪念日和重要事件默认私密。
- 客户端负责本地通知调度，服务端保存提醒规则用于多设备恢复。

## 11. Media

```text
POST /api/media/upload-token
POST /api/media/assets
GET  /api/media/assets/{id}
DELETE /api/media/assets/{id}
```

### POST /api/media/upload-token

用途：

- 后端为当前用户生成短有效期上传授权。
- 当前暂定对接腾讯云 COS。
- 客户端用授权直传图片，不经过后端中转大文件。

请求：

```json
{
  "contentType": "image/jpeg",
  "purpose": "meal_photo",
  "fileSize": 512000
}
```

响应：

```json
{
  "assetId": "uuid",
  "uploadUrl": "https://cos.example.com/...",
  "objectKey": "users/user_uuid/media/asset_uuid.jpg",
  "expiresAt": "2026-05-13T12:10:00Z",
  "headers": {
    "Content-Type": "image/jpeg"
  }
}
```

### POST /api/media/assets

请求：

```json
{
  "assetId": "uuid",
  "objectKey": "users/user_uuid/media/asset_uuid.jpg",
  "contentType": "image/jpeg",
  "purpose": "meal_photo",
  "fileSize": 512000,
  "width": 1280,
  "height": 960
}
```

响应（201）：

```json
{
  "id": "uuid",
  "objectKey": "users/user_uuid/media/asset_uuid.jpg",
  "contentType": "image/jpeg",
  "purpose": "meal_photo",
  "fileSize": 512000,
  "width": 1280,
  "height": 960,
  "status": "uploaded",
  "createdAt": "2026-05-13T12:00:00Z"
}
```

### GET /api/media/assets/{id}

响应（200）：

```json
{
  "id": "uuid",
  "objectKey": "users/user_uuid/media/asset_uuid.jpg",
  "contentType": "image/jpeg",
  "purpose": "meal_photo",
  "fileSize": 512000,
  "width": 1280,
  "height": 960,
  "status": "uploaded",
  "createdAt": "2026-05-13T12:00:00Z"
}
```

备注：只能访问自己的资产，跨用户返回 403。

### DELETE /api/media/assets/{id}

响应（200）：

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

备注：软删除，标记 status 为 deleted。只能删除自己的资产。

### 校验规则

- contentType 仅允许 `image/jpeg`、`image/png`、`image/webp`。
- purpose 仅允许 `meal_photo`、`event_photo`、`avatar`。
- fileSize 默认最大 10MB（通过 `MEDIA_MAX_IMAGE_BYTES` 配置）。

### 环境变量

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| COS_REGION | COS 地域 | ap-guangzhou |
| COS_BUCKET | COS 存储桶 | life-tool-media |
| COS_PUBLIC_BASE_URL | COS 公网访问地址，留空则返回 mock URL | 空 |
| COS_UPLOAD_TOKEN_TTL_SECONDS | 上传授权有效期（秒） | 300 |
| MEDIA_MAX_IMAGE_BYTES | 最大图片大小（字节） | 10485760 |

## 12. AI

```text
POST /api/ai/food-recognition/jobs
GET  /api/ai/food-recognition/jobs/{id}
POST /api/ai/life-advice
POST /api/ai/chat/sessions
POST /api/ai/chat/sessions/{id}/messages
GET  /api/ai/chat/sessions/{id}/messages
GET  /api/ai/memories
DELETE /api/ai/memories/{id}
```

### POST /api/ai/food-recognition/jobs

请求：

```json
{
  "mediaAssetId": "uuid",
  "mealType": "lunch",
  "occurredAt": "2026-05-13T12:00:00Z"
}
```

响应：

```json
{
  "jobId": "uuid",
  "status": "pending"
}
```

### GET /api/ai/food-recognition/jobs/{id}

响应：

```json
{
  "jobId": "uuid",
  "status": "succeeded",
  "result": {
    "items": [
      {
        "name": "米饭",
        "estimatedGrams": 150,
        "estimatedCalories": 174,
        "confidence": 0.78
      }
    ],
    "totalCalories": 174,
    "notes": "结果为估算值，请确认后保存。"
  }
}
```

### POST /api/ai/life-advice

请求：

```json
{
  "period": "last_7_days",
  "topics": ["focus", "habits", "diet"]
}
```

响应：

```json
{
  "summary": "你最近 7 天专注时间较稳定，但晚餐热量偏高。",
  "suggestions": [
    "把高强度专注安排在上午。",
    "晚餐优先记录主食和含糖饮料。"
  ],
  "disclaimer": "AI 建议仅供参考，不构成医疗或营养诊断。"
}
```

### POST /api/ai/chat/sessions

请求：

```json
{
  "title": "最近状态分析",
  "useLongTermMemory": true
}
```

响应：

```json
{
  "id": "uuid",
  "title": "最近状态分析",
  "useLongTermMemory": true,
  "createdAt": "2026-05-14T10:00:00Z"
}
```

### POST /api/ai/chat/sessions/{id}/messages

请求：

```json
{
  "content": "我最近晚睡很多，能结合我的专注和饮食记录给点建议吗？",
  "enabledTools": [
    "get_focus_summary",
    "get_diet_summary",
    "get_habit_summary"
  ]
}
```

响应：

```json
{
  "messageId": "uuid",
  "role": "assistant",
  "content": "你最近 7 天晚间饮食记录偏多，上午专注表现更稳定。建议先把深度专注安排在上午，并在晚餐后设置一个轻提醒。",
  "disclaimer": "AI 建议仅供参考，不构成医疗或营养诊断。",
  "toolCalls": [
    {
      "toolName": "get_focus_summary",
      "status": "succeeded"
    },
    {
      "toolName": "get_diet_summary",
      "status": "succeeded"
    }
  ],
  "createdAt": "2026-05-14T10:00:10Z"
}
```

说明：

- 客户端不能直接调用工具，`enabledTools` 只是本次对话允许使用的工具范围。
- 工具实际执行由后端 `AiOrchestrator` 完成。
- 后端必须按当前登录用户注入 `userId`。

### GET /api/ai/chat/sessions/{id}/messages

响应：

```json
{
  "messages": [
    {
      "id": "uuid",
      "role": "user",
      "content": "我最近晚睡很多，能给点建议吗？",
      "createdAt": "2026-05-14T10:00:00Z"
    },
    {
      "id": "uuid",
      "role": "assistant",
      "content": "可以，我会优先参考你的近期汇总数据。",
      "createdAt": "2026-05-14T10:00:10Z"
    }
  ]
}
```

### GET /api/ai/memories

响应：

```json
{
  "items": [
    {
      "id": "uuid",
      "type": "preference",
      "content": "用户更喜欢早上安排深度专注",
      "source": "user_confirmed",
      "createdAt": "2026-05-14T10:00:00Z"
    }
  ]
}
```

### Function Calling 工具

工具由服务端内部注册，不作为公开 API 暴露。MVP 工具：

| tool | 说明 |
| --- | --- |
| `get_focus_summary` | 查询当前用户近 N 天专注汇总 |
| `get_habit_summary` | 查询当前用户习惯完成率和连续打卡 |
| `get_diet_summary` | 查询当前用户已确认饮食汇总 |
| `get_ledger_summary` | 查询当前用户月度收支和预算汇总 |
| `get_upcoming_events` | 查询当前用户未来纪念日和提醒 |
| `get_user_profile_context` | 查询当前用户基础偏好和隐私配置 |

## 13. 错误码

| code | 含义 |
| --- | --- |
| UNAUTHORIZED | 未登录或 token 无效 |
| FORBIDDEN | 无权限 |
| VALIDATION_ERROR | 参数错误 |
| NOT_FOUND | 资源不存在 |
| CONFLICT | 数据冲突 |
| RATE_LIMITED | 请求过快 |
| FILE_TOO_LARGE | 文件过大 |
| UNSUPPORTED_MEDIA_TYPE | 不支持的媒体类型 |
| AI_JOB_FAILED | AI 任务失败 |
| INTERNAL_ERROR | 服务端错误 |
