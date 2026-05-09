ALTER TABLE delivery_attempts
    ADD COLUMN tracking_next_check_at TIMESTAMPTZ;

ALTER TABLE delivery_evidence
    ADD COLUMN carrier_code VARCHAR(64),
    ADD COLUMN latest_tracking_raw_payload TEXT,
    ADD COLUMN latest_tracking_provider_error TEXT;

UPDATE delivery_evidence
SET carrier_code = CASE
    WHEN lower(replace(replace(carrier_name, '_', '-'), ' ', '-')) IN ('canada-post', 'canadapost', 'postes-canada', 'postescanada', 'cpc') THEN 'canada-post'
    WHEN lower(replace(replace(carrier_name, '_', '-'), ' ', '-')) IN ('fedex', 'fed-ex', 'federal-express') THEN 'fedex'
    WHEN lower(carrier_name) = 'mock' THEN 'mock'
    ELSE lower(replace(replace(carrier_name, '_', '-'), ' ', '-'))
END
WHERE carrier_name IS NOT NULL;

CREATE INDEX idx_delivery_attempts_tracking_next_check_at ON delivery_attempts (tracking_next_check_at);
CREATE INDEX idx_delivery_evidence_carrier_code_tracking_number ON delivery_evidence (carrier_code, tracking_number);
