CREATE TABLE IF NOT EXISTS app_constraints(
  id UUID DEFAULT public.gen_random_uuid() NOT NULL,
  app_id UUID NOT NULL REFERENCES app_credentials(app_id) ON DELETE CASCADE,
  scope VARCHAR NOT NULL,
  entity_type VARCHAR NOT NULL,
  entity_id VARCHAR NOT NULL,
  created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT now() NOT NULL
  );

CREATE INDEX IF NOT EXISTS idx_app_constraints_app_id
  ON app_constraints(app_id);

CREATE INDEX IF NOT EXISTS idx_app_constraints_app_entity
  ON app_constraints(app_id, entity_type, entity_id);


ALTER TABLE IF EXISTS app_credentials
  ADD COLUMN role VARCHAR;

-- =====================================================
-- Permissions
-- =====================================================
GRANT SELECT, INSERT, UPDATE, DELETE
  ON TABLE app_constraints
  TO ${authUser};
