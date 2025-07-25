-- Step 1: Add column as nullable
ALTER TABLE organization_create_requests
ADD COLUMN manager_email VARCHAR;

-- Step 2: Populate each row with unique value (if needed)
UPDATE organization_create_requests
SET manager_email = CONCAT('temp_email_', id, '@example.com'); -- 'id' must be unique

-- Step 3: Alter column to NOT NULL and UNIQUE
ALTER TABLE organization_create_requests
ALTER COLUMN manager_email SET NOT NULL;

ALTER TABLE organization_create_requests
ADD CONSTRAINT unique_manager_email UNIQUE (manager_email);