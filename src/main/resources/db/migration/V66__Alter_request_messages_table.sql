SET search_path TO ${flyway:defaultSchema};

-- Add reply_count only if it doesn't exist
ALTER TABLE request_messages
  ADD COLUMN IF NOT EXISTS reply_count INT NOT NULL DEFAULT 0;

-- Add request_type_id only if it doesn't exist
ALTER TABLE request_messages
  ADD COLUMN IF NOT EXISTS request_type_id UUID;

CREATE TYPE sender_role_enum AS ENUM ('approver', 'requester');

ALTER TABLE request_messages
ALTER COLUMN sender_role TYPE sender_role_enum
  USING sender_role::sender_role_enum;

-- Index for replies (partial index)
CREATE INDEX IF NOT EXISTS idx_request_messages_parent_created
  ON request_messages (parent_msg_id, created_at ASC)
  WHERE parent_msg_id IS NOT NULL;

-- Index for root messages (partial index)
CREATE INDEX IF NOT EXISTS idx_request_messages_root_created
  ON request_messages (request_type, created_at DESC)
  WHERE parent_msg_id IS NULL;
