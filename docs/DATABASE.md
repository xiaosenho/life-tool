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
| 专注 | `focus_sessions`, `focus_preferences` | 番茄钟、倒计时、正计时记录 + 偏好 | MVP + V2 |
| 习惯 | `habits`, `habit_checkins` | 习惯定义与每日打卡 | MVP |
| 好友 | `friendships`, `friend_messages` | 好友申请、通过、删除、互动消息 | MVP + Phase 3 |
| 统计/排行榜 | `daily_stats` | 每日用户汇总统计 | MVP |
| 媒体 | `media_assets` | 图片/文件元数据管理 | Phase 2 |
| 饮食 | `meal_logs`, `meal_items` | 饮食记录与食物条目 | Phase 2 |
| 记账 | `ledger_transactions`, `ledger_budgets` | 收支流水记录 + 月度预算 | Phase 2 + V2 |
| 重要事件 | `event_logs`, `anniversary_events` | 重要日期事件记录 + 纪念日重复提醒 | Phase 2 + V2 |
| 词书 | `vocab_books`, `vocab_entries`, `user_vocab_progress` | 公共词书、单词条目、学习进度 | Phase 2 + V8 |
| AI | `ai_analysis_jobs`, `ai_chat_sessions`, `ai_chat_messages`, `ai_tool_calls`, `ai_memory_items`, `ai_session_summaries`, `ai_agent_runs` | AI 分析任务、对话、工具调用与记忆 | Phase 2 + AI Framework |

## 3. 关键关系

```
users (1) ──< refresh_tokens
users (1) ──< devices
users (1) ──< privacy_settings         (每个 data_type 一行)
users (1) ──< sync_mutations
users (1) ──< focus_sessions
users (1) ──< focus_preferences        (每用户一行，unique)
users (1) ──< habits (1) ──< habit_checkins
users (1) ──< friendships              (requester_id / addressee_id 双向指向 users)
users (1) ──< friend_messages          (from_user_id / to_user_id 双向指向 users)
users (1) ──< daily_stats              (每个 stat_date 一行)
users (1) ──< meal_logs (1) ──< meal_items
users (1) ──< ledger_transactions
users (1) ──< ledger_budgets           (每用户每月可有多条)
users (1) ──< event_logs
users (1) ──< anniversary_events       (每用户可多个纪念日)
users (1) ──< media_assets
users (1) ──< ai_analysis_jobs
users (1) ──< ai_chat_sessions (1) ──< ai_chat_messages
users (1) ──< ai_memory_items
ai_chat_sessions (1) ──< ai_session_summaries
ai_chat_messages (1) ──< ai_tool_calls
ai_chat_sessions (1) ──< ai_agent_runs
users (1) ──< user_vocab_progress >── (1) vocab_books (1) ──< vocab_entries

media_assets 被以下表通过 media_asset_id 引用：
  - users.avatar_asset_id
  - meal_logs.media_asset_id
  - ledger_transactions.media_asset_id
  - event_logs.media_asset_id
  - anniversary_events.media_asset_id
  - ai_analysis_jobs.media_asset_id
  - friend_messages.metadata.assetId（JSON 元数据）
  - ai_chat_messages.attachment.assetId（AI 对话附件元数据）
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
| meal_log | meal_logs | 手动创建或 AI 识别成功写入饮食记录时 |
| meal_item | meal_items | 随 meal_log 一起同步 |
| ledger_transaction | ledger_transactions | 创建/编辑记账时 |
| ledger_budget | ledger_budgets | 创建/编辑月度预算时 |
| event_log | event_logs | 创建/编辑事件时 |
| anniversary_event | anniversary_events | 创建/编辑纪念日和重复提醒时 |
| friend_message | friend_messages | 好友发送消息或鼓励互动时 |

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
- 好友会话汇总依赖 `friend_messages` 上的会话维度聚合索引，避免前端轮询时全量扫描消息。
- 纪念日查询与首页/日历习惯查询已逐步改为批量查询，避免逐天逐条循环打数据库。

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

1. **V1**：初始化所有表结构（MVP + Phase 2 基础表）。
2. **V2**：补充专注偏好、月度预算、纪念日与重复提醒（TASK-DB-002）。
3. **V3**：补充 AI Framework 持久化表（AI 工具调用审计、长期记忆、会话摘要、Agent 运行审计）。
4. **V4**：修复运行期约束与部分状态取值，保证当前后端实现可在真实 PostgreSQL 环境落库。
5. **V5**：好友消息表。
6. **V6**：好友聊天 / AI 聊天媒体附件支持。
7. **V7**：好友消息会话聚合查询优化索引。
8. **V8**：背单词词书、词条与学习进度表。
9. **V9**：统一 `anniversary_events.type` 字段并清理旧 `event_type`。
10. **V10+**：根据实际需求添加字段、索引或调整约束，不在已发布迁移中过度设计。

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

## 10. 当前迁移边界

- 当前迁移文件位于 `backend/src/main/resources/db/migration`，包括 V1 到 V4。
- 后端已接入 Flyway 依赖，并通过 `lifetool.database.migration-enabled` 控制是否在启动时自动执行迁移。
- 当前后端采用 Store 抽象：本地和测试可使用内存 Store，`postgres` profile 使用 PostgreSQL Store。
- 迁移文件不等同于所有业务都必须同步支持离线；客户端同步范围以实际 service 和 `sync_mutations` 实现为准。

## 11. V2 已迁移内容

V2 新增 `focus_preferences`、`ledger_budgets`、`anniversary_events` 三张表，对应迁移文件 `V2__add_focus_preferences_budgets_anniversaries.sql`。

### 11.1 专注偏好 (`focus_preferences`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `user_id` | uuid | FK → users, NOT NULL, UNIQUE | 每个用户一条 |
| `default_focus_minutes` | int | CHECK 1~180, NOT NULL, DEFAULT 25 | 默认专注时长（分钟） |
| `short_break_minutes` | int | CHECK 0~60, NOT NULL, DEFAULT 5 | 短休息时长（分钟） |
| `long_break_minutes` | int | CHECK 0~60, NOT NULL, DEFAULT 15 | 长休息时长（分钟） |
| `auto_start_break` | boolean | NOT NULL, DEFAULT false | 是否自动开始休息 |

- 唯一约束：`uq_focus_preferences_user` — 部分唯一索引 `(user_id)`，保证每用户一条。
- 同步 entity_type：`focus_preference`（已在 §4.3 注册）。

### 11.2 月度预算 (`ledger_budgets`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `user_id` | uuid | FK → users, NOT NULL | |
| `budget_month` | date | NOT NULL, CHECK 当月第一天 | 预算月份，存当月第一天 |
| `category` | text | NULL 表示整月总预算 | 分类预算标识 |
| `amount` | numeric(14,2) | CHECK >= 0, NOT NULL | 预算金额 |
| `currency` | text | NOT NULL, DEFAULT 'CNY' | 币种 |

- 唯一约束：
  - `uq_ledger_budgets_category` — 部分唯一索引 `(user_id, budget_month, category) WHERE category IS NOT NULL AND deleted_at IS NULL`。
  - `uq_ledger_budgets_total` — 部分唯一索引 `(user_id, budget_month) WHERE category IS NULL AND deleted_at IS NULL`。
- 索引：`idx_ledger_budgets_user_month` — `(user_id, budget_month)` 支持按月查询。
- 同步 entity_type：`ledger_budget`（已在 §4.3 注册）。

### 11.3 纪念日与重要事件 (`anniversary_events`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `user_id` | uuid | FK → users, NOT NULL | |
| `event_type` | text | CHECK, NOT NULL | anniversary / birthday / important_day / todo_reminder |
| `title` | text | NOT NULL | |
| `event_date` | date | NOT NULL | 原始事件日期 |
| `repeat_rule` | text | CHECK, NOT NULL, DEFAULT 'none' | none / yearly / monthly / weekly |
| `remind_days_before` | jsonb | NOT NULL, DEFAULT '[]', CHECK array | 提前提醒天数数组 |
| `note` | text | NULL | |
| `media_asset_id` | uuid | FK → media_assets, ON DELETE SET NULL | 可选关联图片 |

- 索引：
  - `idx_anniversary_events_user_date` — `(user_id, event_date)` 支持按日期范围查询即将到来事件。
  - `idx_anniversary_events_user_type` — `(user_id, event_type)` 支持按事件类型过滤。
- 同步 entity_type：`anniversary_event`（已在 §4.3 注册）。

### 11.4 V2 表与现有表关系

```
users (1) ──< focus_preferences        (每用户一行，unique)
users (1) ──< ledger_budgets           (每用户每月可有多条：总预算 + 分类预算)
users (1) ──< anniversary_events       (每用户可多个纪念日)
media_assets (1) ──< anniversary_events (通过 media_asset_id)
```

## 12. V3 已迁移内容

V3 新增 `ai_tool_calls`、`ai_memory_items`、`ai_session_summaries`、`ai_agent_runs` 四张表，对应迁移文件 `V3__add_ai_framework_tables.sql`。

### 12.1 AI 工具调用审计 (`ai_tool_calls`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | uuid | 主键 | |
| `user_id` | uuid | FK → users, NOT NULL | 当前用户 |
| `session_id` | uuid | FK → ai_chat_sessions, NOT NULL | AI 会话 |
| `message_id` | uuid | FK → ai_chat_messages, NOT NULL | 触发工具调用的消息 |
| `tool_name` | text | NOT NULL | 工具名，如 get_focus_summary |
| `arguments` | jsonb | NOT NULL, DEFAULT '{}' | 工具参数，禁止包含客户端传入 userId |
| `result_summary` | jsonb | NULL | 工具结果摘要，不保存过量原始明细 |
| `status` | text | CHECK, NOT NULL, DEFAULT 'pending' | pending / succeeded / failed |
| `latency_ms` | int | CHECK >= 0, NULL | 执行耗时（毫秒） |
| `created_at` | timestamptz | NOT NULL | 创建时间 |

- 索引：
  - `idx_ai_tool_calls_user_session` — `(user_id, session_id)` 按会话查询工具调用。
  - `idx_ai_tool_calls_user_message` — `(user_id, message_id)` 按消息查询工具调用。
  - `idx_ai_tool_calls_user_status` — `(user_id, status)` 按状态过滤。

### 12.2 AI 长期记忆 (`ai_memory_items`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | uuid | 主键 | |
| `user_id` | uuid | FK → users, NOT NULL | 当前用户 |
| `memory_type` | text | CHECK, NOT NULL | preference / goal / constraint / health_note / routine |
| `content` | text | NOT NULL | 记忆内容 |
| `source` | text | CHECK, NOT NULL, DEFAULT 'assistant_suggested' | user_confirmed / assistant_suggested / system_extracted |
| `confidence` | numeric(4,3) | CHECK 0.000~1.000, NULL | 置信度 |
| `enabled` | boolean | NOT NULL, DEFAULT true | 是否启用 |
| `created_at` | timestamptz | NOT NULL | 创建时间 |
| `updated_at` | timestamptz | NOT NULL | 更新时间 |
| `deleted_at` | timestamptz | NULL | 软删除 |

- 索引：
  - `idx_ai_memory_items_user_type` — `(user_id, memory_type)` 按类型筛选。
  - `idx_ai_memory_items_user_enabled` — `(user_id, enabled)` 按启用状态筛选。
- `updated_at` 触发器：`trg_ai_memory_items_updated_at`。

### 12.3 AI 会话摘要 (`ai_session_summaries`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | uuid | 主键 | |
| `session_id` | uuid | FK → ai_chat_sessions, NOT NULL, UNIQUE | AI 会话，每会话一条 |
| `user_id` | uuid | FK → users, NOT NULL | 当前用户 |
| `summary` | text | NOT NULL | 当前会话压缩摘要 |
| `message_count` | int | CHECK >= 0, NOT NULL, DEFAULT 0 | 摘要覆盖的消息数 |
| `created_at` | timestamptz | NOT NULL | 创建时间 |
| `updated_at` | timestamptz | NOT NULL | 更新时间 |

- 唯一约束：`uq_ai_session_summaries_session` — `(session_id)`，每个会话最多一条当前摘要。
- 索引：`idx_ai_session_summaries_user` — `(user_id)` 支持用户侧查询。
- `updated_at` 触发器：`trg_ai_session_summaries_updated_at`。

### 12.4 AI Agent 运行审计 (`ai_agent_runs`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | uuid | 主键 | |
| `user_id` | uuid | FK → users, NOT NULL | 当前用户 |
| `session_id` | uuid | FK → ai_chat_sessions, ON DELETE SET NULL, NULL | AI 会话，可为空（独立 run） |
| `provider` | text | NOT NULL | AI Provider，如 openai / mock |
| `model` | text | NOT NULL | 模型名，如 gpt-4o |
| `input_tokens` | int | CHECK >= 0, NOT NULL, DEFAULT 0 | 输入 token |
| `output_tokens` | int | CHECK >= 0, NOT NULL, DEFAULT 0 | 输出 token |
| `tool_rounds` | int | CHECK >= 0, NOT NULL, DEFAULT 0 | 工具调用轮数 |
| `status` | text | CHECK, NOT NULL, DEFAULT 'succeeded' | succeeded / failed |
| `error_code` | text | NULL | 错误码，失败时记录 |
| `created_at` | timestamptz | NOT NULL | 创建时间 |

- 索引：
  - `idx_ai_agent_runs_user_session` — `(user_id, session_id)` 按会话查询。
  - `idx_ai_agent_runs_user_status` — `(user_id, status)` 按状态过滤。
  - `idx_ai_agent_runs_user_time` — `(user_id, created_at)` 按时间范围查询。

### 12.5 V3 表关系

```
users (1) ──< ai_tool_calls
users (1) ──< ai_memory_items
users (1) ──< ai_session_summaries
users (1) ──< ai_agent_runs
ai_chat_sessions (1) ──< ai_session_summaries (每会话一条)
ai_chat_sessions (1) ──< ai_agent_runs (可多条)
ai_chat_messages (1) ──< ai_tool_calls (每消息可多条工具调用)
```

## 13. V5 已迁移内容

V5 新增 `friend_messages`，用于支持好友间站内消息和轻量鼓励互动，对应迁移文件 `V5__add_friend_messages.sql`。

### 13.1 好友互动消息 (`friend_messages`)

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| `id` | uuid | 主键 | |
| `from_user_id` | uuid | FK → users, NOT NULL | 发送者 |
| `to_user_id` | uuid | FK → users, NOT NULL | 接收者 |
| `message_type` | text | CHECK, NOT NULL | `text` / `cheer` |
| `content` | text | NOT NULL | 消息内容或鼓励文本 |
| `read_at` | timestamptz | NULL | 首次已读时间 |
| `created_at` | timestamptz | NOT NULL | 发送时间 |
| `updated_at` | timestamptz | NOT NULL | 更新时间 |
| `deleted_at` | timestamptz | NULL | 软删除 |

- 索引：
  - `idx_friend_messages_to_user_created` — `(to_user_id, created_at DESC)` 支持收件箱与未读查询。
  - `idx_friend_messages_pair_created` — `(from_user_id, to_user_id, created_at DESC)` 支持好友会话查询。
- 约束：
  - `friend_messages_type_check` — 仅允许 `text` 和 `cheer`。
- 说明：
  - 应用层必须先校验双方是否为好友，再允许写入互动消息。
  - 未读数量按 `to_user_id = 当前用户 AND read_at IS NULL` 统计。
  - 同步 entity_type：当前不进入离线同步队列，按服务端实时互动处理。

- `ai_tool_calls` 同时依赖 `ai_chat_sessions` 和 `ai_chat_messages` 表。
- `ai_agent_runs.session_id` 可为 NULL，以支持不绑定会话的运行（如定时分析任务）。
- `ai_memory_items` 只依赖 `users`，长期记忆跨会话独立存在。

## 13. V4 已迁移内容

V4 对应迁移文件 `V4__fix_runtime_constraints.sql`，用于修复开发过程中真实接口落库暴露出的约束问题。

主要调整：

- 放宽或修正 `friendships.status`，支持当前好友申请流程中的拒绝状态。
- 修复部分运行时状态字段与后端枚举不一致的问题。
- 保持向前兼容，不直接修改已发布的 V1/V2/V3 文件。

后续新增约束或字段时继续使用新的版本号迁移，禁止回改已在环境中执行过的 migration。
