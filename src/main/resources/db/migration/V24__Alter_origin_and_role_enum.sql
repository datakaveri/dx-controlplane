-- Add new value to user_role enum
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_enum e
    JOIN pg_type t ON e.enumtypid = t.oid
    WHERE t.typname = 'user_role' AND e.enumlabel = 'delegate'
  ) THEN
    ALTER TYPE user_role ADD VALUE 'delegate';
  END IF;
END$$;

-- Add new values to origin_system enum
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_enum e
    JOIN pg_type t ON e.enumtypid = t.oid
    WHERE t.typname = 'origin_system' AND e.enumlabel = 'NGSI-LD'
  ) THEN
    ALTER TYPE origin_system ADD VALUE 'NGSI-LD';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_enum e
    JOIN pg_type t ON e.enumtypid = t.oid
    WHERE t.typname = 'origin_system' AND e.enumlabel = 'GATEWAY'
  ) THEN
    ALTER TYPE origin_system ADD VALUE 'GATEWAY';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_enum e
    JOIN pg_type t ON e.enumtypid = t.oid
    WHERE t.typname = 'origin_system' AND e.enumlabel = 'OGC-RS'
  ) THEN
    ALTER TYPE origin_system ADD VALUE 'OGC-RS';
  END IF;
END$$;
