# Configuration Reference

This document explains every key in [`config.json`](./config.json), the main configuration file for the DX Control Plane server.

## How configuration works

The server is launched by `org.cdpg.dx.deploy.Deployer`, which reads `config.json` and deploys each entry under `modules` as a Vert.x verticle.

Top-level option blocks (`postgresOptions`, `commonOptions`, `keycloakOptions`, …) are **not** read globally. Instead, each module lists the blocks it needs in its `required` array, and `ConfigHelper.mergeRequiredConfigs()` merges those blocks **flat** into that module's own config before deployment. Two consequences:

- A key is only visible to a verticle if its block is listed in that verticle's `required` array (or the key is written directly in the module entry).
- Because blocks are merged flat into one JSON object, a key defined directly on a module can be overridden by a same-named key from a later `required` block.

**Required** below means the code reads the key without a fallback — a missing value causes a startup failure or `null`-driven runtime error. Keys with a default are safe to omit.

---

## Top level

| Key | Type | Required | Description |
|---|---|---|---|
| `version` | string | no | Config file version label. Informational only — not read by code. |
| `zookeepers` | string[] | yes | Zookeeper endpoints (`host:port`) used by the Vert.x cluster manager to form the cluster. |
| `clusterId` | string | yes | Cluster identifier passed to the cluster manager. All nodes that should join the same cluster must use the same value. |
| `postgresOptions` … `auditOptions` | object | — | Named option blocks, documented section by section below. |
| `modules` | object[] | yes | List of verticles to deploy. Startup fails if absent. See [Modules](#modules). |

---

## `postgresOptions`

PostgreSQL connection settings, consumed by `PostgresVerticle` (dx-common).

| Key | Type | Required | Description |
|---|---|---|---|
| `databaseIP` | string | yes | PostgreSQL host address. |
| `databasePort` | number | yes | PostgreSQL port. |
| `databaseName` | string | yes | Database name to connect to. |
| `databaseSchema` | string | yes | Schema used for queries (e.g. `public`). |
| `databaseUserName` | string | yes | Database user. |
| `databasePassword` | string | yes | Database password. |
| `poolSize` | number | yes | Max connections in the Vert.x PG connection pool. |

---

## `commonOptions`

Shared platform settings, required by most modules.

### Deployment mode flags

| Key | Type | Required | Description |
|---|---|---|---|
| `isCentralCatEnabled` | boolean | no (default `false`) | When `true`, the API server also wires up the **central catalogue** infrastructure (secondary Elasticsearch service, central-catalogue item flows). |
| `isEdgeCatalogue` | boolean | no (default `false`) | Marks this deployment as an **edge** catalogue that syncs items to a central catalogue. |
| `isStandalone` | boolean | no (default `false`) | Marks this deployment as **standalone** — no central/edge federation behaviour in item flows. |
| `uploadedBy` | string | yes | Default value stamped on catalogue items' `uploadedBy` field when the publishing user doesn't provide one (e.g. organisation name). May be empty. |
| `kycRequired` | boolean | no (default `false`) | When `true`, users must complete DigiLocker KYC before performing protected actions (see `KYCOptions`). |

### URLs and domains

| Key | Type | Required | Description |
|---|---|---|---|
| `controlPlaneUrl` | string | yes | Public URL of this control plane. Used when constructing item/ingestion metadata and links. |
| `dataPlaneUrl` | string | yes | Public URL of the data plane (resource server) associated with this deployment. |
| `ogcDataPlaneUrl` | string | yes | Public URL of the OGC data plane (OGC resource server). |
| `controlPlaneDomain` | string | yes | Bare domain of the control plane (no scheme). Used for identity/URN purposes. |
| `cosDomain` | string | no (default `""`) | COS (Catalogue Operating System) domain — used as the `iss` (issuer) claim in JWTs minted by the token services. |
| `baseUrl` | string | yes | Base URL substituted into the served OpenAPI spec/docs (placeholder replacement in the API server). |
| `apdURL` | string | yes | URL of the Access Policy Domain (APD) server. Embedded in item metadata and token/policy flows. |
| `publisherPanelUrl` | string | yes | URL of the publisher panel UI. Used to build links in notification emails (must **not** end with `/`). |
| `vocContext` | string | yes | URL of the vocabulary (`voc`) server providing the JSON-LD `@context` for catalogue items. |
| `supportEmail` | string | no (has default) | Support contact shown in API error responses / served docs. |

### Behaviour and limits

| Key | Type | Required | Description |
|---|---|---|---|
| `initialCreditBalance` | number | yes | Credits granted to a user's credit account when it is first created (expires 30 days after creation). |
| `tokenExpirationMinutes` | number | no (default `60`) | Lifetime of JWTs minted by the token endpoints, in minutes. |
| `defaultExpiryDays` | number | yes | Default policy validity period (days) applied when a policy is created without an explicit expiry. |
| `corsAllowedOrigin` | string[] | yes | Origins allowed by the API servers' CORS handler. `"*"` allows all — remove it in production. |
| `appIdRevokeExchange` | string | no (default `revoked-appid`) | RabbitMQ exchange on which App-ID revocation events are published so data planes can invalidate revoked app credentials. |
| `envSuffix` | string | — | **Currently unused** — not referenced anywhere in this codebase or dx-common. Safe to leave as-is; candidate for removal. |

### Elasticsearch indices

| Key | Type | Required | Description |
|---|---|---|---|
| `docIndex` | string | yes | Main Elasticsearch index holding catalogue item documents. |
| `docUserIndex` | string | yes | Elasticsearch index storing extended user profile info not kept in Keycloak (`about`, `experience`, `education`, `projects`, `publications`, `skills`), keyed by `userId`. |
| `deletedDocsIndex` | string | yes | Elasticsearch index where deleted catalogue items are archived. |

---

## `emailOptions`

Used by the AAA email composer (`org.cdpg.dx.aaa.email.util.EmailComposer`) — account/organisation notification emails.

| Key | Type | Required | Description |
|---|---|---|---|
| `emailSender` | string | yes | `From:` address for outgoing mails. |
| `platformName` | string | yes | Full platform name used in email subject/body templates. |
| `platformShortName` | string | yes | Short platform name used in templates. |
| `cosAdminEmailId` | string | yes | COS admin address that receives admin-facing notifications. |
| `TGDxUrl` | string | yes | Admin portal / catalogue URL embedded in email bodies. |

## `emailConfig`

Used by the ACL/policy email composers — access-request and policy notification emails. Overlaps with `emailOptions` because different composers read different blocks; keep both consistent.

| Key | Type | Required | Description |
|---|---|---|---|
| `emailSender` | string | yes | `From:` address for policy/access-request mails. |
| `emailSupport` | string[] | yes | Support addresses CC'd/linked in policy mails. |
| `publisherPanelUrl` | string | yes | Publisher panel URL used to build action links in mails (must **not** end with `/`). |
| `senderName` | string | yes | Human-readable sender display name. |

## `emailNotification`

RabbitMQ topology for asynchronous email dispatch (producers publish here; `EmailVerticle` consumes the queue).

| Key | Type | Required | Description |
|---|---|---|---|
| `emailQueue` | string | no (default `email-notification`) | Queue the email consumer reads from. |
| `emailExchange` | string | no (default `Email`) | Exchange email events are published to. |
| `emailRoutingKey` | string | no (default `##`) | Routing key used when publishing email events. |

---

## `keycloakOptions`

Keycloak admin access plus JWT validation settings.

| Key | Type | Required | Description |
|---|---|---|---|
| `keycloakRealm` | string | yes | Keycloak realm containing platform users. |
| `keycloakUrl` | string | yes | Base URL of the Keycloak server. |
| `keycloakAdminClientId` | string | yes | Client ID of the admin service account (e.g. `admin-cli`) used for user management via the Keycloak Admin API. |
| `keycloakAdminClientSecret` | string | yes | Secret for the admin client. |
| `issuers` | object | yes | Trusted JWT issuers map — see below. |

### `keycloakOptions.issuers`

Consumed by the `JwksResolver` in dx-common. Contains three scalar tuning keys plus one entry **per trusted issuer**, keyed by the issuer string that appears in incoming JWTs' `iss` claim.

| Key | Type | Required | Description |
|---|---|---|---|
| `jwtIgnoreExpiry` | boolean | — | When `true`, expired JWTs are accepted. **Testing only — never enable in production.** |
| `jwtLeeway` | number | — | Clock-skew tolerance (seconds) when validating JWT time claims. |
| `jwksRefreshIntervalMs` | number | — | How often cached JWKS keys are refreshed, in milliseconds (e.g. `21600000` = 6 h). |
| `<issuer>` | object | — | One entry per trusted issuer. Fields: `type` — `"internal"` (keys fetched from the platform's own Keycloak) or `"remote"` (keys fetched from `jwksUrl`); `jwksUrl` — JWKS endpoint, required when `type` is `"remote"`; `audience` — allowed `aud` values (empty array = no audience restriction). |

---

## `KYCOptions`

DigiLocker OAuth2 integration for Aadhaar-based KYC (used when `kycRequired` is `true`).

| Key | Type | Required | Description |
|---|---|---|---|
| `digilockerTokenUrl` | string | yes | DigiLocker OAuth2 token endpoint (authorization-code exchange). |
| `digilockerAadhaarUrl` | string | yes | DigiLocker endpoint for fetching eAadhaar data after token exchange. |
| `clientId` | string | yes | OAuth2 client ID registered with DigiLocker. |
| `clientSecret` | string | yes | OAuth2 client secret. |
| `redirectUri` | string | yes | Redirect URI registered with DigiLocker; must match the one used by the frontend when starting the flow. |

---

## `jwtKeystoreOptions`

Keystore holding the private key used to **sign** platform-issued JWTs.

| Key | Type | Required | Description |
|---|---|---|---|
| `keystorePath` | string | yes | Filesystem path to the JKS keystore (e.g. the bundled `keystore.jks`). |
| `keystorePassword` | string | yes | Keystore password. |

---

## `databrokerOptions`

RabbitMQ (data broker) connection settings, consumed by `BaseDataBrokerVerticle` (dx-common) and the publish paths.

| Key | Type | Required | Description |
|---|---|---|---|
| `dataBrokerIP` | string | yes | RabbitMQ host. |
| `dataBrokerPort` | number | yes | RabbitMQ AMQP port used by the client connection. |
| `dataBrokerManagementPort` | number | yes | RabbitMQ HTTP management API port (used for vhost/queue administration). |
| `dataBrokerUserName` | string | yes | RabbitMQ user. |
| `dataBrokerPassword` | string | yes | RabbitMQ password. |
| `prodVhost` | string | yes | Name of the production vhost (adapter/data traffic). |
| `internalVhost` | string | yes | Name of the internal vhost (platform-internal messaging: audit, email, …). |
| `externalVhost` | string | yes | Name of the external vhost (consumer-facing streams). |
| `connectionTimeout` | number | yes | AMQP connection timeout (ms). |
| `requestedHeartbeat` | number | yes | AMQP heartbeat interval (seconds). |
| `handshakeTimeout` | number | yes | AMQP handshake timeout (ms). |
| `requestedChannelMax` | number | yes | Max channels per connection. |
| `networkRecoveryInterval` | number | yes | Delay (ms) between automatic reconnection attempts. |
| `automaticRecoveryEnabled` | string | — | **Not read by code** — automatic recovery is always enabled unconditionally. Kept for backwards compatibility. |
| `brokerAmqpIp` | string | yes | Externally reachable AMQP host advertised to subscribers (may differ from `dataBrokerIP` behind NAT/LB). |
| `brokerAmqpPort` | number | yes | Externally reachable AMQP port advertised to subscribers. |
| `portSsl` | boolean | no (default `false`) | Whether the advertised/client AMQP connection uses TLS. |
| `publishExchange` | string | yes | Exchange used for publishing RPC adapter requests from the API server. |

---

## `auditOptions`

Auditing / metering topology.

| Key | Type | Required | Description |
|---|---|---|---|
| `auditingExchange` | string | yes | Exchange audit records are published to. |
| `auditingRoutingKey` | string | yes | Routing key for audit records. |
| `auditingQueue` | string | yes | Queue the local audit consumer reads from. |
| `leaderboardQueue` | string | yes | Queue the leaderboard consumer reads from (vote/download events). Required since the leaderboard feature was introduced — the code falls back to `leaderboard` if absent, but the queue must exist and match the broker topology. |
| `isRemoteAudit` | boolean | no (default `false`) | When `true`, audit records go to the remote auditing server instead of being consumed locally. |

---

## Modules

Each entry in `modules` describes one verticle deployment.

### Keys common to every module

| Key | Type | Required | Description |
|---|---|---|---|
| `id` | string | yes | Fully-qualified class name of the verticle to deploy. |
| `verticleInstances` | number | yes | Number of instances of this verticle to deploy. |
| `isWorkerVerticle` | boolean | no (default `false`) | Deploy on a worker thread pool (for blocking workloads) instead of the event loop. |
| `threadPoolName` | string | when worker | Name of the dedicated worker pool (only read when `isWorkerVerticle` is `true`). |
| `threadPoolSize` | number | when worker | Size of that worker pool. |
| `required` | string[] | no | Names of top-level option blocks to merge into this module's config (see [How configuration works](#how-configuration-works)). |

### Module-specific keys

**`EmailVerticle`** — SMTP mail sender consuming the email queue:

| Key | Type | Required | Description |
|---|---|---|---|
| `emailHostName` | string | yes | SMTP server hostname. |
| `emailPort` | number | yes | SMTP port (e.g. `587` for STARTTLS). |
| `emailUserName` | string | yes | SMTP username. |
| `emailPassword` | string | yes | SMTP password. |
| `notifyByEmail` | boolean | no (default `true`) | Master switch — when `false`, mails are composed but not sent. |

**`CatalogueVerticle`** — client for an external DX catalogue server. *Note: no other component currently consumes this service — this module block is effectively unused.*

| Key | Type | Required | Description |
|---|---|---|---|
| `catServerHost` | string | yes | Catalogue server host. |
| `catServerPort` | number | yes | Catalogue server port. |
| `dxCatalogueBasePath` | string | yes | Base path of the catalogue API. |

**`ElasticsearchVerticle`** — Elasticsearch client service. Deployable multiple times against different clusters via an optional `serviceAddress` key (defaults to the primary ES service address):

| Key | Type | Required | Description |
|---|---|---|---|
| `databaseIP` | string | yes | Elasticsearch host. |
| `databasePort` | number | yes | Elasticsearch port. |
| `databaseUser` | string | yes | Elasticsearch user. |
| `databasePassword` | string | yes | Elasticsearch password. |

⚠️ These key names collide with `postgresOptions` — do **not** add `postgresOptions` to this module's `required` list, or the merge will overwrite the ES connection values.

**`ApiServerVerticle`** (AAA) and **`ApdApiServerVerticle`** (ACL/APD) — the two HTTP API servers:

| Key | Type | Required | Description |
|---|---|---|---|
| `httpPort` | number | no (has default) | HTTP listen port (`8080` AAA, `8444` APD in the example). |
| `urnPrefix` | string | no (has default) | Prefix for URNs in API responses (e.g. `urn:dx:controlPlane:`). |

**`SchedulerVerticle`** — periodic job that expires ended subscriptions (deletes their queues/bindings):

| Key | Type | Required | Description |
|---|---|---|---|
| `schedulerTimeIntervalInMinutes` | number | no (default `2`) | How often the expiry check runs, in minutes. |

**`GrpcServerVerticle`** — gRPC endpoint used by data planes for token/app-ID verification:

| Key | Type | Required | Description |
|---|---|---|---|
| `grpcPort` | number | yes | gRPC listen port. |
| `keycloakJwksUrl` | string | no (has default) | JWKS endpoint used to validate service-client JWTs presented over gRPC. |
| `grpcAllowedServiceClients` | string[] | no (default `["svc-dx-dataplane"]`) | Keycloak service-account client IDs allowed to call the gRPC API. |

**`PostgresVerticle`**, **`DataBrokerVerticle`**, **`DelegationVerticle`** — no module-specific keys; fully configured via their `required` blocks.