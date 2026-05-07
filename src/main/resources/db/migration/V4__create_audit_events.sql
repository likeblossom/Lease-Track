CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    notice_id UUID NOT NULL,
    delivery_attempt_id UUID,
    event_type VARCHAR(64) NOT NULL,
    actor_role VARCHAR(64) NOT NULL,
    actor_reference VARCHAR(255),
    details JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_audit_events_notice
        FOREIGN KEY (notice_id) REFERENCES notices (id) ON DELETE CASCADE,
    CONSTRAINT fk_audit_events_attempt
        FOREIGN KEY (delivery_attempt_id) REFERENCES delivery_attempts (id) ON DELETE SET NULL
);

CREATE INDEX idx_audit_events_notice_id_created_at ON audit_events (notice_id, created_at);
CREATE INDEX idx_audit_events_attempt_id_created_at ON audit_events (delivery_attempt_id, created_at);
CREATE INDEX idx_audit_events_event_type ON audit_events (event_type);
