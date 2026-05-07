CREATE TABLE delivery_evidence (
    id UUID PRIMARY KEY,
    delivery_attempt_id UUID NOT NULL UNIQUE,
    tracking_number VARCHAR(255),
    carrier_name VARCHAR(255),
    carrier_receipt_ref VARCHAR(255),
    delivery_confirmation BOOLEAN,
    delivery_confirmation_metadata TEXT,
    signed_acknowledgement_ref VARCHAR(255),
    email_acknowledgement_ref VARCHAR(255),
    email_acknowledgement_metadata TEXT,
    bailiff_affidavit_ref VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_delivery_evidence_attempt
        FOREIGN KEY (delivery_attempt_id) REFERENCES delivery_attempts (id) ON DELETE CASCADE
);

CREATE INDEX idx_delivery_evidence_tracking_number ON delivery_evidence (tracking_number);
