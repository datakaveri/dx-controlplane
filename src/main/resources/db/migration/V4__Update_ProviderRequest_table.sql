-- Add ON DELETE CASCADE to organization_id
ALTER TABLE provider_requests
DROP CONSTRAINT provider_requests_organization_id_fkey,
ADD CONSTRAINT provider_requests_organization_id_fkey
  FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE CASCADE;

-- Add ON DELETE CASCADE to user_id
ALTER TABLE provider_requests
DROP CONSTRAINT provider_requests_user_id_fkey,
ADD CONSTRAINT provider_requests_user_id_fkey
  FOREIGN KEY (user_id) REFERENCES organization_users(user_id) ON DELETE CASCADE;