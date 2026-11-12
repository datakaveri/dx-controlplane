SET search_path TO ${flyway:defaultSchema};

-- uq_request_message (request_type, sender_id, content) blocked a user from
-- sending the same text twice under a request type, even across different
-- requests (e.g. continuing a conversation after re-requesting access).
-- Repeated message text is valid in a conversation; rows are already unique by id.
ALTER TABLE request_messages
  DROP CONSTRAINT IF EXISTS uq_request_message;
