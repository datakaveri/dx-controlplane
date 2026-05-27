-- Make organization_id nullable — platform providers do not belong to any organisation
ALTER TABLE provider_requests
  ALTER COLUMN organization_id DROP NOT NULL;

-- Drop the FK that forces user_id to exist in organization_users
-- Platform providers are never in that table
ALTER TABLE provider_requests
  DROP CONSTRAINT IF EXISTS provider_requests_user_id_fkey;

-- Add provider_type discriminator column
ALTER TABLE provider_requests
  ADD COLUMN provider_type VARCHAR NOT NULL DEFAULT 'org'
    CHECK (provider_type IN ('org', 'platform'));

-- Backfill all existing rows
UPDATE provider_requests
  SET provider_type = 'org'
  WHERE organization_id IS NOT NULL;