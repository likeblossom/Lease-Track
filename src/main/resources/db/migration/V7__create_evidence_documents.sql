CREATE TABLE evidence_documents (
    id UUID PRIMARY KEY,
    notice_id UUID NOT NULL REFERENCES notices(id),
    delivery_attempt_id UUID NOT NULL REFERENCES delivery_attempts(id),
    delivery_evidence_id UUID NOT NULL REFERENCES delivery_evidence(id),
    document_type VARCHAR(64) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(255),
    size_bytes BIGINT NOT NULL,
    storage_provider VARCHAR(64) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    sha256_checksum VARCHAR(64) NOT NULL,
    uploaded_by_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_evidence_documents_notice_id
    ON evidence_documents (notice_id, created_at);

CREATE INDEX idx_evidence_documents_delivery_attempt_id
    ON evidence_documents (delivery_attempt_id, created_at);
