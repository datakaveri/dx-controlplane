-- Rename column access_type → query_type
ALTER TABLE resource_servers
RENAME COLUMN access_type TO query_type;