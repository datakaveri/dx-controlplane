# `config.json` Field Reference — dx-controlplane

Complete reference for every field in [`config.json`](./config.json): what it configures, what
breaks if it is wrong, and where to obtain its value. Written for whoever deploys and operates the
control plane.

**Where to start.** §1 explains how config blocks reach each verticle — the most common source of
"the key is set, but the code reads `null`". §2 documents every field individually. §3 groups fields
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

---

## 2. Field blocks

This section walks through every block in **config.json** using the **iudx.io** environment as a reference. Each field shows the dev value and what it does.

## Cluster & Orchestration

These top-level fields configure the Vert.x cluster manager. They must be set before any verticle can start.
{
  "version": "1.0",
  "zookeepers": ["zookeeper-client.zookeeper.svc.cluster.local"],
  "clusterId": "iudx-v2-control-panel-cluster"
}

| **FieldDev ValueWhat It Does**                                                                                              |                                              |                                                                                         |
| --------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------- | --------------------------------------------------------------------------------------- |
| [version](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.vvag3xt2lvj)     | 1.0                                          | Schema marker. No code reads it — documentation only.                                   |
| [zookeepers](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.uz0wmve9iat1) | zookeeper-client.zookeeper.svc.cluster.local | Zookeeper ensemble for clustered Vert.x. Use the client service, not the headless peer. |
| [clusterId](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.n176nk1j7vd0)  | iudx-v2-control-panel-cluster                | Cluster identity. ⚠ No consumer found in code — may be used by deployment tooling only. |

## PostgreSQL Connection

All fields consumed by PostgresVerticle (dx-common). The database and schema must exist before startup. Flyway migrations need DDL rights.
"postgresOptions": {
  "databaseIP": "psql-rw.postgres.svc.cluster.local",
  "databaseName": "iudx_v2_auth",
  "databasePassword": "••••••••",
  "databasePort": 5432,
  "databaseSchema": "aaa",
  "databaseUserName": "postgres",
  "poolSize": 25
}

| **FieldDev ValueWhat It Does**                                                                                                    |                                     |                                                                                               |
| --------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- | --------------------------------------------------------------------------------------------- |
| [databaseIP](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.8bk2xuud7xz)        | psql-rw.postgres.svc.cluster.local | Pooler DNS. Must be the READ-WRITE endpoint.                                                  |
| databasePort                                                                                                                      | 5432                                | Standard Postgres port.                                                                       |
| [databaseName](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.yiu709gen8fx)     | iudx_v2_auth                      | Database name. Convention: <prefix>_controlplane.                                           |
| [databaseSchema](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.5o8x47737bql)   | aaa                                 | Search path schema. Must match Flyway migrations.                                             |
| [databaseUserName](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.n1o28t2q884r) | postgres                            | DB user. Needs full DML on schema aaa.                                                        |
| [databasePassword](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.49vgvianx54i) | ••••••••                            |  DB password. Source: Charts/postgresql/secrets/.                                             |
| [poolSize](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.r7oejaeoba7q)         | 25                                  | ⚠ Connection pool. Size = max_conn ÷ (replicas × instances). Too high → cluster-wide outage. |

**Required GRANT statements:**
GRANT CONNECT ON DATABASE iudx_v2_auth TO <user>;
GRANT USAGE, CREATE ON SCHEMA aaa TO <user>;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA aaa TO <user>;
ALTER DEFAULT PRIVILEGES IN SCHEMA aaa
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO <user>;

## Elasticsearch

Connection settings are inlined in the ElasticsearchVerticle module entry (no required array). The indices must be pre-created with the shipped mappings.
// Inside modules[] — ElasticsearchVerticle entry
"databaseIP": "elastic-es-http.elastic.svc.cluster.local",
"databasePort": 9200,
"databaseUser": "iudx-v2-cat-user",
"databasePassword": "••••••••"

| **FieldDev ValueWhat It Does**                                                                                                    |                                           |                                                                 |
| --------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------- | --------------------------------------------------------------- |
| [databaseIP](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.7qxuc5l3ak1y)       | elastic-es-http.elastic.svc.cluster.local | ES cluster DNS.                                                 |
| databasePort                                                                                                                      | 9200                                      | Standard ES HTTP port.                                          |
| [databaseUser](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.r94nek6wjtwx)     | iudx-v2-cat-user                          | Needs read+write+view_index_metadata on catalogue indices.    |
| [databasePassword](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.g0dnopomwxr1) | ••••••••                                  | Source: Charts/elk/secrets/passwords/elasticsearch-cat-password |

**Required ES indices (create before startup):**

| **IndexMapping FileFields**     |                                                          |                                           |
| ------------------------------- | -------------------------------------------------------- | ----------------------------------------- |
| iudx-v2__cat                  | indices-mappings/cat_mappings.json + cat_settings.json | 81 fields + analyzers                     |
| iudx-v2__cat_deleted_assets | (reuses cat_mappings.json)                              | Soft-deleted catalogue assets             |
| doc_user_index                | indices-mappings/userdoc_mappings.json                  | 7 fields (userId, about, education, etc.) |

## RabbitMQ (databrokerOptions)

The RabbitMQ user needs the administrator tag because this service administers other users' permissions. All vhosts must exist before startup.

```"databrokerOptions": {
  "dataBrokerIP": "rabbitmq.rabbitmq.svc.cluster.local",
  "dataBrokerPort": 5672,
  "dataBrokerManagementPort": 15672,
  "dataBrokerUserName": "admin",
  "dataBrokerPassword": "••••••••",
  "prodVhost": "IUDX-V2",
  "internalVhost": "IUDX-V2-INTERNAL",
  "externalVhost": "IUDX-V2-EXTERNAL",
  "brokerAmqpIp": "iudx.io",
  "brokerAmqpPort": 24567,
  "publishExchange": "rpc-adapter-requests",
  ... // timeout/recovery settings
}

| **FieldDev ValueWhat It Does**                                                                                                            |                                     |                                                                        |
| ----------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------- | ---------------------------------------------------------------------- |
| [dataBrokerIP](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.yoqejj4ezclz)             | rabbitmq.rabbitmq.svc.cluster.local | In-cluster RabbitMQ DNS. Not the external address.                     |
| [dataBrokerPort](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.uq45jmumpfau)           | 5672                                | AMQP port. Must agree with TLS setting.                                |
| [dataBrokerManagementPort](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.j96bvzm279p8) | 15672                               | HTTP management API port.                                              |
| [dataBrokerUserName](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.h6a8z3qsnfzu)       | admin                               | 🔑 Needs administrator TAG — not just permissions.                     |
| [dataBrokerPassword](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.8099s2ulzorv)       | ••••••••                            | 🔑 Source: Charts/databroker/secrets/credentials/admin-password        |
| [prodVhost](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.2tsyxk5buz65)                | IUDX-V2                             | Data vhost. CASE-SENSITIVE. Per-asset exchanges live here.             |
| internalVhost                                                                                                                             | IUDX-V2-INTERNAL                    | Audit/email/leaderboard messaging vhost.                               |
| [externalVhost](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.hdkpa0rturjq)            | IUDX-V2-EXTERNAL                    | Unused by this service but client is built at startup — must exist.    |
| [brokerAmqpIp](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.ypu7mm254if7)             | iudx.io                      | EXTERNAL endpoint advertised to data consumers. Not the in-cluster IP. |
| brokerAmqpPort                                                                                                                            | 24567                               | External AMQPS port.                                                   |
| publishExchange                                                                                                                           | rpc-adapter-requests                | Exchange for RPC adapter requests.                                     |

**Required RabbitMQ permissions:**
rabbitmqctl set_permissions -p IUDX-V2          admin ".\*" ".\*" ".\*"
rabbitmqctl set_permissions -p IUDX-V2-INTERNAL admin ".\*" ".\*" ".\*"
rabbitmqctl set_permissions -p IUDX-V2-EXTERNAL admin "^$" "^$" "^$"
rabbitmqctl set_user_tags admin administrator
**Required exchanges & queues (must exist before startup):**

| **ObjectvHostTypeConsumed By**  |                  |                  |                        |
| ------------------------------- | ---------------- | ---------------- | ---------------------- |
| auditing (exchange)             | IUDX-V2-INTERNAL | direct, durable  | AuditMessageConsumer   |
| Email (exchange)                | IUDX-V2-INTERNAL | direct, durable  | EmailMessageConsumer   |
| rpc-adapter-requests (exchange) | IUDX-V2          | direct, durable  | ConnectorServiceImpl   |
| revoked-appid (exchange)        | IUDX-V2          | direct, durable  | Dataplane invalidation |
| test-auditing (queue)           | IUDX-V2-INTERNAL | classic, durable | Bound to auditing (##) |
| email-notification (queue)      | IUDX-V2-INTERNAL | classic, durable | Bound to Email (##)    |
| leaderboard (queue)             | IUDX-V2-INTERNAL | classic, durable | Bound to auditing (##) |
| database (queue)                | IUDX-V2          | classic, durable | NGSI-LD ingestion side |

## Keycloak & Authentication

Configures the admin client (how this service acts on Keycloak) and the issuer map (which tokens it accepts).
"keycloakOptions": {
  "keycloakUrl": "https://iudx.io/auth",
  "keycloakRealm": "iudx-v2",
  "keycloakAdminClientId": "admin-client",
  "keycloakAdminClientSecret": "••••••••",
  "issuers": {
    "iudx.io/controlplane": {        ← internal issuer
      "audience": [], "type": "internal"
    },
    "https://iudx.io/auth/realms/iudx-v2": {
      "audience": [], "type": "remote",      ← Keycloak issuer
      "jwksUrl": "https://iudx.io/auth/realms/iudx-v2/..."
    },
    "jwksRefreshIntervalMs": 21600000,        ← mixed in with issuers
    "jwtIgnoreExpiry": true,                  ← ⚠ NEVER true in prod
    "jwtLeeway": 30                           ← no consumer found
  }
}

| **FieldDev ValueWhat It Does**                                                                                                             |                                             |                                                                           |
| ------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------- | ------------------------------------------------------------------------- |
| [keycloakUrl](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.qymvg3ulqn8w)               | https://iudx.io/auth                | Full URL with scheme, no trailing /. Admin client and gRPC auth use this. |
| [keycloakRealm](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.20eqqxb03p70)             | iudx-v2                                     | Case-sensitive realm name.                                                |
| [keycloakAdminClientId](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.k80j8bxcuw14)     | admin-client                                | 🔑 Confidential client with service accounts enabled.                     |
| [keycloakAdminClientSecret](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.1tcj9w7wgp03) | ••••••••                                    | 🔑 From Keycloak Clients → Credentials tab.                               |
| [issuers (internal)](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.j9elbbbcve2g)        | iudx.io/controlplane                 | Must equal commonOptions.cosDomain exactly.                               |
| [issuers (remote)](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.1x9gojyehwra)          | https://iudx.io/auth/realms/iudx-v2 | Keycloak realm issuer with jwksUrl.                                       |
| [jwksRefreshIntervalMs](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.s95ion23gt34)     | 21600000 (6h)                               | JWKS cache refresh. Safe: 1h–6h.                                          |
| [jwtIgnoreExpiry](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.uhrr615ixu1)            | true                                        | 🚨 SECURITY: Must be false in production! Expired tokens accepted.        |
| [jwtLeeway](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.f1ere4g4uhwy)                 | 30                                          | ⚠ No consumer found — appears inert.                                      |

 **SECURITY ALERT**   Your dev config has **jwtIgnoreExpiry: true**. This is acceptable for development but must be **false** in staging/production.
** CAUTION**   The issuers map mixes issuer entries with scalar settings (jwksRefreshIntervalMs, jwtIgnoreExpiry, jwtLeeway). Any code iterating issuers as "one entry per issuer" would see these as pseudo-issuers.

## Application Settings (commonOptions)

Cross-cutting URLs, catalogue indices, APD identity, and feature flags consumed by every API-serving verticle.

| **FieldDev ValueWhat It Does**                                                                                                          |                                             |                                                                                           |
| --------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------- | ----------------------------------------------------------------------------------------- |
| [apdURL](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.e28580h8xjdd)                 | iudx.io/controlplane/acl             | APD identity. No scheme! Code prepends https://. Must match item metadata byte-for-byte. |
| [appIdRevokeExchange](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.5lhkh3u1qj85)    | revoked-appid                               | RabbitMQ exchange for app-ID revocations.                                                 |
| [baseUrl](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.2ko3bnbvay74)                | iudx.io/controlplane                 | Host for API docs. No scheme. Replaces ${HOSTNAME} (141 times) in the OpenAPI spec.       |
| [controlPlaneDomain](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.66wfrzv0grzw)     | https://iudx.io/controlplane        | Full URL WITH scheme. Used for user-facing links.                                         |
| controlPlaneUrl                                                                                                                         | iudx.io/controlplane                 | Host + path, no scheme. User-facing links.                                                |
| [corsAllowedOrigin](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.8mf7pcpaphxj)      | ["\*"]                                      | 🚨 CORS allow-list. Replace \* with explicit origins for prod!                            |
| [cosDomain](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.a9rp34a46dfc)              | iudx.io/controlplane                 | JWT issuer string. Must equal keycloakOptions.issuers internal key.                       |
| dataPlaneUrl                                                                                                                            | https://iudx.io/dataplane           | Dataplane links in API responses. Must match dataplane ingress.                           |
| [defaultExpiryDays](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.ek30umj2x7br)      | 12                                          | Default policy validity when no explicit expiry given.                                    |
| deletedDocsIndex                                                                                                                        | iudx-v2__cat_deleted_assets             | ES index for soft-deleted catalogue assets.                                               |
| [docIndex](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.g3zcm8b311pq)               | iudx-v2__cat                              | Primary catalogue ES index. Must be created with proper mappings.                         |
| docUserIndex                                                                                                                            | doc_user_index                            | User-document ES index (7 fields).                                                        |
| [initialCreditBalance](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.zdz8zmqrd0eg)   | 1000                                        | Credits seeded on first user creation.                                                    |
| [isCentralCatEnabled](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.jn6z38r3xnkn)    | false                                       | Feature flag. When true, requires 3 additional changes.                                   |
| kycRequired                                                                                                                             | false                                       | Feature flag. When true, requires entire KYCOptions block.                                |
| ogcDataPlaneUrl                                                                                                                         | iudx.io/geoserver-s3                 | OGC dataplane for geo-server assets.                                                      |
| [publisherPanelUrl](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.okhlnhyjn031)      | https://staging.publisher.tgdex.iudx.io    | ⚠ Must NOT end with /. Validated at startup — only field with fail-fast.                  |
| [supportEmail](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.zce3v8jjoxch)           | support@datakaveri.org                     | Substituted for ${SUPPORT_EMAIL} in OpenAPI spec. Not the CC list.                       |
| [tokenExpirationMinutes](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.fx9rjgpqkvdt) | 60                                          | Token lifetime. Safe: 15–120.                                                             |
| uploadedBy                                                                                                                              | Centre of Data for Public Good (CDPG), IISc | Attribution on catalogue items.                                                           |
| vocContext                                                                                                                              | https://voc.forest-stack.iudx.io/          | JSON-LD vocabulary context. Include trailing /.                                           |

**URL Scheme Rules — follow this table literally:**

| **FieldScheme?Trailing /?** |                          |                            |
| --------------------------- | ------------------------ | -------------------------- |
| apdURL                      | NO — code adds https:// | no                         |
| baseUrl                     | NO — spec adds https:// | no                         |
| controlPlaneDomain          | YES                      | no                         |
| controlPlaneUrl             | no                       | no                         |
| cosDomain                   | no                       | no                         |
| publisherPanelUrl           | YES                      | NO — validated at startup! |
| vocContext                  | YES                      | YES (as shipped)           |

## Email Configuration

Email is configured across three blocks (emailConfig, emailOptions, emailNotification) plus SMTP fields inlined in the EmailVerticle module. The emailSender field is duplicated — both must be kept identical.

### emailConfig

| **FieldDev ValueWhat It Does**                                                                                                |                                                 |                                                                                            |
| ----------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------- | ------------------------------------------------------------------------------------------ |
| [emailSender](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.gpdv1c9s17br)  | no-reply.dev@iudx.io                           | From: address. Must be verified in SMTP provider. ⚠ Duplicate of emailOptions.emailSender. |
| [emailSupport](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.64s57i5lx29x) | [""]                                            | 🚨 CC list. No null guard — missing → NPE. [""] is wrong — replace with real addresses.    |
| senderName                                                                                                                    | Maharashtra Agriculture Data Exchange (MahaAgX) | Display name in From: header and templates.                                                |

 **CAUTION**   Your dev config has **emailSupport: [""]** — an array with one empty string. Replace with real addresses or [].

### emailOptions

| **FieldDev ValueWhat It Does**                                                                                                   |                                                 |                                                                         |
| -------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------- | ----------------------------------------------------------------------- |
| emailSender                                                                                                                      | no-reply.dev@iudx.io                           | ⚠ Duplicate of emailConfig.emailSender. Keep identical.                 |
| platformName                                                                                                                     | Maharashtra Agriculture Data Exchange (MahaAgX) | ${PLATFORM_NAME} in templates.                                         |
| platformShortName                                                                                                                | IUDX v2                                         | Subject-line prefix on access-request emails.                           |
| [envSuffix](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.kbm6b7s715rl)       | DEV                                             | Appended as [DEV] to email subjects. Must be empty for production!      |
| [cosAdminEmailId](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.1cdrk8336lcz) | admin.cos.iudx.v2@datakaveri.org               | Admin notification recipient. If null → admin requests never delivered. |
| TGDxUrl                                                                                                                          | https://dev.mahaagx.iudx.io                    | Admin portal link in notification emails. Legacy name.                  |

### emailNotification

| **FieldDev ValueWhat It Does**                                                                                                 |                    |                                                          |
| ------------------------------------------------------------------------------------------------------------------------------ | ------------------ | -------------------------------------------------------- |
| [emailExchange](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.nrwj388ainsv) | Email              | Case-sensitive (capital E). Exchange for email jobs.     |
| emailQueue                                                                                                                     | email-notification | Queue EmailVerticle consumes from.                       |
| emailRoutingKey                                                                                                                | ##                 | Literal string (not wildcard). Must match queue binding. |

### EmailVerticle (SMTP — inlined in modules[])

| **FieldDev ValueWhat It Does**                                                                                                 |                                     |                                                    |
| ------------------------------------------------------------------------------------------------------------------------------ | ----------------------------------- | -------------------------------------------------- |
| emailHostName                                                                                                                  | email-smtp.ap-south-1.amazonaws.com | SMTP relay host.                                   |
| emailPort                                                                                                                      | 587                                 | SMTP submission port (STARTTLS).                   |
| [emailUserName](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.4lvg4j4vfs8r) | ••••••••                            | 🔑 SMTP username. For SES: NOT the IAM access key. |
| [emailPassword](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.j3q1xzrtxxdn) | ••••••••                            | 🔑 SMTP password.                                  |
| [notifyByEmail](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.tizie5lidkpf) | true                                | Master switch. false silently disables ALL mail.   |

## KYC / DigiLocker

All fields conditional on commonOptions.kycRequired = true. Dev and prod use different DigiLocker registrations.

| **FieldDev ValueWhat It Does**                                                                                                |                                                     |                                                     |
| ----------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- | --------------------------------------------------- |
| [clientId](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.rqjzixha7waj)     | RAE7313316                                          | 🔑 DigiLocker OAuth client ID. From partner portal. |
| [clientSecret](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.xpf2e0dyt16g) | ••••••••                                            | 🔑 DigiLocker client secret.                        |
| digilockerTokenUrl                                                                                                            | https://digilocker.meripehchaan.gov.in/.../token   | OAuth token endpoint. Differs per env.              |
| digilockerAadhaarUrl                                                                                                          | https://digilocker.meripehchaan.gov.in/.../eaad... | Aadhaar demographic-fetch endpoint.                 |
| [redirectUri](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.z9qoi7783oy6)  | https://catalogue.iudx.io/kyc               | Must match DigiLocker registration byte-for-byte.   |

## Audit Options

| **FieldDev ValueWhat It Does**                                                                                                      |               |                                                              |
| ----------------------------------------------------------------------------------------------------------------------------------- | ------------- | ------------------------------------------------------------ |
| auditingExchange                                                                                                                    | auditing      | Exchange for audit events. Required when isRemoteAudit=true. |
| auditingQueue                                                                                                                       | test-auditing | Queue DataBrokerVerticle consumes audit from.                |
| [auditingRoutingKey](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.34ix7dtcduc0) | ##            | Literal string (direct exchange). Mismatch is silent.        |
| isRemoteAudit                                                                                                                       | false         | Feature flag. When true, requires exchange + broker.         |
| leaderboardQueue                                                                                                                    | leaderboard   | Queue for leaderboard events. Defaults to "leaderboard".     |

## JWT Keystore

| **FieldDev ValueWhat It Does**                                                                                                    |                      |                                                               |
| --------------------------------------------------------------------------------------------------------------------------------- | -------------------- | ------------------------------------------------------------- |
| [keystorePath](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.xvv1vrk7tttp)     | secrets/keystore.jks | Path relative to container. Mounted by chart.                 |
| [keystorePassword](https://docs.google.com/document/d/1HI5mwtCrhTMIMeFMVJi0nmv43oGcyHpmIk3YNCzBZCo/edit#bookmark=id.70rfd16dxzj4) | ••••••••             | 🔑 Keystore password. Rotating invalidates all signed tokens. |

## Modules — The required Array

**This is the single most important mechanism in the config.** A verticle can only read a top-level block listed in its required array. A key can be present and correct in config.json and still read as null simply because its block was never added here.
**Gotcha:** Blocks are merged FLAT into one JSON object, so a key written directly on a module can be overwritten by a same-named key from a required block. This is why ElasticsearchVerticle must never list postgresOptions — both define databaseIP and databasePort.

# Section 3 — Quick Reference

## 3.1 All Credentials

| **CredentialSystemHow to Obtain**      |               |                                                                     |
| -------------------------------------- | ------------- | ------------------------------------------------------------------- |
| postgresOptions.databasePassword       | PostgreSQL    | cat Charts/postgresql-cnpg/secrets/passwords/postgres-auth-password |
| databrokerOptions.dataBrokerPassword   | RabbitMQ      | cat Charts/databroker/secrets/credentials/admin-passwor             |
| ElasticsearchVerticle.databasePassword | Elasticsearch | cat Charts/elk/secrets/passwords/elasticsearch-cat-password         |
| EmailVerticle.emailPassword            | SMTP (SES)    | SES console → SMTP credentials (NOT IAM keys)                       |
| keycloakAdminClientSecret              | Keycloak      | Keycloak console → Clients → Credentials tab                        |
| KYCOptions.clientSecret                | DigiLocker    | DigiLocker partner portal                                           |
| jwtKeystoreOptions.keystorePassword    | JKS keystore  | Generated with keytool (see Step 2)                                 |

## 3.2 Feature Flags

| **FlagDev ValueEffect When True** |        |                                                                         |
| --------------------------------- | ------ | ----------------------------------------------------------------------- |
| isCentralCatEnabled               | false  | Requires centralCatDocIndex + second ES verticle + central-openapi.yaml |
| kycRequired                       | false  | Requires entire KYCOptions block                                        |
| isRemoteAudit                     | false  | Requires auditingExchange, auditingRoutingKey, reachable broker         |
| notifyByEmail                     | true   | false silently disables ALL mail — no error anywhere                    |
| jwtIgnoreExpiry                   | true ⚠ | SECURITY HOLE — expired tokens accepted. Must be false in prod.         |

## 3.3 Values to Change Before Production

| **WhatDev ValueProduction Action** |                 |                                           |
| ---------------------------------- | --------------- | ----------------------------------------- |
| corsAllowedOrigin                  | ["\*"]          | Replace with explicit origins             |
| emailConfig.emailSupport           | [""]            | Replace with real support email addresses |
| emailOptions.envSuffix             | DEV             | Set to empty string ""                    |
| jwtIgnoreExpiry                    | true            | Set to false                              |
| publisherPanelUrl                  | staging URL     | Set to production URL                     |
| All passwords                      | Dev credentials | Rotate and use sealed secrets             |