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

## 8. 核心数据实体

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
- LedgerTransaction
- DailyStats

## 9. 安全原则

- 所有业务接口默认需要登录。
- 密码必须哈希存储。
- access token 短有效期。
- refresh token 可吊销。
- 好友只能访问汇总接口。
- 原始记录默认仅本人可见。
- 敏感字段不得写入日志。
