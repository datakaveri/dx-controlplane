ALTER TABLE user_activity_audit_log
    ADD COLUMN IF NOT EXISTS delegatee_id UUID;

ALTER TABLE user_activity_audit_log
DROP
COLUMN IF EXISTS delegator_id;

ALTER TABLE user_activity_audit_log
DROP
COLUMN IF EXISTS delegator_role;
