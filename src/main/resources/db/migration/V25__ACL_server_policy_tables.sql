-- =============================================
-- Migration for user_table and policy table being used for acl policy apis
-- =============================================

-- 1.️Set search_path to your default schema
SET search_path TO ${flyway:defaultSchema};

-- 2.Create uuid-ossp extension if not exists
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

-- 4️Create ENUM type if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_type t
        JOIN pg_namespace n ON t.typnamespace = n.oid
        WHERE t.typname = 'status_type' AND n.nspname = '${flyway:defaultSchema}'
    ) THEN
        EXECUTE format('CREATE TYPE %I.status_type AS ENUM (''ACTIVE'', ''DELETED'')', '${flyway:defaultSchema}');
    END IF;
END
$$;

-- 5️Create user_table if not exists
CREATE TABLE IF NOT EXISTS user_table (
    _id uuid NOT NULL,
    email_id varchar NOT NULL,
    first_name varchar NOT NULL,
    last_name varchar NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    CONSTRAINT user_pk PRIMARY KEY (_id)
);
ALTER TABLE user_table OWNER TO ${flyway:user};

-- 6️ Create policy table if not exists
CREATE TABLE IF NOT EXISTS policy (
    _id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    user_emailid varchar NOT NULL,
    item_id uuid NOT NULL,
    owner_id uuid NOT NULL,
    status ${flyway:defaultSchema}.status_type NOT NULL,
    expiry_at timestamp without time zone NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    constraints json NOT NULL,
    CONSTRAINT policy_pk PRIMARY KEY (_id),
    CONSTRAINT owner_id_fk FOREIGN KEY(owner_id) REFERENCES user_table(_id)
);
ALTER TABLE policy OWNER TO ${flyway:user};

-- 7️Functions for audit timestamps
CREATE OR REPLACE FUNCTION update_modified() RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION update_created() RETURNS trigger AS $$
BEGIN
    NEW.created_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 8️Triggers (idempotent)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='update_ua_created' AND tgrelid='policy'::regclass) THEN
        CREATE TRIGGER update_ua_created BEFORE INSERT ON policy
        FOR EACH ROW EXECUTE FUNCTION update_created();
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='update_ua_modified' AND tgrelid='policy'::regclass) THEN
        CREATE TRIGGER update_ua_modified BEFORE INSERT OR UPDATE ON policy
        FOR EACH ROW EXECUTE FUNCTION update_modified();
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='update_ua_created' AND tgrelid='user_table'::regclass) THEN
        CREATE TRIGGER update_ua_created BEFORE INSERT ON user_table
        FOR EACH ROW EXECUTE FUNCTION update_created();
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_trigger WHERE tgname='update_ua_modified' AND tgrelid='user_table'::regclass) THEN
        CREATE TRIGGER update_ua_modified BEFORE INSERT OR UPDATE ON user_table
        FOR EACH ROW EXECUTE FUNCTION update_modified();
    END IF;
END
$$;

-- 9️ Grants (idempotent via GRANT)
GRANT USAGE ON SCHEMA ${flyway:defaultSchema} TO ${authUser};
GRANT SELECT, INSERT, UPDATE ON TABLE policy TO ${authUser};
GRANT SELECT, INSERT, UPDATE ON TABLE user_table TO ${authUser};

-- 10. Additional columns for policy table (conditionally added)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='policy' AND column_name='additional_info'
    ) THEN
        ALTER TABLE policy ADD COLUMN additional_info JSONB;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='policy' AND column_name='provider_comment'
    ) THEN
        ALTER TABLE policy ADD COLUMN provider_comment varchar(4000);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name='policy' AND column_name='feedback_to_consumer'
    ) THEN
        ALTER TABLE policy ADD COLUMN feedback_to_consumer varchar(4000);
    END IF;
END
$$;
