ALTER TABLE delivery_tracking_events
    ADD COLUMN event_key VARCHAR(512);

CREATE UNIQUE INDEX uk_delivery_tracking_events_attempt_event_key
    ON delivery_tracking_events (delivery_attempt_id, event_key)
    WHERE event_key IS NOT NULL;
