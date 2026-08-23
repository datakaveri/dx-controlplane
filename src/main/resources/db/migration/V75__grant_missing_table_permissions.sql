-- Backfills GRANTs for tables created in V1, V2, V3, V16, V22 and V28, whose
-- migrations created the table but never granted anything to ${authUser}.
-- Everything gets full DML except the two append-only transaction tables below.

GRANT USAGE ON SCHEMA ${flyway:defaultSchema} TO ${authUser};

-- V1 — organisation tables
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE organizations                 TO ${authUser};
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE organization_join_requests    TO ${authUser};
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE organization_users            TO ${authUser};
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE organization_create_requests  TO ${authUser};

-- V2 — credit tables
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE credit_requests               TO ${authUser};
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE compute_role                  TO ${authUser};
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE user_credits                  TO ${authUser};
-- credit_transactions: append-only ledger; rows are never updated or deleted.
GRANT SELECT, INSERT                 ON TABLE credit_transactions           TO ${authUser};

-- V3
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE provider_requests             TO ${authUser};

-- V16
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE asset_request                 TO ${authUser};
-- kyc_transactions: status is updated on callback, but rows are never deleted.
GRANT SELECT, INSERT, UPDATE         ON TABLE kyc_transactions              TO ${authUser};

-- V22
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE resource_servers              TO ${authUser};

-- V28 — delegation tables
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE delegation_grants             TO ${authUser};
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE delegation_scope_constraints  TO ${authUser};
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE delegation_update_requests    TO ${authUser};