
------------------------------------------------------------
-- ENUM: bookmark_entity_type
------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_type WHERE typname = 'bookmark_entity_type'
    ) THEN
CREATE TYPE bookmark_entity_type AS ENUM (
            'DATABANK',
            'AI_MODEL',
            'USECASE',
            'APP'
        );
END IF;
END$$;


------------------------------------------------------------
-- bookmarks TABLE
------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bookmarks
(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    entity_type bookmark_entity_type NOT NULL,
    entity_id UUID NOT NULL,
    created_at  timestamp without time zone DEFAULT now() NOT NULL,

    UNIQUE (user_id, entity_type, entity_id)
);



------------------------------------------------------------
-- Indexes
------------------------------------------------------------
CREATE INDEX idx_bookmarks_user ON bookmarks (user_id);
CREATE INDEX idx_bookmarks_entity ON bookmarks (entity_type, entity_id);

GRANT USAGE ON SCHEMA ${flyway:defaultSchema} TO ${authUser};
GRANT SELECT, INSERT, DELETE ON bookmarks TO ${authUser};

