ALTER TABLE media_assets
    DROP CONSTRAINT IF EXISTS media_assets_content_type_check;

ALTER TABLE media_assets
    ADD CONSTRAINT media_assets_content_type_check
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp', 'audio/m4a', 'audio/mp4', 'audio/mpeg', 'audio/mp3', 'audio/wav'));

ALTER TABLE media_assets
    DROP CONSTRAINT IF EXISTS media_assets_purpose_check;

ALTER TABLE media_assets
    ADD CONSTRAINT media_assets_purpose_check
        CHECK (purpose IN ('meal_photo', 'event_photo', 'avatar', 'chat_image', 'chat_audio'));

ALTER TABLE friend_messages
    DROP CONSTRAINT IF EXISTS friend_messages_type_check;

ALTER TABLE friend_messages
    ADD COLUMN IF NOT EXISTS metadata jsonb NULL;

ALTER TABLE friend_messages
    ADD CONSTRAINT friend_messages_type_check
        CHECK (message_type IN ('text', 'cheer', 'celebrate', 'hug', 'coffee', 'poke', 'image', 'audio'));
