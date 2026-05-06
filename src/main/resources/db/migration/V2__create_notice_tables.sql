CREATE TABLE notices (
    id UUID PRIMARY KEY,
    recipient_name VARCHAR(255) NOT NULL,
    recipient_contact_info VARCHAR(255) NOT NULL,
    notice_type VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    owner_user_id UUID NOT NULL,
    tenant_user_id UUID,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ
);

CREATE TABLE delivery_attempts (
    id UUID PRIMARY KEY,
    notice_id UUID NOT NULL,
    attempt_number INT NOT NULL,
    delivery_method VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    sent_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    deadline_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_delivery_attempts_notice
        FOREIGN KEY (notice_id) REFERENCES notices (id) ON DELETE CASCADE,
    CONSTRAINT uk_delivery_attempts_notice_attempt
        UNIQUE (notice_id, attempt_number)
);

CREATE INDEX idx_notices_status ON notices (status);
CREATE INDEX idx_notices_notice_type ON notices (notice_type);
CREATE INDEX idx_notices_created_at ON notices (created_at);
CREATE INDEX idx_delivery_attempts_notice_id ON delivery_attempts (notice_id);
CREATE INDEX idx_delivery_attempts_status ON delivery_attempts (status);
CREATE INDEX idx_delivery_attempts_deadline_at ON delivery_attempts (deadline_at);
CREATE INDEX idx_delivery_attempts_delivery_method ON delivery_attempts (delivery_method);
