-- ============================================
-- LifeTool V2 迁移：专注偏好、月度预算、纪念日
-- PostgreSQL 16 + pgcrypto
-- Flyway 迁移：V2__add_focus_preferences_budgets_anniversaries.sql
-- ============================================

-- ============================================
-- 1. 专注偏好
-- ============================================

CREATE TABLE focus_preferences (
    id                    uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id               uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    default_focus_minutes int         NOT NULL DEFAULT 25 CHECK (default_focus_minutes BETWEEN 1 AND 180),
    short_break_minutes   int         NOT NULL DEFAULT 5 CHECK (short_break_minutes BETWEEN 0 AND 60),
    long_break_minutes    int         NOT NULL DEFAULT 15 CHECK (long_break_minutes BETWEEN 0 AND 60),
    auto_start_break      boolean     NOT NULL DEFAULT false,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_focus_preferences_user ON focus_preferences (user_id);

COMMENT ON TABLE  focus_preferences                 IS '专注偏好表，每个用户一条偏好配置';
COMMENT ON COLUMN focus_preferences.default_focus_minutes IS '默认专注时长（分钟），1~180';
COMMENT ON COLUMN focus_preferences.short_break_minutes   IS '短休息时长（分钟），0~60';
COMMENT ON COLUMN focus_preferences.long_break_minutes    IS '长休息时长（分钟），0~60';
COMMENT ON COLUMN focus_preferences.auto_start_break      IS '是否自动开始休息';

-- ============================================
-- 2. 月度预算
-- ============================================

CREATE TABLE ledger_budgets (
    id              uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    budget_month    date          NOT NULL CHECK (budget_month = date_trunc('month', budget_month)::date),
                                                  -- 预算月份，存储当月第一天，如 '2026-05-01'
    category        text          NULL,           -- NULL 表示整月总预算，非 NULL 表示分类预算
    amount          numeric(14,2) NOT NULL CHECK (amount >= 0),
    currency        text          NOT NULL DEFAULT 'CNY',
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    deleted_at      timestamptz   NULL
);

-- 分类预算唯一约束：(user_id, budget_month, category) 且 category IS NOT NULL
CREATE UNIQUE INDEX uq_ledger_budgets_category
    ON ledger_budgets (user_id, budget_month, category)
    WHERE category IS NOT NULL AND deleted_at IS NULL;

-- 总预算唯一约束：(user_id, budget_month) 且 category IS NULL
CREATE UNIQUE INDEX uq_ledger_budgets_total
    ON ledger_budgets (user_id, budget_month)
    WHERE category IS NULL AND deleted_at IS NULL;

CREATE INDEX idx_ledger_budgets_user_month ON ledger_budgets (user_id, budget_month);

COMMENT ON TABLE  ledger_budgets              IS '月度预算表，支持总预算和分类预算';
COMMENT ON COLUMN ledger_budgets.budget_month IS '预算月份，存储当月第一天日期';
COMMENT ON COLUMN ledger_budgets.category     IS '分类预算标识，NULL 表示整月总预算';
COMMENT ON COLUMN ledger_budgets.amount       IS '预算金额';
COMMENT ON COLUMN ledger_budgets.currency     IS '币种，默认 CNY';

-- ============================================
-- 3. 纪念日与重要事件
-- ============================================

CREATE TABLE anniversary_events (
    id                  uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    event_type          text        NOT NULL
                        CHECK (event_type IN ('anniversary', 'birthday', 'important_day', 'todo_reminder')),
    title               text        NOT NULL,
    event_date          date        NOT NULL,
    repeat_rule         text        NOT NULL DEFAULT 'none'
                        CHECK (repeat_rule IN ('none', 'yearly', 'monthly', 'weekly')),
    remind_days_before  jsonb       NOT NULL DEFAULT '[]'::jsonb
                        CHECK (jsonb_typeof(remind_days_before) = 'array'),
                        -- 提前提醒天数数组，如 [1, 3, 7]
    note                text        NULL,
    media_asset_id      uuid        NULL REFERENCES media_assets(id) ON DELETE SET NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    deleted_at          timestamptz NULL
);

CREATE INDEX idx_anniversary_events_user_date ON anniversary_events (user_id, event_date);
CREATE INDEX idx_anniversary_events_user_type ON anniversary_events (user_id, event_type);

COMMENT ON TABLE  anniversary_events                  IS '纪念日与重要事件表';
COMMENT ON COLUMN anniversary_events.event_type       IS '事件类型：anniversary 纪念日 / birthday 生日 / important_day 重要日期 / todo_reminder 待办提醒';
COMMENT ON COLUMN anniversary_events.event_date       IS '原始事件日期';
COMMENT ON COLUMN anniversary_events.repeat_rule      IS '重复规则：none 不重复 / yearly 每年 / monthly 每月 / weekly 每周';
COMMENT ON COLUMN anniversary_events.remind_days_before IS '提前提醒天数数组，如 [1, 3, 7]，客户端调度本地通知';

-- ============================================
-- 4. 新表 updated_at 触发器
-- ============================================

CREATE TRIGGER trg_focus_preferences_updated_at
    BEFORE UPDATE ON focus_preferences FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_ledger_budgets_updated_at
    BEFORE UPDATE ON ledger_budgets FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER trg_anniversary_events_updated_at
    BEFORE UPDATE ON anniversary_events FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
