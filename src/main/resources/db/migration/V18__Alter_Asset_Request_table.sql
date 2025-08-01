ALTER TABLE asset_request
DROP CONSTRAINT IF EXISTS asset_request_user_id_key,
DROP CONSTRAINT IF EXISTS asset_request_asset_id_key,
ADD CONSTRAINT asset_request_user_asset_unique UNIQUE (user_id, asset_id);
