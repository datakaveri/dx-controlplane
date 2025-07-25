CREATE TABLE KYC_Transactions (
    id UUID DEFAULT public.gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL,
    txn_id VARCHAR(512) NOT NULL,
    code_verifier VARCHAR(512) NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    confirmed_flag BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS asset_request (
    id UUID DEFAULT public.gen_random_uuid() PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    asset_id UUID NOT NULL UNIQUE,
    status VARCHAR NOT NULL CHECK (status IN ('pending', 'granted', 'rejected')),
    type VARCHAR NOT NULL CHECK (type IN ('SFTP', 'API')),
    additional_info JSONB DEFAULT '{}',
    requested_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);