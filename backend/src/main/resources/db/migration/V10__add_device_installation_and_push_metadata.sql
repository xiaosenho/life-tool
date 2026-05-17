ALTER TABLE devices
    ADD COLUMN IF NOT EXISTS installation_id text,
    ADD COLUMN IF NOT EXISTS vendor_device_id text,
    ADD COLUMN IF NOT EXISTS push_provider text,
    ADD COLUMN IF NOT EXISTS push_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS push_bound_at timestamptz,
    ADD COLUMN IF NOT EXISTS metadata jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE devices
    ADD CONSTRAINT chk_devices_push_provider
    CHECK (push_provider IS NULL OR push_provider IN ('aliyun', 'fcm', 'apns', 'none'));

CREATE UNIQUE INDEX IF NOT EXISTS uq_devices_installation_id
    ON devices (installation_id)
    WHERE installation_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_devices_vendor_device_id
    ON devices (vendor_device_id)
    WHERE vendor_device_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_devices_push_enabled
    ON devices (user_id, push_enabled);
