DROP TYPE IF EXISTS feedback_type_enum CASCADE;
DROP TYPE IF EXISTS feedback_type CASCADE;
DROP TABLE IF EXISTS provider_feedback;

CREATE TYPE feedback_type_enum AS ENUM (
    'FAQ',
    'FEEDBACK',
    'INFO',
    'DATA_DESCRIPTION'
);

CREATE TABLE IF NOT EXISTS provider_feedback
  (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  asset_id UUID NOT NULL,
  feedback_type  feedback_type_enum NOT NULL,
  data JSONB NOT NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_provider_feedback_user_id ON provider_feedback(user_id);
CREATE INDEX IF NOT EXISTS idx_provider_feedback_asset_id ON provider_feedback(asset_id);

-- =====================================================
-- Permissions
-- =====================================================
GRANT SELECT, INSERT, UPDATE, DELETE
  ON TABLE provider_feedback
  TO ${authUser};
