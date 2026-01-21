------------------------------------------------------------
-- 1. ENUM:  user_role (lowercase, correct)
------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_role') THEN
        CREATE TYPE user_role AS ENUM (
            'consumer',
            'provider',
            'cos_admin',
            'org_admin',
            'compute',
            'delegate'
        );
    END IF;
END$$;


------------------------------------------------------------
-- 2. ENUM: origin_server (UPPERCASE, consistent)
------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'origin_server') THEN
        CREATE TYPE origin_server AS ENUM (
            'CATALOGUE',
            'AAA',
            'FILE',
            'ACL_APD',
            'NGSI_LD',
            'GATEWAY',
            'OGC_RS'
        );
    END IF;
END$$;


------------------------------------------------------------
-- 3. ENUM: http_method
------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'http_method') THEN
        CREATE TYPE http_method AS ENUM (
            'GET',
            'POST',
            'PUT',
            'DELETE',
            'PATCH'
        );
    END IF;
END$$;


------------------------------------------------------------
-- 4. ENUM: entity_type
------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'entity_type') THEN
        CREATE TYPE entity_type AS ENUM (
            'DATABA- name: sector
          in: query
          description: Filter by sector (repeatable)
          required: false
          style: form
          explode: true
          schema:
            type: array
            items:
              type: stringNK',
            'AI_MODEL',
            'USECASE',
            'APPS'
            'ASSET',
            'USER_ACCOUNT',
            'ORGANIZATION',
            'ORG_REQUEST',
            'CREDIT_REQUEST',
            'COMPUTE_REQUEST',
            'DELEGATION',
            'POLICY',
            'RESOURCE_SERVER',
            'ACCESS_REQUEST',
            'KYC'
        );
    END IF;
END$$;


------------------------------------------------------------
-- 5. ENUM: action_type
------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'operation_type') THEN
        CREATE TYPE operation_type AS ENUM (
            'CREATE',
            'UPDATE',
            'DELETE',
            'SUBMIT',
            'APPROVE',
            'REJECT',
            'WITHDRAW',
            'VIEW',
            'LIST',
            'DOWNLOAD',
            'VERIFY',
            'DEDUCT',
            'DELEGATE',
            'LOGIN',
            'LOGOUT'
        );
    END IF;
END$$;


------------------------------------------------------------
-- 6. FINAL AUDIT LOG TABLE
------------------------------------------------------------
CREATE TABLE IF NOT EXISTS  activity_audit_log (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- User context
    user_id             UUID NOT NULL,
    org_id              UUID,
    org_name            VARCHAR,
    role                user_role NOT NULL,
    is_delegate         BOOLEAN NOT NULL DEFAULT false,

    -- Delegation context
    delegator_id        UUID,
    delegator_role       user_role,

    -- Provider info
    provider_id         UUID,

    -- API request metadata
    api                 TEXT NOT NULL,
    method              http_method NOT NULL,
    operation           operation_type NOT NULL,
    origin_server       origin_server NOT NULL,
    issuer              VARCHAR,

    -- Business entity context
    entity_type          entity_type NOT NULL,
    entity_id           UUID,
    entity_name         TEXT,
    short_description   TEXT,

    -- Request technical metadata
    ip_address          TEXT,
    user_agent          TEXT,
    size_bytes          BIGINT DEFAULT 0,

    -- Event timestamps (from backend)
    created_at          timestamp without time zone NOT NULL,
    epoch_ms            BIGINT NOT NULL,

    -- DB ingestion time
    ingested_at         timestamp without time zone NOT NULL DEFAULT now(),

    -- Result metadata
    status              VARCHAR DEFAULT 'SUCCESS',
    status_message      TEXT,

    -- Flexible structured metadata
    details             JSONB DEFAULT '{}'::jsonb,

    -- Whether visible in "My Activity"
    myactivity_enabled  BOOLEAN NOT NULL DEFAULT false
);


------------------------------------------------------------
-- 7. Indexes
------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_audit_user_id        ON  activity_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_entity         ON  activity_audit_log(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_operation      ON  activity_audit_log(operation);
CREATE INDEX IF NOT EXISTS idx_audit_created_at     ON  activity_audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_epoch_ms       ON  activity_audit_log(epoch_ms);
CREATE INDEX IF NOT EXISTS idx_audit_provider_id    ON  activity_audit_log(provider_id);

GRANT USAGE ON SCHEMA ${flyway:defaultSchema} TO ${authUser};
GRANT SELECT, INSERT, DELETE ON activity_audit_log TO ${authUser};

