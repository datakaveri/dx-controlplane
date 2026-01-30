DO
$$
BEGIN
  IF
NOT EXISTS (
    SELECT 1 FROM pg_type WHERE typname = 'audit_log_type_enum'
  ) THEN
CREATE TYPE audit_log_type_enum AS ENUM (
      'ASSET',
      'USER_ACTION',
      'CREDIT'
    );
END IF;
END$$;




CREATE TABLE IF NOT EXISTS user_activity_audit_log
(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- User context
    user_id UUID NOT NULL,
    user_name TEXT NOT NULL,
    org_id UUID,
    org_name TEXT,
    org_type TEXT,
    app_id UUID,
    role VARCHAR NOT NULL,
    issuer TEXT,

    -- Delegation
    delegator_id UUID,
    delegator_role VARCHAR,

    -- API metadata
    api VARCHAR NOT NULL,
    http_method http_method NOT NULL,
    operation VARCHAR NOT NULL,
    origin_server origin_server NOT NULL,

    -- Asset dimension
    asset_id UUID,
    asset_name TEXT,
    asset_sort_discription TEXT,
    asset_type VARCHAR,
    asset_access_policy VARCHAR,
    asset_org_id UUID,
    asset_org_name TEXT,
    asset_org_type TEXT,
    asset_provider_id UUID,
    asset_provider_name TEXT,

    -- Metrics / workflow
    size_bytes BIGINT DEFAULT 0,
    amount NUMERIC DEFAULT 0.00,
    request_id UUID,

    -- Classification
    log_type audit_log_type_enum NOT NULL,

    -- Technical metadata
    ip_address TEXT,
    user_agent TEXT,

    -- Time
    created_at  timestamp without time zone NOT NULL,

    -- Extensible
    context JSONB DEFAULT '{}'::jsonb
    );


CREATE INDEX IF NOT EXISTS idx_user_activity_user_time
    ON user_activity_audit_log (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_activity_user_logtype_time
    ON user_activity_audit_log (user_id, log_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_activity_org_time
    ON user_activity_audit_log (org_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_activity_asset_id
    ON user_activity_audit_log (asset_id);

CREATE INDEX IF NOT EXISTS idx_user_activity_org_type
    ON user_activity_audit_log (org_type);

CREATE INDEX IF NOT EXISTS idx_user_activity_asset_type
    ON user_activity_audit_log (asset_type);

CREATE INDEX IF NOT EXISTS idx_user_activity_access_policy
    ON user_activity_audit_log (asset_access_policy);

CREATE INDEX IF NOT EXISTS idx_user_activity_request_id
    ON user_activity_audit_log (request_id);

CREATE INDEX IF NOT EXISTS idx_user_activity_created_at
    ON user_activity_audit_log (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_user_activity_context
    ON user_activity_audit_log
    USING GIN (context);

GRANT SELECT, INSERT
    ON TABLE user_activity_audit_log
    TO ${authUser};

