# ARCHITECTURE：系统架构

## 1. 总体架构

```text
Mobile App
  |
  | HTTPS / JSON
  v
Backend API
  |
  +-- PostgreSQL
  +-- Redis
  +-- Tencent Cloud COS
  +-- AI Provider
  +-- Push Notification Provider
```

## 2. 技术选型

### Frontend

- React Native + Expo
- TypeScript
- expo-router
- expo-sqlite
- Zustand
- TanStack Query
- expo-notifications

### Backend

- Java
- Spring Boot
- Spring Security
- Spring Data JPA
- PostgreSQL
- Redis
- Maven

### Infra

- Docker
- GitHub Actions
- 云服务器或 Railway/Fly.io
- Nginx/Caddy
- 腾讯云 COS（暂定，用于图片和媒体文件）
- AI 服务提供商（用于图片识别、热量估算和生活建议）

## 3. 目录边界

```text
backend/       后端服务，包含 API、业务逻辑、数据库访问、测试
frontend/      Expo 移动端 App
docs/          产品、架构、接口、任务和 Agent 规则
scripts/       本地开发和检查脚本
.github/       CI/CD
```

## 4. 客户端职责

- 提供手机端交互。
- 本地存储用户数据。
- 支持断网记录。
- 管理本地提醒。
- 调用同步 API 上传和拉取数据。
- 通过后端下发的临时凭证或预签名地址上传图片。
- 展示 AI 识别结果，并允许用户确认或修正。
- 展示服务端返回的好友汇总数据。

客户端不负责：

- 判断好友是否有权限查看数据。
- 计算跨用户排行榜最终结果。
- 暴露其他用户原始数据。

## 5. 服务端职责

- 用户认证。
- 权限校验。
- 权威数据存储。
- 多设备同步。
- 好友关系管理。
- 隐私规则执行。
- 排行榜和统计。
- 媒体资产元数据管理。
- 云存储上传授权。
- AI 识别任务调度和结果落库。
- 推送通知。

服务端不允许：

- 信任客户端提交的 `userId`。
- 无权限判断返回好友原始记录。
- 在 Controller 中堆积业务逻辑。

## 6. 后端分层

```text
Controller  接收请求、参数校验、返回响应
Service     业务逻辑、权限判断、事务边界
Repository  数据访问
Entity      数据库实体
DTO         请求/响应对象
VO          视图对象
```

## 7. 同步模型

客户端采用“本地先写 + 变更队列 + 后台同步”。

流程：

1. 用户操作先写入 SQLite。
2. 同时写入本地 `sync_mutations` 队列。
3. 网络可用时调用 `/sync/push`。
4. 服务端落库并返回版本号。
5. 客户端调用 `/sync/pull` 拉取服务端变更。
6. 本地更新同步状态。

## 8. 图片上传与 AI 饮食识别

图片上传采用“客户端直传云存储 + 服务端保存元数据”的方式，避免后端承担大文件流量。

流程：

1. 客户端选择或拍摄图片。
2. 客户端调用 `/media/upload-token` 获取上传授权。
3. 客户端直传图片到腾讯云 COS。
4. 客户端调用 `/media/assets` 通知后端保存媒体资产记录。
5. 用户触发饮食识别，后端创建 `AiAnalysisJob`。
6. 后端调用 AI 服务识别图片内容。
7. AI 结果保存为待确认的饮食草稿。
8. 客户端展示食物、重量、热量和置信度，用户确认后写入正式饮食记录。

原则：

- COS bucket 默认私有读写。
- 客户端只能获得短有效期、最小权限的上传授权。
- 原图、缩略图和 AI 结果都必须绑定 `userId`。
- AI 结果不能绕过用户确认直接进入最终饮食统计。

## 9. AI 对话与生活建议

AI 对话基于用户自己的近期生活数据生成上下文摘要，不直接把完整原始流水无边界地传给模型。

上下文来源：

- 最近 7/30 天专注统计。
- 习惯完成率和连续打卡情况。
- 已确认饮食热量和宏量营养估算。
- 记账分类汇总。
- 用户主动输入的问题。

输出约束：

- 明确标注 AI 建议的非专业性质。
- 不读取好友原始数据。
- 不输出医疗诊断、治疗方案或强制性结论。
- 对话记录默认私密，并支持删除。

## 10. 核心数据实体

- User
- Device
- Friendship
- PrivacySetting
- FocusSession
- Habit
- HabitCheckin
- EventLog
- MealLog
- MealItem
- MediaAsset
- AiAnalysisJob
- AiChatSession
- AiChatMessage
- UserLifeSummary
- LedgerTransaction
- DailyStats

## 11. 安全原则

- 所有业务接口默认需要登录。
- 密码必须哈希存储。
- access token 短有效期。
- refresh token 可吊销。
- 好友只能访问汇总接口。
- 原始记录默认仅本人可见。
- 敏感字段不得写入日志。
- 云存储密钥不得下发到客户端。
- AI 请求中不得包含无关个人敏感信息。
