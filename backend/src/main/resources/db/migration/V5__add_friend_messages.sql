CREATE TABLE IF NOT EXISTS friend_messages (
    id              uuid PRIMARY KEY,
    from_user_id    uuid        NOT NULL REFERENCES users(id),
    to_user_id      uuid        NOT NULL REFERENCES users(id),
    message_type    text        NOT NULL,
    content         text        NOT NULL,
    read_at         timestamptz NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    deleted_at      timestamptz NULL,
    CONSTRAINT friend_messages_type_check
        CHECK (message_type IN ('text', 'cheer'))
);

CREATE INDEX IF NOT EXISTS idx_friend_messages_to_user_created
    ON friend_messages (to_user_id, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_friend_messages_pair_created
    ON friend_messages (from_user_id, to_user_id, created_at DESC)
    WHERE deleted_at IS NULL;
