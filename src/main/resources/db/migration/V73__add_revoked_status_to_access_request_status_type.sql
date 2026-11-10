-- Add REVOKED status to access request status enum
ALTER TYPE _access_request_status_type
ADD VALUE IF NOT EXISTS 'REVOKED';