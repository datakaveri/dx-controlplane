ALTER TABLE organizations
ADD COLUMN organisation_documents VARCHAR;

ALTER TABLE organization_create_requests
ADD COLUMN organisation_documents VARCHAR;
