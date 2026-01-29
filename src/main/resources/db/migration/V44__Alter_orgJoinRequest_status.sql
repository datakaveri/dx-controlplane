ALTER TABLE organization_join_requests
DROP CONSTRAINT organization_join_requests_status_check;

ALTER TABLE organization_join_requests
  ADD CONSTRAINT organization_join_requests_status_check
    CHECK (status IN ('pending', 'granted', 'rejected', 'withdrawn'));
