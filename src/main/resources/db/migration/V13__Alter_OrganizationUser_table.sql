ALTER TABLE organization_users
ADD COLUMN official_email VARCHAR;

UPDATE organization_users
SET official_email = CONCAT('temp_email_', id, '@example.com');

ALTER TABLE organization_users
ALTER COLUMN official_email SET NOT NULL;

ALTER TABLE organization_users
ADD CONSTRAINT unique_official_email UNIQUE (official_email);

ALTER TABLE organization_join_requests
ADD COLUMN official_email VARCHAR;

UPDATE organization_join_requests
SET official_email = CONCAT('temp_email_', id, '@example.com');

ALTER TABLE organization_join_requests
ALTER COLUMN official_email SET NOT NULL;