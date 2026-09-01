ALTER TABLE user_activity_audit_log
    ADD COLUMN IF NOT EXISTS delegate_id UUID;

ALTER TABLE user_activity_audit_log
    ADD COLUMN IF NOT EXISTS actor_type VARCHAR(20) NOT NULL DEFAULT 'SELF';

ALTER TABLE user_activity_audit_log
DROP
COLUMN IF EXISTS delegator_id;

ALTER TABLE user_activity_audit_log
DROP
COLUMN IF EXISTS delegator_role;
