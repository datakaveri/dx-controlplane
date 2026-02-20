ALTER TABLE user_interactions
  ADD COLUMN action_subtype VARCHAR,
  ADD COLUMN action_subdata JSONB,
  ADD COLUMN entity_rating INT CHECK (entity_rating BETWEEN 1 AND 5);

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
