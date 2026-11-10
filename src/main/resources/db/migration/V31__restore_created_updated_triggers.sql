-- Create created_at trigger function (idempotent)
CREATE OR REPLACE FUNCTION update_created()
RETURNS trigger AS $$
BEGIN
    NEW.created_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Create updated_at trigger function (idempotent)
CREATE OR REPLACE FUNCTION update_modified()
RETURNS trigger AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Drop old triggers if missing or corrupted (safe)
DROP TRIGGER IF EXISTS update_ua_created ON request;
DROP TRIGGER IF EXISTS update_ua_modified ON request;

-- Recreate triggers
CREATE TRIGGER update_ua_created
BEFORE INSERT ON request
FOR EACH ROW EXECUTE PROCEDURE update_created();

CREATE TRIGGER update_ua_modified
BEFORE INSERT OR UPDATE ON request
FOR EACH ROW EXECUTE PROCEDURE update_modified();
