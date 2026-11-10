-- Create custom enum type for http_method
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'http_method') THEN
    CREATE TYPE http_method AS ENUM ('GET', 'POST', 'PUT', 'DELETE', 'PATCH');
  END IF;
END$$;

-- Create custom enum type for user_role
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'user_role') THEN
    CREATE TYPE user_role AS ENUM ('consumer', 'provider', 'cos_admin', 'org_admin', 'compute');
  END IF;
END$$;

-- Create custom enum type for origin_system
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'origin_system') THEN
    CREATE TYPE origin_system AS ENUM ('Catalogue', 'AAA', 'File', 'ACL');
  END IF;
END$$;

-- Create the user_activity_log table
CREATE TABLE IF NOT EXISTS user_activity_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid() NOT NULL,
    user_id UUID NOT NULL,
    role user_role NOT NULL,
    asset_name VARCHAR NOT NULL,
    asset_id UUID NOT NULL,
    asset_type VARCHAR NOT NULL,
    org_name VARCHAR,
    org_id UUID,
    operation VARCHAR NOT NULL,
    api VARCHAR NOT NULL,
    short_description VARCHAR,
    method http_method NOT NULL,
    created_at timestamp without time zone NOT NULL,
    size BIGINT NOT NULL DEFAULT 0,
    origin_server origin_system NOT NULL,
    myactivity_enabled BOOLEAN NOT NULL DEFAULT FALSE
);

--add indexes for the columns
CREATE INDEX idx_user_activity_log_user_id ON user_activity_log(user_id);
CREATE INDEX idx_user_activity_log_org_id ON user_activity_log(org_id);
CREATE INDEX idx_user_activity_log_operation ON user_activity_log(operation);
CREATE INDEX idx_user_activity_log_asset_id ON user_activity_log(asset_id);
CREATE INDEX idx_user_activity_log_asset_type ON user_activity_log(asset_type);
CREATE INDEX idx_user_activity_log_created_at ON user_activity_log(created_at);
-- Grant access
GRANT USAGE ON SCHEMA ${flyway:defaultSchema} TO ${authUser};
GRANT SELECT, INSERT, UPDATE, DELETE ON client_credentials TO ${authUser};
