CREATE TABLE properties (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address_line1 VARCHAR(255) NOT NULL,
    address_line2 VARCHAR(255),
    city VARCHAR(120) NOT NULL,
    region VARCHAR(120) NOT NULL,
    postal_code VARCHAR(40) NOT NULL,
    country VARCHAR(120) NOT NULL DEFAULT 'Canada',
    owner_user_id UUID NOT NULL,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE property_units (
    id UUID PRIMARY KEY,
    property_id UUID NOT NULL,
    unit_label VARCHAR(80) NOT NULL,
    status VARCHAR(64) NOT NULL,
    bedrooms INTEGER,
    bathrooms NUMERIC(3,1),
    square_feet INTEGER,
    current_tenant_names TEXT,
    current_rent_cents BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_property_units_property
        FOREIGN KEY (property_id) REFERENCES properties (id) ON DELETE CASCADE,
    CONSTRAINT uq_property_units_property_label UNIQUE (property_id, unit_label)
);

ALTER TABLE leases ADD COLUMN unit_id UUID;

ALTER TABLE leases
    ADD CONSTRAINT fk_leases_unit
    FOREIGN KEY (unit_id) REFERENCES property_units (id) ON DELETE SET NULL;

CREATE INDEX idx_properties_owner_user_id ON properties (owner_user_id);
CREATE INDEX idx_properties_city_region ON properties (city, region);
CREATE INDEX idx_property_units_property_id ON property_units (property_id);
CREATE INDEX idx_property_units_status ON property_units (status);
CREATE INDEX idx_leases_unit_id ON leases (unit_id);
