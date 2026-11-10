CREATE TABLE IF NOT EXISTS aaa.app_credentials (
                                                  app_id UUID DEFAULT public.gen_random_uuid() NOT NULL,

  -- ownership
  user_id UUID NOT NULL,

  -- credentials (hashed secret)
  app_secret_hash TEXT NOT NULL,

  -- lifecycle
  expiry_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'active',

  -- audit timestamps
  created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  modified_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
  revoked_at TIMESTAMP WITHOUT TIME ZONE,

  CONSTRAINT app_credentials_pk PRIMARY KEY (app_id),
  CONSTRAINT app_credentials_status_check
  CHECK (status IN ('active', 'revoked', 'expired'))
  );


CREATE INDEX IF NOT EXISTS idx_app_credentials_app_id
  ON aaa.app_credentials (app_id);

CREATE INDEX IF NOT EXISTS idx_app_credentials_user_id
  ON aaa.app_credentials (user_id);

CREATE INDEX IF NOT EXISTS idx_app_credentials_status_expiry
  ON aaa.app_credentials (status, expiry_at);

CREATE INDEX IF NOT EXISTS idx_app_credentials_expiry_at
  ON aaa.app_credentials (expiry_at);


CREATE TRIGGER update_app_credentials_modified
  BEFORE INSERT OR UPDATE ON aaa.app_credentials
                     FOR EACH ROW
                     EXECUTE PROCEDURE update_modified();

GRANT SELECT, INSERT, UPDATE, DELETE
  ON aaa.app_credentials
  TO ${authUser};
