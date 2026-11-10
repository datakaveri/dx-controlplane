ALTER TABLE aaa.app_constraints
DROP COLUMN IF EXISTS user_id;

ALTER TABLE aaa.app_constraints
  ADD COLUMN user_id UUID;

ALTER TABLE app_constraints
  ADD CONSTRAINT uq_app_constraints_scope_entity
    UNIQUE (user_id,scope, entity_type, entity_id);

CREATE TABLE IF NOT EXISTS custom_user_role (
  id UUID DEFAULT public.gen_random_uuid() NOT NULL,
  user_id UUID NOT NULL,
  role VARCHAR NOT NULL,
  scope JSONB,
  created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT now() NOT NULL,
  updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT now() NOT NULL,
  CONSTRAINT uq_custom_user_role
  UNIQUE (user_id, role)
  );

-- =====================================================
-- Permissions
-- =====================================================
GRANT SELECT, INSERT, UPDATE, DELETE
  ON TABLE custom_user_role
  TO ${authUser};
