CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

ALTER TABLE shared_asset_visibility
ALTER COLUMN id
SET DEFAULT uuid_generate_v4();