-- Join requests: replace the free-text "purpose" (required for dept_admin) with
-- an optional, UI-owned "additional_info" JSON object. The API stores and
-- returns it as-is; what goes in it (purpose or anything else) is up to the UI.

ALTER TABLE organization_join_requests
    ADD COLUMN IF NOT EXISTS additional_info JSONB;

-- Keep any purpose already captured, as {"purpose": "..."}.
DO $$
BEGIN
    IF EXISTS (SELECT 1
               FROM information_schema.columns
               WHERE table_name = 'organization_join_requests'
                 AND column_name = 'purpose') THEN
        UPDATE organization_join_requests
        SET additional_info = jsonb_build_object('purpose', purpose)
        WHERE purpose IS NOT NULL
          AND btrim(purpose) <> ''
          AND additional_info IS NULL;
    END IF;
END $$;

-- dept_admin requests no longer need a purpose, only a department.
ALTER TABLE organization_join_requests
    DROP CONSTRAINT IF EXISTS organization_join_requests_requested_role_check;

ALTER TABLE organization_join_requests
    ADD CONSTRAINT organization_join_requests_requested_role_check
        CHECK (
            requested_role IN ('member', 'dept_admin')
            AND (requested_role = 'member' OR requested_node_id IS NOT NULL)
        );

ALTER TABLE organization_join_requests
    DROP COLUMN IF EXISTS purpose;
