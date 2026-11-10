-- Add the updated_at column
ALTER TABLE client_credentials
ADD COLUMN updated_at TIMESTAMP WITHOUT TIME ZONE;

-- Create trigger to automatically set updated_at on insert or update
-- Assuming your existing function update_modified() handles updated_at
CREATE TRIGGER trg_update_modified
BEFORE INSERT OR UPDATE ON client_credentials
FOR EACH ROW
EXECUTE FUNCTION update_modified();