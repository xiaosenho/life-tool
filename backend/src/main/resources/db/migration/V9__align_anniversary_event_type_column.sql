ALTER TABLE anniversary_events
    ADD COLUMN IF NOT EXISTS type text;

UPDATE anniversary_events
SET type = event_type
WHERE type IS NULL
  AND event_type IS NOT NULL;

ALTER TABLE anniversary_events
    ALTER COLUMN type SET NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'anniversary_events'
          AND column_name = 'event_type'
    ) THEN
        ALTER TABLE anniversary_events DROP CONSTRAINT IF EXISTS anniversary_events_event_type_check;
        DROP INDEX IF EXISTS idx_anniversary_events_user_type;
        ALTER TABLE anniversary_events DROP COLUMN event_type;
    END IF;
END $$;

ALTER TABLE anniversary_events
    ADD CONSTRAINT anniversary_events_type_check
        CHECK (type IN ('anniversary', 'birthday', 'important_day', 'todo_reminder'));

CREATE INDEX IF NOT EXISTS idx_anniversary_events_user_type ON anniversary_events (user_id, type);

COMMENT ON COLUMN anniversary_events.type IS '事件类型：anniversary 纪念日 / birthday 生日 / important_day 重要日期 / todo_reminder 待办提醒';
