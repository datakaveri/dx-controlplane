-- One-time "define the department hierarchy" request for an existing (flat)
-- organization -- approved/rejected by cos_admin as a single atomic unit.
-- The organization itself is the implicit top of the tree, so
-- proposed_hierarchy holds its top-level departments as submitted (no ids):
--   {"departments": [{"name": "...", "subDepartments": [{"name": "...", ...}]}]}
CREATE TABLE IF NOT EXISTS organization_hierarchy_definition_requests (
    id UUID DEFAULT public.gen_random_uuid() PRIMARY KEY,

    organization_id UUID NOT NULL
        REFERENCES organizations(id) ON DELETE CASCADE,

    proposed_hierarchy JSONB NOT NULL,

    status VARCHAR NOT NULL
        CHECK (status IN ('pending', 'granted', 'rejected')),

    remarks VARCHAR,

    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS organization_hierarchy_definition_requests_org_status_idx
ON organization_hierarchy_definition_requests (organization_id, status);

-- Permissions
GRANT USAGE ON SCHEMA ${flyway:defaultSchema} TO ${authUser};

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE organization_hierarchy_definition_requests TO ${authUser};
