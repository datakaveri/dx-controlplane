-- Record which user rejected an access request so "Rejected By" can be shown.
-- When a delegate rejects on behalf of a provider/org admin, this holds the
-- delegator's user id; the delegate is captured in user_activity_audit_log.
ALTER TABLE request
ADD COLUMN IF NOT EXISTS rejected_by UUID;

-- Backfill already-rejected requests from the latest reject audit entry
-- (user_id there is the delegator for delegated calls).
UPDATE request r
SET rejected_by = a.actor_id
FROM (
    SELECT DISTINCT ON (request_id)
        request_id,
        user_id AS actor_id
    FROM user_activity_audit_log
    WHERE action = 'Download Access Rejected'
      AND request_id IS NOT NULL
    ORDER BY request_id, created_at DESC
) a
WHERE r.request_id = a.request_id
  AND r.status = 'REJECTED'
  AND r.rejected_by IS NULL;
