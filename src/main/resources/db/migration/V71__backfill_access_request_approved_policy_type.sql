UPDATE policy
SET policy_type = 'ACCESS_REQUEST_APPROVED'::policy_type_enum
WHERE request_id IS NOT NULL;