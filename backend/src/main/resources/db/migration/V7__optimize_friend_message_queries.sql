CREATE INDEX IF NOT EXISTS idx_friend_messages_user_conversation_latest
    ON friend_messages (
        from_user_id,
        to_user_id,
        created_at DESC
    )
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_friend_messages_user_inbox_unread
    ON friend_messages (
        to_user_id,
        from_user_id,
        created_at DESC
    )
    WHERE deleted_at IS NULL AND read_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_friendships_active_user_pair
    ON friendships (
        requester_id,
        addressee_id
    )
    WHERE deleted_at IS NULL AND status IN ('accepted', 'pending');

CREATE INDEX IF NOT EXISTS idx_friendships_active_addressee_pair
    ON friendships (
        addressee_id,
        requester_id
    )
    WHERE deleted_at IS NULL AND status IN ('accepted', 'pending');
