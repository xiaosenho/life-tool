# ARCHITECTURE：系统架构

## 1. 总体架构

```text
Expo Mobile App / Web Preview
  |
  | HTTPS / JSON / JWT
  v
Spring Boot Backend
  |
  +-- PostgreSQL 16 / pgvector16
  +-- Redis 7.4
  +-- Tencent Cloud COS
  +-- Spring AI / OpenAI-compatible Provider
```

LifeTool 以手机端为主，后端保存权威数据，前端保留 SQLite 本地兜底能力。正常联网时优先调用后端接口；网络失败时写入本地，并向用户提示当前为离线兜底状态，后续需要手动或自动同步。

## 2. 技术选型

### Frontend

- React Native + Expo SDK 55
- TypeScript
- expo-router
- expo-sqlite
- React Native Web 预览
- 本地 service 层统一访问 API，失败时走 SQLite 兜底

### Backend

- Java 21
- Spring Boot
- Spring Security + JWT
- Spring AI
- Maven
- PostgreSQL 16 / pgvector16
- Redis 7.4
- Flyway 迁移文件 + 可开关迁移 runner
- JDBC/Store 分层；本地和测试可使用内存 Store，`postgres` profile 使用数据库实现

### Infra

- Docker / Docker Compose
- 阿里云 ECS 承载 PostgreSQL、Redis 和后端运行环境
- 腾讯云 COS 用于图片和媒体文件
- GitHub Actions 作为 CI 基线
- Android APK 使用 Expo prebuild + Gradle 构建

## 3. 目录边界

```text
backend/       后端服务，包含 API、业务逻辑、数据库访问、测试
frontend/      Expo 移动端 App，含 Web 预览与 Android 构建配置
docs/          产品、架构、接口、任务和 Agent 规则
scripts/       本地开发、检查、辅助脚本和可选本地资源
.github/       CI/CD
```

## 4. 客户端职责

- 提供中文手机端交互。
- 管理登录状态和 JWT。
- 默认优先调用服务端 API。
- 网络失败时写入本地 SQLite，并提示用户当前数据尚未同步。
- 管理专注计时、习惯打卡、饮食/记账/纪念日记录入口。
- 通过后端签发的预签名 URL 上传图片到 COS。
- 展示 AI 饮食识别结果和后端生成的饮食记录。
- 展示好友、好友申请、排行榜和轻量对比。
- 在“我的”页提供 AI 辅助入口、同步状态、账号与隐私相关入口。

客户端不负责：

- 判断好友是否有权限查看数据。
- 计算跨用户排行榜最终结果。
- 暴露其他用户原始数据。
- 持有 COS、数据库或 AI Provider 的长期密钥。

## 5. 服务端职责

- 用户认证、JWT 签发、刷新和登出。
- 权限校验和用户隔离。
- 权威数据存储。
- 多设备同步。
- 好友关系管理。
- 隐私规则执行。
- 排行榜和统计查询。
- 专注、习惯、饮食、记账、纪念日等业务 API。
- 媒体资产元数据管理和 COS 上传/读取签名。
- AI 对话、长期记忆、工具调用和饮食图片识别。

服务端不允许：

- 信任客户端提交的 `userId`。
- 无权限判断返回好友原始记录。
- 在 Controller 中堆积业务逻辑。
- 将 AI、COS、数据库密钥下发给客户端。

## 6. 后端分层

```text
Controller  接收请求、参数校验、返回 ApiResponse
Service     业务逻辑、权限判断、事务边界
Store       数据访问抽象，按环境切换内存实现或 PostgreSQL 实现
Model       领域对象
DTO         请求/响应对象
Config      安全、AI、COS、迁移和环境配置
```

当前代码包边界：

- `auth`：注册、登录、刷新 token、当前用户。
- `focus`：专注记录和专注偏好。
- `habits`：习惯和打卡。
- `friends` / `leaderboards`：好友申请、好友列表、排行榜。
- `media`：上传授权、媒体资产、COS 签名读写。
- `ai`：AI 对话、记忆、工具调用、饮食识别。
- `meals`：饮食记录和今日/区间汇总。
- `ledger`：记账流水、预算和月度汇总。
- `events`：纪念日和重要事件。
- `sync`：客户端变更 push/pull。
- `infra` / `health`：运行状态和基础设施检查。

## 7. 同步模型

客户端采用“后端优先 + 本地兜底 + 变更队列”。

正常流程：

1. 用户操作先调用后端接口。
2. 后端成功后返回权威数据。
3. 客户端更新本地 SQLite 缓存和同步状态。

网络失败流程：

1. 用户操作写入 SQLite。
2. 同时写入本地 `sync_mutations` 队列。
3. 界面提示当前为离线数据，需要联网同步。
4. 网络恢复后调用 `/api/sync/push`。
5. 客户端调用 `/api/sync/pull` 拉取服务端变更并更新 cursor。

## 8. 图片上传与 AI 饮食识别

图片上传采用“客户端直传 COS + 服务端保存元数据”的方式，避免后端承担大文件流量。

当前流程：

1. 客户端选择或拍摄图片。
2. 客户端调用 `POST /api/media/upload-token` 获取短有效期 PUT 预签名 URL。
3. 客户端直传图片到腾讯云 COS。
4. 客户端调用 `POST /api/media/assets` 保存媒体资产记录。
5. 后端返回媒体资产信息和短有效期 `readUrl`。
6. 客户端调用 `POST /api/ai/food-recognition`，传入 `imageUrl`、`mediaAssetId` 和可选 `mealType`。
7. 后端通过 AI 多模态能力识别食物和热量。
8. 后端将识别结果写入当前用户饮食记录，并返回 `mealLogId` 与 `totalCalories`。
9. 客户端刷新今日饮食统计。

原则：

- COS bucket 默认私有读写。
- 客户端只能获得短有效期、最小权限的上传和读取地址。
- 原图、识别结果和饮食记录都必须绑定当前 `userId`。
- AI 热量估算必须带免责声明，后续可增加“识别后编辑确认”体验。

## 9. AI 对话与生活建议

AI 能力基于 Spring AI 和 LifeTool 业务服务实现，详细设计见 `docs/AI_FRAMEWORK.md`。

当前后端核心组件：

- `AiController`：公开 AI API。
- `AiService`：会话、建议、工具执行、饮食识别编排。
- `AiAssistantClient`：模型调用抽象。
- `SpringAiAssistantClient`：OpenAI-compatible / 豆包等真实模型适配。
- `MockAiAssistantClient`：本地和测试降级。
- `UserDataTools`：Spring AI `@Tool` 工具集合。
- `AiChatStore` / `AiMemoryStore`：AI 会话、消息、工具调用、长期记忆存储抽象。

已开放只读工具：

- `get_focus_summary`
- `get_habit_summary`
- `get_diet_summary`
- `get_ledger_summary`
- `get_upcoming_events`
- `get_user_profile_context`

输出约束：

- 明确标注 AI 建议仅供参考。
- 不读取好友原始数据。
- 不允许模型绕过工具直接查询数据库。
- 不输出医疗诊断、治疗方案或强制性结论。
- 对话记录默认私密，并支持删除长期记忆。

## 10. 记录与提醒模块

Records 页承载饮食、记账、纪念日/重要事件三类个人记录。它们共用以下原则：

- 正常联网时优先写服务端。
- 网络失败时本地 SQLite 兜底，并提示用户同步状态。
- 服务端统一校验当前登录用户，只允许访问自己的原始记录。
- 图片凭证统一通过 `MediaAsset` 关联，不在业务表中保存外部长期密钥。
- 默认隐私为 `private`，好友侧不展示原始记录。

### 10.1 专注偏好

- 专注记录使用 `focus_sessions` 保存单次实际数据。
- 用户默认专注时长、短休息时长、长休息时长等偏好使用 `focus_preferences` 保存。
- 默认专注时长范围为 1 到 180 分钟。
- 服务端统计排行榜时只使用已完成的 `focus_sessions` 汇总数据。

### 10.2 纪念日与重要事件

- 纪念日使用 `anniversary_events` 或事件接口保存。
- 事件包含标题、日期、重复规则、提前提醒天数、备注、图片。
- 本地提醒由客户端调度；服务端保存规则，用于多设备恢复。
- 周年和倒数天数由客户端展示，服务端提供即将到来的事件列表。

### 10.3 记账

- 记账流水使用 `ledger_transactions` 保存。
- 月度预算使用 `ledger_budgets` 保存。
- 分类、账户、金额、发生时间是统计的核心字段。
- 月度统计由服务端基于流水聚合返回。

## 11. 核心数据实体

- User
- Device
- Friendship
- PrivacySetting
- FocusPreference
- FocusSession
- Habit
- HabitCheckin
- EventLog
- AnniversaryEvent
- MealLog
- MealItem
- MediaAsset
- AiAnalysisJob
- AiChatSession
- AiChatMessage
- AiToolCall
- AiMemoryItem
- AiSessionSummary
- AiAgentRun
- LedgerTransaction
- LedgerBudget
- DailyStats

## 12. 安全原则

- 所有业务接口默认需要登录。
- 密码必须哈希存储。
- access token 短有效期。
- refresh token 可吊销。
- 好友只能访问汇总接口。
- 原始记录默认仅本人可见。
- 敏感字段不得写入日志。
- 云存储密钥不得下发到客户端。
- AI 请求中不得包含无关个人敏感信息。
- 生产环境必须通过 `.env` 或云端密钥系统注入数据库、Redis、COS 和 AI Key。

