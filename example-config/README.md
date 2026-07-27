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
| **Produced from** | [`CONFIG-DOC-TEMPLATE.md`](./CONFIG-DOC-TEMPLATE.md) |
| **Last updated** | 2026-07-27                                                                      |

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
| `DataBrokerVerticle` | `databrokerOptions`, `auditOptions`, `emailNotification` |
| `ElasticsearchVerticle` | *(none — settings inlined in the module entry)* |
| `ApiServerVerticle` | `postgresOptions`, `commonOptions`, `keycloakOptions`, `KYCOptions`, `emailOptions`, `emailConfig`, `jwtKeystoreOptions`, `databrokerOptions`, `auditOptions` |
| `ApdApiServerVerticle` | same as above **plus** `emailNotification` |
| `SchedulerVerticle` | *(none)* |
| `DelegationVerticle` | `commonOptions`, `keycloakOptions` |
| `GrpcServerVerticle` | `postgresOptions`, `commonOptions`, `keycloakOptions` |

---

## 2. Field blocks

### `version`

- **Type / format:** string
- **Required:** no
- **Purpose:** Schema marker only. No code reads it.
- **Expected value:** `"1.0"` until the schema changes shape.
- **Example value:** `"1.0"`
- **Default if omitted:** none — nothing breaks.
- **How to obtain:** operator decision; bump when the config shape changes.
- **Failure mode:** none.
- **Change impact:** none at runtime.
- **Notes / gotchas:** Documentation-only. Keep it accurate for humans, not for the app.

### `zookeepers`

- **Type / format:** array of strings; hostname (optionally `host:port`), no scheme
- **Required:** conditional — only in clustered mode
- **Purpose:** Zookeeper ensemble used by `BaseDeployer` *(dx-common)* to build the Vert.x cluster
  manager. Ignored in single-node deployment.
- **Expected value:** in-cluster service DNS names, one per ensemble member.
- **Example value:** `["zookeeper-client.zookeeper.svc.cluster.local"]`
- **Default if omitted:** none — clustered startup fails; standalone unaffected.
- **How to obtain:** the Zookeeper chart's service name in your namespace.
- **Failure mode:** deployment hangs at startup then fails to form a cluster; verticles never report ready.
- **Change impact:** cross-service — all clustered members must agree on the same ensemble.
- **Notes / gotchas:** Use the *client* service, not the headless peer service.

### `clusterId`

- **Type / format:** string
- **Required:** no (see §4)
- **Purpose:** Intended as cluster identity. **No consumer found** in this repo or `dx-common`.
- **Expected value:** `<tenantPrefix>-controlplane-cluster`
- **Example value:** `"cdpg-controlplane-cluster"`
- **Default if omitted:** none — nothing observed to break.
- **Failure mode:** none observed.
- **Notes / gotchas:** Verify against the chart before removing — it may be consumed by
  deployment tooling rather than the app.

---

### `commonOptions.apdURL`

- **Type / format:** string; **host + path, no scheme** — the scheme is prepended by the code
- **Required:** yes
- **Purpose:** This deployment's own APD (Access Policy Domain) identity. Its main job is to tell
  the item service whether a given asset's access policy is governed **internally or by an external
  APD**.

  Catalogue items carry their own `apdURL` in their Elasticsearch metadata (same key name, via the
  `APD_URL` constant at `catalogueService/config/Constants`). When a restricted item is
  accessed, `ItemServiceImpl` (and `CentralItemServiceImpl` in central mode)
  reads that per-item value and hands it to `PolicyVerifyServiceImpl.verify()`, which branches on
  it in `PolicyVerifyServiceImpl`:

  - **item `apdURL` == configured `apdURL`** → *internal APD.* Policy is verified locally via
    `policyService.initiateVerifyPolicy(...)` against this deployment's own policy tables.
  - **item `apdURL` != configured `apdURL`** → *external APD.* The service makes an outbound
    `POST https://<item apdURL>/iudx/acl/apd/v2/verify` (`PolicyVerifyServiceImpl`,
    path from `VERIFY_API_PATH`), forwarding the requester's bearer token, and honours that remote
    decision.

  The same configured-vs-item comparison guards two other paths, which **reject** rather than
  delegate on mismatch: `CatalogueServiceImpl` and
  `acl/policy/service/impl/PolicyServiceImpl` (policy creation against a resource whose
  APD is not this one).

  Secondary uses: `GrpcServerVerticle` and `DelegationVerticle` read it as this
  deployment's APD identity, and it is stamped into gRPC app-ID verification responses at
  `AppIdVerificationGrpcService`.
- **Expected value:** `<domain>/controlplane/acl` — no scheme, no trailing slash. It must equal,
  byte for byte, the `apdURL` written into the metadata of items this deployment governs.
- **Example value:** `"cdpg.org.in/controlplane/acl"`
- **Default if omitted:** `""` in `GrpcServerVerticle`; `null` in `DelegationVerticle` and
  in `aaa/apiserver/ControllerFactory` / `SharedServices`, which feed the item service.
- **How to obtain:** operator decision — must match the ingress route for the ACL API **and** the
  value the catalogue stamps onto items.
- **Failure mode:** the damaging case is a **value that does not match your own items' metadata**.
  Every locally-governed item then takes the external branch, and the service issues outbound
  HTTPS verify calls to a host derived from item metadata — producing access denials, or hangs and
  connect timeouts on the request path, for assets this deployment should have verified locally.
  Nothing in the logs names the config field. If the item's own `apdURL` is missing or blank the
  error is explicit: `Access denied, APD URL missing` (`ItemServiceImpl`).
- **Change impact:** **cross-service and data-affecting.** Changing it re-partitions every item into
  internal vs external. Existing catalogue documents keep the old value in their metadata, so a
  change here without a reindex silently flips governed items to the external path.
- **Notes / gotchas:** No scheme — `PolicyVerifyServiceImpl` prepends `https://`. Including one
  yields `https://https://...`. The comparison is exact string equality, so a trailing slash or a
  case difference is enough to route an internal item externally.

### `commonOptions.appIdRevokeExchange`

- **Type / format:** string; RabbitMQ exchange name
- **Required:** no — defaulted in code
- **Purpose:** Exchange used to broadcast app-ID revocations. Read at
  `GrpcServerVerticle` and `aaa/apiserver/ControllerFactory`.
- **Expected value:** exchange name that exists on the broker.
- **Example value:** `"revoked-appid"`
- **Default if omitted:** `"revoked-appid"` (hardcoded fallback in both call sites).
- **How to obtain:** must match the exchange declared by the broker provisioning step.
- **Failure mode:** revocations publish to a non-existent exchange; tokens stay valid at the
  dataplane after revocation — a security-relevant silent failure.
- **Notes / gotchas:** Present in both `commonOptions` here and referenced by dataplane; keep identical.

### `commonOptions.baseUrl`

- **Type / format:** string; **hostname (+ path), no scheme** — the spec supplies `https://`
- **Required:** yes in practice
- **Purpose:** The host shown in the **served API documentation**. `AbstractApiServerVerticle`
  *(dx-common)* loads the OpenAPI spec, substitutes this value for the literal token `${HOSTNAME}`
  throughout the YAML, and serves the result — the spec at `/apis/spec` and the rendered docs page
  (`docs/apidoc.html`) at `/apis`.

  This is not a cosmetic one-liner: `${HOSTNAME}` appears **141 times** in `docs/openapi.yaml` (129
  in `docs/central-openapi.yaml`). It fills the `servers:` block —

  ```yaml
  servers:
    - url: https://${HOSTNAME}
      description: dynamic server url
  ```

  — and every copy-paste `curl` example in the documentation, e.g.
  `curl --location 'https://${HOSTNAME}/iudx/v2/auth/app'`.
- **Expected value:** the public hostname users should call, no scheme and no trailing slash, since
  the spec already writes `https://` in front of the token.
- **Example value:** `"cdpg.org.in/controlplane"`
- **Default if omitted:** none — the token is left unsubstituted, so the docs advertise a literal
  `https://${HOSTNAME}` server and every curl example is uncopyable.
- **How to obtain:** the public ingress hostname for this service.
- **Failure mode:** entirely confined to the docs — the API itself serves normally. Symptom is
  "Try it out" in the docs page hitting the wrong host, or users reporting that the curl commands
  in the documentation don't work.
- **Change impact:** documentation only; takes effect on restart, no data or cross-service concern.
- **Notes / gotchas:** Including a scheme yields `https://https://…` in every example. Distinct
  from `controlPlaneUrl`, which feeds generated links in API *responses*, not the docs — the values
  look nearly identical but are consumed by different code, so changing one does not change the
  other. Sibling tokens substituted the same way: `${SUPPORT_EMAIL}` from
  `commonOptions.supportEmail`. `${TOKEN}` is **not** substituted — it stays literal in the docs as
  a placeholder for the reader's own bearer token.

### `commonOptions.controlPlaneDomain`

- **Type / format:** string; **full URL with scheme**
- **Required:** yes
- **Purpose:** Read by `aaa/apiserver/ControllerFactory` and passed into controller construction.
- **Expected value:** `https://<domain>/controlplane` — note this is the one field in the group
  that carries a scheme.
- **Example value:** `"https://cdpg.org.in/controlplane"`
- **Default if omitted:** `null` — downstream NPE or malformed links.
- **How to obtain:** the public ingress URL for this service.
- **Failure mode:** links emitted to users point at a malformed host.
- **Notes / gotchas:** Scheme **required** here, absent in `controlPlaneUrl`. Easy to swap by mistake.

### `commonOptions.controlPlaneUrl`

- **Type / format:** string; host + path, **no scheme**
- **Required:** yes
- **Purpose:** Read by `aaa/apiserver/ControllerFactory`.
- **Expected value:** `<domain>/controlplane`
- **Example value:** `"cdpg.org.in/controlplane"`
- **Default if omitted:** `null`.
- **Failure mode:** same class as above — malformed generated URLs.
- **Notes / gotchas:** See `controlPlaneDomain`; these two differ only by scheme.

### `commonOptions.corsAllowedOrigin`

- **Type / format:** array of strings; origins or `*`
- **Required:** yes in practice
- **Purpose:** CORS allow-list, applied by `AbstractApiServerVerticle` *(dx-common)*.
- **Expected value:** explicit scheme+host origins in production; `["*"]` only for dev.
- **Example value:** `["https://dashboard.cdpg.org.in"]`
- **Default if omitted:** unverified in `dx-common`.
- **How to obtain:** the front-end origins that call this API.
- **Failure mode:** browser console CORS errors; API works fine from curl, which makes this a
  frequent false "the API is down" report.
- **Change impact:** none server-side; front-end-visible immediately.
- **Notes / gotchas:** The shipped example is `["*"]` — **tighten before production.**

### `commonOptions.cosDomain`

- **Type / format:** string; used verbatim as a JWT `iss` claim
- **Required:** yes for token issuance
- **Purpose:** Issuer string stamped into tokens this service mints —
  `AppTokenControllerFactory`, `TokenControllerFactory`.
- **Expected value:** stable identity string; changing it invalidates existing tokens' issuer check.
- **Example value:** `"cdpg.org.in/controlplane"`
- **Default if omitted:** `""` — tokens are issued with an empty issuer and rejected downstream.
- **How to obtain:** operator decision, coordinated with every service that validates these tokens.
- **Failure mode:** dataplane rejects tokens with an issuer-mismatch 401.
- **Change impact:** **cross-service, breaking.** Must land in `keycloakOptions.issuers` of every
  consumer at the same time. All live tokens become invalid.

### `commonOptions.dataPlaneUrl`

- **Type / format:** string; host + path, no scheme
- **Required:** yes
- **Purpose:** Read by `aaa/apiserver/ControllerFactory`; used to build user-facing dataplane links.
- **Expected value:** `<domain>/dataplane`
- **Example value:** `"cdpg.org.in/dataplane"`
- **Default if omitted:** `null`.
- **Failure mode:** broken links in API responses and emails.
- **Change impact:** cross-service — must match the dataplane's ingress.

### `commonOptions.defaultExpiryDays`

- **Type / format:** int; days
- **Required:** yes
- **Purpose:** Default validity applied to policies created without an explicit expiry —
  `PolicyController`.
- **Expected value:** positive integer; organisation policy decision.
- **Example value:** `12`
- **Default if omitted:** none — `config.getLong` returns `null`, and the request path NPEs.
- **How to obtain:** data-governance decision.
- **Failure mode:** policy creation fails with a 500 when no expiry is supplied.
- **Change impact:** applies to newly created policies only; existing policies keep their expiry.

### `commonOptions.deletedDocsIndex`

- **Type / format:** string; Elasticsearch index name
- **Required:** yes
- **Purpose:** Index holding soft-deleted catalogue assets. Read via the `DELETED_DOCS_INDEX`
  constant (`Constants`) in `SharedServices`, `GrpcServerVerticle`,
  `DataBrokerVerticle`, `aaa/apiserver/ControllerFactory`.
- **Expected value:** `<tenantPrefix>__deleted_assets`
- **Example value:** `"cdpg__deleted_assets"`
- **Default if omitted:** `null` → queries target a null index and fail.
- **How to obtain:** must match the index created by the ES provisioning step. No dedicated
  mapping file ships for it — it holds archived catalogue documents, so it is expected to reuse
  [`indices-mappings/cat_mappings.json`](./indices-mappings/cat_mappings.json). Confirm with
  whoever provisions the cluster.
- **Failure mode:** deleted-asset queries return ES `index_not_found_exception`.
- **Change impact:** data migration — reindex before switching.

### `commonOptions.docIndex`

- **Type / format:** string; Elasticsearch index name
- **Required:** yes
- **Purpose:** Primary catalogue index. Read by `SharedServices`,
  `GrpcServerVerticle`, `DelegationVerticle`, `DataBrokerVerticle`.
- **Expected value:** `<tenantPrefix>__cat`
- **Example value:** `"cdpg__cat"`
- **Default if omitted:** `"iudx-docs"` in `GrpcServerVerticle` only; `null` elsewhere — an
  inconsistency worth knowing, since one component silently works while others fail.
- **How to obtain:** operator choice, but the index must be created with the mapping and settings
  in [`indices-mappings/cat_mappings.json`](./indices-mappings/cat_mappings.json) and
  [`indices-mappings/cat_settings.json`](./indices-mappings/cat_settings.json) — 81 mapped fields
  plus the analyzer settings the catalogue search depends on.
- **Failure mode:** all catalogue search/read returns empty or `index_not_found_exception`. An
  index created **without** that mapping accepts writes and then returns wrong or empty search
  results, which is harder to diagnose than a missing index.
- **Change impact:** data migration — reindex required.

### `commonOptions.docUserIndex`

- **Type / format:** string; Elasticsearch index name
- **Required:** yes
- **Purpose:** User-document index. Read via `DOC_USER_INDEX` (`Constants`) in
  `SharedServices`.
- **Expected value:** `<tenantPrefix>__userdoc`
- **Example value:** `"cdpg__userdoc"`
- **Default if omitted:** `null`.
- **How to obtain:** create with [`indices-mappings/userdoc_mappings.json`](./indices-mappings/userdoc_mappings.json),
  which defines exactly the seven fields this index holds — `about`, `education`, `experience`,
  `projects`, `publications`, `skills`, keyed by `userId`.
- **Failure mode:** user-document lookups fail with `index_not_found_exception`.
- **Change impact:** data migration.

### `commonOptions.initialCreditBalance`

- **Type / format:** int
- **Required:** yes
- **Purpose:** Credit balance seeded when a user's credit record is first created —
  `CreditServiceImpl`.
- **Expected value:** non-negative integer.
- **Example value:** `1`
- **Default if omitted:** none — `getInteger` returns `null` and user-credit creation NPEs.
- **How to obtain:** product decision.
- **Failure mode:** first credit-consuming action per user fails with a 500.
- **Change impact:** applies to newly created users only; existing balances unchanged.

### `commonOptions.isCentralCatEnabled`

- **Type / format:** bool — **feature flag**
- **Required:** no
- **Purpose:** Switches catalogue reads to the central catalogue. Read via
  `IS_CENTRAL_CATALOGUE_ENABLED` in `ApiServerVerticle`.
- **Expected value:** `false` for a standalone tenant.
- **Example value:** `false`
- **Default if omitted:** `false`.
- **Turning this on requires three further changes** — none of them are in this `config.json`
  today, and only the first is a config key:

  1. **`commonOptions.centralCatDocIndex`** — the central catalogue's Elasticsearch index. Read at
     `aaa/apiserver/ControllerFactory` (via the `CENTRAL_CAT_DOC_INDEX` constant) and used to build
     the central search and item controllers. Absent → `null` index and every central query fails.
     It holds the same document shape as `docIndex`, so create it with
     [`indices-mappings/cat_mappings.json`](./indices-mappings/cat_mappings.json) and
     [`cat_settings.json`](./indices-mappings/cat_settings.json).
  2. **A second `ElasticsearchVerticle` module entry**, carrying
     `"serviceAddress": "org.cdpg.dx.database.elastic.central.service"`.
     `InfrastructureServices.create()` builds a proxy to
     `CENTRAL_ELASTIC_SERVICE_ADDRESS`, which resolves to exactly that string in `dx-common`'s
     `ServiceProxyAddressConstants`. Add it alongside the existing entry — same class, deployed
     twice:

     ```json
     {
       "id": "org.cdpg.dx.database.elastic.ElasticsearchVerticle",
       "serviceAddress": "org.cdpg.dx.database.elastic.central.service",
       "isWorkerVerticle": false,
       "verticleInstances": 1,
       "databaseIP": "<central-es-host>",
       "databasePort": 24034,
       "databaseUser": "<central-es-user>",
       "databasePassword": "<central-es-password>"
     }
     ```

     If local and central share one Elasticsearch cluster, these connection values are identical to
     the primary entry's and the two catalogues are separated only by `docIndex` vs
     `centralCatDocIndex`. Do **not** copy the `tenantPrefix` key that appears on both entries in
     `config_central_cat_integration.json` — it is read nowhere (verified across all 326 `dx-common`
     classes and this repo).
  3. **The central OpenAPI spec must be deployed.** `ApiServerVerticle.getOpenApiSpecPath()`
     returns `docs/central-openapi.yaml` instead of `docs/openapi.yaml` when the flag is true.
     Both files exist under `docs/`, but the central one must be present in the image.

  Behaviourally it also registers `CentralSearchController` (`aaa/apiserver/ControllerFactory`) and the
  central list controller, and switches `ItemRegistryServiceImpl`, `ItemExistenceValidator` and
  `ItemFetchService` onto dual local + central paths.

  `example-config/config_central_cat_integration.json` is a working example of this shape.
- **Failure mode:** the proxy in step 2 constructs whether or not the verticle is deployed, so a
  half-finished switch-on **starts up cleanly** and fails later at runtime with no handler
  registered on the central service address. Missing `centralCatDocIndex` behaves the same way —
  healthy boot, failing central queries.

### `commonOptions.kycRequired`

- **Type / format:** bool — **feature flag**
- **Required:** no
- **Purpose:** Gates user flows behind KYC verification. `aaa/apiserver/ControllerFactory`.
- **Expected value:** `false` unless DigiLocker onboarding is live.
- **Default if omitted:** `false`.
- **Fields that become required when `true`:** the whole `KYCOptions` block.
- **Failure mode:** `true` with unset `KYCOptions` → KYC initiation fails and users cannot complete
  registration.

### `commonOptions.ogcDataPlaneUrl`

- **Type / format:** string; host + path, no scheme
- **Required:** yes when OGC/geoserver assets are served
- **Purpose:** Base URL for OGC dataplane links. `aaa/apiserver/ControllerFactory`.
- **Example value:** `"cdpg.org.in/geoserver"`
- **Default if omitted:** `null` → malformed OGC links.
- **Change impact:** cross-service — must match the geo-server ingress.

### `commonOptions.publisherPanelUrl`

- **Type / format:** string; base URL, **must not end with `/`**
- **Required:** yes — **validated at startup**
- **Purpose:** Base for dashboard/data-card links in emails and API responses. Validated in the
  `email/util/EmailComposer` constructor, and used to build dashboard URLs and the contact-us link.
- **Expected value:** scheme + host, no trailing slash.
- **Example value:** `"https://cdpg.org.in"`
- **Default if omitted:** none — **startup fails**.
- **Failure mode:** `IllegalArgumentException: Publisher panel URL is not configured or ends with a
  slash`, thrown during verticle construction. The whole email path fails to deploy. This is the
  one field in the file with an explicit fail-fast guard — treat a trailing slash as a hard error.
- **Notes / gotchas:** The trailing-slash rule is enforced, not advisory.

### `commonOptions.supportEmail`

- **Type / format:** string; email address
- **Required:** no — `AbstractApiServerVerticle` has a `getDefaultSupportEmail()` fallback
- **Purpose:** Substituted for the `${SUPPORT_EMAIL}` token in the served OpenAPI spec, exactly as
  `baseUrl` is for `${HOSTNAME}`. It populates the spec's contact block:

  ```yaml
  contact:
    email: ${SUPPORT_EMAIL}
  ```

  One occurrence per spec file. **Not** the field used for email CC — that is
  `emailConfig.emailSupport`.
- **Example value:** `"support@datakaveri.org"`
- **Default if omitted:** the `dx-common` built-in default.
- **Failure mode:** documentation only — the published docs show the wrong (or default) support
  address. No effect on the API or on outgoing mail.
- **Notes / gotchas:** Easily confused with `emailConfig.emailSupport`. Different fields, different
  readers, different purposes — this one is a docs string, that one is a real CC list on
  notification email. Keep both set and consistent.

### `commonOptions.tokenExpirationMinutes`

- **Type / format:** int; minutes — **tuning knob**
- **Required:** no
- **Purpose:** Lifetime of tokens minted by this service. `AppTokenControllerFactory`,
  `TokenControllerFactory`.
- **Expected value:** 15–120. Lower increases refresh load; higher widens the revocation window.
- **Example value:** `60`
- **Default if omitted:** `60`.
- **Failure mode:** too low → clients see frequent 401s mid-session; too high → revoked access
  persists until expiry.

### `commonOptions.uploadedBy`

- **Type / format:** string; free text
- **Required:** yes for AI-model items
- **Purpose:** Attribution string stored on catalogue items. `AiModelItem`.
- **Example value:** `"Centre of Data for Public Good (CDPG), IISc"`
- **Default if omitted:** `null` stored on new items.
- **Failure mode:** items display blank attribution.
- **Change impact:** affects newly created items only; existing documents keep the old value.

### `commonOptions.vocContext`

- **Type / format:** string; URL, trailing slash as shown
- **Required:** yes
- **Purpose:** JSON-LD vocabulary context for catalogue documents. Read via `VOC_CONTEXT`
  (`Constants`).
- **Example value:** `"https://voc.cdpg.org.in/"`
- **Default if omitted:** `null` → documents emitted without a context.
- **Failure mode:** schema validation failures on catalogue items.

---

### `KYCOptions.clientId` / `KYCOptions.clientSecret`

**Documented as a credential pair.** Consumed by `KYCServiceImpl`.

- **Type / format:** string / string (secret)
- **Required:** conditional on `commonOptions.kycRequired = true`
- **Purpose:** OAuth client credentials for DigiLocker. Without them the Aadhaar/KYC exchange
  cannot obtain a token.
- **Example value:** `"DL-1234ABCD"` / *(secret)*
- **Default if omitted:** none — KYC calls fail.
- **How to obtain:** **external provider.** Register the application on the DigiLocker partner
  portal; the credentials are issued there. Ownership of that relationship sits with the
  programme/partnership owner, not DevOps.
- **Privileges required:** the DigiLocker client must be approved for the Aadhaar demographic scope
  used by `digilockerAadhaarUrl`, with `redirectUri` whitelisted on the provider side.
- **Failure mode:** `401`/`invalid_client` from DigiLocker; user-facing KYC flow aborts after the
  consent screen.
- **Change impact:** rotate in the provider portal and the secret together; in-flight KYC sessions fail.
- **Notes / gotchas:** Dev and production DigiLocker endpoints use **different** client
  registrations — do not reuse credentials across environments.

### `KYCOptions.digilockerTokenUrl`

- **Type / format:** string; full URL with scheme
- **Required:** conditional on `kycRequired`
- **Purpose:** OAuth token endpoint. `KYCServiceImpl`.
- **Default if omitted:** none — KYC token exchange fails.
- **How to obtain:** DigiLocker partner documentation; differs per environment.
- **Failure mode:** connection/404 errors during KYC callback.

### `KYCOptions.digilockerAadhaarUrl`

- **Type / format:** string; full URL with scheme
- **Required:** conditional on `kycRequired`
- **Purpose:** Aadhaar demographic-fetch endpoint. `KYCServiceImpl`.
- **Default if omitted:** none.
- **Failure mode:** KYC completes the token step then fails to retrieve identity data.

### `KYCOptions.redirectUri`

- **Type / format:** string; must match the provider registration **verbatim**
- **Required:** conditional on `kycRequired`
- **Purpose:** OAuth redirect target after user consent. `KYCServiceImpl`.
- **Example value:** `"https://cdpg.org.in/kyc"`
- **Failure mode:** `redirect_uri_mismatch` from DigiLocker; the user is stranded on the consent page.
- **Notes / gotchas:** Byte-for-byte match with the provider portal entry — trailing slash and
  scheme included.

---

### `auditOptions.auditingExchange`

- **Type / format:** string; RabbitMQ exchange name
- **Required:** yes when `isRemoteAudit = true`
- **Purpose:** Exchange audit events are published to. `SharedServices`,
  `acl/apiserver/ControllerFactory`.
- **Example value:** `"auditing"`
- **Default if omitted:** `null` → publish to a null exchange.
- **Failure mode:** audit events silently dropped; no user-visible symptom until an audit is requested.

### `auditOptions.auditingQueue`

- **Type / format:** string; queue name
- **Required:** yes
- **Purpose:** Queue the `DataBrokerVerticle` consumes audit messages from.
  `DataBrokerVerticle`.
- **Example value:** `"auditing"`
- **Default if omitted:** `null` → consumer fails to bind.
- **Failure mode:** messages accumulate unconsumed; broker queue depth grows.

### `auditOptions.auditingRoutingKey`

- **Type / format:** string; AMQP routing key / pattern
- **Required:** yes
- **Purpose:** Routing key for published audit events. `SharedServices`,
  `acl/apiserver/ControllerFactory`.
- **Example value:** `"##"`
- **Failure mode:** events published but never routed to the audit queue.
- **Notes / gotchas:** `##` is **not** a wildcard — it is a literal string, and the exchange is
  `direct`, so the routing key must match the queue's binding key **exactly**. A mismatch is silent:
  the publish succeeds, the message is discarded, and neither side logs an error. Change this only
  together with the binding.

### `auditOptions.isRemoteAudit`

- **Type / format:** bool — **feature flag**
- **Required:** no
- **Purpose:** Turns remote audit publishing on. `SharedServices`,
  `acl/apiserver/ControllerFactory`.
- **Default if omitted:** `false`.
- **Fields that become required when `true`:** `auditingExchange`, `auditingRoutingKey`, and a
  reachable broker.
- **Failure mode:** `true` without a valid exchange → publish errors on every audited request.

### `auditOptions.leaderboardQueue`

- **Type / format:** string; queue name
- **Required:** no — defaulted
- **Purpose:** Queue consumed by `LeaderboardConsumer`. `DataBrokerVerticle`.
- **Example value:** `"leaderboard"`
- **Default if omitted:** `"leaderboard"`.
- **Failure mode:** leaderboard vote/interaction events are not consumed; leaderboard stops updating.

---

### `postgresOptions` — credential + connection block

All seven fields are consumed by `PostgresVerticle` *(dx-common)*, which builds `PgConnectOptions`
and the pool. None are read in this repo.

#### `postgresOptions.databaseIP`

- **Type / format:** string; hostname only, no scheme, no port
- **Required:** yes
- **Example value:** `"psql-pooler-rw.postgres.svc.cluster.local"`
- **Default if omitted:** none — startup fails.
- **How to obtain:** the Postgres pooler service DNS name in-cluster.
- **Failure mode:** `UnknownHostException` / connection refused at startup; no verticle serves traffic.
- **Notes / gotchas:** Point at the **read-write** pooler; a read-only endpoint fails on the first write.

#### `postgresOptions.databasePort`

- **Type / format:** int
- **Required:** yes
- **Example value:** `5432`
- **Failure mode:** connection refused.

#### `postgresOptions.databaseName`

- **Type / format:** string
- **Required:** yes
- **Expected value:** `<tenantPrefix>_controlplane`
- **Failure mode:** `database "..." does not exist` at startup.

#### `postgresOptions.databaseSchema`

- **Type / format:** string; schema name
- **Required:** yes
- **Expected value:** `aaa`
- **Purpose:** Set as the connection's search path by `PostgresVerticle`.
- **Failure mode:** every query fails with `relation ... does not exist` even though the tables
  exist — the classic symptom of a wrong schema.
- **Change impact:** must match what Flyway migrated into.

#### `postgresOptions.databaseUserName` + `postgresOptions.databasePassword`

**Documented as a pair.**

- **Type / format:** string / string (secret)
- **Required:** yes
- **Expected value:** `<tenantPrefix>_controlplane_user`
- **How to obtain:** password is generated by the Postgres chart —
  `cat iudx-installer/K8s-deployment/Charts/postgresql/secrets/passwords/postgres-auth-password`
- **Privileges required:** ownership of (or full DML on) the `aaa` schema in the
  `<tenantPrefix>_controlplane` database, plus `CONNECT` on the database and `USAGE` on the schema.
  Flyway migrations additionally need DDL rights:
  ```sql
  GRANT CONNECT ON DATABASE <tenantPrefix>_controlplane TO <tenantPrefix>_controlplane_user;
  GRANT USAGE, CREATE ON SCHEMA aaa TO <tenantPrefix>_controlplane_user;
  GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA aaa
    TO <tenantPrefix>_controlplane_user;
  ALTER DEFAULT PRIVILEGES IN SCHEMA aaa
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO <tenantPrefix>_controlplane_user;
  ```
- **Failure mode:** `password authentication failed for user` at startup, or — if only the grants
  are wrong — startup succeeds and individual queries fail with `permission denied for table`.
- **Change impact:** rotate in Postgres and the secret together; requires a pod restart.

#### `postgresOptions.poolSize`

- **Type / format:** int — **tuning knob**
- **Required:** no
- **Expected value:** size against Postgres `max_connections` divided by
  (replica count × verticle instances). 25 is a reasonable single-replica default.
- **Example value:** `25`
- **Failure mode:** too high → `FATAL: sorry, too many clients already` and cluster-wide outage as
  other services are starved; too low → request latency climbs and pool-acquisition timeouts appear
  under load.
- **Notes / gotchas:** This is the field most likely to cause a *shared* outage. Count every
  service pointing at the same pooler before raising it.

---

### `databrokerOptions` — RabbitMQ connection block

All fields consumed by `BaseDataBrokerVerticle` *(dx-common)*, except `publishExchange`
(read in this repo). None of the connection fields are read in this repo.

#### `databrokerOptions.dataBrokerIP`

- **Type / format:** string; hostname, no scheme
- **Required:** yes
- **Example value:** `"rabbitmq.rabbitmq.svc.cluster.local"`
- **Failure mode:** broker connection retries forever; publish/consume paths degrade.

#### `databrokerOptions.dataBrokerPort`

- **Type / format:** int; AMQP port
- **Required:** yes
- **Example value:** `5672` (plain) / `5671` (TLS)
- **Notes / gotchas:** Must agree with `portSsl`. `5672` + `portSsl: true` fails handshake.

#### `databrokerOptions.dataBrokerManagementPort`

- **Type / format:** int; HTTP management API port
- **Required:** yes — used by `RabbitWebClient` for queue/exchange administration
- **Example value:** `15672`
- **Failure mode:** queue/binding creation calls fail with connection refused while AMQP traffic
  still works — a confusing partial outage.

#### `databrokerOptions.dataBrokerUserName` + `databrokerOptions.dataBrokerPassword`

**Documented as a pair.**

- **Type / format:** string / string (secret)
- **Required:** yes
- **Expected value:** `<tenantPrefix>-controlplane-user`
- **How to obtain:**
  `cat iudx-installer/K8s-deployment/Charts/databroker/secrets/credentials/admin-password`
- **Privileges required:** this account is **not** an ordinary publish/consume user — it
  administers *other* RabbitMQ users and therefore needs the **`administrator`** tag. Full
  derivation below.
- **Failure mode:** `ACCESS_REFUSED` on connect, or — if only one vhost's permissions are missing —
  that single feature (audit, email, or leaderboard) fails while everything else works. With the
  `management` tag instead of `administrator`, startup and consumption succeed normally and only
  subscription/connector creation fails, with a `403` from the management API buried in the logs.

##### Derivation of the RabbitMQ grants

**Operations by vhost** — traced from the call sites:

| Vhost | What the service does | Permission needed |
|---|---|---|
| `prodVhost` | Declares and deletes per-asset exchanges, binds them to the `database` queue, deletes per-subscription queues (16 `Vhosts.IUDX_PROD` call sites, all via the management API) | configure + write + read, on **unpredictable names** |
| `internalVhost` | Consumes `auditingQueue`, `leaderboardQueue`, `emailQueue` via `basicConsumer` (`AuditMessageConsumer`, `LeaderboardConsumer`, `EmailMessageConsumer`); publishes via `publishMessageInternal`; deletes connector queues | read + write, plus configure for the deletes |
| `externalVhost` | **Never referenced in this repo.** `BaseDataBrokerVerticle` *(dx-common)* still reads the key and builds a client at startup, so the vhost must exist and be connectable | connect only |

##### RabbitMQ topology to provision

Everything below must **already exist** before the control plane starts or onboards an asset. The
service creates per-asset objects at runtime, but never creates the vhosts, the shared exchanges, or
the shared queues.

**vHosts**

| vHost | Config key | Details |
|---|---|---|
| `IUDX` | `databrokerOptions.prodVhost` | Data vhost — per-asset exchanges and the `database` queue |
| `IUDX-INTERNAL` | `databrokerOptions.internalVhost` | This service's own audit / email / leaderboard messaging |
| `IUDX-EXTERNAL` | `databrokerOptions.externalVhost` | Unused by the control plane, but a client is built at startup, so it must exist and be connectable |

**Exchanges** 

| Exchange Name | Type of exchange | Features                 | Details                                                                                                                                              |
|---|--|--------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------|
| `auditing` | direct | durable, not auto-delete | Carries every audited request. Published by `AuditingHandler` on the internal vhost; consumed from the `auditOptions.auditingQueue`.                 |
| `Email` | direct | durable, not auto-delete | Carries email jobs published by `AccessRequestController`; drained by `EmailMessageConsumer` via the `email-notification` queue.                     |
| `rpc-adapter-requests` | direct | durable, not auto-delete | Binding *source* for per-connector queues created by `ConnectorServiceImpl`. Never published to by this service.                                     |
| `revoked-appid` | direct | durable, not auto-delete | App-credential revocations published by `AppCredentialsServiceImpl` so data planes can invalidate revoked app IDs. Not declared in `dev/rabbitmq-definitions.json` — confirm it exists in your environment. |

**Queues and bindings**

| Queue Name                 | Type | Features | vHost | Bound to exchange (routing key)                     | Consumed by                                                                            |
|----------------------------|---|---|---|-----------------------------------------------------|----------------------------------------------------------------------------------------|
| `test-auditing`            | classic | durable | internal | `auditing` (`##`)                                   | `AuditMessageConsumer`                                                                 |
| `email-notification`       | classic | durable | internal | `Email` (`##`)                                      | `EmailMessageConsumer`                                                                 |
| `leaderboard`              | classic | durable | internal | `auditing` (`##`)                                   | `LeaderboardConsumer`                                                                  |
| `database`                 | classic | durable | prod | per-asset exchanges, bound at runtime (`<assetId>`) | the NGSI-LD / database ingestion side, not this service                                |


```bash
# prodVhost — exchanges/queues are named after asset and subscription UUIDs, so the names
# cannot be enumerated: configure/write/read must all be ".*"
rabbitmqctl set_permissions -p <TENANTPREFIX>          <tenantPrefix>-controlplane-user ".*" ".*" ".*"

# internalVhost — this service's own audit/email/leaderboard messaging
rabbitmqctl set_permissions -p <TENANTPREFIX>-INTERNAL <tenantPrefix>-controlplane-user ".*" ".*" ".*"

# externalVhost — no call sites in this repo; a client is still built at startup,
# so the vhost must exist and be connectable
rabbitmqctl set_permissions -p <TENANTPREFIX>-EXTERNAL <tenantPrefix>-controlplane-user "^$" "^$" "^$"

# Required for /api/users/ and /api/permissions/ — these are tag-governed, not permission-governed
rabbitmqctl set_user_tags <tenantPrefix>-controlplane-user administrator
```


#### `databrokerOptions.brokerAmqpIp` / `databrokerOptions.brokerAmqpPort`

- **Type / format:** string (host) / int
- **Required:** yes
- **Purpose:** Externally reachable AMQPS endpoint advertised to clients, as distinct from the
  in-cluster `dataBrokerIP` used by this service itself.
- **Example value:** `"cdpg.org.in"` / `24567`
- **Failure mode:** consumers receive connection details that do not resolve outside the cluster.
- **Notes / gotchas:** These are *advertised* values; `dataBrokerIP`/`dataBrokerPort` are what this
  service dials. Do not collapse them into one.

#### `databrokerOptions.prodVhost` / `internalVhost` / `externalVhost`

- **Type / format:** string; vhost names (uppercase by convention here)
- **Required:** yes
- **Expected value:** `<TENANTPREFIX>`, `<TENANTPREFIX>-INTERNAL`, `<TENANTPREFIX>-EXTERNAL`
- **Purpose, per vhost:**
  - **`prodVhost`** — the **data vhost**, and the busiest of the three. Every per-asset exchange
    created when an item is onboarded lives here, along with the pre-existing `database` queue they
    are bound to, and the per-user permissions granted over them. See *Adapter registration* below.
  - **`internalVhost`** — this service's own messaging: the audit, leaderboard and email queues it
    consumes, and the messages it publishes via `publishMessageInternal`.
  - **`externalVhost`** — read from config and a client is built for it at startup, but **no code
    path in this repo uses it** (`Vhosts.IUDX_EXTERNAL` has zero call sites). It must still exist
    and be connectable.
- **Failure mode:** `NOT_ALLOWED - vhost ... not found`; the affected client fails to start.
- **Notes / gotchas:** **Case-sensitive** and must exist before startup. The uppercase convention
  differs from the lowercase tenant prefix used in Postgres — a frequent copy-paste error.

##### Adapter registration — what happens on `prodVhost` when an item is onboarded

When an item is created for the NGSI-LD server, `ItemRegistryServiceImpl` calls
`ingestionService.registerAdapter(assetId, userId)`, which performs four RabbitMQ operations, **all
on `prodVhost`** and all through the management API:

1. **Creates the RabbitMQ user if absent** — `RabbitClient.createUserIfNotPresent()` via
   `/api/users/`, generating a random password. That password is the "apiKey" returned to the
   caller on registration; if lost it can only be regenerated through the reset-password path, not
   recovered.
2. **Creates an exchange named after the `assetId`** — `registerExchange(userId, assetId, IUDX_PROD)`.
3. **Grants that user `write` on the exchange** — `updatePermission(..., ADD_WRITE, IUDX_PROD)`.
4. **Binds the new exchange to the pre-existing `database` queue** —
   `queueBinding(assetId, DATABASE_QUEUE, assetId, IUDX_PROD)`, with the `assetId` as the routing
   key. `DATABASE_QUEUE` is the literal `"database"`, and the queue is **not** created by this
   service — it must already exist on `prodVhost`.

Deletion (`deleteAdapter`) reverses steps 2 and 3: `deleteExchange` then
`updatePermission(..., DELETE_WRITE, IUDX_PROD)`. It does **not** remove the user.

#### `databrokerOptions.portSsl`

- **Type / format:** bool
- **Required:** yes
- **Purpose:** Selects TLS for the AMQP connection.
- **Failure mode:** mismatch with `dataBrokerPort` → handshake timeout that looks like a network
  problem rather than a config one.

#### `databrokerOptions.automaticRecoveryEnabled`

- **Type / format:** **string** `"true"`/`"false"` — note: quoted in the example, not a JSON bool
- **Required:** no
- **Purpose:** RabbitMQ client auto-reconnect.
- **Example value:** `"true"`
- **Notes / gotchas:** Keep it a string to match the shipped example unless you have verified the
  consumer coerces a bool.

#### `databrokerOptions.connectionTimeout` / `handshakeTimeout` / `networkRecoveryInterval` / `requestedChannelMax` / `requestedHeartbeat`

- **Type / format:** int (ms for the timeouts and interval; count for channel max; seconds for heartbeat)
- **Required:** no — **tuning knobs**
- **Example values:** `6000`, `6000`, `500`, `5`, `60`
- **Failure mode:** timeouts too low → spurious reconnect storms under load; `requestedChannelMax`
  too low → `channel-max` exhaustion errors when many consumers are registered; heartbeat too high →
  dead connections linger and messages stall.

#### `databrokerOptions.publishExchange`

- **Type / format:** string; exchange name
- **Required:** yes
- **Purpose:** Exchange for RPC-adapter requests. Read in this repo at
  `aaa/apiserver/ControllerFactory`.
- **Example value:** `"rpc-adapter-requests"`
- **Default if omitted:** `null` → publish fails.

---

### `emailConfig.emailSender`

- **Type / format:** string; email address
- **Required:** yes
- **Purpose:** `From:` address on all outgoing mail. Read by both `EmailComposer` classes.
- **Example value:** `"no-reply@cdpg.org.in"`
- **Default if omitted:** `null` → SMTP rejects the message.
- **How to obtain:** must be a verified sender identity in your SMTP provider (SES etc.).
- **Failure mode:** SMTP `554 Message rejected: Email address is not verified`.
- **Notes / gotchas:** **Duplicated as `emailOptions.emailSender`.** Both exist and are read by
  different composers — keep them identical. See §4.

### `emailConfig.emailSupport`

- **Type / format:** array of strings; email addresses
- **Required:** yes — read with `getJsonArray(...).getList()`, **no null guard**
- **Purpose:** CC list on outgoing notification mail. `email/util/EmailComposer`, in both the create and consumer-ack paths.
- **Example value:** `["support@cdpg.org.in"]`
- **Default if omitted:** none — **NPE at email composition**, so the mail is never sent.
- **Failure mode:** if the key is missing, `getJsonArray` returns null and `.getList()` throws.
- **Notes / gotchas:** The shipped example is `[""]` — an array containing one **empty string**.
  That is a CC to an empty address and should be replaced with a real address or an empty array
  `[]` before deployment. See §4.

### `emailConfig.senderName`

- **Type / format:** string; display name
- **Required:** yes
- **Purpose:** Sender display name and the `SENDER_NAME` template placeholder.
- **Example value:** `"CDPG Data Exchange"`
- **Default if omitted:** `null` → renders as an empty signature block in emails.

---

### `emailNotification.emailExchange`

- **Type / format:** string; exchange name
- **Required:** no — defaulted at the read site
- **Purpose:** Exchange the API server publishes email jobs to.
  `acl/apiserver/ControllerFactory`.
- **Example value:** `"Email"`
- **Failure mode:** email jobs published nowhere; **no error surfaces to the API caller** — the
  request succeeds and the mail simply never arrives.
- **Notes / gotchas:** Case-sensitive (`"Email"`, capital E in the example).

### `emailNotification.emailQueue`

- **Type / format:** string; queue name
- **Required:** no — defaulted
- **Purpose:** Queue the `EmailVerticle` consumes from. `DataBrokerVerticle`.
- **Example value:** `"email-notification"`
- **Default if omitted:** `"email-notification"`.
- **Failure mode:** mismatch between publisher binding and this value → mail queues up unconsumed.

### `emailNotification.emailRoutingKey`

- **Type / format:** string; routing key
- **Required:** no — defaulted
- **Purpose:** Routing key for published email jobs. `acl/apiserver/ControllerFactory`.
- **Example value:** `"##"`
- **Failure mode:** published but unrouted messages — same silent symptom as above.

---

### `emailOptions.emailSender`

- **Type / format:** string; email address
- **Required:** yes
- **Purpose:** Same role as `emailConfig.emailSender`, read by the AAA composer path.
- **Notes / gotchas:** Duplicate — keep in sync with `emailConfig.emailSender`. See §4.

### `emailOptions.platformName`

- **Type / format:** string; full platform name
- **Required:** yes
- **Purpose:** `${PLATFORM_NAME}` in email templates; also interpolated into status/action
  sentences in `EmailComposer`.
- **Example value:** `"Forest Stack Rajasthan"`
- **Default if omitted:** `null`, rendered as empty by the `safe()` guard — emails read
  "on the  platform".
- **Failure mode:** cosmetic but user-visible: blank platform name in every notification.

### `emailOptions.platformShortName`

- **Type / format:** string; short platform name
- **Required:** yes
- **Purpose:** `${PLATFORM_SHORT_NAME}` in templates and the **subject-line prefix** on the
  access-request acknowledgement (`email/util/EmailComposer`, `:269-270`, `:316-318`).
- **Example value:** `"TGDeX"`
- **Default if omitted:** `null` → `safe()` yields `""`, producing a subject that begins with
  `" Platform – Dataset Access Request Received"`.
- **Failure mode:** user-visible in the inbox subject line — worth verifying per environment.

### `emailOptions.envSuffix`

- **Type / format:** string; short environment tag, **empty for production**
- **Required:** no
- **Purpose:** Appended as ` [VALUE]` to email subject lines so non-production mail is
  identifiable. Read once into a field (`email/util/EmailComposer`,
  `aaa/email/util/EmailComposer`) and used in every subject builder.
- **Expected value:** `DEV`, `STAGING`, `UAT`, or empty/absent in production.
- **Example value:** `"STAGING"`
- **Default if omitted:** `null`, handled — no suffix appended.
- **Failure mode:** left set in production, every user-facing email is tagged `[STAGING]`. This is
  the single most common cause of "why does production mail say staging".
- **Notes / gotchas:** The code adds the brackets and leading space; supply the bare word only.

### `emailOptions.cosAdminEmailId`

- **Type / format:** string; email address
- **Required:** yes
- **Purpose:** Recipient for admin-facing request notifications (org creation, compute role,
  credit, provider role). `aaa/email/util/EmailComposer.java`.
- **Example value:** `"admin@cdpg.org.in"`
- **Default if omitted:** `null` → SMTP rejects for missing recipient; **admin approval requests
  are never delivered** and requests appear to hang.

### `emailOptions.TGDxUrl`

- **Type / format:** string; full URL with scheme, trailing slash as shipped
- **Required:** yes
- **Purpose:** Admin portal link (`ADMIN_PORTAL_URL`) in admin notification emails.
- **Example value:** `"https://cdpg.org.in/"`
- **Default if omitted:** `null` → broken "take action" link in admin mail.
- **Notes / gotchas:** Legacy name (TGDx) retained for compatibility; it is the admin portal URL
  regardless of the deployment's branding.

---

### `jwtKeystoreOptions.keystorePath`

- **Type / format:** string; path relative to the container working directory
- **Required:** yes
- **Purpose:** JKS keystore holding the signing key for tokens this service issues.
  `AppTokenControllerFactory`, `TokenControllerFactory.java`.
- **Example value:** `"secrets/all-verticles-configs/keystore.jks"`
- **Default if omitted:** none — token issuance fails.
- **How to obtain:** mounted by the chart alongside `config.json`.
- **Failure mode:** `FileNotFoundException` on the first token request; login appears broken while
  the rest of the API works.

### `jwtKeystoreOptions.keystorePassword`

- **Type / format:** string (secret)
- **Required:** yes
- **Purpose:** Password for the keystore above.
- **How to obtain:** generated with the keystore; stored in the chart's secrets.
- **Privileges required:** n/a — file-level secret.
- **Failure mode:** `UnrecoverableKeyException` / `Keystore was tampered with, or password was
  incorrect` at first token issuance.
- **Change impact:** rotating the keystore invalidates every token signed with the old key.

---

### `keycloakOptions.keycloakUrl`

- **Type / format:** string; full URL with scheme, **no trailing slash**
- **Required:** yes
- **Purpose:** Keycloak base URL for the admin client and gRPC auth.
  `GrpcServerVerticle`; `KeycloakClientProvider` *(dx-common)*.
- **Example value:** `"https://cdpg.org.in/auth"`
- **Failure mode:** admin-client calls fail; user lookups (needed to compose *every* email) fail,
  so email delivery breaks as a side effect.

### `keycloakOptions.keycloakRealm`

- **Type / format:** string; realm name
- **Required:** yes
- **Purpose:** Realm the admin client operates against. `GrpcServerVerticle`.
- **Expected value:** `<tenantPrefix>`
- **Failure mode:** `404 Realm does not exist`.
- **Notes / gotchas:** Case-sensitive; must match the realm segment in `issuers` and `jwksUrl`.

### `keycloakOptions.keycloakAdminClientId` + `keycloakOptions.keycloakAdminClientSecret`

**Keycloak client — documented as a client, not two fields.** Consumed by `KeycloakClientProvider`
*(dx-common)*.

- **Type / format:** string / string (secret)
- **Required:** yes
- **Client type:** **confidential**, with **service accounts enabled**. Standard flow and direct
  grant are not required for this client.
- **Naming convention:** an admin client within the tenant realm (the example labels it
  "AAA-admin").
- **Service-account role mappings required:** from the `realm-management` client —
  `manage-users`, `view-users`, `view-clients`, and `query-users`. Without `manage-users` the
  service cannot create or update users; without `view-users` it cannot resolve the name/email
  used to compose notification emails.
- **Relationship to other fields:** it lives in `keycloakRealm` at `keycloakUrl`. It is **not** the
  same client as the issuers in `issuers` — those describe tokens this service *accepts*, whereas
  this client is how the service *acts* on Keycloak.
- **How to obtain:** created in the configured realm by DevOps; the secret is on the client's
  Credentials tab.
- **Failure mode:** `401 invalid_client` at startup or on first user lookup. Because user lookup is
  in the email path, the visible symptom is often "emails stopped" rather than "auth broken".
- **Change impact:** rotate the secret in Keycloak and the config together; restart required.

### `keycloakOptions.issuers.<issuer>`

- **Type / format:** object keyed by issuer string; each value has `type`, `audience`, and for
  remote issuers `jwksUrl`
- **Required:** yes
- **Purpose:** The set of token issuers this service accepts. Consumed by
  `AbstractApiServerVerticle` *(dx-common)*.
- **Expected value:** two entries in the shipped example —
  - `<domain>/controlplane` with `"type": "internal"` — tokens **this** service mints; the value
    must equal `commonOptions.cosDomain` exactly.
  - `https://<domain>/auth/realms/<tenantPrefix>` with `"type": "remote"` and a `jwksUrl` — tokens
    from Keycloak.
- **`audience`:** array; empty disables audience checking.
- **`jwksUrl`:** required for `remote`; the realm's `protocol/openid-connect/certs` endpoint.
- **Failure mode:** an issuer missing here → every request bearing that token returns 401 with an
  issuer-mismatch, even though the token is otherwise valid.
- **Change impact:** **cross-service.** Changing `cosDomain` requires the matching internal issuer
  key to change in lockstep here and in every consumer.

### `keycloakOptions.issuers.jwksRefreshIntervalMs`

- **Type / format:** int; milliseconds — **tuning knob**
- **Required:** no
- **Purpose:** JWKS cache refresh interval. Named in `AuthConstants` *(dx-common)*.
- **Example value:** `21600000` (6 hours)
- **Failure mode:** too long → after a Keycloak key rotation, valid tokens are rejected until the
  cache expires; too short → avoidable load on Keycloak.
- **Notes / gotchas:** **Placement is suspect** — this sits *inside* the `issuers` map alongside
  issuer URLs. See §4.

### `keycloakOptions.issuers.jwtIgnoreExpiry`

- **Type / format:** bool — **feature flag**
- **Required:** no
- **Purpose:** Disables the token expiry check. Named in `AuthConstants` *(dx-common)*.
- **Expected value:** `false`. **Never `true` in production** — expired tokens would be accepted
  indefinitely.
- **Failure mode:** `true` is a security hole with no visible symptom.
- **Notes / gotchas:** Same placement concern as above — see §4.

### `keycloakOptions.issuers.jwtLeeway`

- **Type / format:** int; seconds
- **Required:** no
- **Purpose:** Intended clock-skew allowance. **No consumer found** in this repo or in
  `dx-common`'s `AuthConstants` (which names only `jwksRefreshIntervalMs`, `jwksUrl`,
  `jwtIgnoreExpiry` and `type`). See §4.
- **Example value:** `30`
- **Failure mode:** none observed — the value appears inert.

---

### `modules[]` — deployment list

Consumed by `BaseDeployer` *(dx-common)*. Four keys repeat on every entry and are documented once
here rather than nine times; module-specific keys follow.

#### `modules[].id`

- **Type / format:** string; fully-qualified Java class name
- **Required:** yes
- **Purpose:** Verticle to deploy.
- **Failure mode:** `ClassNotFoundException` at startup, deployment aborts. **Two entries resolve
  from `dx-common`, not this repo** (`PostgresVerticle`, `ElasticsearchVerticle`) — that is
  correct, not a missing class.

#### `modules[].verticleInstances`

- **Type / format:** int — **tuning knob**
- **Required:** yes
- **Expected value:** 1 for stateful/singleton verticles (scheduler, databroker); scale API server
  instances against CPU. Multiply by `postgresOptions.poolSize` when sizing DB connections.
- **Example value:** `1`
- **Failure mode:** >1 on the scheduler duplicates scheduled work; on API servers, too many
  exhausts the DB pool.

#### `modules[].isWorkerVerticle`

- **Type / format:** bool
- **Required:** yes
- **Purpose:** Runs the verticle on the worker pool for blocking work. `true` for `EmailVerticle`
  and `SchedulerVerticle` in the shipped config.
- **Failure mode:** `false` on a blocking verticle → `Thread blocked` warnings and event-loop
  stalls affecting unrelated requests.

#### `modules[].required`

- **Type / format:** array of top-level block names (`commonOptions`, `postgresOptions`, …)
- **Required:** conditional — needed only when the verticle reads a top-level option block. Seven
  of the nine modules have one. `ElasticsearchVerticle` and `SchedulerVerticle` deliberately omit
  it, because every setting they need is written **directly inside their own module entry**
  (`databaseIP`/`databasePort`/credentials, and `schedulerTimeIntervalInMinutes`/`threadPoolSize`
  respectively) rather than pulled in from a shared block.
- **Purpose:** Selects which top-level blocks are merged into that verticle's `config()`. A verticle
  can only read a block listed here. Values written directly in the module entry are always visible
  to it; values in a top-level block are visible **only** if that block is named in this array.
- **Failure mode:** **the highest-value line in this document.** A key can be present and correct in
  `config.json` and still read back as `null`, simply because its block was never added to the
  consuming module's `required`. Example: adding a key to `emailOptions` makes it visible to
  `EmailVerticle`, `ApiServerVerticle` and `ApdApiServerVerticle` — which list that block — but not
  to `DataBrokerVerticle`, which does not. The symptom is a NullPointerException or a silently
  disabled feature that looks like a code bug rather than a config one.
- **Change impact:** adding a block here is safe; removing one silently strips every key it carried
  from that verticle's view.
- **Notes / gotchas:** Blocks are merged **flat** into one JSON object, so a key written directly on
  a module can be overwritten by a same-named key from a block listed here. This is why the
  `ElasticsearchVerticle` entry must never list `postgresOptions` — both define `databaseIP` and
  `databasePort`.

#### `EmailVerticle` — SMTP fields

`emailHostName`, `emailPort`, `emailUserName`, `emailPassword` — read at
`email/verticle/EmailVerticle`.

- **Type / format:** string / int / string / string (secret)
- **Required:** yes
- **Expected value:** SMTP relay host, submission port (587 for STARTTLS), and the SMTP
  credential pair.
- **Example value:** `"email-smtp.ap-south-1.amazonaws.com"`, `587`, SMTP username, SMTP password
- **How to obtain:** SMTP provider console (for SES, the generated SMTP credentials — **not** the
  IAM access key).
- **Privileges required:** the account must be authorised to send as `emailConfig.emailSender`;
  in SES the sender identity must be verified and the account out of sandbox for external recipients.
- **Failure mode:** `535 Authentication Credentials Invalid`, or in an SES sandbox account, silent
  rejection of any recipient not on the verified list.
- **Notes / gotchas:** `emailUserName` in the example equals the sender address, but for SES they
  are different values — do not assume they match.

#### `EmailVerticle.notifyByEmail`

- **Type / format:** bool — **feature flag**
- **Required:** no
- **Purpose:** Master switch for outbound email. `EmailVerticle`.
- **Default if omitted:** `true` — email is on unless explicitly disabled.
- **Failure mode:** `false` suppresses all mail with no error anywhere — check this first when
  "emails stopped".

#### `EmailVerticle.threadPoolName` / `EmailVerticle.threadPoolSize` / `SchedulerVerticle.threadPoolSize`

- **Type / format:** string / int — **tuning knobs**
- **Required:** no
- **Purpose:** Named worker pool and its size, applied by `BaseDeployer` *(dx-common)*.
- **Example value:** `"emailVerticleThreadPool"` / `10`; scheduler uses `3`.
- **Failure mode:** too small → email/scheduled jobs queue behind each other and notifications lag.

#### `ElasticsearchVerticle` — connection fields

`databaseIP`, `databasePort`, `databaseUser`, `databasePassword` — inlined in the module entry
(this verticle has no `required` array). `ElasticsearchVerticle` *(dx-common)* reads `databaseIP`
and `databasePort` itself; the credential pair is consumed by `ElasticClient` *(dx-common)*, which
it builds.

- **Required:** yes
- **Example value:** `"elastic-es-http.elastic.svc.cluster.local"`, `9200`,
  `<tenantPrefix>-controlplane-user`
- **How to obtain:**
  `cat iudx-installer/K8s-deployment/Charts/elk/secrets/passwords/elasticsearch-cat-password`
- **Privileges required:** an ES role granting `read`/`write`/`view_index_metadata` on the index
  patterns from `commonOptions` — `docIndex`, `deletedDocsIndex`, `docUserIndex`. Read-only rights
  are not enough: catalogue publish/delete writes to these indices.
- **Failure mode:** `security_exception: action [indices:data/write/index] is unauthorized` on
  publish while search continues to work.
- **Notes / gotchas:** Field name is `databaseUser` here, but `databaseUserName` in
  `postgresOptions`. Not interchangeable.

#### `ElasticsearchVerticle.serviceAddress` — second Elasticsearch instance

- **Type / format:** string; Vert.x event-bus service address
- **Required:** no — **absent from this `config.json`**, and only needed for the central catalogue
- **Purpose:** The event-bus address the verticle registers its `ElasticsearchService` on. This is
  the mechanism that lets `ElasticsearchVerticle` be **deployed more than once**: the class is the
  same, only the address and connection settings differ.
- **Expected value:** omit for the primary instance — it registers on the default
  `org.cdpg.dx.database.elastic`. Set `"org.cdpg.dx.database.elastic.central.service"` on a second
  module entry to back the central catalogue; the string must match
  `CENTRAL_ELASTIC_SERVICE_ADDRESS` in `dx-common`'s `ServiceProxyAddressConstants` exactly.
- **Default if omitted:** the primary elastic address.
- **How to obtain:** fixed constant, not an operator choice.
- **Failure mode:** a typo produces no startup error — the verticle registers on the wrong address,
  the consumer's proxy finds no handler, and every central-catalogue call fails at request time.
- **Notes / gotchas:** Adding the second entry means the *pair* of ES module entries must be kept
  consistent. There is **no separate central-catalogue verticle class**; a second
  `ElasticsearchVerticle` is the whole mechanism. The two entries may point at the same ES cluster
  with different indices (`docIndex` vs `centralCatDocIndex`) or at two different clusters — the
  code only ever talks to the two service addresses. See `commonOptions.isCentralCatEnabled` for
  the full switch-on checklist, and `config_central_cat_integration.json` for a worked example.

> **Dead key seen in the wild:** the two `ElasticsearchVerticle` blocks in
> `config_central_cat_integration.json` also carry `tenantPrefix`. It is read nowhere — not in this
> repo and not in `dx-common`. Do not copy it into new configs.

#### `ApiServerVerticle.httpPort` / `ApdApiServerVerticle.httpPort`

- **Type / format:** int
- **Required:** yes
- **Purpose:** Listen port, read via the `PORT` constant (`Constants`).
- **Example value:** `8080` (AAA API), `8444` (ACL/APD API)
- **Failure mode:** `BindException: Address already in use`; must match the chart's service and
  probe ports.

#### `ApiServerVerticle.urnPrefix` / `ApdApiServerVerticle.urnPrefix`

- **Type / format:** string; URN prefix
- **Required:** no (see §4)
- **Purpose:** Intended URN namespace for generated identifiers. **No consumer found** in this repo
  or `dx-common`.
- **Example value:** `"urn:dx:controlPlane:"`, `"urn:dx:aclApd:"`
- **Failure mode:** none observed.

#### `SchedulerVerticle.schedulerTimeIntervalInMinutes`

- **Type / format:** int — **tuning knob**
- **Required:** yes
- **Purpose:** Interval between scheduler runs. `SchedulerVerticle`.
- **Example value:** `30`
- **Failure mode:** too frequent → duplicated/overlapping runs and DB load; too infrequent →
  expired policies linger past their expiry.
- **Notes / gotchas:** The local variable at the read site is named `timeIntervalInHours` while the
  config key says minutes. Verify the intended unit before tuning this.

#### `GrpcServerVerticle.grpcPort`

- **Type / format:** int
- **Required:** no — defaulted
- **Purpose:** gRPC listen port. `GrpcServerVerticle`.
- **Default if omitted:** `9090`.

#### `GrpcServerVerticle.grpcAllowedServiceClients`

- **Type / format:** array of strings; Keycloak service-account client IDs
- **Required:** no — defaulted, but always set it explicitly
- **Purpose:** Allow-list of service clients permitted to call the gRPC API.
  `GrpcServerVerticle`.
- **Example value:** `["svc-dx-dataplane", "svc-dx-dataplane-ogc"]`
- **Default if omitted:** a built-in single-entry list (`svc-dx-dataplane`). Relying on it silently
  locks out the OGC dataplane, so treat the field as required in practice.
- **Failure mode:** a caller missing from this list is rejected; the dataplane logs permission
  denied on app-ID verification and access checks fail cluster-wide.
- **Change impact:** cross-service — each entry must match a real Keycloak client ID in
  `keycloakRealm`.

#### `GrpcServerVerticle.keycloakJwksUrl`

- **Type / format:** string; full URL
- **Required:** yes
- **Purpose:** JWKS endpoint used to validate incoming gRPC caller tokens.
  `GrpcServerVerticle`.
- **Example value:** `"https://cdpg.org.in/auth/realms/<tenantPrefix>/protocol/openid-connect/certs"`
- **Failure mode:** all gRPC calls fail signature validation.
- **Notes / gotchas:** Duplicates the `jwksUrl` inside `keycloakOptions.issuers`. Keep both in sync.

---

## 3. Extra requirements by field category

Cross-cutting view of the fields already documented in §2. Full detail — failure modes, exact
grants, how to obtain each value — stays in the individual blocks; this section exists so you can
check a whole category at once.

### Credentials (user + password pairs)

Six credential pairs, each living in a different system. Never reuse one across systems.

| Pair | System | Account created by | Detail |
|---|---|---|---|
| `postgresOptions.databaseUserName` + `databasePassword` | PostgreSQL | Postgres chart | §2 — includes the exact `GRANT` statements |
| `databrokerOptions.dataBrokerUserName` + `dataBrokerPassword` | RabbitMQ | Databroker chart | §2 — needs the `administrator` tag, see the derivation |
| `ElasticsearchVerticle.databaseUser` + `databasePassword` | Elasticsearch | ELK chart | §2 — needs write, not just read |
| `EmailVerticle.emailUserName` + `emailPassword` | SMTP relay | Provider console (e.g. SES) | §2 — SMTP credentials, **not** IAM keys |
| `keycloakOptions.keycloakAdminClientId` + `keycloakAdminClientSecret` | Keycloak | DevOps, in the tenant realm | See next subsection |
| `KYCOptions.clientId` + `clientSecret` | DigiLocker | Partner portal | See *External provider fields* |

`jwtKeystoreOptions.keystorePath` + `keystorePassword` is a seventh secret but not a user/password
pair — it is a JKS file mounted by the chart plus its password.

Privilege summaries, with the full commands in §2:

- **Postgres** — `CONNECT` on the database, `USAGE, CREATE` on schema `aaa`, full DML on its tables,
  plus `ALTER DEFAULT PRIVILEGES` so future tables are covered. DDL rights are needed for Flyway.
- **RabbitMQ** — read + write on the internal vhost; connect on prod and external. The
  `administrator` **tag** is mandatory because the service edits *other* users' permissions.
- **Elasticsearch** — `read`, `write`, `view_index_metadata` on `docIndex`, `deletedDocsIndex` and
  `docUserIndex`. Read-only is not enough; publish and delete write to these indices.
- **SMTP** — must be authorised to send as `emailConfig.emailSender`; in SES the sender identity
  must be verified and the account out of sandbox for external recipients.

### Keycloak client IDs / secrets

One client: `keycloakOptions.keycloakAdminClientId` / `keycloakAdminClientSecret`.

- **Type:** confidential, **service accounts enabled**. Standard flow and direct grant are not
  required.
- **Realm:** `keycloakOptions.keycloakRealm`, at `keycloakOptions.keycloakUrl`.
- **Service-account role mappings** (from the `realm-management` client): `manage-users`,
  `view-users`, `view-clients`, `query-users`.
- **Relationship to the other Keycloak fields** — these three are distinct and are often confused:
  - `keycloakAdminClientId/Secret` is how this service **acts on** Keycloak.
  - `keycloakOptions.issuers` is the set of token issuers this service **accepts**, including a
    `remote` entry whose `jwksUrl` points at the same realm.
  - `GrpcServerVerticle.grpcAllowedServiceClients` lists *other* services' client IDs permitted to call
    this one over gRPC. Each entry must be a real client in the same realm.
- **Failure mode worth memorising:** user lookup runs in the email path, so a broken admin client
  usually presents as "emails stopped", not "auth is broken".

### Domains / URLs

The most error-prone category, because the fields look interchangeable and are not. Scheme and
trailing-slash rules are enforced inconsistently across the code, so follow this table literally.

| Field | Scheme? | Trailing `/`? | Must match |
|---|---|---|---|
| `commonOptions.baseUrl` | **no** — spec adds `https://` | no | public ingress hostname |
| `commonOptions.controlPlaneUrl` | no | no | — |
| `commonOptions.controlPlaneDomain` | **yes** | no | public ingress URL |
| `commonOptions.cosDomain` | no | no | the `internal` issuer key in `keycloakOptions.issuers`, **exactly** |
| `commonOptions.apdURL` | **no** — code prepends `https://` | no | the `apdURL` stamped into item metadata, **exactly** |
| `commonOptions.dataPlaneUrl` | no | no | dataplane ingress |
| `commonOptions.ogcDataPlaneUrl` | no | no | geo-server ingress |
| `commonOptions.publisherPanelUrl` | yes | **no — validated at startup** | — |
| `commonOptions.vocContext` | yes | yes (as shipped) | — |
| `emailOptions.TGDxUrl` | yes | yes (as shipped) | admin portal |
| `keycloakOptions.keycloakUrl` | yes | no | realm segment in `issuers` / `jwksUrl` |
| `KYCOptions.redirectUri` | yes | must match provider registration byte-for-byte | DigiLocker portal entry |

Only `publisherPanelUrl` is validated — a trailing slash there fails startup outright. Everywhere
else a wrong scheme or slash produces malformed links at runtime with no error.

**Cross-service values** — these must be changed in lockstep with other services' configs, and are
breaking if they drift: `cosDomain`, `apdURL`, `dataPlaneUrl`, `ogcDataPlaneUrl`,
`appIdRevokeExchange`, `grpcAllowedServiceClients`, and the `issuers` entries.

### Tuning knobs

| Field | Safe range | Size against | Symptom if wrong |
|---|---|---|---|
| `postgresOptions.poolSize` | 10–30 | Postgres `max_connections` ÷ (replicas × instances) | too high → `too many clients already`, a **cluster-wide** outage; too low → acquisition timeouts |
| `modules[].verticleInstances` | 1 for singletons | CPU, for API servers | >1 on the scheduler duplicates scheduled work |
| `modules[].threadPoolSize` | 3–10 | blocking work per verticle | email / scheduled jobs queue behind each other |
| `commonOptions.tokenExpirationMinutes` | 15–120 | session length vs revocation window | too low → mid-session 401s; too high → revoked access persists |
| `SchedulerVerticle.schedulerTimeIntervalInMinutes` | 15–60 | expiry granularity needed | too frequent → overlapping runs; too rare → expired policies linger |
| `databrokerOptions` timeouts, `requestedChannelMax`, `requestedHeartbeat` | as shipped | consumer count | reconnect storms, `channel-max` exhaustion, stalled messages |
| `keycloakOptions.issuers.jwksRefreshIntervalMs` | 1h–6h | Keycloak key rotation cadence | after rotation, valid tokens rejected until the cache expires |

`poolSize` is the one to think hardest about: it is the only knob here whose misconfiguration takes
down services **other than this one**.

### Feature flags

| Flag | Default | Becomes required when `true` |
|---|---|---|
| `commonOptions.isCentralCatEnabled` | `false` | `centralCatDocIndex`, a second `ElasticsearchVerticle` on the central service address, and `docs/central-openapi.yaml` in the image — see §2 |
| `commonOptions.kycRequired` | `false` | the entire `KYCOptions` block |
| `auditOptions.isRemoteAudit` | `false` | `auditingExchange`, `auditingRoutingKey`, reachable broker |
| `EmailVerticle.notifyByEmail` | `true` | — (setting it `false` silently disables all mail) |
| `keycloakOptions.issuers.jwtIgnoreExpiry` | `false` | — **never enable in production**; expired tokens would be accepted |

Two of these fail *quietly*: an incomplete `isCentralCatEnabled` switch-on boots cleanly and fails
at request time, and `notifyByEmail: false` produces no error anywhere. Check both first when
something "just stopped working".

### External provider fields

**DigiLocker / KYC** — `KYCOptions.clientId`, `clientSecret`, `digilockerTokenUrl`,
`digilockerAadhaarUrl`, `redirectUri`.

- Onboarding is a **partnership**, not a DevOps task: the application is registered on the
  DigiLocker partner portal and credentials are issued there. The programme owner holds that
  relationship.
- The client must be approved for the Aadhaar demographic scope used by `digilockerAadhaarUrl`,
  with `redirectUri` whitelisted on the provider side.
- **Dev and production use different endpoints and different client registrations.** Do not carry
  credentials between environments.
- Everything here is inert unless `commonOptions.kycRequired` is `true`.

**SMTP relay** — `EmailVerticle.emailHostName` / `emailPort` / `emailUserName` / `emailPassword`.
Provider-issued SMTP credentials; for SES these are generated separately from IAM keys, and the
sender identity must be verified before any mail is accepted.

## 4. Findings — fields to resolve

Raised by the template's checklist item *"Unused/legacy fields are marked deprecated and dropped
from the example file."*

**No consumer found** (searched this repo and the `dx-common` jar):

| Field | Notes |
|---|---|
| `clusterId` | Not referenced by `BaseDeployer`, which does read `zookeepers`. May be used by deployment tooling — confirm against the chart before removing. |
| `keycloakOptions.issuers.jwtLeeway` | `AuthConstants` names only `jwksRefreshIntervalMs`, `jwksUrl`, `jwtIgnoreExpiry`, `type`. Appears inert. |
| `ApiServerVerticle.urnPrefix`, `ApdApiServerVerticle.urnPrefix` | No read site found under either name or a constant. |
| `version` | Documentation-only by design; keep. |

**Read but discarded** — `commonOptions.isEdgeCatalogue` and `commonOptions.isStandalone`. Unlike
the fields above these *are* read (`aaa/apiserver/ControllerFactory`) and passed into
`ItemControllerFactory.create(...)` (as parameters on `ItemControllerFactory`), but the
method body never references either one. Neither reaches `ItemRegistryServiceImpl` — which takes
only `isCentralCatEnabled` — nor `ItemController`. Both read sites use `getBoolean(key, false)`, so
**removing them from `config.json` is safe and changes nothing**. The unused parameters remain in
the code signature and are the real cleanup; removing them there is a code change, deliberately not
made here.

**`CatalogueVerticle` is dead code.** `catalogueService/CatalogueVerticle.java` is not listed in the
`modules` array of this `config.json`, and nothing references it. Its whole subgraph is unreachable:
`CatalogueVerticle`, `service/CatalogueServiceImpl`, `service/CatalogueService`,
`client/CatalogueClient` and `models/ResourceObj`. (The rest of the `catalogueService` package —
`models/Asset`, `models/ItemType` and `config/Constants` — **is** live and used across the item,
policy and access-request paths, so the package cannot simply be dropped.)

Consequently its three config keys — `catServerHost`, `catServerPort`, `dxCatalogueBasePath` — are
read nowhere else and are dead wherever they still appear. This `config.json` has already dropped
them, but the module block is still declared in `dev/config-dev.json`, `dev/config-docker.json`,
`example-config/config-test.json` and `example-config/config_central_cat_integration.json`.

Note also that `CatalogueVerticle` reads `config().getString("apdUrl")` — lowercase `u` —
where every live consumer uses `apdURL`. Against any of these files that key would resolve to
`null`. The bug is inert only because the verticle is never deployed.

**Suspect structure — `keycloakOptions.issuers`.** The map mixes two different kinds of entry at
the same level: issuer URLs (whose values are objects with `type`/`audience`/`jwksUrl`) and three
scalar settings (`jwksRefreshIntervalMs`, `jwtIgnoreExpiry`, `jwtLeeway`). Any code that iterates
`issuers` as "one entry per issuer" would encounter those three scalars as pseudo-issuers. Worth
confirming against `AbstractApiServerVerticle` in `dx-common` — if they are meant to be
issuer-level settings they belong under `keycloakOptions` directly, not inside `issuers`.

**Duplicated values that must be kept in sync:**

- `emailConfig.emailSender` and `emailOptions.emailSender` — same address, two blocks, both read.
- `GrpcServerVerticle.keycloakJwksUrl` and the `jwksUrl` inside `keycloakOptions.issuers`.
- `commonOptions.cosDomain` and the `internal` issuer key in `keycloakOptions.issuers`.
- `commonOptions.supportEmail` vs `emailConfig.emailSupport` — **not** duplicates; different
  fields, different readers. Both are live.

**Example-file values that must not ship to production:**

- `emailConfig.emailSupport` is `[""]` — an array holding one empty string, not an empty array.
- `commonOptions.corsAllowedOrigin` is `["*"]`.
- `emailOptions.envSuffix` must be emptied for production.

**Inconsistent defaults worth knowing:** `docIndex` falls back to `"iudx-docs"` in
`GrpcServerVerticle` but has no default in `SharedServices`. If the key goes
missing, gRPC keeps working against a wrong index while the API server fails outright.

---

## 5. Submission checklist

- [x] Every leaf field in `config.json` has a block. The four `modules[]` scaffolding keys
      (`id`, `verticleInstances`, `isWorkerVerticle`, `required`) are documented once as a pattern
      plus a per-module `required` table in §1, rather than nine identical repetitions.
- [x] Every credential documents its exact privileges and who creates the account —
      Postgres, RabbitMQ, Elasticsearch, SMTP, Keycloak admin client, DigiLocker.
- [x] The Keycloak admin client has service-account role mappings listed.
- [x] Cross-service fields are flagged (`cosDomain`, `dataPlaneUrl`, `ogcDataPlaneUrl`,
      `appIdRevokeExchange`, `grpcAllowedServiceClients`, issuer entries).
- [x] Each field states its failure mode.
- [x] Unused/legacy fields are called out in §4.
- [x] All six field categories from the template are covered in §3 — credentials, Keycloak
      clients, domains/URLs, tuning knobs, feature flags, external provider fields.

> **Section numbering:** §0–§3 follow the template exactly. §4 (Findings) is an addition beyond the
> template; the template's §4 submission checklist is §5 here.
- [ ] **Open:** confirm `clusterId`, `jwtLeeway`, `urnPrefix` with DevOps, then drop them from
      `config.json` or wire them up.
- [ ] **Open:** confirm the `issuers` nesting question in §4.
- [ ] **Open:** fill in maintainer / point of contact in §0.