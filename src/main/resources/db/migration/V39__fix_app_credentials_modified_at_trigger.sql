-- Fix trigger to update modified_at instead of updated_at
-- Do NOT touch existing migrations

-- 1. Create a table-specific trigger function
CREATE OR REPLACE FUNCTION aaa.update_app_credentials_modified_at()
RETURNS trigger AS $$
BEGIN
  NEW.modified_at = CURRENT_TIMESTAMP;
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. Drop incorrect trigger if it exists
DROP TRIGGER IF EXISTS update_app_credentials_modified
ON aaa.app_credentials;

-- 3. Create correct trigger
CREATE TRIGGER update_app_credentials_modified
  BEFORE INSERT OR UPDATE
                     ON aaa.app_credentials
                     FOR EACH ROW
                     EXECUTE PROCEDURE aaa.update_app_credentials_modified_at();
