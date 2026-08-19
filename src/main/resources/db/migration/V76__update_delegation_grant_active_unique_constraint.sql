-- Remove the existing unique constraint.
ALTER TABLE delegation_grants
DROP CONSTRAINT IF EXISTS delegation_grants_delegator_id_delegate_id_justification_ex_key;

-- Enforce uniqueness only for active delegations.
CREATE UNIQUE INDEX delegation_grants_active_unique_idx
ON delegation_grants (
    delegator_id,
    delegate_id,
    justification,
    expiry_at
)
WHERE status = 'active';