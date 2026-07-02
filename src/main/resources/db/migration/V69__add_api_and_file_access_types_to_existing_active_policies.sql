UPDATE policy p
SET constraints = jsonb_set(
    COALESCE(p.constraints::jsonb, '{}'::jsonb),
    '{access}',
    (
        WITH existing AS (
            SELECT
                CASE
                    WHEN jsonb_typeof(value) = 'object' THEN value
                    WHEN jsonb_typeof(value) = 'string'
                        THEN jsonb_build_object('accessType', trim(both '"' from value::text))
                END AS obj
            FROM jsonb_array_elements(
                COALESCE(p.constraints::jsonb->'access', '[]'::jsonb)
            )
        )
        SELECT jsonb_agg(DISTINCT obj)
        FROM (
            SELECT obj
            FROM existing
            WHERE obj IS NOT NULL

            UNION

            SELECT '{"accessType":"api"}'::jsonb

            UNION

            SELECT '{"accessType":"file"}'::jsonb
        ) t
    ),
    true
)::json
WHERE status = 'ACTIVE';