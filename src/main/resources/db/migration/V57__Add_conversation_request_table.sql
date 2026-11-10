DROP TABLE IF EXISTS request_type_mapping CASCADE;
DROP TABLE IF EXISTS request_messages CASCADE;
DROP TYPE IF EXISTS message_type_enum;

SET search_path TO ${flyway:defaultSchema};

CREATE TABLE request_type_mapping (
  id            SMALLSERIAL PRIMARY KEY,     -- auto generated, don't care about value
  type_key      VARCHAR(50) UNIQUE NOT NULL, -- 'join_org', 'credit' — used in code
  display_name  TEXT NOT NULL,               -- 'Organisation Join Request' — used in UI
  is_active     BOOLEAN DEFAULT TRUE,        -- soft disable without deleting
  created_at    TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
);

INSERT INTO request_type_mapping (type_key, display_name) VALUES
     ('join_org',    'Organisation Join Request'),
     ('org_create',  'Organisation Create Request'),
     ('credit',      'Credit Request'),
     ('debit',       'Debit Request');

CREATE TYPE message_type_enum AS ENUM (
  'text', 'question', 'answer', 'info_request', 'note', 'system'
);


CREATE TABLE request_messages
(
  id               UUID DEFAULT public.gen_random_uuid() PRIMARY KEY NOT NULL,
  request_type_id  SMALLINT NOT NULL REFERENCES request_type_mapping(id),
  sender_id        UUID      NOT NULL,
  sender_role      VARCHAR(20) NOT NULL,
  message_type     message_type_enum NOT NULL DEFAULT 'text',
  content          TEXT NOT NULL CHECK (length(content) <= 1000),
  parent_msg_id    UUID REFERENCES request_messages (id),
  metadata         JSONB                DEFAULT '{}',
  is_internal      BOOLEAN              DEFAULT FALSE,
  created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  CONSTRAINT uq_request_message UNIQUE (request_type_id, sender_id, content)
);


GRANT SELECT, INSERT, UPDATE, DELETE
  ON TABLE request_messages , request_type_mapping
  TO ${authUser};
