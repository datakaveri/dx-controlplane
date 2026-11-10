-- Audit records from the Go services dx-marketplace-go and the community
-- service carry origin_server MARKETPLACE and COMMUNITY. Without these values
-- their inserts fail (22P02) and the records are dead-lettered. Mirrors
-- dx-audit-go migration 0002_origin_community_marketplace.
ALTER TYPE origin_server
    ADD VALUE IF NOT EXISTS 'MARKETPLACE';

ALTER TYPE origin_server
    ADD VALUE IF NOT EXISTS 'COMMUNITY';
