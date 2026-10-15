-- Gateway security/traffic ledger. Populated by GatewayLogConsumer from the
-- "gateway-logs" RabbitMQ exchange (events published by dx-gateway-go for
-- 401/403/404/5xx outcomes). Deliberately separate from
-- user_activity_audit_log: that table records successful business actions,
-- this one records denied/failed traffic at the platform edge.
CREATE TABLE IF NOT EXISTS gateway_access_log (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id   varchar(64),
    event        varchar(32) NOT NULL,  -- AUTH_FAILED | ACCESS_DENIED | ROUTE_NOT_FOUND | UPSTREAM_ERROR
    user_id      uuid,
    method       varchar(10),
    api          text,
    status       int,
    auth_path    varchar(16),           -- jwt | hmac | appid | none
    fga_relation varchar(64),
    fga_resource varchar(64),
    upstream     text,
    ip_address   varchar(64),
    user_agent   text,
    detail       jsonb,
    created_at   timestamp NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_gateway_access_log_event
    ON gateway_access_log (event, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_gateway_access_log_user
    ON gateway_access_log (user_id) WHERE user_id IS NOT NULL;
