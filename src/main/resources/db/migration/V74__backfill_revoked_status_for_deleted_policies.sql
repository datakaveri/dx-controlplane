-- Backfill access requests for policies that were previously deleted.
-- If a policy was created from an access request and has since been deleted,
-- mark the corresponding access request as REVOKED.

UPDATE request r
SET
    status = 'REVOKED',
    updated_at = p.updated_at
FROM policy p
WHERE
    p.request_id = r.request_id
    AND p.status = 'DELETED'
    AND p.request_id IS NOT NULL
    AND r.status = 'GRANTED';