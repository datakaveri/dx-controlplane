ALTER TABLE user_interactions
    ADD COLUMN feedback_created_at TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN feedback_updated_at TIMESTAMP WITHOUT TIME ZONE;
