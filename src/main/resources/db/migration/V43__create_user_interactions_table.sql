-- =====================================================
-- Description: Create new_user_interactions table
-- =====================================================

-- Required for gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =====================================================
-- Table
-- =====================================================
CREATE TABLE IF NOT EXISTS user_interactions (
   id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL,
    asset_id UUID NOT NULL,
    asset_type TEXT NOT NULL,

    is_liked BOOLEAN NOT NULL DEFAULT FALSE,
    is_disliked BOOLEAN NOT NULL DEFAULT FALSE,
    is_bookmarked BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),

    -- One row per user per entity
    CONSTRAINT uniq_user_asset
    UNIQUE (user_id, asset_id),

    -- Like and dislike cannot both be true
    CONSTRAINT chk_like_dislike
    CHECK (NOT (is_liked AND is_disliked))
    );

-- =====================================================
-- Trigger function
-- =====================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =====================================================
-- Trigger
-- =====================================================
DROP TRIGGER IF EXISTS trg_user_interactions_updated_at ON user_interactions;

CREATE TRIGGER trg_user_interactions_updated_at
    BEFORE UPDATE ON user_interactions
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- =====================================================
-- Indexes
-- =====================================================

-- Fast lookup for user + entity
CREATE INDEX IF NOT EXISTS idx_user_interactions_user_entity
    ON user_interactions(user_id, asset_id);

-- Useful for "my bookmarks" tab
CREATE INDEX IF NOT EXISTS idx_user_interactions_bookmarked
    ON user_interactions(user_id)
    WHERE is_bookmarked = TRUE;

-- Useful for analytics (optional)
CREATE INDEX IF NOT EXISTS idx_user_interactions_liked
    ON user_interactions(asset_id)
    WHERE is_liked = TRUE;

-- =====================================================
-- Permissions
-- =====================================================
GRANT SELECT, INSERT, UPDATE, DELETE
    ON TABLE user_interactions
    TO ${authUser};
