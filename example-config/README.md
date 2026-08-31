# ControlPlane — `config.json` Reference


Complete reference for every field in [`config.json`](./config.json): what it configures, what
breaks if it is wrong, and where to obtain its value. Written for whoever deploys and operates the
control plane.

**Where to start.** 1 explains how config blocks reach each verticle — the most common source of
"the key is set, but the code reads `null`". 2 documents every field individually. §3 groups fields
by category (credentials, URLs, tuning knobs, feature flags) so a whole category can be checked at
once. §4 lists fields that still need a decision.

Every field was traced to the code that reads it. Where a field is consumed by the `dx-common`
dependency rather than this repo, the class is named and marked *(dx-common)*.

Code references name the **consuming class only, never a line number** — line numbers go stale on
the first unrelated edit, and this document is meant to outlive that. To locate any field's read
site yourself:

For a field marked *(dx-common)* there will be no hit in this repo — that is expected, and is the
reason the marker exists. Those live in the `org.cdpg.dx:dx-common` dependency.

## 0. Document header

| |                                                                                 |
|---|---------------------------------------------------------------------------------|
| **Service** | controlplane                                                                    |
| **Code repo / branch** | `datakaveri/dx-controlplane` — `dev` (source of truth), `stable/v2.3` (release) |
| **Config path in chart** | `Charts/api-layer/v2/controlplane/example-secrets/secrets/config.json`          |
| **Config schema version** | `1.0` (top-level `version`)                                                     |
| **Maintainer / point of contact** | Ananjay Kumar, Kranthi Guribilli                                                |
| **Produced from** | [`CONFIG-DOC-TEMPLATE.md`](./CONFIG-DOC-TEMPLATE.md)                            |
| **Last updated** | 2026-08-03                                                                      |

Dependency note: `org.cdpg.dx:dx-common:1.0.0-SNAPSHOT` supplies `BaseDeployer`,
`PostgresVerticle`, `ElasticsearchVerticle`, `BaseDataBrokerVerticle`, `AbstractApiServerVerticle`,
`KeycloakClientProvider` and `AuthConstants`. Several config blocks are consumed only there — the
classes do not exist in this repo, which is expected, not a packaging error.

## 1. Top-level structure

| Key | Configures | Consumed by |
|---|---|---|
| `version` | Config schema marker | Nothing — see §4 |
| `zookeepers` | Zookeeper hosts for clustered Vert.x | `BaseDeployer` *(dx-common)* |
| `clusterId` | Cluster identity | Not found — see §4 |
| `commonOptions` | Cross-cutting URLs, catalogue indices, APD identity, feature flags | Every API-serving verticle |
| `KYCOptions` | DigiLocker/Aadhaar KYC integration | `ApiServerVerticle` |
| `auditOptions` | Audit + leaderboard exchange/queue names | `ApiServerVerticle`, `ApdApiServerVerticle`, `DataBrokerVerticle` |
| `postgresOptions` | Postgres connection + pool | `PostgresVerticle` *(dx-common)* |
| `databrokerOptions` | RabbitMQ connection + vhosts | `BaseDataBrokerVerticle` *(dx-common)* |
| `emailConfig` | Sender identity + support CC list | `EmailComposer` (both) |
| `emailNotification` | Email exchange/queue/routing key | `DataBrokerVerticle`, `ApdApiServerVerticle` |
| `emailOptions` | Platform naming + env tagging in emails | `EmailComposer` (both) |
| `jwtKeystoreOptions` | Keystore for signing issued tokens | `TokenControllerFactory`, `AppTokenControllerFactory` |
| `keycloakOptions` | Realm, admin client, accepted JWT issuers | `AbstractApiServerVerticle` *(dx-common)*, `KeycloakClientProvider` *(dx-common)*, `GrpcServerVerticle` |
| `modules` | Verticle deployment list; each entry's `required` array selects which top-level blocks are merged into that verticle's `config()` | `BaseDeployer` *(dx-common)* |

**Elasticsearch index mappings.** The indices named in `commonOptions` are not created by this
service — they must exist before startup. The mappings and settings to create them with ship
alongside this file in [`indices-mappings/`](./indices-mappings/):

| File | Creates | Used for |
|---|---|---|
| [`cat_mappings.json`](./indices-mappings/cat_mappings.json) | catalogue document mapping, 81 fields | `docIndex`, and by extension `deletedDocsIndex` and `centralCatDocIndex` |
| [`cat_settings.json`](./indices-mappings/cat_settings.json) | index settings / analyzers | the same catalogue indices |
| [`userdoc_mappings.json`](./indices-mappings/userdoc_mappings.json) | user-document mapping, 7 fields | `docUserIndex` |

**The `required` array is the key mechanism.** A verticle can only read a top-level block listed in
its own `required`. Adding a config key without adding its block to the consuming module's
`required` yields a silent `null`.

| Module | `required` blocks |
|---|---|
| `PostgresVerticle` | `postgresOptions`, `commonOptions` |
| `EmailVerticle` | `commonOptions`, `databrokerOptions`, `keycloakOptions`, `emailOptions`, `emailConfig` |
| `DataBrokerVerticle` | `databrokerOptions`, `auditOptions`, `emailNotification`, `commonOptions`, `keycloakOptions` |
| `ElasticsearchVerticle` | *(none — settings inlined in the module entry)* |
| `ApiServerVerticle` | `postgresOptions`, `commonOptions`, `keycloakOptions`, `KYCOptions`, `emailOptions`, `emailConfig`, `jwtKeystoreOptions`, `databrokerOptions`, `auditOptions` |
| `ApdApiServerVerticle` | same as above **plus** `emailNotification` |
| `SchedulerVerticle` | *(none)* |
| `DelegationVerticle` | `commonOptions`, `keycloakOptions` |
| `GrpcServerVerticle` | `postgresOptions`, `commonOptions`, `keycloakOptions` |

This section walks through every block in `config.json` using the **`iudx.io`** environment as a reference. Each field shows the dev value and what it does.

---

## 3.1 Cluster & Orchestration

These top-level fields configure the Vert.x cluster manager. They must be set **before any verticle can start**.

```json
{
  "version": "1.0",
  "zookeepers": ["zookeeper-client.zookeeper.svc.cluster.local"],
  "clusterId": "iudx-control-panel-cluster"
}
```

| Field | Dev Value | What It Does |
|---|---|---|
| `version` | `1.0` | Schema marker. No code reads it — documentation only. |
| `zookeepers` | `zookeeper-client.zookeeper.svc.cluster.local` | Zookeeper ensemble for clustered Vert.x. |
| `clusterId` | `iudx-control-panel-cluster` | Cluster identity. |

---

## 3.2 PostgreSQL Connection

All fields consumed by `PostgresVerticle` (`dx-common`). The database and schema must exist before startup. Flyway migrations need DDL rights.

```json
"postgresOptions": {
  "databaseIP": "psql-rw.postgres.svc.cluster.local",
  "databaseName": "iudx_auth",
  "databasePassword": "••••••••",
  "databasePort": 5432,
  "databaseSchema": "aaa",
  "databaseUserName": "postgres",
  "poolSize": 25
}
```

| Field | Dev Value | What It Does |
|---|---|---|
| `databaseIP` | `psql-rw.postgres.svc.cluster.local` | Pooler DNS. Must be the **READ-WRITE** endpoint. |
| `databasePort` | `5432` | Standard Postgres port. |
| `databaseName` | `iudx_auth` | Database name. Convention: `<prefix>_controlplane`. |
| `databaseSchema` | `aaa` | Search path schema. Must match Flyway migrations. |
| `databaseUserName` | `postgres` | DB user. Needs full DML on schema `aaa`. |
| `databasePassword` | `••••••••` | DB password. Source: `Charts/postgresql/secrets/`. |
| `poolSize` | `25` | ⚠️ Connection pool. Size = `max_conn ÷ (replicas × instances)`. Too high → cluster-wide outage. |

**Required `GRANT` statements** (User Privileges are taken care by the flyway migration scripts):

```sql
GRANT CONNECT ON DATABASE iudx_auth TO <user>;
GRANT USAGE, CREATE ON SCHEMA aaa TO <user>;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA aaa TO <user>;
ALTER DEFAULT PRIVILEGES IN SCHEMA aaa
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO <user>;
```

---

## 3.3 Elasticsearch

Connection settings are inlined in the `ElasticsearchVerticle` module entry (**no `required` array**). The indices must be pre-created with the shipped mappings.

```json
// Inside modules[] — ElasticsearchVerticle entry
"databaseIP": "elastic-es-http.elastic.svc.cluster.local",
"databasePort": 9200,
"databaseUser": "iudx-cat-user",
"databasePassword": "••••••••"
```

| Field | Dev Value | What It Does |
|---|---|---|
| `databaseIP` | `elastic-es-http.elastic.svc.cluster.local` | ES cluster DNS. |
| `databasePort` | `9200` | Standard ES HTTP port. |
| `databaseUser` | `iudx-cat-user` | Needs `read` + `write` + `view_index_metadata` on catalogue indices. |
| `databasePassword` | `••••••••` | Source: `Charts/elk/secrets/passwords/elasticsearch-cat-password` |

**Required ES indices (create before startup):**

| Index | Mapping File | Fields |
|---|---|---|
| `iudx__cat` | `indices-mappings/cat_mappings.json` + `cat_settings.json` | 81 fields + analyzers |
| `iudx__cat_deleted_assets` | *(reuses `cat_mappings.json`)* | Soft-deleted catalogue assets |
| `doc_user_index` | `indices-mappings/userdoc_mappings.json` | 7 fields (`userId`, `about`, `education`, etc.) |

---

## 3.4 RabbitMQ (`databrokerOptions`)

The RabbitMQ user needs the **`administrator` tag** because this service administers other users' permissions. All vhosts must exist before startup.

```json
"databrokerOptions": {
  "dataBrokerIP": "rabbitmq.rabbitmq.svc.cluster.local",
  "dataBrokerPort": 5672,
  "dataBrokerManagementPort": 15672,
  "dataBrokerUserName": "rabbitmq_user",
  "dataBrokerPassword": "••••••••",
  "prodVhost": "iudx",
  "internalVhost": "iudx-INTERNAL",
  "externalVhost": "iudx-EXTERNAL",
  "brokerAmqpIp": "iudx.io",
  "brokerAmqpPort": 24567,
  "publishExchange": "rpc-adapter-requests"
  // ... timeout/recovery settings
}
```

| Field | Dev Value | What It Does |
|---|---|---|
| `dataBrokerIP` | `rabbitmq.rabbitmq.svc.cluster.local` | In-cluster RabbitMQ DNS. **Not** the external address. |
| `dataBrokerPort` | `5672` | AMQP port. Must agree with TLS setting. |
| `dataBrokerManagementPort` | `15672` | HTTP management API port. |
| `dataBrokerUserName` | `rabbitmq_user` | 🔑 User with appropriate privileges to access different vhosts. |
| `dataBrokerPassword` | `••••••••` | 🔑 Source: `Charts/oss-layer/databroker/example-secrets/secrets/credential` |
| `prodVhost` | `iudx` | vhost. **CASE-SENSITIVE.** Per-asset exchanges live here. |
| `internalVhost` | `iudx-INTERNAL` | Audit/email/leaderboard messaging vhost. |
| `externalVhost` | `iudx-EXTERNAL` | Unused by this service but client is built at startup — **must exist**. |
| `brokerAmqpIp` | `iudx.io` | **EXTERNAL** endpoint advertised to data consumers. Not the in-cluster IP. |
| `brokerAmqpPort` | `24567` | External AMQPS port. |
| `publishExchange` | `rpc-adapter-requests` | Exchange for RPC adapter requests. |


**Required exchanges & queues (must exist before startup):**

| Object | vHost | Type | Consumed By |
|---|---|---|---|
| `auditing` (exchange) | `iudx-INTERNAL` | direct, durable | `AuditMessageConsumer` |
| `Email` (exchange) | `iudx-INTERNAL` | direct, durable | `EmailMessageConsumer` |
| `rpc-adapter-requests` (exchange) | `iudx` | direct, durable | `ConnectorServiceImpl` |
| `revoked-appid` (exchange) | `iudx` | direct, durable | Dataplane invalidation |
| `auditing` (queue) | `iudx-INTERNAL` | classic, durable | Bound to `auditing` (`##`) |
| `email-notification` (queue) | `iudx-INTERNAL` | classic, durable | Bound to `Email` (`##`) |
| `leaderboard` (queue) | `iudx-INTERNAL` | classic, durable | Bound to `auditing` (`##`) |
| `database` (queue) | `iudx` | classic, durable | NGSI-LD ingestion side |

---

## 3.5 Keycloak & Authentication

Configures the **admin client** (how this service acts on Keycloak) and the **issuer map** (which tokens it accepts).

```jsonc
"keycloakOptions": {
  "keycloakUrl": "https://iudx.io/auth",
  "keycloakRealm": "iudx",
  "keycloakAdminClientId": "admin-client",
  "keycloakAdminClientSecret": "••••••••",
  "issuers": {
    "iudx.io/controlplane": {           // ← internal issuer
      "audience": [], "type": "internal"
    },
    "https://iudx.io/auth/realms/iudx": {
      "audience": [], "type": "remote",        // ← Keycloak issuer
      "jwksUrl": "https://iudx.io/auth/realms/iudx/..."
    },
    "jwksRefreshIntervalMs": 21600000,         // ← mixed in with issuers
    "jwtIgnoreExpiry": true,                   // ← ⚠️ NEVER true in prod
    "jwtLeeway": 30                            // ← no consumer found
  }
}
```

| Field | Dev Value | What It Does |
|---|---|---|
| `keycloakUrl` | `https://iudx.io/auth` | Full URL with scheme, **no trailing `/`**. Admin client and gRPC auth use this. |
| `keycloakRealm` | `iudx` | Case-sensitive realm name. |
| `keycloakAdminClientId` | `admin-client` | 🔑 Confidential client with service accounts enabled. |
| `keycloakAdminClientSecret` | `••••••••` | 🔑 From Keycloak **Clients → Credentials** tab. |
| `issuers` (internal) | `iudx.io/controlplane` | Must equal `commonOptions.cosDomain` **exactly**. |
| `issuers` (remote) | `https://iudx.io/auth/realms/iudx` | Keycloak realm issuer with `jwksUrl`. |
| `jwksRefreshIntervalMs` | `21600000` (6h) | JWKS cache refresh. Safe: 1h–6h. |
| `jwtIgnoreExpiry` | `true` | 🚨 **SECURITY:** Must be `false` in production! Expired tokens accepted. |
| `jwtLeeway` | `30` | ⚠️ No consumer found — appears inert. |

---

## 3.6 Application Settings (`commonOptions`)

Cross-cutting URLs, catalogue indices, APD identity, and feature flags consumed by every API-serving verticle.

| Field | Dev Value | What It Does |
|---|---|---|
| `apdURL` | `iudx.io/controlplane/acl` | APD identity. **No scheme!** Code prepends `https://`. Must match item metadata byte-for-byte. |
| `appIdRevokeExchange` | `revoked-appid` | RabbitMQ exchange for app-ID revocations. |
| `baseUrl` | `iudx.io/controlplane` | Host for API docs. No scheme. Replaces `${HOSTNAME}` (141 times) in the OpenAPI spec. |
| `controlPlaneDomain` | `https://iudx.io/controlplane` | Full URL **WITH** scheme. Used for user-facing links. |
| `controlPlaneUrl` | `iudx.io/controlplane` | Host + path, no scheme. User-facing links. |
| `corsAllowedOrigin` | `["*"]` | 🚨 CORS allow-list. **Replace `*` with explicit origins for prod!** |
| `cosDomain` | `iudx.io/controlplane` | JWT issuer string. Must equal `keycloakOptions.issuers` internal key. |
| `dataPlaneUrl` | `https://iudx.io/dataplane` | Dataplane links in API responses. Must match dataplane ingress. |
| `defaultExpiryDays` | `12` | Default policy validity when no explicit expiry given. |
| `deletedDocsIndex` | `iudx__cat_deleted_assets` | ES index for soft-deleted catalogue assets. |
| `docIndex` | `iudx__cat` | Primary catalogue ES index. Must be created with proper mappings. |
| `docUserIndex` | `doc_user_index` | User-document ES index (7 fields). |
| `initialCreditBalance` | `1000` | Credits seeded on first user creation. |
| `isCentralCatEnabled` | `false` | Feature flag. When `true`, requires 3 additional changes. |
| `kycRequired` | `false` | Feature flag. When `true`, requires entire `KYCOptions` block. |
| `ogcDataPlaneUrl` | `iudx.io/geoserver-s3` | OGC dataplane for geo-server assets. |
| `publisherPanelUrl` | `https://iudx.io` | Validated at startup — only field with fail-fast. |
| `supportEmail` | `support@datakaveri.org` | Substituted for `${SUPPORT_EMAIL}` in OpenAPI spec. **Not** the CC list. |
| `tokenExpirationMinutes` | `60` | Token lifetime. Safe: 15–120. |
| `uploadedBy` | `Centre of Data for Public Good (CDPG), IISc` | Attribution on catalogue items. |
| `vocContext` | `https://voc.forest-stack.iudx.io/` | JSON-LD vocabulary context. **Include trailing `/`.** |

### URL Scheme Rules — follow this table literally

| Field | Scheme? | Trailing `/`? |
|---|---|---|
| `apdURL` | **NO** — code adds `https://` | no |
| `baseUrl` | **NO** — spec adds `https://` | no |
| `controlPlaneDomain` | **YES** | no |
| `controlPlaneUrl` | no | no |
| `cosDomain` | no | no |
| `publisherPanelUrl` | **YES** | **NO** — validated at startup! |
| `vocContext` | **YES** | **YES** (as shipped) |

---

## 3.7 Email Configuration

Email is configured across three blocks (`emailConfig`, `emailOptions`, `emailNotification`) plus SMTP fields inlined in the `EmailVerticle` module. The `emailSender` field is **duplicated** — both must be kept identical.

### `emailConfig`

| Field | Dev Value | What It Does |
|---|---|---|
| `emailSender` | `no-reply.dev@iudx.io` | `From:` address. Must be verified in SMTP provider. |
| `emailSupport` | `[""]` | 🚨 CC list. **No null guard — missing → NPE.** `[""]` is wrong — replace with real addresses. |
| `senderName` | `Maharashtra Agriculture Data Exchange (MahaAgX)` | Display name in `From:` header and templates. |


### `emailOptions`

| Field | Dev Value | What It Does |
|---|---|---|
| `emailSender` | `no-reply.dev@iudx.io` | ⚠️ Duplicate of `emailConfig.emailSender`. Keep identical. |
| `platformName` | `Maharashtra Agriculture Data Exchange (MahaAgX)` | `${PLATFORM_NAME}` in templates. |
| `platformShortName` | `IUDX v2` | Subject-line prefix on access-request emails. |
| `envSuffix` | `DEV` | Appended as `[DEV]` to email subjects. **Must be empty for production!** |
| `cosAdminEmailId` | `admin.cos.iudx.v2@datakaveri.org` | Admin notification recipient. If `null` → admin requests never delivered. |
| `TGDxUrl` | `https://dev.mahaagx.iudx.io` | Admin portal link in notification emails. Legacy name. |

### `emailNotification`

| Field | Dev Value | What It Does |
|---|---|---|
| `emailExchange` | `Email` | Case-sensitive (capital **E**). Exchange for email jobs. |
| `emailQueue` | `email-notification` | Queue `EmailVerticle` consumes from. |
| `emailRoutingKey` | `##` | Literal string (**not** a wildcard). Must match queue binding. |

### `EmailVerticle` (SMTP — inlined in `modules[]`)

| Field | Dev Value | What It Does |
|---|---|---|
| `emailHostName` | `email-smtp.ap-south-1.amazonaws.com` | SMTP relay host. |
| `emailPort` | `587` | SMTP submission port (STARTTLS). |
| `emailUserName` | `••••••••` | 🔑 SMTP username. For SES: **NOT** the IAM access key. |
| `emailPassword` | `••••••••` | 🔑 SMTP password. |
| `notifyByEmail` | `true` | Master switch. `false` **silently** disables ALL mail. |

---

## 3.8 KYC / DigiLocker

All fields conditional on `commonOptions.kycRequired = true`. Dev and prod use **different** DigiLocker registrations.

| Field | Dev Value | What It Does |
|---|---|---|
| `clientId` | `RAE7313316` | 🔑 DigiLocker OAuth client ID. From partner portal. |
| `clientSecret` | `••••••••` | 🔑 DigiLocker client secret. |
| `digilockerTokenUrl` | `https://digilocker.meripehchaan.gov.in/.../token` | OAuth token endpoint. Differs per env. |
| `digilockerAadhaarUrl` | `https://digilocker.meripehchaan.gov.in/.../eaad...` | Aadhaar demographic-fetch endpoint. |
| `redirectUri` | `https://catalogue.iudx.io/kyc` | Must match DigiLocker registration **byte-for-byte**. |

---

## 3.9 Audit Options

| Field | Dev Value | What It Does |
|---|---|---|
| `auditingExchange` | `auditing` | Exchange for audit events. Required when `isRemoteAudit=true`. |
| `auditingQueue` | `test-auditing` | Queue `DataBrokerVerticle` consumes audit from. |
| `auditingRoutingKey` | `##` | Literal string (direct exchange). Mismatch is **silent**. |
| `isRemoteAudit` | `false` | Feature flag. When `true`, requires exchange + broker. |
| `leaderboardQueue` | `leaderboard` | Queue for leaderboard events. Defaults to `"leaderboard"`. |

---

## 3.10 JWT Keystore

| Field | Dev Value | What It Does |
|---|---|---|
| `keystorePath` | `secrets/keystore.jks` | Path relative to container. Mounted by chart. |
| `keystorePassword` | `••••••••` | 🔑 Keystore password. **Rotating invalidates all signed tokens.** |

---

## 3.11 Modules — The `required` Array

This is the single most important mechanism in the config. **A verticle can only read a top-level block listed in its `required` array.** A key can be present and correct in `config.json` and still read as `null` simply because its block was never added here.

---

# Section 4 — Quick Reference

## 4.1 All Credentials

| Credential | System | How to Obtain |
|---|---|---|
| `postgresOptions.databasePassword` | PostgreSQL | `cat Charts/postgresql-cnpg/secrets/passwords/postgres-auth-password` |
| `databrokerOptions.dataBrokerPassword` | RabbitMQ | `cat Charts/databroker/secrets/credentials/admin-password` |
| `ElasticsearchVerticle.databasePassword` | Elasticsearch | `cat Charts/elk/secrets/passwords/elasticsearch-cat-password` |
| `EmailVerticle.emailPassword` | SMTP (SES) | SES console → SMTP credentials (**NOT** IAM keys) |
| `keycloakAdminClientSecret` | Keycloak | Keycloak console → Clients → Credentials tab |
| `KYCOptions.clientSecret` | DigiLocker | DigiLocker partner portal |
| `jwtKeystoreOptions.keystorePassword` | JKS keystore | Generated with `keytool` (see Step 2) |

## 4.2 Feature Flags

| Flag | Dev Value | Effect When True |
|---|---|---|
| `isCentralCatEnabled` | `false` | Requires `centralCatDocIndex` + second ES verticle + `central-openapi.yaml` |
| `kycRequired` | `false` | Requires entire `KYCOptions` block |
| `isRemoteAudit` | `false` | Requires `auditingExchange`, `auditingRoutingKey`, reachable broker |
| `notifyByEmail` | `true` | `false` **silently** disables ALL mail — no error anywhere |
| `jwtIgnoreExpiry` | `true` ⚠️ | 🚨 **SECURITY HOLE** — expired tokens accepted. Must be `false` in prod. |

## 4.3 Values to Change Before Production

| What | Dev Value | Production Action |
|---|---|---|
| `corsAllowedOrigin` | `["*"]` | Replace with explicit origins |
| `emailConfig.emailSupport` | `[""]` | Replace with real support email addresses |
| `emailOptions.envSuffix` | `DEV` | Set to empty string `""` |
| `jwtIgnoreExpiry` | `true` | Set to `false` |
| `publisherPanelUrl` | staging URL | Set to production URL |
| All passwords | Dev credentials | Rotate and use sealed secrets |

