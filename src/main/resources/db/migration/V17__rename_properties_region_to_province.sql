ALTER TABLE properties RENAME COLUMN region TO province;

ALTER INDEX idx_properties_city_region RENAME TO idx_properties_city_province;
