ALTER TABLE delivery_attempts
    ADD COLUMN tracking_sync_status VARCHAR(64),
    ADD COLUMN last_tracking_checked_at TIMESTAMPTZ,
    ADD COLUMN deadline_reminder_sent BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE delivery_evidence
    ADD COLUMN latest_tracking_status VARCHAR(255),
    ADD COLUMN latest_tracking_status_code VARCHAR(255),
    ADD COLUMN latest_tracking_event_at TIMESTAMPTZ;

CREATE INDEX idx_delivery_attempts_deadline_reminder
    ON delivery_attempts (deadline_at, deadline_reminder_sent);

CREATE INDEX idx_delivery_attempts_tracking_sync
    ON delivery_attempts (delivery_method, last_tracking_checked_at);
