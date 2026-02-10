ALTER TABLE custom_user_role
  ADD COLUMN requested_by UUID,
  DROP COLUMN role;
