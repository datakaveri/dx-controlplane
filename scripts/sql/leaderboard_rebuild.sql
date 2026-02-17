-- ============================================================
-- LEADERBOARD FULL REBUILD
-- Depends on: aaa.asset_visibility_snapshot (from ES backfill)
-- ============================================================

BEGIN;

-- ------------------------------------------------------------
-- Optional: prevent concurrent rebuilds
-- ------------------------------------------------------------
SELECT pg_advisory_lock(900001);

-- ============================================================
-- 1️⃣ ASSET LEADERBOARD
-- ============================================================
DELETE FROM asset_leaderboard;

INSERT INTO  asset_leaderboard (
    asset_id,
    asset_name,
    asset_type,
    access_policy,
    data_upload_status,
    publish_status,
    provider_id,
    provider_name,
    organization_id,
    organization_name,
    organization_type,
    views,
    downloads,
    likes,
    updated_at
)
SELECT
    avs.asset_id,
    avs.asset_name,
    avs.asset_type,
    avs.access_policy,
    TRUE        AS data_upload_status,
    'ACTIVE'    AS publish_status,
    avs.provider_id,
    ou.user_name        AS provider_name,
    avs.organization_id,
    avs.organization_name,
    avs.organization_type,
    COALESCE(ac.views, 0),
    COALESCE(ac.downloads, 0),
    COALESCE(lc.likes, 0),
    NOW()
FROM aaa.asset_visibility_snapshot avs

         LEFT JOIN aaa.organization_users ou
                   ON ou.user_id = avs.provider_id

         LEFT JOIN (
    SELECT
        asset_id,
        COUNT(*) FILTER (WHERE action = 'View')     AS views,
        COUNT(*) FILTER (WHERE action = 'Download') AS downloads
    FROM aaa.user_activity_audit_log
    WHERE log_type = 'ASSET'
    GROUP BY asset_id
) ac
                   ON avs.asset_id = ac.asset_id

         LEFT JOIN (
    SELECT
        asset_id,
        COUNT(*) AS likes
    FROM aaa.user_interactions
    WHERE is_liked = true
    GROUP BY asset_id
) lc
                   ON avs.asset_id = lc.asset_id

WHERE avs.provider_id IS NOT NULL

    ON CONFLICT (asset_id)
DO UPDATE SET
    asset_name          = EXCLUDED.asset_name,
           asset_type          = EXCLUDED.asset_type,
           access_policy       = EXCLUDED.access_policy,
           data_upload_status  = TRUE,
           publish_status      = 'ACTIVE',
           provider_id         = EXCLUDED.provider_id,
           provider_name       = EXCLUDED.provider_name,
           organization_id     = EXCLUDED.organization_id,
           organization_name   = EXCLUDED.organization_name,
           organization_type   = EXCLUDED.organization_type,
           views               = EXCLUDED.views,
           downloads           = EXCLUDED.downloads,
           likes               = EXCLUDED.likes,
           updated_at          = NOW();

-- ============================================================
-- 2️⃣ PROVIDER LEADERBOARD
-- ============================================================

DELETE  FROM provider_leaderboard;

INSERT INTO provider_leaderboard (
    provider_id,
    provider_name,
    organization_id,
    organization_name,
    organization_type,
    published_databank,
    published_ai_models,
    published_usecases,
    total_published,
    downloads,
    likes,
    updated_at
)
SELECT
    al.provider_id,

    MAX(al.provider_name)                       AS provider_name,
    MIN(al.organization_id::text)::uuid         AS organization_id,
    MAX(al.organization_name)                   AS organization_name,
    MAX(al.organization_type)                   AS organization_type,

    COUNT(*) FILTER (WHERE al.asset_type = 'DATABANK') AS published_databank,
    COUNT(*) FILTER (WHERE al.asset_type = 'AI_MODEL') AS published_ai_models,
    COUNT(*) FILTER (WHERE al.asset_type = 'USECASE')  AS published_usecases,

    COUNT(*)                       AS total_published,
    SUM(al.downloads)              AS downloads,
    SUM(al.likes)                  AS likes,
    NOW()
FROM aaa.asset_leaderboard al
WHERE al.provider_id IS NOT NULL
GROUP BY al.provider_id
    ON CONFLICT (provider_id)
DO UPDATE SET
    provider_name        = EXCLUDED.provider_name,
           organization_id      = EXCLUDED.organization_id,
           organization_name    = EXCLUDED.organization_name,
           organization_type    = EXCLUDED.organization_type,
           published_databank   = EXCLUDED.published_databank,
           published_ai_models  = EXCLUDED.published_ai_models,
           published_usecases   = EXCLUDED.published_usecases,
           total_published      = EXCLUDED.total_published,
           downloads            = EXCLUDED.downloads,
           likes                = EXCLUDED.likes,
           updated_at           = NOW();

-- ============================================================
-- 3️⃣ ORGANIZATION LEADERBOARD
-- ============================================================

DELETE  FROM organization_leaderboard;

INSERT INTO organization_leaderboard (
    organization_id,
    organization_name,
    organization_type,
    published_databank,
    published_ai_models,
    published_usecases,
    total_published,
    downloads,
    likes,
    views,
    updated_at
)
SELECT
    al.organization_id,

    MAX(al.organization_name)                   AS organization_name,
    MAX(al.organization_type)                   AS organization_type,

    COUNT(*) FILTER (WHERE al.asset_type = 'DATABANK') AS published_databank,
    COUNT(*) FILTER (WHERE al.asset_type = 'AI_MODEL') AS published_ai_models,
    COUNT(*) FILTER (WHERE al.asset_type = 'USECASE')  AS published_usecases,

    COUNT(*)                       AS total_published,
    SUM(al.downloads)              AS downloads,
    SUM(al.likes)                  AS likes,
    SUM(al.views)                  AS views,
    NOW()
FROM aaa.asset_leaderboard al
WHERE al.organization_id IS NOT NULL
GROUP BY al.organization_id
    ON CONFLICT (organization_id)
DO UPDATE SET
    organization_name    = EXCLUDED.organization_name,
           organization_type    = EXCLUDED.organization_type,
           published_databank   = EXCLUDED.published_databank,
           published_ai_models  = EXCLUDED.published_ai_models,
           published_usecases   = EXCLUDED.published_usecases,
           total_published      = EXCLUDED.total_published,
           downloads            = EXCLUDED.downloads,
           likes                = EXCLUDED.likes,
           views                = EXCLUDED.views,
           updated_at           = NOW();

-- ------------------------------------------------------------
-- Release lock + commit
-- ------------------------------------------------------------
SELECT pg_advisory_unlock(900001);

COMMIT;
