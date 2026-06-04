ALTER TABLE policy
ADD COLUMN IF NOT EXISTS request_id UUID NULL;

CREATE TYPE policy_type_enum AS ENUM ('INDIVIDUAL', 'GROUP');

ALTER TABLE policy
ADD COLUMN IF NOT EXISTS policy_type policy_type_enum;

UPDATE policy
SET policy_type =
  CASE
    WHEN constraints->'subjects' IS NOT NULL THEN 'GROUP'::policy_type_enum
    ELSE 'INDIVIDUAL'::policy_type_enum
  END
WHERE policy_type IS NULL;

ALTER TABLE policy
ALTER COLUMN policy_type SET NOT NULL;