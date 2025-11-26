-- Step 1: Add column (nullable to avoid table rewrite)
ALTER TABLE request
ADD COLUMN IF NOT EXISTS constraints JSONB;

-- Step 2: Fill existing rows
UPDATE request
SET constraints = '{}'::jsonb
WHERE constraints IS NULL;

-- Step 3: Apply NOT NULL constraint
ALTER TABLE request
ALTER COLUMN constraints SET NOT NULL;

-- Step 4 (optional but recommended): Add a default
ALTER TABLE request
ALTER COLUMN constraints SET DEFAULT '{}'::jsonb;

-- Step 5: (Optional) Touch updated_at
UPDATE request
SET updated_at = NOW()
WHERE constraints = '{}'::jsonb;
