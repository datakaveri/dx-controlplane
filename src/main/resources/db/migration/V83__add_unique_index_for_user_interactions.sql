-- Ensure there is only one interaction-state row
-- per user and asset, while allowing multiple
-- feedback submissions for the same user and asset.

CREATE UNIQUE INDEX uniq_user_asset_interaction
ON user_interactions (user_id, asset_id)
WHERE entity_rating IS NULL
  AND action_subtype IS NULL
  AND action_subdata IS NULL
  AND feedback_created_at IS NULL;