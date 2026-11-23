-- Organisation departments and request workflow:
--   1. create-org requests can carry an optional department tree
--   2. create-org requests can be withdrawn
--   3. join requests carry the role asked for in the chosen department
--   4. join requests get an approval_scope column for dept_admin listings
--   5. departments can be added after the tree is first defined


-- 1. Optional department tree proposed together with an organisation create
-- request, so cos_admin can review and approve the organisation and its
-- hierarchy in a single step. Same shape as
-- organization_hierarchy_definition_requests.proposed_hierarchy:
--   {"departments": [{"name": "...", "subDepartments": [{"name": "...", ...}]}]}
-- NULL means the organisation is created flat (existing behaviour); the
-- hierarchy can still be defined later via a hierarchy definition request.
ALTER TABLE organization_create_requests
    ADD COLUMN IF NOT EXISTS proposed_hierarchy JSONB;


-- 2. A consumer can withdraw a pending organisation create request. The row is
-- kept (status = 'withdrawn') so the consumer still sees it in their history,
-- mirroring organization_join_requests (V44).
ALTER TABLE organization_create_requests
    DROP CONSTRAINT IF EXISTS organization_create_requests_status_check;

ALTER TABLE organization_create_requests
    ADD CONSTRAINT organization_create_requests_status_check
        CHECK (status IN ('pending', 'granted', 'rejected', 'withdrawn'));


-- 3. When joining a hierarchical organisation the user picks a department and
-- the role they want in it: a plain member, or the department's admin. Asking
-- to be the admin also needs a purpose. Existing rows are plain member requests.
ALTER TABLE organization_join_requests
    ADD COLUMN IF NOT EXISTS requested_role VARCHAR NOT NULL DEFAULT 'member',
    ADD COLUMN IF NOT EXISTS purpose TEXT;

ALTER TABLE organization_join_requests
    DROP CONSTRAINT IF EXISTS organization_join_requests_requested_role_check;

ALTER TABLE organization_join_requests
    ADD CONSTRAINT organization_join_requests_requested_role_check
        CHECK (
            requested_role IN ('member', 'dept_admin')
            AND (
                requested_role = 'member'
                OR (requested_node_id IS NOT NULL
                    AND purpose IS NOT NULL
                    AND btrim(purpose) <> '')
            )
        );


-- 4. "<requested_role>:<requested_node_id>", e.g. "dept_admin:9b2e...". Lets a
-- dept_admin's join-request list match member requests anywhere in their
-- subtree but dept_admin requests only *below* their own department (those for
-- their own department need someone above them) with a single IN filter.
-- NULL for flat-organisation requests, which have no department.
-- Depends on requested_role from section 3.
ALTER TABLE organization_join_requests
    ADD COLUMN IF NOT EXISTS approval_scope TEXT
        GENERATED ALWAYS AS (requested_role || ':' || requested_node_id::text) STORED;

CREATE INDEX IF NOT EXISTS idx_org_join_requests_approval_scope
    ON organization_join_requests (organization_id, approval_scope);


-- 5. Departments can be added after an organisation's tree is first defined.
-- A definition request either defines the first departments (parent_node_id
-- NULL on a flat organisation), adds new top-level departments (parent_node_id
-- NULL on a hierarchical one), or adds departments under an existing one.
ALTER TABLE organization_hierarchy_definition_requests
    ADD COLUMN IF NOT EXISTS parent_node_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'org_hierarchy_definition_requests_parent_node_fk'
          AND conrelid = 'organization_hierarchy_definition_requests'::regclass
    ) THEN
        ALTER TABLE organization_hierarchy_definition_requests
            ADD CONSTRAINT org_hierarchy_definition_requests_parent_node_fk
            FOREIGN KEY (parent_node_id)
            REFERENCES organization_hierarchy_nodes(id)
            ON DELETE CASCADE;
    END IF;
END
$$;

-- cos_admin's queue lists requests across organisations by status.
CREATE INDEX IF NOT EXISTS organization_hierarchy_definition_requests_status_idx
    ON organization_hierarchy_definition_requests (status, created_at);
