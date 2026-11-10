
-- Enable PostgreSQL ltree support for organization hierarchy paths.
CREATE EXTENSION IF NOT EXISTS ltree;

-- Stores the configurable hierarchy structure of an organization.
CREATE TABLE IF NOT EXISTS organization_hierarchy_nodes (
    id UUID DEFAULT public.gen_random_uuid() PRIMARY KEY,
    organization_id UUID NOT NULL
        REFERENCES organizations(id) ON DELETE CASCADE,
    parent_id UUID,
    name VARCHAR NOT NULL,
    level SMALLINT NOT NULL,
    path LTREE NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT organization_hierarchy_nodes_level_check
        CHECK (level >= 1),

    CONSTRAINT organization_hierarchy_nodes_id_org_unique
        UNIQUE (id, organization_id),

    CONSTRAINT organization_hierarchy_nodes_parent_fk
        FOREIGN KEY (parent_id, organization_id)
        REFERENCES organization_hierarchy_nodes(id, organization_id)
        ON DELETE CASCADE,

    CONSTRAINT organization_hierarchy_nodes_org_path_unique
        UNIQUE (organization_id, path)
);

-- Index for ancestor/descendant queries using ltree operators.
CREATE INDEX IF NOT EXISTS organization_hierarchy_nodes_path_gist_idx
    ON organization_hierarchy_nodes USING GIST (path);

-- Associate an organization member with a hierarchy node.
ALTER TABLE organization_users
    ADD COLUMN IF NOT EXISTS hierarchy_node_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'organization_users_hierarchy_node_fk'
          AND conrelid = 'organization_users'::regclass
    ) THEN
        ALTER TABLE organization_users
            ADD CONSTRAINT organization_users_hierarchy_node_fk
            FOREIGN KEY (hierarchy_node_id)
            REFERENCES organization_hierarchy_nodes(id)
            ON DELETE SET NULL;
    END IF;
END
$$;

-- Which department subtree a dept_admin administers.
ALTER TABLE organization_users
    ADD COLUMN IF NOT EXISTS admin_of_node_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'organization_users_admin_of_node_fk'
          AND conrelid = 'organization_users'::regclass
    ) THEN
        ALTER TABLE organization_users
            ADD CONSTRAINT organization_users_admin_of_node_fk
            FOREIGN KEY (admin_of_node_id)
            REFERENCES organization_hierarchy_nodes(id)
            ON DELETE SET NULL;
    END IF;
END
$$;

-- Store the hierarchy node requested during organization joining.
ALTER TABLE organization_join_requests
    ADD COLUMN IF NOT EXISTS requested_node_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'organization_join_requests_requested_node_fk'
          AND conrelid = 'organization_join_requests'::regclass
    ) THEN
        ALTER TABLE organization_join_requests
            ADD CONSTRAINT organization_join_requests_requested_node_fk
            FOREIGN KEY (requested_node_id)
            REFERENCES organization_hierarchy_nodes(id)
            ON DELETE SET NULL;
    END IF;
END
$$;

-- Store the hierarchy node requested on a provider-role request.
ALTER TABLE provider_requests
    ADD COLUMN IF NOT EXISTS requested_node_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'provider_requests_requested_node_fk'
          AND conrelid = 'provider_requests'::regclass
    ) THEN
        ALTER TABLE provider_requests
            ADD CONSTRAINT provider_requests_requested_node_fk
            FOREIGN KEY (requested_node_id)
            REFERENCES organization_hierarchy_nodes(id)
            ON DELETE SET NULL;
    END IF;
END
$$;

-- Permissions
GRANT USAGE ON SCHEMA ${flyway:defaultSchema} TO ${authUser};

GRANT SELECT, INSERT, UPDATE, DELETE
    ON TABLE organization_hierarchy_nodes TO ${authUser};
