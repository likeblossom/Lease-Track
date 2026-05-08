CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    role VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

INSERT INTO users (id, email, password_hash, display_name, role, enabled, created_at) VALUES
('11111111-1111-1111-1111-111111111111', 'landlord@leasetrack.dev', '$2a$10$XTO81aOGCEDkuXi8nUUd0OPfVpALu3884RFazRCIOFCxBJ7YFFup6', 'Demo Landlord', 'LANDLORD', true, now()),
('22222222-2222-2222-2222-222222222222', 'manager@leasetrack.dev', '$2a$10$XTO81aOGCEDkuXi8nUUd0OPfVpALu3884RFazRCIOFCxBJ7YFFup6', 'Demo Property Manager', 'PROPERTY_MANAGER', true, now()),
('33333333-3333-3333-3333-333333333333', 'tenant@leasetrack.dev', '$2a$10$XTO81aOGCEDkuXi8nUUd0OPfVpALu3884RFazRCIOFCxBJ7YFFup6', 'Demo Tenant', 'TENANT', true, now()),
('44444444-4444-4444-4444-444444444444', 'admin@leasetrack.dev', '$2a$10$XTO81aOGCEDkuXi8nUUd0OPfVpALu3884RFazRCIOFCxBJ7YFFup6', 'Demo Admin', 'ADMIN', true, now());
