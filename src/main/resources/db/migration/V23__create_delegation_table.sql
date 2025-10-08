CREATE TABLE IF NOT EXISTS delegation_grants (
    delegation_id UUID DEFAULT public.gen_random_uuid() PRIMARY KEY,
    delegator_id UUID NOT NULL,
    delegate_id UUID NOT NULL,
    justification TEXT NOT NULL,
    expiry_at TIMESTAMP WITH TIME ZONE NOT NULL,   -- global expiry
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','REVOKED','EXPIRED')),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMP WITHOUT TIME ZONE
);

CREATE TABLE IF NOT EXISTS delegation_scope_constraints (
    id UUID DEFAULT public.gen_random_uuid() PRIMARY KEY,
    delegation_id UUID NOT NULL REFERENCES delegation_grants(delegation_id) ON DELETE CASCADE,
    scope VARCHAR(50) NOT NULL CHECK (
            scope IN (
                'cos_admin_access',
                'org_management',
                'asset_management',
                'user_management',
                'compute_management',
                'credit_management',
                'provider_management',
                'data_access',
                'api',
                'subs'
            )
        ),
    entity_id UUID,                              -- which org_id, asset_id, etc.
    expiry_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (delegation_id, scope, entity_id)
);

CREATE TABLE IF NOT EXISTS delegation_update_requests (
    request_id UUID DEFAULT public.gen_random_uuid() PRIMARY KEY,
    delegation_id UUID NOT NULL REFERENCES delegation_grants(delegation_id) ON DELETE CASCADE,
    requester_id UUID NOT NULL,   -- delegate who is asking for change
    requested_scopes JSONB,       -- new/extra scopes requested
    requested_resources JSONB,    -- new/extra resources requested
    requested_expiry TIMESTAMP WITH TIME ZONE, -- if asking for extension
    justification TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP WITHOUT TIME ZONE,
    reviewer_id UUID
);

CREATE TABLE IF NOT EXISTS issued_tokens (
    jti UUID DEFAULT public.gen_random_uuid() PRIMARY KEY,
    delegation_id UUID REFERENCES delegation_grants(delegation_id) ON DELETE CASCADE,
    delegate_id UUID NOT NULL,
    scopes JSONB NOT NULL,  -- storing multiple scopes in JSON
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN DEFAULT FALSE,
    revoked_at TIMESTAMP WITHOUT TIME ZONE,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
