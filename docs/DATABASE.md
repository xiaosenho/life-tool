# 数据库设计文档

## 1. 概述

- 数据库：PostgreSQL 16
- 迁移工具：Flyway
- 主键策略：UUID v4（`gen_random_uuid()`）
- 时间字段：`timestamptz`，统一使用 UTC
- 金额字段：`numeric(14,2)`，精确到分
- 热量/重量/营养字段：`numeric(10,2)`
- 扩展：启用 `pgcrypto`

## 2. 表分组与业务域

| 分组 | 表 | 业务域 | 状态 |
|------|-----|--------|------|
| 用户与认证 | `users`, `refresh_tokens`, `devices` | 注册、登录、多设备管理 | MVP |
| 隐私 | `privacy_settings` | 按数据类型的可见性控制 | MVP |
| 同步 | `server_versions`, `sync_mutations` | 多设备数据同步 | MVP |
| 专注 | `focus_sessions` | 番茄钟、倒计时、正计时记录 | MVP |
| 习惯 | `habits`, `habit_checkins` | 习惯定义与每日打卡 | MVP |
| 好友 | `friendships` | 好友申请、通过、删除 | MVP |
| 统计/排行榜 | `daily_stats` | 每日用户汇总统计 | MVP |
| 媒体 | `media_assets` | 图片/文件元数据管理 | Phase 2 |
| 饮食 | `meal_logs`, `meal_items` | 饮食记录与食物条目 | Phase 2 |
| 记账 | `ledger_transactions` | 收支流水记录 | Phase 2 |
| 重要事件 | `event_logs` | 重要日期事件记录 | Phase 2 |
| AI | `ai_analysis_jobs`, `ai_chat_sessions`, `ai_chat_messages` | AI 分析任务与对话 | Phase 2 |

## 3. 关键关系

```
users (1) ──< refresh_tokens
users (1) ──< devices
users (1) ──< privacy_settings         (每个 data_type 一行)
users (1) ──< sync_mutations
users (1) ──< focus_sessions
users (1) ──< habits (1) ──< habit_checkins
users (1) ──< friendships              (requester_id / addressee_id 双向指向 users)
users (1) ──< daily_stats              (每个 stat_date 一行)
users (1) ──< meal_logs (1) ──< meal_items
users (1) ──< ledger_transactions
users (1) ──< event_logs
users (1) ──< media_assets
users (1) ──< ai_analysis_jobs
users (1) ──< ai_chat_sessions (1) ──< ai_chat_messages

media_assets 被以下表通过 media_asset_id 引用：
  - users.avatar_asset_id
  - meal_logs.media_asset_id
  - ledger_transactions.media_asset_id
  - event_logs.media_asset_id
  - ai_analysis_jobs.media_asset_id
```

## 4. 同步版本模型

### 4.1 核心原理

同步基于**全局递增版本号**（server version / cursor）机制：

1. 每次客户端 `/sync/push` 或服务端内部数据变更，都会在 `server_versions` 中插入一行并通过 identity 主键分配一个递增的 `id`。
2. `sync_mutations` 记录每个实体的每次变更，`server_version` 指向 `server_versions.id`。
3. 客户端 `/sync/pull` 时传入 `cursor`（即上次拉取到的最大版本号），服务端返回所有 `server_version > cursor` 的变更。
4. 客户端使用 `nextCursor` 更新本地记录，下次拉取时传入。

### 4.2 数据流

```
客户端本地写入 -> 本地 sync_mutations 队列
  -> /sync/push (携带 mutations)
  -> 服务端写入 sync_mutations + 分配 server_version
  -> 返回 applied[{mutationId, serverVersion}] + serverCursor
  -> 客户端 /sync/pull (携带 cursor = serverCursor)
  -> 服务端返回 changes[{serverVersion > cursor}] + nextCursor
  -> 客户端更新本地数据 + 更新 cursor
```

### 4.3 实体版本选择

`sync_mutations.entity_type` 字段值约定如下，新增实体时需在此注册：

| entity_type | 对应表 | 加入同步的时机 |
|-------------|--------|----------------|
| focus_session | focus_sessions | 创建/完成番茄钟时 |
| focus_preference | focus_preferences | 修改默认专注时长和休息偏好时 |
| habit | habits | 创建/编辑/归档习惯时 |
| habit_checkin | habit_checkins | 每日打卡时 |
| privacy_setting | privacy_settings | 修改隐私设置时 |
| meal_log | meal_logs | 确认饮食记录时 |
| meal_item | meal_items | 随 meal_log 一起同步 |
| ledger_transaction | ledger_transactions | 创建/编辑记账时 |
| ledger_budget | ledger_budgets | 创建/编辑月度预算时 |
| event_log | event_logs | 创建/编辑事件时 |
| anniversary_event | anniversary_events | 创建/编辑纪念日和重复提醒时 |

## 5. 隐私原则

1. `privacy_settings` 表按 `(user_id, data_type)` 唯一索引，每个数据类型一行。
2. 可见性值：`private`（仅自己）、`friends_summary`（好友仅汇总）、`friends_detail`（好友可看详情）。
3. 服务端**必须**在返回好友侧数据前检查隐私设置，客户端不自行判断。
4. `privacy_settings` 本身也通过 `sync_mutations` 同步到客户端。
5. 默认值（在应用层注册时初始化，不在 DDL 中硬编码）：

| data_type | 默认值 |
|-----------|--------|
| focus | friends_summary |
| habit | friends_summary |
| diet | private |
| ledger | private |
| event | private |
| media | private |
| ai_chat | private |

## 6. 索引策略

- 所有外键字段都有索引。
- 用户私有数据通过 `(user_id, 时间字段)` 复合索引支持按时间范围查询。
- 状态字段通过 `(user_id, status)` 复合索引支持按状态过滤。
- 好友关系通过 `(requester_id, status)` 和 `(addressee_id, status)` 支持好友列表查询。
- 好友关系使用 `LEAST(requester_id, addressee_id)` + `GREATEST(...)` 唯一索引，防止 A-B 与 B-A 重复建关系。
- 同步通过 `(user_id, server_version)` 复合索引支持游标拉取。
- 唯一约束使用部分索引（`WHERE deleted_at IS NULL`）避免软删除冲突（`users.email`）。
- 用户邮箱唯一索引使用 `lower(email)`，避免大小写不同导致重复账号。

## 7. 命名规范

| 规则 | 示例 |
|------|------|
| 表名：蛇形复数 | `users`, `focus_sessions`, `meal_items` |
| 字段名：蛇形 | `display_name`, `password_hash`, `server_version` |
| 主键：`id` | `uuid PRIMARY KEY DEFAULT gen_random_uuid()` |
| 外键：`{目标表}_id` | `user_id`, `meal_log_id` |
| 创建时间：`created_at` | `timestamptz NOT NULL DEFAULT now()` |
| 更新时间：`updated_at` | `timestamptz NOT NULL DEFAULT now()` |
| 软删除：`deleted_at` | `timestamptz NULL` |
| 唯一索引：`uq_{表}_{字段}` | `uq_users_email` |
| 普通索引：`idx_{表}_{字段}` | `idx_focus_sessions_user_time` |

## 8. CHECK 约束说明

所有状态字段使用 `CHECK` 约束枚举取值范围，**不创建 PostgreSQL enum 类型**。原因：

- `CHECK` 约束修改成本低：ALTER TABLE ... DROP CONSTRAINT / ADD CONSTRAINT。
- `enum` 需要 ALTER TYPE，在分布式环境或复制中风险更高。
- 未来迁移到其他数据库时兼容性更好。

## 9. 后续迁移建议

### 9.1 分阶段迁移

1. **V1（当前）**：初始化所有表结构。
2. **V2+**：根据实际需求添加字段、索引或调整约束，不在 V1 中过度设计。

### 9.2 常见变更模式

```sql
-- 加字段
ALTER TABLE habits ADD COLUMN sort_order int NOT NULL DEFAULT 0;

-- 加索引
CREATE INDEX idx_meal_logs_note ON meal_logs USING gin (to_tsvector('simple', note));

-- 软删除表改为硬删除（数据量过大时）
-- 先确认数据已备份，然后 DROP COLUMN deleted_at
```

### 9.3 数据清理策略

- 定期清理已吊销的 `refresh_tokens`（超过过期时间 + 7 天）。
- `server_versions` 和 `sync_mutations` 保留最近 90 天数据，使用定时任务归档。
- 软删除的 `media_assets` 保留 30 天后清理云存储对象和记录。

### 9.4 性能关注点

- `sync_mutations` 是高频写入表，`payload` 为 jsonb，注意控制 payload 大小（建议 < 10KB）。
- `daily_stats` 建议通过定时任务或事件触发计算，避免实时聚合。
- `daily_stats` 的 `stat_date` 索引在排行榜查询时使用，数据量大后考虑按 date 分区。

## 10. 当前 V1 边界

- V1 只提供数据库结构，不代表后端已经切换到 JPA/数据库仓储。
- 当前后端仍有部分内存仓储实现，后续应按模块逐步迁移到 Repository。
- `backend/src/main/resources/db/migration/V1__init_schema.sql` 按 Flyway 迁移文件组织，但项目尚未接入 Flyway 依赖；接入时可直接复用该路径。

## 11. V2 规划补充

以下能力已进入产品规划，但是否需要新增表要在实现前复查现有 V1 DDL。原则是：**如果 V1 已发布，不直接修改 `V1__init_schema.sql`，使用新的 `V2__*.sql` 迁移补充。**

### 11.1 专注偏好

建议新增 `focus_preferences`：

| 字段 | 说明 |
|------|------|
| `user_id` | 用户 ID，建议唯一 |
| `default_focus_minutes` | 默认专注时长，1 到 180 |
| `short_break_minutes` | 短休息时长 |
| `long_break_minutes` | 长休息时长 |
| `auto_start_break` | 是否自动开始休息 |

同步实体类型建议使用 `focus_preference`。

### 11.2 月度预算

建议新增 `ledger_budgets`：

| 字段 | 说明 |
|------|------|
| `user_id` | 用户 ID |
| `budget_month` | 预算月份，如 `2026-05-01` 表示 2026 年 5 月 |
| `category` | 分类预算，空值表示整月总预算 |
| `amount` | 预算金额 |
| `currency` | 币种，默认 CNY |

唯一约束建议：`(user_id, budget_month, category)`，其中总预算可通过部分唯一索引处理 `category IS NULL`。

### 11.3 纪念日与重要事件

现有 `event_logs` 可承载重要事件，但纪念日需要更明确的重复和提醒语义。实现前有两个选择：

1. 扩展 `event_logs`，增加 `event_date`、`repeat_rule`、`remind_days_before`、`event_type`。
2. 新增 `anniversary_events`，专门承载纪念日、生日和重复提醒。

推荐先新增 `anniversary_events`，避免和普通事件记录混在一起：

| 字段 | 说明 |
|------|------|
| `user_id` | 用户 ID |
| `event_type` | anniversary / birthday / important_day / todo_reminder |
| `title` | 标题 |
| `event_date` | 原始日期 |
| `repeat_rule` | none / yearly / monthly / weekly |
| `remind_days_before` | 提前提醒天数数组，可用 jsonb |
| `media_asset_id` | 可选图片 |

客户端负责本地通知调度，服务端负责保存提醒规则并支持多设备恢复。
