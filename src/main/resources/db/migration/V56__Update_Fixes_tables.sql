-- Add 'disabled' to the status check constraint
ALTER TABLE aaa.app_credentials
DROP CONSTRAINT app_credentials_status_check;

ALTER TABLE aaa.app_credentials
  ADD CONSTRAINT app_credentials_status_check
    CHECK (status IN ('active', 'disable'));

-----------------------------------------------------------
ALTER TABLE provider_feedback
  RENAME COLUMN feedback_type TO type;

-------------------------------------------------------------
CREATE TYPE entity_type_enum AS ENUM (
  'org',
  'provider',
  'adex:AiModel',
  'adex:DataBank',
  'adex:Apps',
  '*'
);

-- Migrate existing data to match new enum values
UPDATE app_constraints SET entity_type = 'adex:AiModel' WHERE entity_type = 'aimodel';
UPDATE app_constraints SET entity_type = 'adex:DataBank' WHERE entity_type = 'databank';
UPDATE app_constraints SET entity_type = 'adex:Apps' WHERE entity_type = 'apps';

ALTER TABLE app_constraints
ALTER COLUMN entity_type TYPE entity_type_enum
  USING entity_type::entity_type_enum;

------------------------------------------------------------------------
ALTER TYPE delegation_entity_type RENAME TO delegation_entity_type_old;

CREATE TYPE delegation_entity_type AS ENUM (
  'org',
  'provider',
  'adex:AiModel',
  'adex:DataBank',
  'adex:Apps',
  '*'
);

-- Migrate existing data
UPDATE delegation_scope_constraints SET entity_type = 'adex:AiModel' WHERE entity_type = 'aimodel';
UPDATE delegation_scope_constraints SET entity_type = 'adex:DataBank' WHERE entity_type = 'databank';
UPDATE delegation_scope_constraints SET entity_type = 'adex:Apps' WHERE entity_type = 'apps';

ALTER TABLE delegation_scope_constraints
ALTER COLUMN entity_type TYPE delegation_entity_type
  USING entity_type::TEXT::delegation_entity_type;

DROP TYPE delegation_entity_type_old;
-----------------------------------------------------------------------------
