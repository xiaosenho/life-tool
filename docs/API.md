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
GET   /api/devices
POST  /api/devices
PATCH /api/devices/{id}
```

用途：

- 注册移动设备。
- 保存 push token / 厂商 deviceId。
- 标记设备最后活跃时间。
- 为离线推送绑定安装实例。

### POST /api/devices

请求：

```json
{
  "installationId": "device_may17_xxx",
  "deviceName": "Xiaomi 14",
  "deviceType": "android",
  "pushToken": "optional-token",
  "vendorDeviceId": "aliyun-device-id",
  "pushProvider": "aliyun",
  "pushEnabled": true,
  "metadata": {
    "platform": "android"
  }
}
```

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
GET    /api/friends/messages
GET    /api/friends/messages/{friendUserId}
POST   /api/friends/messages/{friendUserId}
POST   /api/friends/messages/{friendUserId}/read
GET    /api/friends/events/stream
```

好友申请状态：

```text
pending
accepted
blocked
deleted
```

### GET /api/friends/messages

响应：

```json
[
  {
    "friendUserId": "uuid",
    "friendDisplayName": "Alex",
    "friendEmail": "alex@example.com",
    "lastMessage": "今天继续加油！",
    "lastMessageType": "cheer",
    "lastMessageAt": "2026-05-15T12:00:00Z",
    "unreadCount": 1
  }
]
```

### GET /api/friends/messages/{friendUserId}

响应：

```json
[
  {
    "id": "uuid",
    "fromUserId": "uuid",
    "toUserId": "uuid",
    "type": "text",
    "content": "晚上继续加油",
    "attachment": null,
    "createdAt": "2026-05-17T10:00:00Z",
    "readAt": null
  }
]
```

### GET /api/friends/events/stream

用途：

- 取代好友聊天页和底栏未读的短轮询。
- 服务端在新消息、好友申请、申请状态变化、已读回执时推送事件。

SSE 事件名：

```text
friend_message_created
friend_request_created
friend_request_updated
friend_conversation_read
ping
connected
```

`friend_message_created` 示例：

```json
{
  "id": "uuid",
  "type": "FRIEND_MESSAGE_CREATED",
  "userId": "uuid",
  "createdAt": "2026-05-17T10:00:00Z",
  "payload": {
    "message": {
      "id": "uuid",
      "fromUserId": "friend-id",
      "toUserId": "self-id",
      "type": "text",
      "content": "在吗",
      "attachment": null,
      "createdAt": "2026-05-17T10:00:00Z",
      "readAt": null
    },
    "conversation": {
      "friendUserId": "friend-id",
      "friendDisplayName": "Alex",
      "friendEmail": "alex@example.com",
      "lastMessage": "在吗",
      "lastMessageType": "text",
      "lastMessageAt": "2026-05-17T10:00:00Z",
      "unreadCount": 1
    }
  }
}
```

```json
[
  {
    "id": "uuid",
    "fromUserId": "uuid",
    "toUserId": "uuid",
    "type": "text",
    "content": "今晚继续冲一下榜单？",
    "createdAt": "2026-05-15T12:00:00Z",
    "readAt": null
  }
]
```

### POST /api/friends/messages/{friendUserId}

请求：

```json
{
  "content": "今天继续加油！",
  "type": "cheer",
  "attachment": {
    "assetId": "uuid",
    "width": 1080,
    "height": 1440,
    "durationSeconds": 6
  }
}
```

说明：

- `type` 当前支持 `text`、`cheer`、`celebrate`、`hug`、`coffee`、`poke`、`image`、`audio`。
- `image` / `audio` 类型需配合 `attachment` 传递已上传媒体资产信息。
- 服务端响应会返回 `attachment.url` 等可直接展示的附件信息。
- 只有已建立好友关系的双方才允许互发消息。

### POST /api/friends/messages/{friendUserId}/read

响应：

```json
{
  "updated": 1
}
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
GET /api/leaderboards/focus/detail?period=today
GET /api/leaderboards/focus/detail?period=week
GET /api/leaderboards/habits?period=today
GET /api/leaderboards/habits/detail?period=today
GET /api/leaderboards/streaks
GET /api/leaderboards/streaks/detail
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

### GET /api/leaderboards/focus/detail?period=today

响应：

```json
{
  "period": "today",
  "metric": "focus_seconds",
  "entries": [
    {
      "userId": "uuid",
      "displayName": "Alex",
      "avatarUrl": null,
      "value": 7200,
      "rank": 1
    }
  ],
  "self": {
    "userId": "uuid",
    "displayName": "Alex",
    "avatarUrl": null,
    "value": 7200,
    "rank": 1
  },
  "gapToPrevious": 0,
  "totalParticipants": 3
}
```

说明：

- detail 接口返回完整好友榜单，而不是仅摘要。
- `self` 表示当前用户自己的榜单条目。
- `gapToPrevious` 表示当前用户与前一名的差距；若当前已是并列第 1，则返回 0。

## 8. Habits

```text
POST   /api/habits
GET    /api/habits
GET    /api/habits/calendar?from=2026-05-01&to=2026-05-31
PATCH  /api/habits/{id}
DELETE /api/habits/{id}
POST   /api/habits/{id}/checkins
GET    /api/habits/{id}/checkins
DELETE /api/habits/{id}/checkins?checkinDate=2026-05-17
```

### POST /api/habits/{id}/checkins

请求：

```json
{
  "count": 1,
  "note": "今天完成",
  "checkinDate": "2026-05-17"
}
```

说明：

- `checkinDate` 为空时按当前日期处理。
- 前端可通过 `DELETE /api/habits/{id}/checkins?checkinDate=YYYY-MM-DD` 取消指定日期打卡。

## 9. Focus Preferences

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

## 10. Ledger

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

## 11. Anniversaries And Events

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
  "displayDate": "2026-09-30",
  "reminderOffsetDays": 1,
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
- 客户端可负责本地通知调度，服务端保存提醒规则并可复用阿里云推送执行离线提醒。
- `displayDate` 表示当前这条提醒实例实际展示的日期。
- `reminderOffsetDays` 表示该实例相对事件日期提前了多少天，用于“提前 1 天 / 7 天提醒”等日历视图。
- `GET /api/events` 除了返回即将到来的事件，也会返回过去的重要非重复事件，便于记录页完整查看。
- 服务端纪念日提醒默认每小时补扫一次，但同一天同一提醒只会推送一次；可通过 `LIFETOOL_PUSH_REMINDERS_SCAN_DELAY_MS` 调整扫描周期。

## 12. Media

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
  "readUrl": "https://lifetool-media-prod.cos.ap-guangzhou.myqcloud.com/...",
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
  "readUrl": "https://lifetool-media-prod.cos.ap-guangzhou.myqcloud.com/...",
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
- purpose 仅允许 `meal_photo`、`event_photo`、`avatar`、`friend_chat_image`、`friend_chat_audio`、`ai_chat_image`、`ai_chat_audio`。
- fileSize 默认最大 10MB（通过 `MEDIA_MAX_IMAGE_BYTES` 配置）。

### 环境变量

| 变量 | 说明 | 默认值 |
| --- | --- | --- |
| COS_REGION | COS 地域 | ap-guangzhou |
| COS_BUCKET | COS 存储桶 | life-tool-media |
| COS_SECRET_ID | COS 子账号 SecretId | 空 |
| COS_SECRET_KEY | COS 子账号 SecretKey | 空 |
| COS_PUBLIC_BASE_URL | 兼容保留字段；当前私有读优先返回后端签发的短有效期 readUrl | 空 |
| COS_UPLOAD_TOKEN_TTL_SECONDS | 上传授权有效期（秒） | 300 |
| MEDIA_MAX_IMAGE_BYTES | 最大图片大小（字节） | 10485760 |

## 13. AI

```text
POST /api/ai/food-recognition
POST /api/ai/life-advice
POST /api/ai/chat/sessions
POST /api/ai/chat/sessions/{id}/messages
GET  /api/ai/chat/sessions/{id}/messages
GET  /api/ai/memories
DELETE /api/ai/memories/{id}
```

### POST /api/ai/food-recognition

请求：

```json
{
  "mediaAssetId": "uuid",
  "mealType": "lunch",
  "customPrompt": "这是一份午餐，请估算热量。"
}
```

响应：

```json
{
  "result": "图片中可能包含米饭、鸡胸肉和青菜。总热量约 620 千卡。",
  "disclaimer": "AI 建议仅供参考，不构成医疗或营养诊断。",
  "mealLogId": "uuid",
  "totalCalories": 620
}
```

说明：

- 当前接口为同步识别接口，不再使用 `/jobs` 轮询模型。
- `mediaAssetId` 为必填；后端会基于媒体资产实时生成可访问的 COS 短时效读链接。
- 当模型拉取图片命中 COS `403`（签名过期等场景）时，后端会自动刷新读链接并重试一次。
- 识别成功后，后端会为当前用户生成一条饮食记录，并返回 `mealLogId`。
- `totalCalories` 由后端从模型文本中提取；无法可靠提取时返回 0 或后端默认值。
- 前端识别成功后应刷新今日饮食汇总。

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
  ],
  "attachment": {
    "assetId": "uuid",
    "width": 1080,
    "height": 1440,
    "durationSeconds": 8
  }
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
- 工具实际执行由后端 `AiService` 和 `UserDataTools` 完成。
- 后端必须按当前登录用户注入 `userId`。
- `attachment` 当前支持图片和语音媒体，统一通过 `mediaAssetId` 关联到 COS 私有资源。
- AI 会话在语音 / 图片场景下仍复用同一多轮会话接口，不额外拆分独立上传协议。

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

说明：

- 当前长期记忆采用保守写入策略：仅当用户明确表达稳定偏好时才入库。
- 助手回复中会附带 `longTermMemorySaved` 字段，表示本轮是否新写入了一条长期记忆。

### Function Calling 工具

工具由服务端内部注册，不作为公开 API 暴露。MVP 工具：

| tool | 说明 |
| --- | --- |
| `get_focus_summary` | 查询当前用户近 N 天专注汇总 |
| `get_habit_summary` | 查询当前用户习惯完成率和连续打卡 |
| `get_diet_summary` | 查询当前用户饮食热量、餐次分布和记录数 |
| `get_ledger_summary` | 查询当前用户月度收支和预算汇总 |
| `get_upcoming_events` | 查询当前用户未来纪念日和提醒 |
| `get_user_profile_context` | 查询当前用户基础偏好和隐私配置 |

## 14. News

```text
GET /api/news/top
```

响应：

```json
[
  {
    "title": "国内精选新闻标题",
    "source": "联合早报",
    "url": "https://example.com/news/1",
    "publishedAt": "Sat, 17 May 2026 09:00:00 +0800",
    "summary": "新闻摘要",
    "imageUrl": "https://example.com/cover.jpg"
  }
]
```

说明：

- 当前新闻源为国内 RSS 聚合，优先返回标题、摘要和可提取到的封面图。
- 服务端使用进程内缓存 + Redis 缓存，默认缓存 5 分钟。
- 应用启动后会异步预热一次新闻缓存，降低今日页首屏等待时间。

## 15. Vocab

```text
GET /api/vocab/books
GET /api/vocab/page?bookCode=cet4&variant=ordered&offset=0&limit=30
GET /api/vocab/progress?bookCode=cet4&variant=ordered
PUT /api/vocab/progress
```

### GET /api/vocab/books

响应：

```json
[
  {
    "code": "cet4",
    "variant": "ordered",
    "name": "英语四级",
    "version": "2026.1",
    "wordCount": 2000
  },
  {
    "code": "cet4",
    "variant": "shuffled",
    "name": "英语四级（乱序）",
    "version": "2026.1",
    "wordCount": 2000
  }
]
```

### GET /api/vocab/page

响应：

```json
{
  "bookCode": "cet4",
  "variant": "ordered",
  "bookName": "英语四级",
  "offset": 0,
  "limit": 30,
  "total": 2000,
  "entries": [
    {
      "seqNo": 1,
      "word": "abandon",
      "phonetic": "/əˈbændən/",
      "meaningZh": "放弃"
    }
  ]
}
```

### PUT /api/vocab/progress

请求：

```json
{
  "bookCode": "cet4",
  "variant": "ordered",
  "lastSeqNo": 30,
  "hideMeaning": true
}
```

说明：

- 当前内置六本词书：四级 / 六级 / 考研，各自提供正序和乱序版本。
- `lastSeqNo` 表示用户当前学到的位置，`hideMeaning` 表示当前词书的中文隐藏偏好。
- 词条音标优先取原始词书字段，缺失时由后端按同书正序版本回退补齐。

## 16. App 更新

```text
GET /api/app/releases/latest
```

公开接口，无需登录。返回 Android 最新发布信息：

```json
{
  "success": true,
  "data": {
    "platform": "android",
    "versionName": "1.1.0",
    "versionCode": 2,
    "downloadUrl": "https://gitee.com/your-name/your-repo/releases",
    "releaseNotes": "修复已知问题并优化使用体验",
    "forceUpdate": false
  },
  "error": null
}
```

客户端比较 `versionName`，发现新版后提示用户下载；`downloadUrl` 可以是 APK 直链或 Gitee Releases 页面；`forceUpdate=true` 时不提供“稍后”按钮。

## 17. 错误码

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
| AI_RECOGNITION_FAILED | AI 识图失败 |
| INTERNAL_ERROR | 服务端错误 |
