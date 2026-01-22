-- =====================================================
-- Description: Create user_interactions table
-- =====================================================

-- Required for gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =====================================================
-- Table
-- =====================================================
CREATE TABLE IF NOT EXISTS user_interactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL,
    entity_id UUID NOT NULL,
    entity_type TEXT NOT NULL,

    action_type TEXT NOT NULL,
    value TEXT NOT NULL,

    created_at timestamp without time zone NOT NULL DEFAULT now(),
    updated_at timestamp without time zone NOT NULL DEFAULT now(),

    -- One interaction per type per user per entity
    CONSTRAINT uniq_user_entity_action
    UNIQUE (user_id, entity_id, action_type),

    -- Only allow valid action types
    CONSTRAINT chk_action_type
    CHECK (action_type IN ('VOTE', 'BOOKMARK')),

    -- Only allow valid values depending on action_type
    CONSTRAINT chk_action_value
    CHECK (
    (action_type = 'VOTE' AND value IN ('LIKE', 'DISLIKE'))
    OR (action_type = 'BOOKMARK' AND value = 'ADD')
    )
    );

-- =====================================================
-- Trigger function (must exist before trigger)
-- =====================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;


-- =====================================================
-- Trigger (assumes function already exists globally)
-- =====================================================
DROP TRIGGER IF EXISTS trg_user_interactions_updated_at ON user_interactions;

CREATE TRIGGER trg_user_interactions_updated_at
    BEFORE UPDATE ON user_interactions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- Indexes
-- =====================================================

-- Fast lookup for GET interaction API
CREATE INDEX IF NOT EXISTS idx_user_interactions_user_entity
    ON user_interactions(user_id, entity_id);

-- Fast UPSERT for updates
CREATE INDEX IF NOT EXISTS idx_user_interactions_user_entity_action
    ON user_interactions(user_id, entity_id, action_type);

-- =====================================================
-- Permissions
-- =====================================================
GRANT SELECT, INSERT, UPDATE, DELETE
    ON TABLE user_interactions
    TO ${authUser};
