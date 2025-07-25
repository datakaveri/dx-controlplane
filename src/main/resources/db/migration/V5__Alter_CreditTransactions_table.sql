ALTER TABLE credit_transactions
DROP COLUMN user_name,
ADD COLUMN updated_balance DOUBLE PRECISION,
ADD COLUMN requested_at TIMESTAMP WITHOUT TIME ZONE;

-- Migration to make user_id unique in user_credits
ALTER TABLE IF EXISTS user_credits
ADD CONSTRAINT user_credits_user_id_unique UNIQUE (user_id);
