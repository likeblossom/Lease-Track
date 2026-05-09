CREATE TABLE delivery_tracking_events (
    id UUID PRIMARY KEY,
    delivery_attempt_id UUID NOT NULL REFERENCES delivery_attempts(id),
    tracking_number VARCHAR(255) NOT NULL,
    status VARCHAR(255),
    status_code VARCHAR(255),
    delivered BOOLEAN NOT NULL,
    event_at TIMESTAMPTZ,
    checked_at TIMESTAMPTZ NOT NULL,
    error_message TEXT
);

CREATE INDEX idx_delivery_tracking_events_attempt_id
    ON delivery_tracking_events (delivery_attempt_id, checked_at);
