-- =============================================
-- Migration for access_rule tables (rule-template based ACL)
-- =============================================

-- 1️ Set search_path to default schema
SET search_path TO ${flyway:defaultSchema};

-- 2️ Create uuid-ossp extension if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_extension WHERE extname = 'uuid-ossp'
    ) THEN
        CREATE EXTENSION "uuid-ossp" SCHEMA public;
    END IF;
END
$$;

-- 3️Ensure schema ownership
ALTER SCHEMA ${flyway:defaultSchema} OWNER TO ${flyway:user};

-- 4 Create ENUM type for rule status if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n ON t.typnamespace = n.oid
        WHERE t.typname = 'access_rule_status_type'
        AND n.nspname = '${flyway:defaultSchema}'
    ) THEN
        EXECUTE format(
            'CREATE TYPE %I.access_rule_status_type AS ENUM (''ACTIVE'', ''INACTIVE'')',
            '${flyway:defaultSchema}'
        );
    END IF;
END
$$;

-- 5 Create access_rule table
CREATE TABLE IF NOT EXISTS access_rule (
    _id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    item_id uuid NOT NULL,
    owner_id uuid NOT NULL,
    constraints jsonb NOT NULL DEFAULT '{}'::jsonb,
    status ${flyway:defaultSchema}.access_rule_status_type NOT NULL DEFAULT 'ACTIVE',
    created_at timestamp without time zone NOT NULL DEFAULT now(),
    updated_at timestamp without time zone NOT NULL DEFAULT now(),
    CONSTRAINT access_rule_pk PRIMARY KEY (_id)
);
ALTER TABLE access_rule OWNER TO ${flyway:user};

-- Indexes
CREATE INDEX IF NOT EXISTS idx_access_rule_item_id
    ON access_rule(item_id);

CREATE INDEX IF NOT EXISTS idx_access_rule_owner_id
    ON access_rule(owner_id);

CREATE INDEX IF NOT EXISTS idx_access_rule_status
    ON access_rule(status);

-- 6 Create access_rule_allowed_org table
CREATE TABLE IF NOT EXISTS access_rule_allowed_org (
    rule_id uuid NOT NULL,
    org_id varchar(255) NOT NULL,
    CONSTRAINT access_rule_allowed_org_pk PRIMARY KEY (rule_id, org_id),
    CONSTRAINT fk_access_rule_org
        FOREIGN KEY(rule_id)
        REFERENCES access_rule(_id)
        ON DELETE CASCADE
);
ALTER TABLE access_rule_allowed_org OWNER TO ${flyway:user};

CREATE INDEX IF NOT EXISTS idx_access_rule_allowed_org_org_id
    ON access_rule_allowed_org(org_id);

-- 7 Create access_rule_allowed_user table
CREATE TABLE IF NOT EXISTS access_rule_allowed_user (
    rule_id uuid NOT NULL,
    user_id varchar(255) NOT NULL,
    CONSTRAINT access_rule_allowed_user_pk PRIMARY KEY (rule_id, user_id),
    CONSTRAINT fk_access_rule_user
        FOREIGN KEY(rule_id)
        REFERENCES access_rule(_id)
        ON DELETE CASCADE
);
ALTER TABLE access_rule_allowed_user OWNER TO ${flyway:user};

CREATE INDEX IF NOT EXISTS idx_access_rule_allowed_user_user_id
    ON access_rule_allowed_user(user_id);

-- 8 Create access_rule_allowed_role table
CREATE TABLE IF NOT EXISTS access_rule_allowed_role (
    rule_id uuid NOT NULL,
    role varchar(255) NOT NULL,
    CONSTRAINT access_rule_allowed_role_pk PRIMARY KEY (rule_id, role),
    CONSTRAINT fk_access_rule_role
        FOREIGN KEY(rule_id)
        REFERENCES access_rule(_id)
        ON DELETE CASCADE
);
ALTER TABLE access_rule_allowed_role OWNER TO ${flyway:user};

CREATE INDEX IF NOT EXISTS idx_access_rule_allowed_role_role
    ON access_rule_allowed_role(role);

-- 9 Function for updating modified timestamp
CREATE OR REPLACE FUNCTION update_access_rule_modified()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 10 Trigger (idempotent)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_trigger
        WHERE tgname='update_ar_modified'
        AND tgrelid='access_rule'::regclass
    ) THEN
        CREATE TRIGGER update_ar_modified
        BEFORE UPDATE ON access_rule
        FOR EACH ROW EXECUTE FUNCTION update_access_rule_modified();
    END IF;
END
$$;

-- 11 Grants
GRANT USAGE ON SCHEMA ${flyway:defaultSchema} TO ${authUser};

GRANT SELECT, INSERT, UPDATE, DELETE
ON TABLE access_rule TO ${authUser};

GRANT SELECT, INSERT, UPDATE, DELETE
ON TABLE access_rule_allowed_org TO ${authUser};

GRANT SELECT, INSERT, UPDATE, DELETE
ON TABLE access_rule_allowed_user TO ${authUser};

GRANT SELECT, INSERT, UPDATE, DELETE
ON TABLE access_rule_allowed_role TO ${authUser};
-------
ALTER TABLE access_rule
ADD COLUMN policy_id uuid NOT NULL;

ALTER TABLE access_rule
ADD CONSTRAINT fk_access_rule_policy
FOREIGN KEY (policy_id)
REFERENCES policy(_id)
ON DELETE CASCADE;

CREATE INDEX idx_access_rule_policy_id
ON access_rule(policy_id);