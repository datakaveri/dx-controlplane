-- provider_feedback (V55) never got updated_at populated: no DEFAULT and no
-- trigger, unlike the sibling user_interactions table (see V43). It also had no
-- DB constraint stopping duplicate rows for the same (asset_id, type) under
-- concurrent requests, since the app matched on (user_id, asset_id, type) via a
-- racy SELECT-then-insert/update. Fixing both here in one migration since
-- provider_feedback is a new, unreleased feature.

-- feedback_type_enum (V55) never got SUGGESTION added even though the Java enum
-- and API spec have always included it as a valid provider feedback type.
ALTER TYPE feedback_type_enum ADD VALUE IF NOT EXISTS 'SUGGESTION';

-- Backfill existing rows so updated_at matches created_at instead of staying NULL.
UPDATE provider_feedback SET updated_at = created_at WHERE updated_at IS NULL;

ALTER TABLE provider_feedback ALTER COLUMN updated_at SET DEFAULT now();
ALTER TABLE provider_feedback ALTER COLUMN updated_at SET NOT NULL;

-- Reuses update_updated_at_column(), created in V43__create_user_interactions_table.sql
DROP TRIGGER IF EXISTS trg_provider_feedback_updated_at ON provider_feedback;

CREATE TRIGGER trg_provider_feedback_updated_at
    BEFORE UPDATE ON provider_feedback
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- provider_feedback must have exactly one row per (asset_id, type). id stays the
-- table's primary key unchanged; this adds a separate UNIQUE constraint on
-- (asset_id, type) so ProviderFeedbackDaoImpl can use an atomic
-- INSERT ... ON CONFLICT (asset_id, type) DO UPDATE upsert (ON CONFLICT only needs
-- a unique constraint on the target columns, not for them to be the primary key).

-- Dedup: keep only the most recently updated row per (asset_id, type).
DELETE FROM provider_feedback pf
USING provider_feedback newer
WHERE pf.asset_id = newer.asset_id
  AND pf.type = newer.type
  AND (pf.updated_at, pf.id) < (newer.updated_at, newer.id);

ALTER TABLE provider_feedback ADD CONSTRAINT uq_provider_feedback_asset_type UNIQUE (asset_id, type);
