-- Drop constraints first
ALTER TABLE user_interactions
DROP CONSTRAINT IF EXISTS chk_entity_rating_range,
  DROP CONSTRAINT IF EXISTS chk_subtype_requires_data;

-- Drop the inline check on entity_rating column
ALTER TABLE user_interactions
DROP CONSTRAINT IF EXISTS user_interactions_entity_rating_check;

-- Drop columns
ALTER TABLE user_interactions
DROP COLUMN IF EXISTS action_subtype,
  DROP COLUMN IF EXISTS action_subdata,
  DROP COLUMN IF EXISTS entity_rating;

-- Re-add columns
ALTER TABLE user_interactions
  ADD COLUMN action_subtype VARCHAR,
  ADD COLUMN action_subdata JSONB,
  ADD COLUMN entity_rating INT CHECK (entity_rating BETWEEN 1 AND 5);

-- Re-add constraints
ALTER TABLE user_interactions
  ADD CONSTRAINT chk_entity_rating_range
    CHECK (
      entity_rating IS NULL
        OR entity_rating BETWEEN 1 AND 10
      );

ALTER TABLE user_interactions
  ADD CONSTRAINT chk_subtype_requires_data
    CHECK (
      action_subtype IS NULL
        OR action_subdata IS NOT NULL
      );
