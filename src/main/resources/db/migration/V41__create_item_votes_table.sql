


CREATE TYPE vote_type AS ENUM ('LIKE', 'DISLIKE');


CREATE TABLE IF NOT EXISTS item_votes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    entity_id UUID NOT NULL,
    entity_type entity_type NOT NULL,
    vote_type vote_type NOT NULL,
    created_at  timestamp without time zone NOT NULL DEFAULT now(),
    updated_at timestamp without time zone NOT NULL DEFAULT now(),
    CONSTRAINT uq_item_votes_user_entity UNIQUE (user_id, entity_id)
    );

CREATE INDEX IF NOT EXISTS idx_item_votes_entity
    ON item_votes (entity_id);

CREATE INDEX IF NOT EXISTS idx_item_votes_entity_vote
    ON item_votes (entity_id, vote_type);

CREATE INDEX IF NOT EXISTS idx_item_votes_user
    ON item_votes (user_id);

-- Table permissions
GRANT SELECT, INSERT, UPDATE, DELETE
    ON TABLE item_votes
    TO ${authUser};