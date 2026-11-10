CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_org_users_user_name_trgm
    ON organization_users USING GIN (user_name gin_trgm_ops);