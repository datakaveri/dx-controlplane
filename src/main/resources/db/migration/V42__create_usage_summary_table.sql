-- -------------------------------------------------------------------
-- Usage summary table
-- Stores pre-aggregated usage metrics for dashboard (projection table)
-- -------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS usage_summary (
    description TEXT NOT NULL,
    count        BIGINT NOT NULL DEFAULT 0,
    size         BIGINT NOT NULL DEFAULT 0,
    updated_at   TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT usage_summary_pk PRIMARY KEY (description)
    );

-- Set table owner
ALTER TABLE usage_summary OWNER TO ${flyway:user};

-- Schema usage
GRANT USAGE ON SCHEMA ${flyway:defaultSchema} TO ${authUser};

-- Table permissions
GRANT SELECT, INSERT, UPDATE, DELETE
    ON TABLE usage_summary
    TO ${authUser};
