CREATE EXTENSION IF NOT EXISTS pgcrypto;

ALTER TABLE shared_asset_visibility
    ALTER COLUMN id
        SET DEFAULT gen_random_uuid();