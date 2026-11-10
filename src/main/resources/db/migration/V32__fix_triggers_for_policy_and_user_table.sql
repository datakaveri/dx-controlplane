-- =============================================
-- Fix incorrect triggers for user_table & policy
-- =============================================

SET search_path TO ${flyway:defaultSchema};

-- 1. Drop old buggy triggers safely (if they exist)
DROP TRIGGER IF EXISTS update_ua_created ON policy;
DROP TRIGGER IF EXISTS update_ua_modified ON policy;
DROP TRIGGER IF EXISTS update_ua_created ON user_table;
DROP TRIGGER IF EXISTS update_ua_modified ON user_table;

-- 2. Recreate correct per-table unique triggers

-- POLICY TABLE
CREATE TRIGGER policy_created_trigger
BEFORE INSERT ON policy
FOR EACH ROW EXECUTE FUNCTION update_created();

CREATE TRIGGER policy_updated_trigger
BEFORE INSERT OR UPDATE ON policy
FOR EACH ROW EXECUTE FUNCTION update_modified();

-- USER_TABLE
CREATE TRIGGER user_created_trigger
BEFORE INSERT ON user_table
FOR EACH ROW EXECUTE FUNCTION update_created();

CREATE TRIGGER user_updated_trigger
BEFORE INSERT OR UPDATE ON user_table
FOR EACH ROW EXECUTE FUNCTION update_modified();
