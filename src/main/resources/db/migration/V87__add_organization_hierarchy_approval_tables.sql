CREATE TABLE IF NOT EXISTS organization_approvals (
    id UUID DEFAULT public.gen_random_uuid() PRIMARY KEY,

    organization_id UUID NOT NULL
        REFERENCES organizations(id) ON DELETE CASCADE,

    request_id UUID NOT NULL,

    approval_type VARCHAR NOT NULL,

    required_level SMALLINT NOT NULL,

    processed_by_user_id UUID,

    processed_by_node_id UUID
        REFERENCES organization_hierarchy_nodes(id)
        ON DELETE SET NULL,

    processed_by_level SMALLINT NOT NULL DEFAULT 0,

    status VARCHAR NOT NULL
        CHECK (status IN ('pending', 'approved', 'rejected')),

    remarks VARCHAR,

    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP,

    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL
        DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS organization_approvals_request_idx
ON organization_approvals (request_id, approval_type);

CREATE INDEX IF NOT EXISTS organization_approvals_approver_idx
ON organization_approvals (processed_by_user_id);

CREATE INDEX IF NOT EXISTS organization_approvals_org_status_idx
ON organization_approvals (
    organization_id,
    status
);

-- Permissions
GRANT USAGE ON SCHEMA ${flyway:defaultSchema} TO ${authUser};

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE organization_approvals TO ${authUser};
