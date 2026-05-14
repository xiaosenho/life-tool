-- Keep runtime schema changes in Flyway instead of application constructors.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'sync_mutations'
          AND column_name = 'entity_id'
          AND data_type <> 'text'
    ) THEN
        ALTER TABLE sync_mutations
            ALTER COLUMN entity_id TYPE text USING entity_id::text;
    END IF;
END $$;

ALTER TABLE focus_sessions
    ALTER COLUMN status SET DEFAULT 'running';

ALTER TABLE focus_sessions
    DROP CONSTRAINT IF EXISTS focus_sessions_status_check;

ALTER TABLE focus_sessions
    ADD CONSTRAINT focus_sessions_status_check
    CHECK (status IN ('running', 'completed', 'interrupted', 'abandoned'));

COMMENT ON COLUMN focus_sessions.status IS '状态：running 进行中 / completed 完成 / interrupted 中断 / abandoned 放弃';

ALTER TABLE friendships
    DROP CONSTRAINT IF EXISTS friendships_status_check;

ALTER TABLE friendships
    ADD CONSTRAINT friendships_status_check
    CHECK (status IN ('pending', 'accepted', 'rejected', 'blocked', 'deleted'));

COMMENT ON COLUMN friendships.status IS '状态：pending 待确认 / accepted 已通过 / rejected 已拒绝 / blocked 已屏蔽 / deleted 已删除';
