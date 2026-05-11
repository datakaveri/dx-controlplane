SET search_path TO ${flyway:defaultSchema};

-- Drop the lookup table (CASCADE removes the FK on request_messages.request_type_id)
DROP TABLE IF EXISTS request_type_mapping CASCADE;

-- Replace request_type_id with a plain text column
ALTER TABLE request_messages
DROP COLUMN request_type_id,
    ADD COLUMN  request_type TEXT ;

-- Recreate the unique constraint using the new text column
ALTER TABLE request_messages
DROP CONSTRAINT IF EXISTS uq_request_message,
    ADD CONSTRAINT uq_request_message
        UNIQUE (request_type, sender_id, content);

GRANT SELECT, INSERT, UPDATE, DELETE
  ON TABLE request_messages
  TO ${authUser};

