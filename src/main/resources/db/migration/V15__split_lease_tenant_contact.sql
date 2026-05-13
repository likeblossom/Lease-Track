ALTER TABLE leases ADD COLUMN tenant_email VARCHAR(255);
ALTER TABLE leases ADD COLUMN tenant_phone VARCHAR(64);

UPDATE leases
SET tenant_email = tenant_contact_info
WHERE tenant_contact_info LIKE '%@%';

UPDATE leases
SET tenant_phone = tenant_contact_info
WHERE tenant_contact_info IS NOT NULL
  AND tenant_contact_info NOT LIKE '%@%';
