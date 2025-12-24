-- Roles derived from delegator token
CREATE TYPE delegation_role AS ENUM (
  'cos_admin',
  'org_admin',
  'provider',
  'consumer',
  'compute'
);

-- Entity types for scoped delegation
CREATE TYPE delegation_entity_type AS ENUM (
  'org',
  'provider',
  'databank',
  'aimodel'
);

-- Allow wildcard scope
ALTER TABLE delegation_scope_constraints
ALTER COLUMN scope TYPE TEXT
  USING scope::TEXT;

-- Add role (mandatory)
ALTER TABLE delegation_scope_constraints
  ADD COLUMN role delegation_role NOT NULL;

-- Add entity type
ALTER TABLE delegation_scope_constraints
  ADD COLUMN entity_type delegation_entity_type;

-- Wildcard must not have entity info
ALTER TABLE delegation_scope_constraints
  ADD CONSTRAINT chk_entity_type_wildcard
    CHECK (
      (scope = '*' AND entity_id IS NULL AND entity_type IS NULL)
        OR
      (scope <> '*')
      );

-- entity_id and entity_type must appear together
ALTER TABLE delegation_scope_constraints
  ADD CONSTRAINT chk_entity_pairing
    CHECK (
      (entity_id IS NULL AND entity_type IS NULL)
        OR
      (entity_id IS NOT NULL AND entity_type IS NOT NULL)
      );

-- Remove old uniqueness
ALTER TABLE delegation_scope_constraints
DROP CONSTRAINT IF EXISTS delegation_scope_constraints_delegation_id_scope_entity_id_key;

-- New uniqueness includes role
ALTER TABLE delegation_scope_constraints
  ADD CONSTRAINT ux_delegation_role_scope
    UNIQUE (delegation_id, role, scope, entity_id);

-- One wildcard per role per delegation
CREATE UNIQUE INDEX ux_delegation_role_wildcard
  ON delegation_scope_constraints (delegation_id, role)
  WHERE scope = '*';
