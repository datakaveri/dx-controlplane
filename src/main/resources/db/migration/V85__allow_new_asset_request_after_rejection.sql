-- A user whose asset request was rejected may raise a fresh request for the same asset,
-- stored as a new row (the rejected row is kept as history). Only one active
-- (pending or granted) request is allowed per user and asset.

ALTER TABLE asset_request
DROP CONSTRAINT IF EXISTS asset_request_user_asset_unique;

CREATE UNIQUE INDEX IF NOT EXISTS uniq_active_asset_request_per_user
ON asset_request (user_id, asset_id)
WHERE status IN ('pending', 'granted');
