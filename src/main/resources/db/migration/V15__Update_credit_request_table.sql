ALTER TABLE credit_requests
DROP COLUMN amount;

ALTER TABLE credit_requests
ADD COLUMN additional_info JSONB DEFAULT '{}';