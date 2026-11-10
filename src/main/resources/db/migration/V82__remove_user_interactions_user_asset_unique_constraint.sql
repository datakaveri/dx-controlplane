-- =====================================================
-- Description: Allow multiple user feedback submissions
-- =====================================================

-- Remove the one-row-per-user-per-asset constraint.
-- This allows a user to submit feedback again after
-- their previous feedback was rejected.
ALTER TABLE user_interactions
DROP CONSTRAINT IF EXISTS uniq_user_asset;