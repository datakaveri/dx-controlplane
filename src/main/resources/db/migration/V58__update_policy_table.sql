-- =============================================
-- Migration: userId + item organization policy schema update
-- =============================================

SET search_path TO ${flyway:defaultSchema};

------------------------------------------------
-- 1. Add new columns
------------------------------------------------

ALTER TABLE policy
ADD COLUMN IF NOT EXISTS consumer_id uuid;

ALTER TABLE policy
ADD COLUMN IF NOT EXISTS item_organization_id uuid;

------------------------------------------------
-- 2. Backfill consumer_id using email mapping
------------------------------------------------

UPDATE policy p
SET consumer_id = rq.consumer_id
FROM request rq
WHERE p.user_emailid = rq.consumer_email_id
  AND p.consumer_id IS NULL;

------------------------------------------------
-- 3. Backfill item_organization_id from owner_id
------------------------------------------------

UPDATE policy p
SET item_organization_id = ou.organization_id
FROM organization_users ou
WHERE p.owner_id = ou.user_id
  AND p.item_organization_id IS NULL;

------------------------------------------------
-- 4. Drop old email column
------------------------------------------------

ALTER TABLE policy
DROP COLUMN IF EXISTS user_emailid;

------------------------------------------------
-- 5. Create indexes
------------------------------------------------

CREATE INDEX IF NOT EXISTS idx_policy_consumer
ON policy (consumer_id);

CREATE INDEX IF NOT EXISTS idx_policy_owner
ON policy (owner_id);

CREATE INDEX IF NOT EXISTS idx_policy_item_org
ON policy (item_organization_id);