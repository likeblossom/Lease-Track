ALTER TABLE leases ADD COLUMN rent_cents BIGINT;
ALTER TABLE leases ADD COLUMN security_deposit_cents BIGINT;
ALTER TABLE leases ADD COLUMN status VARCHAR(64);
ALTER TABLE leases ADD COLUMN renewal_decision_due_date DATE;
ALTER TABLE leases ADD COLUMN terminated_at TIMESTAMPTZ;

UPDATE leases
SET rent_cents = 0
WHERE rent_cents IS NULL;

UPDATE leases
SET status = CASE
    WHEN lease_end_date < CURRENT_DATE THEN 'EXPIRED'
    ELSE 'ACTIVE'
END
WHERE status IS NULL;

ALTER TABLE leases ALTER COLUMN rent_cents SET NOT NULL;
ALTER TABLE leases ALTER COLUMN status SET NOT NULL;

CREATE TABLE lease_events (
    id UUID PRIMARY KEY,
    lease_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    actor_role VARCHAR(64) NOT NULL,
    actor_reference VARCHAR(255) NOT NULL,
    details JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_lease_events_lease
        FOREIGN KEY (lease_id) REFERENCES leases (id) ON DELETE CASCADE
);

CREATE INDEX idx_leases_status ON leases (status);
CREATE INDEX idx_leases_renewal_due ON leases (renewal_decision_due_date);
CREATE INDEX idx_leases_end_date_status ON leases (lease_end_date, status);
CREATE INDEX idx_lease_events_lease_id_created_at ON lease_events (lease_id, created_at);
CREATE INDEX idx_lease_events_event_type ON lease_events (event_type);
