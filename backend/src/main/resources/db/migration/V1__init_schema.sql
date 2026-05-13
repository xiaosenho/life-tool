-- ============================================
-- LifeTool 数据库初始化 DDL
-- PostgreSQL 16 + pgcrypto
-- Flyway 迁移：V1__init_schema.sql
-- ============================================

-- 启用 pgcrypto 扩展（gen_random_uuid()）
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================
-- 1. 用户与认证
-- ============================================

-- 1.1 用户表
CREATE TABLE users (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    email           text        NOT NULL,
    password_hash   text        NOT NULL,
    display_name    text        NOT NULL,
    avatar_asset_id uuid        NULL,       -- 头像媒体资产 ID，后续关联 media_assets
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz NULL        -- 软删除
);

CREATE UNIQUE INDEX uq_users_email ON users (lower(email)) WHERE deleted_at IS NULL;
CREATE INDEX idx_users_created_at ON users (created_at);

COMMENT ON TABLE  users              IS '用户表';
COMMENT ON COLUMN users.email        IS '登录邮箱，唯一';
COMMENT ON COLUMN users.password_hash IS '密码哈希值（BCrypt）';
COMMENT ON COLUMN users.display_name IS '用户显示昵称';
COMMENT ON COLUMN users.avatar_asset_id IS '头像媒体资产 ID';

-- 1.2 刷新令牌表
CREATE TABLE refresh_tokens (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      text        NOT NULL,   -- 刷新令牌的哈希值
    device_id       text        NULL,       -- 关联设备标识
    expires_at      timestamptz NOT NULL,
    revoked_at      timestamptz NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_user_id   ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires   ON refresh_tokens (expires_at);
CREATE UNIQUE INDEX uq_refresh_tokens_hash ON refresh_tokens (token_hash);

COMMENT ON TABLE  refresh_tokens            IS '刷新令牌表，用于 access token 续期';
COMMENT ON COLUMN refresh_tokens.token_hash IS '刷新令牌的哈希值，不存原文';
COMMENT ON COLUMN refresh_tokens.revoked_at IS '吊销时间，非空表示已吊销';

-- 1.3 设备表
CREATE TABLE devices (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_name     text        NOT NULL,   -- 设备名称，如 "iPhone 15 Pro"
    device_type     text        NOT NULL
                    CHECK (device_type IN ('ios', 'android', 'web')),   -- 设备类型
    push_token      text        NULL,       -- 推送通知 token
    last_active_at  timestamptz NOT NULL DEFAULT now(),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_devices_user_id ON devices (user_id);
CREATE INDEX idx_devices_push_token ON devices (push_token);

COMMENT ON TABLE  devices             IS '用户设备表';
COMMENT ON COLUMN devices.device_name IS '设备名称';
COMMENT ON COLUMN devices.device_type IS '设备类型：ios / android / web';

-- ============================================
-- 2. 隐私设置
-- ============================================

CREATE TABLE privacy_settings (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    data_type       text        NOT NULL
                    CHECK (data_type IN ('focus', 'habit', 'diet', 'ledger', 'event', 'media', 'ai_chat')),
    visibility      text        NOT NULL DEFAULT 'private'
                    CHECK (visibility IN ('private', 'friends_summary', 'friends_detail')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_privacy_settings_user_type ON privacy_settings (user_id, data_type);

COMMENT ON TABLE  privacy_settings            IS '隐私设置表，控制各业务数据在好友侧的可见性';
COMMENT ON COLUMN privacy_settings.data_type  IS '数据类型，如 focus / habit / diet / ledger / event / media / ai_chat';
COMMENT ON COLUMN privacy_settings.visibility IS '可见性：private 私密 / friends_summary 好友仅汇总 / friends_detail 好友可见详情';

-- ============================================
-- 3. 同步（version/cursor 模型）
-- ============================================

-- 3.1 服务端全局版本序列（用于同步 cursor 递增）
CREATE TABLE server_versions (
    id              bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE  server_versions IS '全局版本递增序列，每接受一次推拉操作插入一行';

-- 3.2 同步变更记录（每个实体的版本快照）
CREATE TABLE sync_mutations (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    entity_type     text        NOT NULL,   -- 实体类型，如 focus_session / habit / habit_checkin
    entity_id       uuid        NOT NULL,   -- 对应业务实体的主键
    operation       text        NOT NULL
                    CHECK (operation IN ('create', 'update', 'delete')),
    server_version  bigint      NOT NULL REFERENCES server_versions(id),   -- 对应 server_versions.id
    payload         jsonb       NOT NULL DEFAULT '{}',  -- 实体快照
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_sync_mutations_user_version ON sync_mutations (user_id, server_version);
CREATE INDEX idx_sync_mutations_entity       ON sync_mutations (entity_type, entity_id);
CREATE UNIQUE INDEX uq_sync_mutations ON sync_mutations (user_id, entity_type, entity_id, server_version);

COMMENT ON TABLE  sync_mutations              IS '同步变更记录表，记录每个实体的每一次版本变化';
COMMENT ON COLUMN sync_mutations.entity_type  IS '实体类型，如 focus_session / habit / habit_checkin / privacy_setting 等';
COMMENT ON COLUMN sync_mutations.operation    IS '变更操作：create / update / delete';
COMMENT ON COLUMN sync_mutations.server_version IS '全局递增版本号，用于游标分页拉取';
COMMENT ON COLUMN sync_mutations.payload      IS '实体完整 json 快照（不含已删除标记）';

-- ============================================
-- 4. 专注
-- ============================================

CREATE TABLE focus_sessions (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mode            text        NOT NULL DEFAULT 'pomodoro'
                    CHECK (mode IN ('pomodoro', 'countdown', 'stopwatch')),
    target_seconds  int         NOT NULL DEFAULT 1500 CHECK (target_seconds > 0),  -- 目标时长（秒），番茄钟默认 25min
    actual_seconds  int         NOT NULL DEFAULT 0 CHECK (actual_seconds >= 0),     -- 实际专注时长（秒）
    status          text        NOT NULL DEFAULT 'completed'
                    CHECK (status IN ('completed', 'interrupted', 'abandoned')),
    started_at      timestamptz NOT NULL,
    ended_at        timestamptz NULL,
    note            text        NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_focus_sessions_user_time ON focus_sessions (user_id, started_at);
CREATE INDEX idx_focus_sessions_user_status ON focus_sessions (user_id, status);

COMMENT ON TABLE  focus_sessions               IS '专注记录表，支持番茄钟 / 倒计时 / 正计时';
COMMENT ON COLUMN focus_sessions.mode           IS '专注模式：pomodoro 番茄钟 / countdown 倒计时 / stopwatch 正计时';
COMMENT ON COLUMN focus_sessions.target_seconds IS '目标专注时长（秒）';
COMMENT ON COLUMN focus_sessions.actual_seconds IS '实际专注时长（秒）';
COMMENT ON COLUMN focus_sessions.status         IS '状态：completed 完成 / interrupted 中断 / abandoned 放弃';

-- ============================================
-- 5. 习惯
-- ============================================

-- 5.1 习惯定义
CREATE TABLE habits (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name            text        NOT NULL,
    description     text        NULL,
    frequency_type  text        NOT NULL DEFAULT 'daily'
                    CHECK (frequency_type IN ('daily', 'weekly', 'custom')),
    frequency_days  int[]       NULL,       -- 自定义周期：星期几（0=周日, 1=周一, ...）
    target_count    int         NOT NULL DEFAULT 1 CHECK (target_count > 0),     -- 每日/每周目标次数
    color           text        NULL,       -- 习惯颜色（十六进制，如 #FF6B6B）
    icon            text        NULL,       -- 习惯图标标识
    is_archived     boolean     NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz NULL
);

CREATE INDEX idx_habits_user_id ON habits (user_id);
CREATE INDEX idx_habits_user_archived ON habits (user_id, is_archived);

COMMENT ON TABLE  habits              IS '习惯定义表';
COMMENT ON COLUMN habits.frequency_type IS '打卡频率：daily 每日 / weekly 每周 / custom 自定义';
COMMENT ON COLUMN habits.frequency_days IS '自定义周期：星期几数组，0=周日';
COMMENT ON COLUMN habits.is_archived    IS '是否归档，归档后不再提醒';

-- 5.2 习惯打卡记录
CREATE TABLE habit_checkins (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    habit_id        uuid        NOT NULL REFERENCES habits(id) ON DELETE CASCADE,
    checkin_date    date        NOT NULL,       -- 打卡日期（用户时区）
    count           int         NOT NULL DEFAULT 1 CHECK (count > 0),  -- 打卡次数（支持多次打卡）
    note            text        NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_habit_checkins_user_date ON habit_checkins (user_id, checkin_date);
CREATE UNIQUE INDEX uq_habit_checkins ON habit_checkins (habit_id, user_id, checkin_date);

COMMENT ON TABLE  habit_checkins       IS '习惯打卡记录表';
COMMENT ON COLUMN habit_checkins.checkin_date IS '打卡日期（用户本地时区的日期）';

-- ============================================
-- 6. 好友
-- ============================================

CREATE TABLE friendships (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id    uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,  -- 申请方
    addressee_id    uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,  -- 接收方
    status          text        NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'accepted', 'blocked', 'deleted')),
    requested_at    timestamptz NOT NULL DEFAULT now(),
    responded_at    timestamptz NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz NULL,
    CHECK (requester_id <> addressee_id)
);

CREATE INDEX idx_friendships_requester ON friendships (requester_id, status);
CREATE INDEX idx_friendships_addressee ON friendships (addressee_id, status);
CREATE UNIQUE INDEX uq_friendships_pair
    ON friendships (LEAST(requester_id, addressee_id), GREATEST(requester_id, addressee_id))
    WHERE deleted_at IS NULL;

COMMENT ON TABLE  friendships              IS '好友关系表，也用于存储好友申请记录';
COMMENT ON COLUMN friendships.requester_id IS '申请方用户 ID';
COMMENT ON COLUMN friendships.addressee_id IS '接收方用户 ID';
COMMENT ON COLUMN friendships.status       IS '状态：pending 待确认 / accepted 已通过 / blocked 已屏蔽 / deleted 已删除';

-- ============================================
-- 7. 每日统计 / 排行榜
-- ============================================

CREATE TABLE daily_stats (
    id                      uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id                 uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    stat_date               date        NOT NULL,       -- 统计日期
    focus_total_seconds     int         NOT NULL DEFAULT 0,
    focus_session_count     int         NOT NULL DEFAULT 0,
    habit_completed_count   int         NOT NULL DEFAULT 0,
    habit_total_count       int         NOT NULL DEFAULT 0,
    habit_streak_days       int         NOT NULL DEFAULT 0,   -- 截止当日的连续打卡天数
    meal_total_calories     numeric(10,2) NULL,              -- 当日饮食总热量
    ledger_total_expense    numeric(14,2) NULL,              -- 当日记账总支出
    note                    text        NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_daily_stats_user_date ON daily_stats (user_id, stat_date);
CREATE INDEX idx_daily_stats_date ON daily_stats (stat_date);
CREATE INDEX idx_daily_stats_focus_rank ON daily_stats (stat_date, focus_total_seconds DESC);
CREATE INDEX idx_daily_stats_habit_rank ON daily_stats (stat_date, habit_completed_count DESC, habit_streak_days DESC);

COMMENT ON TABLE  daily_stats              IS '每日用户统计表，用于排行榜和用户统计页展示';
COMMENT ON COLUMN daily_stats.stat_date     IS '统计日期';
COMMENT ON COLUMN daily_stats.habit_streak_days IS '截止当日的连续打卡天数';

-- ============================================
-- 8. 媒体资产
-- ============================================

CREATE TABLE media_assets (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    object_key      text        NOT NULL,       -- 云存储对象键
    content_type    text        NOT NULL
                    CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),       -- MIME 类型
    purpose         text        NOT NULL
                    CHECK (purpose IN ('meal_photo', 'event_photo', 'avatar')),
    file_size       bigint      NOT NULL CHECK (file_size > 0),       -- 文件大小（字节）
    width           int         NULL CHECK (width IS NULL OR width > 0),           -- 图片宽度（像素）
    height          int         NULL CHECK (height IS NULL OR height > 0),           -- 图片高度（像素）
    status          text        NOT NULL DEFAULT 'uploaded'
                    CHECK (status IN ('uploaded', 'deleted')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz NULL
);

CREATE INDEX idx_media_assets_user_id ON media_assets (user_id);
CREATE INDEX idx_media_assets_purpose ON media_assets (user_id, purpose);
CREATE UNIQUE INDEX uq_media_assets_object_key ON media_assets (object_key);

COMMENT ON TABLE  media_assets              IS '媒体资产表，存储图片元数据和状态';
COMMENT ON COLUMN media_assets.object_key   IS '云存储中的对象键';
COMMENT ON COLUMN media_assets.purpose      IS '用途：meal_photo 饮食图片 / event_photo 事件图片 / avatar 头像';
COMMENT ON COLUMN media_assets.status       IS '状态：uploaded 已上传 / deleted 已删除';

ALTER TABLE users
    ADD CONSTRAINT fk_users_avatar_asset
    FOREIGN KEY (avatar_asset_id) REFERENCES media_assets(id) ON DELETE SET NULL;

-- ============================================
-- 9. 饮食
-- ============================================

-- 9.1 饮食记录
CREATE TABLE meal_logs (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    meal_type       text        NOT NULL
                    CHECK (meal_type IN ('breakfast', 'lunch', 'dinner', 'snack')),
    occurred_at     timestamptz NOT NULL,       -- 用餐时间
    total_calories  numeric(10,2) NULL CHECK (total_calories IS NULL OR total_calories >= 0),         -- 总热量（各 meal_items 汇总）
    note            text        NULL,
    media_asset_id  uuid        NULL REFERENCES media_assets(id) ON DELETE SET NULL,  -- 关联图片
    is_ai_generated boolean     NOT NULL DEFAULT false,  -- 是否由 AI 识别生成
    ai_job_id       uuid        NULL,                   -- 关联的 AI 识别任务
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_meal_logs_user_time ON meal_logs (user_id, occurred_at);

COMMENT ON TABLE  meal_logs              IS '饮食记录表，包含一餐的整体信息';
COMMENT ON COLUMN meal_logs.meal_type    IS '餐别：breakfast 早餐 / lunch 午餐 / dinner 晚餐 / snack 加餐';

-- 9.2 饮食项目（每餐中的具体食物）
CREATE TABLE meal_items (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    meal_log_id     uuid        NOT NULL REFERENCES meal_logs(id) ON DELETE CASCADE,
    food_name       text        NOT NULL,           -- 食物名称
    estimated_grams numeric(10,2) NULL CHECK (estimated_grams IS NULL OR estimated_grams >= 0),             -- 估算重量（克）
    calories        numeric(10,2) NULL CHECK (calories IS NULL OR calories >= 0),             -- 热量（千卡）
    protein_grams   numeric(10,2) NULL CHECK (protein_grams IS NULL OR protein_grams >= 0),             -- 蛋白质（克）
    fat_grams       numeric(10,2) NULL CHECK (fat_grams IS NULL OR fat_grams >= 0),             -- 脂肪（克）
    carbs_grams     numeric(10,2) NULL CHECK (carbs_grams IS NULL OR carbs_grams >= 0),             -- 碳水化合物（克）
    confidence      numeric(4,3) NULL CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),              -- AI 识别置信度
    sort_order      int         NOT NULL DEFAULT 0, -- 排序
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_meal_items_meal_log ON meal_items (meal_log_id);

COMMENT ON TABLE  meal_items          IS '饮食项目表，一餐中的具体食物条目';
COMMENT ON COLUMN meal_items.confidence IS 'AI 识别置信度，0~1 之间';

-- ============================================
-- 10. 记账
-- ============================================

CREATE TABLE ledger_transactions (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            text        NOT NULL
                    CHECK (type IN ('income', 'expense')),
    category        text        NOT NULL,               -- 分类，如 food / transport / entertainment 等
    sub_category    text        NULL,                   -- 子分类
    amount          numeric(14,2) NOT NULL CHECK (amount > 0),             -- 金额
    currency        text        NOT NULL DEFAULT 'CNY', -- 币种
    occurred_at     timestamptz NOT NULL,               -- 交易时间
    description     text        NULL,                   -- 备注
    media_asset_id  uuid        NULL REFERENCES media_assets(id) ON DELETE SET NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz NULL
);

CREATE INDEX idx_ledger_transactions_user_time ON ledger_transactions (user_id, occurred_at);
CREATE INDEX idx_ledger_transactions_category  ON ledger_transactions (user_id, category);
CREATE INDEX idx_ledger_transactions_type      ON ledger_transactions (user_id, type);

COMMENT ON TABLE  ledger_transactions            IS '记账流水表';
COMMENT ON COLUMN ledger_transactions.type       IS '类型：income 收入 / expense 支出';
COMMENT ON COLUMN ledger_transactions.amount     IS '金额，精确到分';
COMMENT ON COLUMN ledger_transactions.currency   IS '币种，默认 CNY';

-- ============================================
-- 11. 重要事件
-- ============================================

CREATE TABLE event_logs (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           text        NOT NULL,
    description     text        NULL,
    event_date      date        NOT NULL,       -- 事件日期
    event_time      time        NULL,           -- 事件时间（可选）
    importance      int         NOT NULL DEFAULT 3
                    CHECK (importance BETWEEN 1 AND 5),  -- 重要程度 1~5
    category        text        NULL,           -- 分类，如 work / personal / health / travel 等
    media_asset_id  uuid        NULL REFERENCES media_assets(id) ON DELETE SET NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz NULL
);

CREATE INDEX idx_event_logs_user_date ON event_logs (user_id, event_date);
CREATE INDEX idx_event_logs_importance ON event_logs (user_id, importance);

COMMENT ON TABLE  event_logs            IS '重要事件记录表';
COMMENT ON COLUMN event_logs.importance IS '重要程度，1 最低，5 最高';

-- 12. AI
-- ============================================

-- 12.1 AI 分析任务
CREATE TABLE ai_analysis_jobs (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    job_type        text        NOT NULL
                    CHECK (job_type IN ('food_recognition', 'life_advice')),
    status          text        NOT NULL DEFAULT 'pending'
                    CHECK (status IN ('pending', 'processing', 'succeeded', 'failed')),
    media_asset_id  uuid        NULL REFERENCES media_assets(id) ON DELETE SET NULL,
    input_context   jsonb       NULL,       -- 任务输入上下文（如饮食识别传入 mealType）
    result          jsonb       NULL,       -- AI 结果 JSON
    error_message   text        NULL,       -- 失败原因
    confidence      numeric(4,3) NULL CHECK (confidence IS NULL OR (confidence >= 0 AND confidence <= 1)),      -- 整体置信度
    started_at      timestamptz NULL,       -- AI 开始处理时间
    completed_at    timestamptz NULL,       -- AI 完成处理时间
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_analysis_jobs_user_id   ON ai_analysis_jobs (user_id);
CREATE INDEX idx_ai_analysis_jobs_status    ON ai_analysis_jobs (user_id, status);

COMMENT ON TABLE  ai_analysis_jobs            IS 'AI 分析任务表，包括饮食识别和生活建议';
COMMENT ON COLUMN ai_analysis_jobs.job_type   IS '任务类型：food_recognition 饮食识别 / life_advice 生活建议';
COMMENT ON COLUMN ai_analysis_jobs.status     IS '状态：pending / processing / succeeded / failed';
COMMENT ON COLUMN ai_analysis_jobs.result     IS 'AI 输出结果 JSON';

-- 12.2 AI 对话会话
CREATE TABLE ai_chat_sessions (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title           text        NULL,       -- 对话标题（AI 自动生成）
    context_summary jsonb       NULL,       -- 对话时使用的用户数据摘要
    message_count   int         NOT NULL DEFAULT 0,
    is_deleted      boolean     NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz NULL
);

CREATE INDEX idx_ai_chat_sessions_user_id ON ai_chat_sessions (user_id);
CREATE INDEX idx_ai_chat_sessions_updated ON ai_chat_sessions (user_id, updated_at DESC);

COMMENT ON TABLE  ai_chat_sessions              IS 'AI 对话会话表';
COMMENT ON COLUMN ai_chat_sessions.context_summary IS '创建对话时的用户数据上下文摘要（jsonb）';

-- 12.3 AI 对话消息
CREATE TABLE ai_chat_messages (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id      uuid        NOT NULL REFERENCES ai_chat_sessions(id) ON DELETE CASCADE,
    role            text        NOT NULL
                    CHECK (role IN ('user', 'assistant', 'system')),
    content         text        NOT NULL,       -- 消息内容
    metadata        jsonb       NULL,           -- 附加元数据
    seq             int         NOT NULL,       -- 消息序号，用于排序
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_ai_chat_messages_session ON ai_chat_messages (session_id, seq);

COMMENT ON TABLE  ai_chat_messages         IS 'AI 对话消息表';
COMMENT ON COLUMN ai_chat_messages.role    IS '角色：user 用户 / assistant AI 助手 / system 系统';
COMMENT ON COLUMN ai_chat_messages.seq     IS '消息序号，用于按顺序展示';

ALTER TABLE meal_logs
    ADD CONSTRAINT fk_meal_logs_ai_job
    FOREIGN KEY (ai_job_id) REFERENCES ai_analysis_jobs(id) ON DELETE SET NULL;

-- ============================================
-- 初始化数据：隐私默认值
-- ============================================
-- 注意：新用户注册时，由应用层初始化 privacy_settings 默认值。
-- 此处仅作文档参考，不通过 SQL 硬编码默认值：
--   focus   -> friends_summary
--   habit   -> friends_summary
--   diet    -> private
--   ledger  -> private
--   event   -> private
--   media   -> private
--   ai_chat -> private

-- ============================================
-- 函数：自动更新 updated_at
-- ============================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 为所有需要自动更新 updated_at 的表创建触发器
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_refresh_tokens_updated_at
    BEFORE UPDATE ON refresh_tokens FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_devices_updated_at
    BEFORE UPDATE ON devices FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_privacy_settings_updated_at
    BEFORE UPDATE ON privacy_settings FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_server_versions_updated_at
    BEFORE UPDATE ON server_versions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_sync_mutations_updated_at
    BEFORE UPDATE ON sync_mutations FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_focus_sessions_updated_at
    BEFORE UPDATE ON focus_sessions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_habits_updated_at
    BEFORE UPDATE ON habits FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_habit_checkins_updated_at
    BEFORE UPDATE ON habit_checkins FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_friendships_updated_at
    BEFORE UPDATE ON friendships FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_daily_stats_updated_at
    BEFORE UPDATE ON daily_stats FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_meal_logs_updated_at
    BEFORE UPDATE ON meal_logs FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_meal_items_updated_at
    BEFORE UPDATE ON meal_items FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_ledger_transactions_updated_at
    BEFORE UPDATE ON ledger_transactions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_event_logs_updated_at
    BEFORE UPDATE ON event_logs FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_media_assets_updated_at
    BEFORE UPDATE ON media_assets FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_ai_analysis_jobs_updated_at
    BEFORE UPDATE ON ai_analysis_jobs FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_ai_chat_sessions_updated_at
    BEFORE UPDATE ON ai_chat_sessions FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_ai_chat_messages_updated_at
    BEFORE UPDATE ON ai_chat_messages FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
