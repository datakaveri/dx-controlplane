-- 1. Add SANDBOX to enum (independent change)
ALTER TYPE origin_server
    ADD VALUE IF NOT EXISTS 'SANDBOX';

-- 2. Add sandbox_type column as VARCHAR
ALTER TABLE user_activity_audit_log
    ADD COLUMN IF NOT EXISTS sandbox_type VARCHAR;
