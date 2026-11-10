-- =====================================================
-- Feedback approval workflow
-- =====================================================

ALTER TABLE user_interactions
    ADD COLUMN IF NOT EXISTS feedback_status VARCHAR(20),
    ADD COLUMN IF NOT EXISTS feedback_comment TEXT,
    ADD COLUMN IF NOT EXISTS feedback_status_updated_at
        TIMESTAMP WITHOUT TIME ZONE;

-- =====================================================
-- Status constraint
-- =====================================================

ALTER TABLE user_interactions
    DROP CONSTRAINT IF EXISTS chk_feedback_status;

ALTER TABLE user_interactions
    ADD CONSTRAINT chk_feedback_status
    CHECK (
        feedback_status IS NULL
        OR feedback_status IN (
            'PENDING',
            'APPROVED',
            'REJECTED'
        )
    );


-- =====================================================
-- Indexes
-- =====================================================

CREATE INDEX IF NOT EXISTS idx_user_interactions_feedback_status
    ON user_interactions(feedback_status);

CREATE INDEX IF NOT EXISTS idx_user_interactions_user_feedback_status
    ON user_interactions(user_id, feedback_status);

CREATE INDEX IF NOT EXISTS idx_user_interactions_asset_feedback_status
    ON user_interactions(asset_id, feedback_status);