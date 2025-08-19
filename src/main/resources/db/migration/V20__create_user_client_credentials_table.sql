--
-- Table Definition
--
CREATE TABLE client_credentials (
    user_id UUID NOT NULL,
    client_id VARCHAR NOT NULL,
    client_secret VARCHAR NOT NULL,
    created_at timestamp without time zone NOT NULL,

    -- Ensure client_id is PRIMARY globally
    CONSTRAINT user_id_pk PRIMARY KEY (user_id)
);



create trigger update_ua_created before insert on client_credentials for each row EXECUTE procedure update_created ();

--
-- Indexes
--
CREATE INDEX idx_client_credentials_user_id
    ON client_credentials (user_id);

CREATE INDEX idx_client_credentials_client_id
    ON client_credentials (client_id);

--
-- Grants
--
GRANT USAGE ON SCHEMA ${flyway:defaultSchema} TO ${authUser};
GRANT SELECT, INSERT, UPDATE, DELETE ON client_credentials TO ${authUser};
