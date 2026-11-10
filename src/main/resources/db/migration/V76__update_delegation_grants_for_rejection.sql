-- Supports the delegation rejection endpoint: a delegate may reject a grant,
-- and a rejected grant must not block re-requesting the same delegation.

-- Allow 'rejected' alongside the existing statuses.
ALTER TABLE delegation_grants
DROP CONSTRAINT IF EXISTS delegation_grants_status_check;

ALTER TABLE delegation_grants
ADD CONSTRAINT delegation_grants_status_check
CHECK (status IN ('active', 'revoked', 'expired', 'rejected'));

-- Replace the unconditional unique constraint with one scoped to active
-- delegations, so rejected and expired rows no longer collide.
ALTER TABLE delegation_grants
DROP CONSTRAINT IF EXISTS delegation_grants_delegator_id_delegate_id_justification_ex_key;

CREATE UNIQUE INDEX IF NOT EXISTS delegation_grants_active_unique_idx
ON delegation_grants (
    delegator_id,
    delegate_id,
    justification,
    expiry_at
)
WHERE status = 'active';
