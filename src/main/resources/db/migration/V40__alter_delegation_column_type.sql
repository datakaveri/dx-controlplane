ALTER TABLE delegation_scope_constraints
ALTER COLUMN entity_id TYPE VARCHAR(36)
  USING entity_id::VARCHAR,
  ALTER COLUMN entity_type TYPE VARCHAR(36)
  USING entity_type::VARCHAR;

ALTER TABLE delegation_scope_constraints
  ALTER COLUMN entity_id SET NOT NULL,
  ALTER COLUMN entity_type SET NOT NULL;

ALTER TABLE delegation_scope_constraints
  DROP CONSTRAINT chk_entity_type_wildcard;

ALTER TABLE delegation_scope_constraints
  DROP CONSTRAINT chk_entity_pairing;

ALTER TABLE delegation_scope_constraints
DROP CONSTRAINT IF EXISTS chk_entity_wildcard_pairing;

ALTER TABLE delegation_scope_constraints
  ADD CONSTRAINT chk_entity_wildcard_pairing
    CHECK (
      (entity_id = '*' AND entity_type = '*')
        OR
      (entity_id <> '*' AND entity_type <> '*')
      );
