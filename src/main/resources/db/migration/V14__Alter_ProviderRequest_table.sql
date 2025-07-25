ALTER TABLE provider_requests
RENAME COLUMN requested_at TO created_at;

ALTER TABLE provider_requests
RENAME COLUMN processed_at TO updated_at;