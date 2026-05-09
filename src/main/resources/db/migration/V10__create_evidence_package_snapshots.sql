CREATE TABLE evidence_package_snapshots (
    id UUID PRIMARY KEY,
    notice_id UUID NOT NULL REFERENCES notices(id),
    package_version VARCHAR(32) NOT NULL,
    package_hash VARCHAR(64) NOT NULL,
    generated_by_user_id UUID NOT NULL REFERENCES users(id),
    generated_at TIMESTAMPTZ NOT NULL,
    package_json JSONB NOT NULL
);

CREATE INDEX idx_evidence_package_snapshots_notice_id
    ON evidence_package_snapshots (notice_id, generated_at);

CREATE INDEX idx_evidence_package_snapshots_package_hash
    ON evidence_package_snapshots (package_hash);
