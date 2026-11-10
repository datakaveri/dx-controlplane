-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Create table
CREATE TABLE acl_servers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),  -- auto-generate UUID
    name VARCHAR(255) NOT NULL,                     -- Human-readable name
    url VARCHAR(255) NOT NULL,                      -- RS URL like datakaveri.org
    owner_id UUID NOT NULL,                         -- Owner UUID
    visibility VARCHAR(50) NOT NULL CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    status VARCHAR(50) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'PENDING')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Function: set updated_at only on update
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger: auto-update updated_at on UPDATE
CREATE TRIGGER trigger_set_updated_at
BEFORE UPDATE ON acl_servers
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

