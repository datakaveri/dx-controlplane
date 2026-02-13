--CREATE TYPE publish_status_enum AS ENUM ('ACTIVE', 'PENDING');

/*CREATE TABLE IF NOT EXISTS asset_status (
    asset_id UUID PRIMARY KEY,

    data_upload_status BOOLEAN NOT NULL,
    publish_status publish_status_enum NOT NULL,

    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()

);*/


CREATE TABLE IF NOT EXISTS asset_leaderboard (
    asset_id UUID PRIMARY KEY,
    asset_name TEXT NOT NULL,
    asset_type TEXT NOT NULL,
    access_policy TEXT NOT NULL,


    provider_id UUID DEFAULT NULL,
    --provider_name TEXT NOT NULL,

    organization_id UUID   DEFAULT NULL,
    organization_name TEXT DEFAULT NULL,
    organization_type TEXT DEFAULT NULL,

    downloads BIGINT NOT NULL DEFAULT 0,
    likes BIGINT NOT NULL DEFAULT 0,
    views BIGINT NOT NULL DEFAULT 0,

    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
    );

CREATE INDEX idx_asset_lb_asset_type
    ON asset_leaderboard (asset_type);

CREATE INDEX idx_asset_lb_organization_type
    ON asset_leaderboard (organization_type);

CREATE INDEX idx_asset_lb_access_policy
    ON asset_leaderboard (access_policy);

CREATE INDEX idx_asset_lb_lb_downloads
    ON asset_leaderboard (downloads);

CREATE INDEX idx_asset_lb_lb_likes
    ON asset_leaderboard (likes);

CREATE INDEX idx_asset_lb_views
    ON asset_leaderboard (views);



CREATE TABLE IF NOT EXISTS provider_leaderboard (
    provider_id UUID PRIMARY KEY,
    provider_name TEXT NOT NULL,

    organization_id UUID DEFAULT NULL,
    organization_name TEXT DEFAULT NULL,
    organization_type TEXT DEFAULT NULL,

    published_databank BIGINT NOT NULL DEFAULT 0,
    published_ai_models BIGINT NOT NULL DEFAULT 0,
    published_usecases BIGINT NOT NULL DEFAULT 0,
    total_published BIGINT NOT NULL DEFAULT 0,

    downloads BIGINT NOT NULL DEFAULT 0,
    likes BIGINT NOT NULL DEFAULT 0,
    views BIGINT NOT NULL DEFAULT 0,

    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
    );

CREATE INDEX idx_pro_lb_organization_type
    ON provider_leaderboard (organization_type);

CREATE INDEX idx_pro_lb_total_published
    ON provider_leaderboard (total_published);

CREATE INDEX idx_pro_lb_lb_downloads
    ON provider_leaderboard (downloads);

CREATE INDEX idx_pro_lb_lb_likes
    ON provider_leaderboard (likes);

CREATE INDEX idx_pro_lb_views
    ON provider_leaderboard (views);


CREATE TABLE IF NOT EXISTS organization_leaderboard (
    organization_id UUID PRIMARY KEY,
    organization_name TEXT NOT NULL,
    organization_type TEXT NOT NULL,

   -- total_members INT NOT NULL DEFAULT 0,

    published_databank BIGINT NOT NULL DEFAULT 0,
    published_ai_models BIGINT NOT NULL DEFAULT 0,
    published_usecases BIGINT NOT NULL DEFAULT 0,
    total_published BIGINT NOT NULL DEFAULT 0,

    downloads BIGINT NOT NULL DEFAULT 0,
    likes BIGINT NOT NULL DEFAULT 0,
    views BIGINT NOT NULL DEFAULT 0,

    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT now()
    );

CREATE INDEX idx_org_lb_organization_type
    ON organization_leaderboard (organization_type);

CREATE INDEX idx_org_lb_total_published
    ON organization_leaderboard (total_published);

CREATE INDEX idx_org_lb_downloads
    ON organization_leaderboard (downloads);

CREATE INDEX idx_org_lb_likes
    ON organization_leaderboard (likes);

CREATE INDEX idx_org_lb_views
    ON organization_leaderboard (views);



GRANT ALL PRIVILEGES
ON TABLE
--asset_status,
asset_leaderboard,
provider_leaderboard,
organization_leaderboard
TO ${authUser};






