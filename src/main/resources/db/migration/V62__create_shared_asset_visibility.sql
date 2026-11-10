CREATE TABLE shared_asset_visibility (

    id UUID PRIMARY KEY,

    item_id UUID NOT NULL,

    share_type VARCHAR(20) NOT NULL,
    -- USER / ORGANIZATION

    user_id UUID NULL,

    org_id UUID NULL,

    shared_by UUID NOT NULL,

    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),

    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_share_type
    CHECK (
        (share_type = 'USER' AND user_id IS NOT NULL AND org_id IS NULL)
        OR
        (share_type = 'ORGANIZATION' AND org_id IS NOT NULL AND user_id IS NULL)
    )
);
