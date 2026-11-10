-- Widen organization_users.role to allow the new dept_admin role
-- (originally constrained to 'admin'/'member' in V1__Create_OrganizationRequest_table.sql).
ALTER TABLE organization_users
DROP CONSTRAINT IF EXISTS organization_users_role_check;

ALTER TABLE organization_users
ADD CONSTRAINT organization_users_role_check
CHECK (role IN ('admin', 'dept_admin', 'member'));


-- Snapshot the item's owning hierarchy node at access-request creation time,
-- mirroring the existing item_organization_id snapshot on this same table --
-- lets dept_admins approve/reject access requests scoped to their subtree.
ALTER TABLE request
ADD COLUMN IF NOT EXISTS item_hierarchy_node_id UUID;


-- A member's request to become dept_admin of their own department --
-- approved by the org_admin or any strict ancestor dept_admin above them.
CREATE TABLE IF NOT EXISTS dept_admin_requests (
    id UUID DEFAULT public.gen_random_uuid() PRIMARY KEY,

    organization_id UUID NOT NULL
        REFERENCES organizations(id) ON DELETE CASCADE,

    user_id UUID NOT NULL,

    requested_node_id UUID NOT NULL
        REFERENCES organization_hierarchy_nodes(id) ON DELETE CASCADE,

    status VARCHAR NOT NULL
        CHECK (status IN ('pending', 'granted', 'rejected')),

    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS dept_admin_requests_org_status_idx
ON dept_admin_requests (organization_id, status);

CREATE INDEX IF NOT EXISTS dept_admin_requests_user_idx
ON dept_admin_requests (user_id);

-- Permissions
GRANT USAGE ON SCHEMA ${flyway:defaultSchema} TO ${authUser};

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE dept_admin_requests TO ${authUser};
