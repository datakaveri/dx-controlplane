-- Table: public.subscriptions

-- DROP TABLE IF EXISTS public.subscriptions;

CREATE TABLE IF NOT EXISTS subscriptions
(
    id UUID NOT NULL UNIQUE,
    queue_name character varying NOT NULL,
    entityId character varying NOT NULL,
    expiryAt timestamp without time zone NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    dataset_name character varying ,
    user_id uuid NOT NULL,
    delegator_id uuid,
    provider_id uuid,
    CONSTRAINT sub_pk PRIMARY KEY (id,queue_name, entityId)
);

CREATE
OR REPLACE
   FUNCTION update_modified () RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = now ();
RETURN NEW;
END;
$$ language 'plpgsql';

-- created_at column function
CREATE
OR REPLACE
   FUNCTION update_created () RETURNS TRIGGER AS $$
BEGIN NEW.created_at = now ();
RETURN NEW;
END;
$$ language 'plpgsql';

ALTER TABLE IF EXISTS subscriptions
    OWNER to postgres;

REVOKE ALL ON TABLE subscriptions FROM ${authUser};

GRANT INSERT, DELETE, SELECT, UPDATE ON TABLE subscriptions TO ${authUser};

GRANT ALL ON TABLE subscriptions TO postgres;

-- Trigger: update_sub_created

-- DROP TRIGGER IF EXISTS update_sub_created ON public.subscriptions;

CREATE OR REPLACE TRIGGER update_sub_created
    BEFORE INSERT
    ON subscriptions
    FOR EACH ROW
    EXECUTE FUNCTION update_created();

-- Trigger: update_sub_modified

-- DROP TRIGGER IF EXISTS update_sub_modified ON public.subscriptions;

CREATE OR REPLACE TRIGGER update_sub_modified
    BEFORE INSERT OR UPDATE
    ON subscriptions
    FOR EACH ROW
    EXECUTE FUNCTION update_modified();