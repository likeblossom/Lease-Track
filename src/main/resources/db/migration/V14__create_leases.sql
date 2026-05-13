CREATE TABLE leases (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    property_address VARCHAR(512) NOT NULL,
    tenant_names TEXT NOT NULL,
    tenant_contact_info TEXT,
    lease_start_date DATE NOT NULL,
    lease_end_date DATE NOT NULL,
    owner_user_id UUID NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

ALTER TABLE notices ADD COLUMN lease_id UUID;

ALTER TABLE notices
    ADD CONSTRAINT fk_notices_lease
    FOREIGN KEY (lease_id) REFERENCES leases (id) ON DELETE SET NULL;

CREATE INDEX idx_leases_owner_user_id ON leases (owner_user_id);
CREATE INDEX idx_leases_lease_period ON leases (lease_start_date, lease_end_date);
CREATE INDEX idx_notices_lease_id ON notices (lease_id);
