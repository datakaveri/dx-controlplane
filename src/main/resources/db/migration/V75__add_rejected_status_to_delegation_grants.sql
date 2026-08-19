ALTER TABLE delegation_grants
DROP CONSTRAINT IF EXISTS delegation_grants_status_check;

ALTER TABLE delegation_grants
ADD CONSTRAINT delegation_grants_status_check
CHECK (status IN ('active', 'revoked', 'expired', 'rejected'));