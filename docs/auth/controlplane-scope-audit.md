# Controlplane API → v2 Scope audit

**Repo:** `dx-controlplane`
**Status:** Audit — for review before mass migration to auth-v2
**Last verified against:** `dx-controlplane:refact/auth-v2-wiring`, `dx-common:refact/auth-module`
**Scope coverage:** all 31 controllers under `src/main/java/org/cdpg/dx/`

---

## 1. Purpose

Catalog every controlplane endpoint, its current v1 role gate, and the
recommended v2 scope. Identify gaps where the existing 13 scopes don't
fit, and propose minimal additions. Migration follows this map verbatim
once it's approved.

The audit answers three questions:

1. Are the existing 13 scopes enough? **Mostly yes — 4 genuine gaps.**
2. How does `DELEGATE` (v1 role, removed in v2) translate? **Drop it
   from every gate and use the underlying scope; `DelegationResolver`
   handles delegation at scope level.**
3. Are there controllers using ad-hoc auth (custom handlers, in-handler
   role checks) that need separate treatment? **Yes — flagged in §6.**

## 2. Existing scope inventory (dx-common)

The 13 scopes defined in `org.cdpg.dx.auth.v2.model.Scopes`:

| Scope | Holder roles | Trust class |
|---|---|---|
| `DATA_ACCESS` | CONSUMER | Consumer can access data — read/use/subscribe |
| `OWN_ASSET_MANAGEMENT` | PROVIDER, ORG_ADMIN | Manage assets I own |
| `ORG_USER_MANAGEMENT` | ORG_ADMIN | Manage users within my org |
| `ORG_ASSET_MANAGEMENT` | ORG_ADMIN | Manage assets within my org |
| `ORG_ASSET_PUBLISH` | ORG_ADMIN | Publish org assets to catalogue |
| `ORG_PUBLISHER_MANAGEMENT` | ORG_ADMIN | Manage publishers within org |
| `ORG_MANAGEMENT` | COS_ADMIN | Platform-wide org management |
| `USER_MANAGEMENT` | COS_ADMIN | Platform-wide user management |
| `ASSET_MANAGEMENT` | COS_ADMIN | Platform-wide asset management |
| `ASSET_PUBLISH` | COS_ADMIN | Platform-wide asset publish |
| `PUBLISHER_MANAGEMENT` | COS_ADMIN | Platform-wide publisher management |
| `ROLE_MANAGEMENT` | COS_ADMIN | Manage role assignments |
| `COMPUTE_ACCESS` | COMPUTE | Service-account capability gate |

## 3. Migration translation rules

Apply these rules mechanically to every endpoint before consulting the
per-controller table:

| v1 pattern | v2 replacement | Why |
|---|---|---|
| `forRoles(X, DELEGATE)` | `forScopes(scope-for-X)` | DELEGATE role doesn't exist in v2 — `DelegationResolver` already builds a `DxPrincipal` whose `directScopes` reflect the (capped) delegator scopes. The scope check covers both direct and delegated callers uniformly. |
| `forDelegationScopes(scope1, scope2)` | folded into `forScopes(scope1, scope2)` | v2's scope check handles delegation transparently. The "delegation scope" concept goes away. |
| `forRoles(X, Y)` where X and Y differ in level (e.g. ORG_ADMIN, COS_ADMIN) | `forScopesWithContext(platform(scope-for-Y), org(scope-for-X))` | Replaces the ad-hoc "admin-vs-org" branching in handlers with a declared `AuthorizationContext`. |
| `forRoles(COMPUTE)` | `forScopes(COMPUTE_ACCESS)` or keep `forRoles(COMPUTE)` | Either works — COMPUTE_ACCESS is a 1:1 mapping. Recommend `forScopes` for consistency. |
| `forRoles(X)` single role | `forScopes(scope-for-X)` | Plain scope check. |
| `KycVerification(isKycRequired)` | unchanged for now | Orthogonal concern (verifies a JWT claim). v2 doesn't displace it. Future cleanup may move KYC into `DxPrincipal`. |
| Custom validators (`SubscriptionAuthorizationHandler`, `UserAccessHandler`, `verifyItemTypeAndRole`, `ItemOwnershipValidator`) | unchanged for now | These check domain-specific constraints (does this user own this item, is this consumer allowed at this resource), not coarse capability gates. They run AFTER the scope check, just like in v1. |

## 4. Controller-by-controller mapping

Operations are quoted as they appear in the source. Missing rows mean
that operation has no `forRoles(...)` gate today (it relies on
JWT-presence + custom handlers — flagged in §6).

### 4.1 Activity / reporting (already drafted in `activity-auth-v2-migration-plan.md`)

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `ActivityController` | `OP_GET_ACTIVITY_FOR_CONSUMER` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `ActivityController` | `OP_GET_ACTIVITY_FOR_ADMIN` | `forRoles(ORG_ADMIN, COS_ADMIN)` | `forScopesWithContext(platform(USER_MANAGEMENT), org(ORG_USER_MANAGEMENT))` |
| `ActivityReportController` | `get-consumer-report` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `ActivityReportController` | `get-admin-report` | `forRoles(ORG_ADMIN, COS_ADMIN)` | `forScopesWithContext(platform(USER_MANAGEMENT), org(ORG_USER_MANAGEMENT))` |

**Existing scopes sufficient. No additions needed.**

### 4.2 Bookmarks

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `BookmarksController` | `OP_POST_BOOKMARK` | `forRoles(ORG_ADMIN, COS_ADMIN, CONSUMER)` | `forScopes(DATA_ACCESS)` ⚠ |
| `BookmarksController` | `OP_GET_BOOKMARKS` | same | same |
| `BookmarksController` | `OP_DELETE_BOOKMARK` | same | same |

⚠ **Question:** the v1 gate allows ORG_ADMIN and COS_ADMIN to bookmark
too. Are admins really a target audience for bookmarks, or did this
combo accumulate by copy-paste? If admins genuinely need it, they
already hold `ORG_USER_MANAGEMENT` / `USER_MANAGEMENT` not `DATA_ACCESS`
— so `forScopes(DATA_ACCESS)` would *exclude* them. Two choices:

- (a) Bookmarks are user-data — `forScopes(DATA_ACCESS)`. Admins can't
  bookmark unless they also hold CONSUMER. **Cleaner — recommended.**
- (b) Keep behavior identical: `forScopes(DATA_ACCESS, USER_MANAGEMENT, ORG_USER_MANAGEMENT)`.

**Existing scopes sufficient regardless.**

### 4.3 User self-service / custom roles

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `UserController` | `post-auth-v2-user-info` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `UserController` | `get-auth-v2-user-info` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `UserController` | `patch-auth-v2-user-info` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `UserController` | `post-auth-v2-custom-role` | `forRoles(ORG_ADMIN, COS_ADMIN)` | `forScopesWithContext(platform(ROLE_MANAGEMENT), org(ORG_USER_MANAGEMENT))` |
| `UserController` | `get-auth-v2-custom-role` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `UserController` | `get-auth-v2-custom-role-requester` | `forRoles(ORG_ADMIN, COS_ADMIN)` | same as `post-auth-v2-custom-role` |
| `UserController` | `delete-auth-v2-custom-role` | `forRoles(ORG_ADMIN, COS_ADMIN)` | same |

**Existing scopes sufficient.** Note: "user-info" self-service uses
`DATA_ACCESS` (the only consumer-held scope). This is a stretch — see
§7 gap #1 (`SELF_PROFILE` proposal).

### 4.4 Admin (Keycloak user CRUD)

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `AdminController` | `get-auth-v2-user` | none today | leave unchanged for now (or add `DATA_ACCESS`) |
| `AdminController` | `get-auth-v2-user-id-admin` | `forRoles(COS_ADMIN, DELEGATE)` | `forScopes(USER_MANAGEMENT)` |
| `AdminController` | `get-auth-v2-admin-user` | `forRoles(COS_ADMIN)` | `forScopes(USER_MANAGEMENT)` |
| `AdminController` | `get-auth-v2-user-search` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `AdminController` | `put-auth-v2-user` | none | unchanged — see §6 |
| `AdminController` | `put-auth-v2-user-password` | none | unchanged — see §6 |
| `AdminController` | `post-auth-v2-user-update` | none | unchanged — see §6 |
| `AdminController` | `delete-auth-v2-user` | none | unchanged — see §6 |
| `AdminController` | `post-auth-v2-admin-id-update` | `forRoles(COS_ADMIN, DELEGATE)` | `forScopes(USER_MANAGEMENT)` |

**Existing scopes sufficient.** ⚠ Several operations have no role gate
today — flag for §6 review.

### 4.5 Organization

ORG_ADMIN and COS_ADMIN paths plus a few CONSUMER (self) request flows.
Heavy use of `forDelegationScopes` — all collapse to plain `forScopes`
in v2.

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `OrganizationController` | `OP_GET_ORG_CREATE_REQUESTS` | `forRoles(COS_ADMIN, DELEGATE)` + `forDelegationScopes(COS_ADMIN_ACCESS)` | `forScopes(ORG_MANAGEMENT)` |
| `OrganizationController` | `OP_GET_USER_ORG_CREATE_REQUESTS` | `forRoles(CONSUMER, DELEGATE)` | `forScopes(DATA_ACCESS)` |
| `OrganizationController` | `OP_DELETE_USER_ORG_CREATE_REQUEST` | `forRoles(CONSUMER, DELEGATE)` | `forScopes(DATA_ACCESS)` |
| `OrganizationController` | `OP_CREATE_ORG_REQUEST` | `KycVerification(isKycRequired)` only | KYC unchanged + `forScopes(DATA_ACCESS)` (any consumer can apply) |
| `OrganizationController` | `OP_APPROVE_ORG_CREATE_REQUEST` | `forRoles(COS_ADMIN, DELEGATE)` + `forDelegationScopes(COS_ADMIN_ACCESS)` | `forScopes(ORG_MANAGEMENT)` |
| `OrganizationController` | `OP_CREATE_ORG_JOIN_REQUEST` | `KycVerification(isKycRequired)` only | KYC unchanged + `forScopes(DATA_ACCESS)` |
| `OrganizationController` | `OP_GET_ORG_JOIN_REQUESTS` | `forRoles(ORG_ADMIN, DELEGATE)` + `forDelegationScopes(USER_MANAGEMENT, ORG_ADMIN_ACCESS)` | `forScopes(ORG_USER_MANAGEMENT)` |
| `OrganizationController` | `OP_GET_USER_ORG_JOIN_REQUESTS` | `forRoles(CONSUMER, DELEGATE)` | `forScopes(DATA_ACCESS)` |
| `OrganizationController` | `OP_WITHDRAW_USER_ORG_JOIN_REQUESTS` | `forRoles(CONSUMER, DELEGATE)` | `forScopes(DATA_ACCESS)` |
| `OrganizationController` | `OP_DELETE_USER_ORG_JOIN_REQUEST` | `forRoles(CONSUMER, DELEGATE)` | `forScopes(DATA_ACCESS)` |
| `OrganizationController` | `OP_APPROVE_ORG_JOIN_REQUEST` | `forRoles(ORG_ADMIN, DELEGATE)` + `forDelegationScopes(USER_MANAGEMENT, ORG_ADMIN_ACCESS)` | `forScopes(ORG_USER_MANAGEMENT)` |
| `OrganizationController` | `OP_LIST_ORGANISATIONS` | none in current handler chain | leave unchanged or `forScopes(DATA_ACCESS)` (public org listing?) |
| `OrganizationController` | `OP_GET_ORGANISATION_BY_ID` | none | same — confirm it's intentionally open |
| `OrganizationController` | `OP_UPDATE_ORGANISATION_BY_ID` | `forRoles(COS_ADMIN, DELEGATE)` + `forDelegationScopes(USER_MANAGEMENT, COS_ADMIN_ACCESS)` | `forScopes(ORG_MANAGEMENT)` |
| `OrganizationController` | `OP_DELETE_ORGANISATION_BY_ID` | `forRoles(COS_ADMIN, ORG_ADMIN, DELEGATE)` + `forDelegationScopes(USER_MANAGEMENT, COS_ADMIN_ACCESS, ORG_ADMIN_ACCESS)` | `forScopesWithContext(platform(ORG_MANAGEMENT), org(ORG_USER_MANAGEMENT))` |
| `OrganizationController` | `OP_GET_ORG_USERS` | `forRoles(ORG_ADMIN, DELEGATE)` + `forDelegationScopes(USER_MANAGEMENT, ORG_ADMIN_ACCESS)` | `forScopes(ORG_USER_MANAGEMENT)` |
| `OrganizationController` | `OP_GET_ORG_USER_INFO` | same | `forScopes(ORG_USER_MANAGEMENT)` |
| `OrganizationController` | `OP_DELETE_ORG_USER` | same | `forScopes(ORG_USER_MANAGEMENT)` |
| `OrganizationController` | `OP_UPDATE_ORG_USER_ROLE` | same | `forScopes(ORG_USER_MANAGEMENT)` |
| `OrganizationController` | `OP_CREATE_PROVIDER_REQUEST` | `KycVerification` only | KYC unchanged + `forScopes(DATA_ACCESS)` |
| `OrganizationController` | `OP_GET_PROVIDER_REQUEST` | `forRoles(ORG_ADMIN, DELEGATE)` | `forScopes(ORG_USER_MANAGEMENT)` |
| `OrganizationController` | `OP_UPDATE_PROVIDER_REQUEST` | `forRoles(ORG_ADMIN, DELEGATE)` + `forDelegationScopes(USER_MANAGEMENT, ORG_ADMIN_ACCESS)` | `forScopes(ORG_USER_MANAGEMENT)` |
| `OrganizationController` | `OP_GET_USER_PROVIDER_REQUESTS` | `forRoles(CONSUMER, DELEGATE)` | `forScopes(DATA_ACCESS)` |
| `OrganizationController` | `OP_DELETE_USER_PROVIDER_REQUEST` | `forRoles(CONSUMER, DELEGATE)` | `forScopes(DATA_ACCESS)` |
| `OrganizationController` | `OP_CREATE_PROVIDER_ROLE` | `forRoles(ORG_ADMIN, DELEGATE)` + `forDelegationScopes(USER_MANAGEMENT, ORG_ADMIN_ACCESS)` | `forScopes(ORG_USER_MANAGEMENT)` |

**Existing scopes sufficient.** Heaviest controller — most of the
`DELEGATE` complexity disappears once translated.

### 4.6 Search

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `SearchController` | `POST_SEARCH` | none | leave open — public catalogue search |
| `SearchController` | `POST_COUNT_SEARCH` | none | leave open |
| `SearchController` | `POST_ASSET_SEARCH` | TOKEN_CHECK only | (private — needs scope; `forScopes(DATA_ACCESS)` if any logged-in user) |
| `SearchController` | `GET_ASSET_SEARCH` | TOKEN_CHECK only | same |
| `SearchController` | `GET_ORG_ASSETS` | `forRoles(ORG_ADMIN)` | `forScopes(ORG_ASSET_MANAGEMENT)` |
| `SearchController` | `GET_ORG_ASSETS_VTH_FILTERS` | `forRoles(ORG_ADMIN)` | `forScopes(ORG_ASSET_MANAGEMENT)` |
| `SearchController` | `GET_PLATFORM_ASSETS` | `forRoles(COS_ADMIN)` | `forScopes(ASSET_MANAGEMENT)` |
| `SearchController` | `GET_PLATFORM_ASSETS_VTH_FILTERS` | `forRoles(COS_ADMIN)` | `forScopes(ASSET_MANAGEMENT)` |

**Existing scopes sufficient.**

### 4.7 Central catalogue search/list

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `CentralSearchController` | `CENTRAL_POST_SEARCH` | none | leave open (public central search) |
| `CentralSearchController` | `CENTRAL_POST_COUNT_SEARCH` | none | leave open |
| `CentralListController` | `LIST_AVAILABLE_CENTRAL_CAT_FILTERS` | none | leave open |

**No scopes needed (public).**

### 4.8 List / static lookups

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `ListController` | `LIST_AVAILABLE_FILTER` | none | leave open or `forScopes(DATA_ACCESS)` |

**No scopes needed today.**

### 4.9 Subscription

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `SubscriptionController` | `CREATE_SUBSCRIPTION` | `forRoles(DELEGATE, CONSUMER)` + `SubscriptionAuthorizationHandler` (custom) | `forScopes(DATA_ACCESS)` + `SubscriptionAuthorizationHandler` (unchanged) |
| `SubscriptionController` | `UPDATE_SUBSCRIPTION` | same | same |
| `SubscriptionController` | `GET_BY_ID_SUBSCRIPTION` | `forRoles(DELEGATE, CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `SubscriptionController` | `DELETE_SUBSCRIPTION` | same | `forScopes(DATA_ACCESS)` |
| `SubscriptionController` | `GET_ALL_SUBSCRIPTION` | same | `forScopes(DATA_ACCESS)` |

**Existing scopes sufficient.** All `DELEGATE` terms drop.

### 4.10 Asset (post/get/put/delete asset request)

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `AssetController` | `post-auth-v2-asset-request` | `forRoles(PROVIDER)` | `forScopes(OWN_ASSET_MANAGEMENT)` |
| `AssetController` | `get-auth-v2-asset-request` | `forRoles(COS_ADMIN, CONSUMER, DELEGATE)` + `forDelegationScopes(COS_ADMIN_ACCESS)` | `forScopes(ASSET_MANAGEMENT, DATA_ACCESS)` ⚠ — see note |
| `AssetController` | `put-auth-v2-asset-request` | `forRoles(COS_ADMIN, DELEGATE)` + `forDelegationScopes(COS_ADMIN_ACCESS)` | `forScopes(ASSET_MANAGEMENT)` |
| `AssetController` | `delete-auth-v2-asset-request` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |

⚠ The GET admits both COS_ADMIN and CONSUMER — likely behaves
differently per caller (admin sees all requests; consumer sees their
own). If so, this is a `forScopesWithContext(platform(ASSET_MANAGEMENT), self(DATA_ACCESS))` pattern. Confirm during migration.

**Existing scopes sufficient.**

### 4.11 Item (catalogue item CRUD)

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `ItemController` | `CREATE_ITEM` | none — relies on `verifyItemTypeAndRole` (custom handler that branches per role) | leave custom handler in place; consider adding `forScopes(OWN_ASSET_MANAGEMENT, ORG_ASSET_MANAGEMENT, ASSET_MANAGEMENT)` upstream |
| `ItemController` | `GET_ITEM` | none | leave open (public catalogue read) or `forScopes(DATA_ACCESS)` |
| `ItemController` | `DELETE_ITEM` | none — `ItemOwnershipValidator` enforces | as create |
| `ItemController` | `UPDATE_ITEM` | none — `verifyItemTypeAndRole` enforces | as create |
| `ItemController` | `PATCH_ITEM` | `forRoles(COS_ADMIN, ORG_ADMIN, PROVIDER)` | `forScopes(ASSET_MANAGEMENT, ORG_ASSET_MANAGEMENT, OWN_ASSET_MANAGEMENT)` |
| `ItemController` | `GET_ITEM_WITH_ACCESS` | none — service-level check | leave |
| `ItemController` | `CHECK_ITEM_NAME_AVAILABILITY` | none | leave |
| `ItemController` | `DOWNLOAD_SCRIPT` | none | leave or `forScopes(OWN_ASSET_MANAGEMENT)` |

**Existing scopes sufficient.** Most of `ItemController`'s auth is in
custom handlers (`verifyItemTypeAndRole`, `ItemOwnershipValidator`)
that should NOT be ripped out — they enforce ownership / per-item
access rules. They run after the scope check.

### 4.12 Resource server

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `ResourceServerController` | `CREATE_RESOURCE_SERVER` | `forRoles(ORG_ADMIN, COS_ADMIN)` | **GAP — see §7 #2** |
| `ResourceServerController` | `LIST_RESOURCE_SERVERS` | `forRoles(ORG_ADMIN, COS_ADMIN)` | same gap |
| `ResourceServerController` | `GET_RESOURCE_SERVER` | `forRoles(ORG_ADMIN, COS_ADMIN)` | same gap |
| `ResourceServerController` | `DELETE_RESOURCE_SERVER` | `forRoles(ORG_ADMIN, COS_ADMIN)` | same gap |

⚠ **Gap.** No existing scope cleanly represents "manage resource
servers." Closest are `ASSET_MANAGEMENT` (COS) / `ORG_ASSET_MANAGEMENT`
(ORG) — but resource servers aren't assets, they're infrastructure
registrations. **Recommend reusing those for now, revisit if a
dedicated scope is needed.** See §7 #2.

### 4.13 Policy / ACL

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `PolicyController` | `CREATE_POLICY_API` | `forRoles(PROVIDER, ORG_ADMIN, DELEGATE)` | `forScopesWithContext(org(ORG_ASSET_MANAGEMENT), self(OWN_ASSET_MANAGEMENT))` |
| `PolicyController` | `GET_POLICY_API` | `forRoles(CONSUMER, PROVIDER, DELEGATE)` | `forScopes(DATA_ACCESS, OWN_ASSET_MANAGEMENT)` |
| `PolicyController` | `DELETE_POLICY_API` | `forRoles(PROVIDER, ORG_ADMIN, DELEGATE)` | `forScopesWithContext(org(ORG_ASSET_MANAGEMENT), self(OWN_ASSET_MANAGEMENT))` |
| `PolicyController` | `VERIFY_API` | `forRoles(PROVIDER, ORG_ADMIN, CONSUMER)` | `forScopes(DATA_ACCESS, OWN_ASSET_MANAGEMENT, ORG_ASSET_MANAGEMENT)` |

**Existing scopes sufficient — but stretched.** Policies *are* asset
management primitives ("who can access this asset"), so reuse fits
better than I initially thought. Revisit only if there's a concrete
"manage policies without managing assets" use case.

### 4.14 Access request (ACL)

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `AccessRequestController` | `CREATE_ACCESS_REQUEST_API` | none — service-level check | leave + recommend `forScopes(DATA_ACCESS)` |
| `AccessRequestController` | `GET_ACCESS_REQUEST_CONSUMER_API` | none | as above |
| `AccessRequestController` | `WITHDRAW_ACCESS_REQUEST_API_FOR_CONSUMER` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `AccessRequestController` | `GET_ACCESS_REQUEST_FOR_ORG_ADMIN_API` | `forRoles(ORG_ADMIN)` | `forScopes(ORG_ASSET_MANAGEMENT)` |
| `AccessRequestController` | `GET_ACCESS_REQUEST_FOR_COS_ADMIN_API` | `forRoles(COS_ADMIN)` | `forScopes(ASSET_MANAGEMENT)` |
| `AccessRequestController` | `GET_ACCESS_REQUEST_PROVIDER_API` | `forRoles(PROVIDER, ORG_ADMIN)` | `forScopesWithContext(org(ORG_ASSET_MANAGEMENT), self(OWN_ASSET_MANAGEMENT))` |
| `AccessRequestController` | `UPDATE_ACCESS_REQUEST_API` | `forRoles(PROVIDER, ORG_ADMIN)` | same |
| `AccessRequestController` | `CHECK_ACCESS_REQUEST_API` | none | leave |

**Existing scopes sufficient.**

### 4.15 Access report (ACL)

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `AccessReportController` | `GET_ACCESS_REQUEST_REPORT_API` | `forRoles(PROVIDER)` | `forScopes(OWN_ASSET_MANAGEMENT)` |
| `AccessReportController` | `GET_ACCESS_REQUEST_REPORT_FOR_ORG_ADMIN_API` | `forRoles(ORG_ADMIN)` | `forScopes(ORG_ASSET_MANAGEMENT)` |

**Existing scopes sufficient.**

### 4.16 Delegation CRUD

All routes today gate on `forRoles(CONSUMER)` with the comment "anyone
can create the delegation grant." The intent is "any authenticated
user can manage their own delegations." But **only CONSUMER** holds
`DATA_ACCESS` in the current map — a PROVIDER trying to create a
delegation would fail.

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `DelegationController` | `post-auth-v2-delegation` | `forRoles(CONSUMER)` | **GAP — see §7 #3** |
| `DelegationController` | `delete-auth-v2-delegation-id` | `forRoles(CONSUMER)` | same |
| `DelegationController` | `get-auth-v2-delegation-id` | `forRoles(CONSUMER)` | same |
| `DelegationController` | `get-auth-v2-delegation-delegate` | `forRoles(CONSUMER)` | same |
| `DelegationController` | `get-auth-v2-delegation-delegator` | `forRoles(CONSUMER)` | same |
| `DelegationController` | `get-auth-v2-delegator-roles` | `forRoles(CONSUMER)` | same |

⚠ **Gap.** "Any authenticated user manages their own delegations"
isn't really `DATA_ACCESS` — it's a self-service capability that every
role should have. See §7 #3.

### 4.17 App credentials

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `AppCredentialsController` | `OP_POST_APPID` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `AppCredentialsController` | `OP_GET_APPID` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `AppCredentialsController` | `OP_DELETE_APPID` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `AppCredentialsController` | `OP_UPDATE_STATUS_APPID` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |

**Existing scopes sufficient.** Note: same "any authenticated user"
issue as Delegation — currently restricted to CONSUMER. If providers
need app credentials too, see §7 #3.

### 4.18 Credit / KYC

`CreditController` uses `forRoles(COS_ADMIN, DELEGATE)` for admin
operations and `forRoles(CONSUMER)` for self operations. Plus
`forRoles(COMPUTE)` for compute-service paths.

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `CreditController` | `post-auth-v2-credit-request` | `forRoles(COMPUTE)` + `KycVerification` | `forScopes(COMPUTE_ACCESS)` + KYC unchanged |
| `CreditController` | `get-auth-v2-credit` | `forRoles(COS_ADMIN, DELEGATE)` | `forScopes(USER_MANAGEMENT)` |
| `CreditController` | `get-auth-v2-user-credit` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `CreditController` | `delete-auth-v2-user-credit` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `CreditController` | `put-auth-v2-credit-request` | `forRoles(COS_ADMIN, DELEGATE)` | `forScopes(USER_MANAGEMENT)` |
| `CreditController` | `put-auth-v2-user-credit` | `forRoles(COS_ADMIN, DELEGATE)` | `forScopes(USER_MANAGEMENT)` |
| `CreditController` | `put-auth-v2-user-credit-add` | `forRoles(COS_ADMIN, DELEGATE)` | `forScopes(USER_MANAGEMENT)` |
| `CreditController` | `post-auth-v2-compute-role-request` | `KycVerification` only | `forScopes(DATA_ACCESS)` + KYC |
| `CreditController` | `get-auth-v2-compute-role-request` | `forRoles(COS_ADMIN, DELEGATE)` | `forScopes(USER_MANAGEMENT)` |
| `CreditController` | `get-auth-v2-user-compute-role-request` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `CreditController` | `delete-auth-v2-user-compute-role-request` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `CreditController` | `put-auth-v2-compute-role-request` | `forRoles(COS_ADMIN, DELEGATE)` | `forScopes(USER_MANAGEMENT)` |
| `CreditController` | `get-auth-v2-admin-user-credit-balance` | `forRoles(COS_ADMIN, DELEGATE)` | `forScopes(USER_MANAGEMENT)` |
| `CreditController` | `get-auth-v2-user-credit-balance` | `forRoles(COMPUTE)` + `KycVerification` | `forScopes(COMPUTE_ACCESS)` + KYC |

**Existing scopes sufficient.** Stretching `USER_MANAGEMENT` for
"manage user credits" is acceptable — same trust class.

### 4.19 KYC

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `KYCController` | `get-auth-v2-kyc-confirm` | none | `forScopes(DATA_ACCESS)` (any logged-in user can complete KYC) |
| `KYCController` | `post-auth-v2-kyc-verify` | none | as above |
| `KYCController` | `post-auth-v2-kyc-revoke` | none | `forScopes(USER_MANAGEMENT)` (admin revokes) — confirm |

**Existing scopes sufficient.** Confirm `kyc-revoke` is admin-only.

### 4.20 User interactions / feedback / votes

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `UserInteractionV2Controller` | `OP_POST_USER_INTERACTION` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `UserInteractionV2Controller` | `OP_GET_USER_INTERACTIONS` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `UserInteractionV2Controller` | `OP_SYNC_INTERACTION_METRICS` | `forRoles(COS_ADMIN)` | `forScopes(USER_MANAGEMENT)` |
| `UserInteractionV2Controller` | `OP_POST_USER_FEEDBACK` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `UserInteractionV2Controller` | `OP_GET_USER_FEEDBACK` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `UserInteractionV2Controller` | `OP_DELETE_USER_FEEDBACK` | `forRoles(CONSUMER)` | `forScopes(DATA_ACCESS)` |
| `UserInteractionV2Controller` | `OP_POST_PROVIDER_FEEDBACK` | `forRoles(CONSUMER)` ⚠ | `forScopesWithContext(self(OWN_ASSET_MANAGEMENT), org(ORG_ASSET_MANAGEMENT))` |
| `UserInteractionV2Controller` | `OP_GET_PROVIDER_FEEDBACK` | `forRoles(CONSUMER)` ⚠ | `forScopesWithContext(self(OWN_ASSET_MANAGEMENT), org(ORG_ASSET_MANAGEMENT))` |
| `UserInteractionV2Controller` | `OP_DELETE_PROVIDER_FEEDBACK` | `forRoles(CONSUMER)` ⚠ | `forScopesWithContext(self(OWN_ASSET_MANAGEMENT), org(ORG_ASSET_MANAGEMENT))` |
| `UserInteractionController` (v1) | various | none in current code | leave for now; deprecate if v2 controller supersedes |
| `VoteController` | `OP_POST_ITEM_VOTE` | none | `forScopes(DATA_ACCESS)` |

**Existing scopes sufficient.** ⚠ Provider-feedback rows: v1 gates by `CONSUMER` but the OpenAPI spec
(`paths/interactions.yaml` `post-provider-feedback`) requires the caller to be the asset owner, a
provider who has interacted with the asset, or `org_admin`. The v1 gate is left as-is per scope of
the migration; v2 is corrected to match the spec.

### 4.21 Reports / dashboards / leaderboard

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `OrganizationReportController` | `OP_ORG_CREATE_REQUEST_REPORT` | none | `forScopes(USER_MANAGEMENT, ORG_USER_MANAGEMENT)` |
| `OrganizationReportController` | `OP_ORG_LIST_REPORT` | none | as above |
| `OrganizationReportController` | `OP_ORG_JOIN_REQUEST_REPORT` | none | as above |
| `OrganizationReportController` | `OP_COMPUTE_ROLE_REQUEST_REPORT` | none | as above |
| `OrganizationReportController` | `OP_PROVIDER_ROLE_REQUEST_REPORT` | none | as above |
| `OrganizationReportController` | `OP_CREDIT_REQUEST_REPORT` | none | as above |
| `SummaryController` | `OP_GET_DASHBOARD_USAGE_SUMMARY` | none | confirm intended audience — likely admin → `forScopes(USER_MANAGEMENT)` |
| `LeaderboardController` | `OP_GET_ORG_LEADERBOARD` | none | `forScopes(DATA_ACCESS)` (public-ish?) |
| `LeaderboardController` | `OP_GET_PROVIDER_LEADERBOARD` | none | as above |
| `LeaderboardController` | `OP_GET_ASSET_LEADERBOARD` | none | as above |

⚠ All these have NO scope gate today. **Decide each one's intended
audience during migration** — flag in §6.

### 4.22 Token / auth-flow

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `TokenController` | `OP_POST_ClIENT_TOKEN` | none | leave open — token issuance is an auth flow, pre-token |
| `AppTokenController` | `OP_POST_APP_TOKEN` | none | leave open — app token issuance flow |
| `ClientController` | `post-create-client-secret` | none | confirm intended — likely `forScopes(DATA_ACCESS)` |
| `PublicController` | `get-auth-v2-jwks` | none | leave open — public JWKS endpoint |

**No scopes needed.**

### 4.23 Health

| Controller | Operation | v1 gate | v2 scope |
|---|---|---|---|
| `HealthController` | n/a — `/health/live` mounted directly on Router | none | leave open |

**No scopes needed.**

## 5. Summary of v2 scope coverage

Across the 31 controllers and ~110 operations:

| Status | Count |
|---|---|
| Maps cleanly to existing 13 scopes | ~95 |
| Genuine gap → recommendation in §7 | ~15 (split across 4 distinct gaps) |
| Has no role gate today; needs an audience decision | ~25 (overlaps with above) |

**The existing 13 scopes cover ~85-90% of cases.** Four genuine gaps,
none requiring more than 1-2 new scopes each.

## 6. Operations with NO role gate today — needs audience decision

These operations have no `forRoles(...)` in the current code. Some are
intentionally public (search, JWKS, health), others are bugs/oversights.
**Each needs a decision during migration:** scope-gate it, or document
as intentionally open.

| Controller | Operation | Likely intent |
|---|---|---|
| `AdminController` | `get-auth-v2-user`, `put-auth-v2-user`, `put-auth-v2-user-password`, `post-auth-v2-user-update`, `delete-auth-v2-user` | Self-update vs admin-only?? Confirm — currently anyone with a JWT can call them |
| `OrganizationController` | `OP_LIST_ORGANISATIONS`, `OP_GET_ORGANISATION_BY_ID` | Public listing? Or DATA_ACCESS? |
| `SearchController` | `POST_SEARCH`, `POST_COUNT_SEARCH`, `POST_ASSET_SEARCH`, `GET_ASSET_SEARCH` | Public catalogue? Or scoped? |
| `ItemController` | `CREATE_ITEM`, `GET_ITEM`, `DELETE_ITEM`, `UPDATE_ITEM`, `GET_ITEM_WITH_ACCESS`, `CHECK_ITEM_NAME_AVAILABILITY`, `DOWNLOAD_SCRIPT` | Custom ownership handlers run; should still add scope upstream |
| `LeaderboardController` | all 3 | Public-ish? `forScopes(DATA_ACCESS)`? |
| `SummaryController` | `OP_GET_DASHBOARD_USAGE_SUMMARY` | Admin only |
| `OrganizationReportController` | all 6 | Admin only |
| `KYCController` | `get-auth-v2-kyc-confirm`, `post-auth-v2-kyc-verify` | Self-service for any consumer |
| `AccessRequestController` | `CREATE_ACCESS_REQUEST_API`, `GET_ACCESS_REQUEST_CONSUMER_API`, `CHECK_ACCESS_REQUEST_API` | Consumer self |
| `VoteController` | `OP_POST_ITEM_VOTE` | Consumer |
| `ListController` | `LIST_AVAILABLE_FILTER` | Public? |
| `CentralListController` / `CentralSearchController` | all | Public central catalogue — confirm |
| `ClientController` | `post-create-client-secret` | Consumer self |
| `TokenController`, `AppTokenController`, `PublicController` | token / JWKS | Auth flow — leave open intentionally |

**Action:** for each row, pick a target scope (or "intentionally open")
during the per-controller migration PR. Don't decide them all in this
audit doc — defer to the controller's owner.

## 7. Recommended scope additions (gaps)

After full audit: **4 gaps, 0 essential additions, 2 worth considering.**

### Gap #1 — `SELF_PROFILE` / personal-account capability

**Symptom:** "view/edit my own profile/credentials/delegations" is held
exclusively by `DATA_ACCESS` (CONSUMER's scope) today. A PROVIDER user
cannot edit their own profile under that mapping; an ORG_ADMIN can't
manage their own delegations.

**Affected:** UserController self-info ops, DelegationController, partial
AppCredentialsController, KYCController self-ops.

**Options:**
- (a) Status quo: stretch `DATA_ACCESS` for everything self-service.
  Low risk if every primary role gets `DATA_ACCESS` added to its bundle.
- (b) Add a new scope `SELF_PROFILE` granted to *every* role
  (CONSUMER, PROVIDER, ORG_ADMIN, COS_ADMIN, COMPUTE).
- (c) Replace with a meta-rule "any authenticated principal" — could be
  a method `forAuthenticated()` on `AuthorizationHandler`.

**Recommendation:** (a) — add `DATA_ACCESS` to every primary role's
scope bundle in `SystemRoleScopeMap`. Cheapest. Audit shows no case
where `DATA_ACCESS` granted broadly causes problems (it doesn't grant
admin capabilities). Document the change in `Scopes.java` javadoc.

**dx-common impact:** small — modify `SystemRoleScopeMap` to grant
`DATA_ACCESS` to PROVIDER, ORG_ADMIN, COS_ADMIN as well. Update
`SystemRoleScopeMapTest`.

### Gap #2 — Resource server management

**Symptom:** `ResourceServerController` is gated on `forRoles(ORG_ADMIN, COS_ADMIN)`.
No existing scope cleanly represents "manage resource server registrations" —
they aren't really assets, they're infrastructure.

**Options:**
- (a) Reuse `ASSET_MANAGEMENT` (COS) and `ORG_ASSET_MANAGEMENT` (ORG).
  Slight semantic stretch — resource servers host assets.
- (b) Add `RESOURCE_SERVER_MANAGEMENT` (platform) and
  `ORG_RESOURCE_SERVER_MANAGEMENT` (org). Two new scopes.

**Recommendation:** (a) for now. Resource server management is
infrequent (4 endpoints, admin-only), and keeping scope count down is
worth a tiny semantic stretch. Revisit if a "manage resource servers
without managing platform assets" use case emerges.

**dx-common impact:** none. Use existing scopes.

### Gap #3 — Delegation/AppCred self-service for non-CONSUMER roles

**Symptom:** PolicyController, DelegationController, AppCredentialsController
are gated on CONSUMER but logically should permit any authenticated
user (a PROVIDER who wants to delegate to someone, an ORG_ADMIN who
wants an app credential for their service).

**Options:**
- Already covered by Gap #1's recommendation: granting `DATA_ACCESS` to
  every primary role solves this transparently.

**dx-common impact:** falls out of Gap #1's fix.

### Gap #4 — Open/anonymous endpoints (search, central catalogue, JWKS, health)

**Symptom:** Some endpoints are intentionally public (no JWT required).
Today this is implicit — no `forRoles(...)` registered. In v2, the
`/auth/v2/whoami`-style chain expects an authenticated principal.

**Options:**
- (a) Skip the v2 `AuthenticationHandler` on these routes entirely.
  Implement at controller level: register the operation handler without
  any auth-stack handler.
- (b) Add an `forPublic()` no-op handler on `AuthorizationHandler` that
  short-circuits.

**Recommendation:** (a). It's already what the OpenAPI spec drives —
operations declared without a security requirement skip the security
handler. No code changes needed; just don't add v2 handlers to those
routes during migration.

**dx-common impact:** none. Document the convention in the migration plan.

## 8. Recommended changes to dx-common (scope/registry)

Only one concrete change recommended:

### Change — Broaden `DATA_ACCESS` to all primary roles

In `SystemRoleScopeMap.java`, update:

```java
// before
m.put(DxRole.CONSUMER, Set.of(Scopes.DATA_ACCESS));
m.put(DxRole.PROVIDER, Set.of(Scopes.OWN_ASSET_MANAGEMENT));
m.put(DxRole.ORG_ADMIN, Set.of(/*5 ORG_*/));
m.put(DxRole.COS_ADMIN, Set.of(/*6 platform*/));

// after — add DATA_ACCESS to every primary role
m.put(DxRole.CONSUMER, Set.of(Scopes.DATA_ACCESS));
m.put(DxRole.PROVIDER, Set.of(Scopes.DATA_ACCESS, Scopes.OWN_ASSET_MANAGEMENT));
m.put(DxRole.ORG_ADMIN, Set.of(Scopes.DATA_ACCESS, /*5 ORG_*/));
m.put(DxRole.COS_ADMIN, Set.of(Scopes.DATA_ACCESS, /*6 platform*/));
// COMPUTE keeps just COMPUTE_ACCESS — service identity, not user-facing
```

**Rationale:** Every authenticated human user has self-service
capabilities (profile, delegation, app-credentials) that aren't really
"data access" but share that trust class. This change makes
`forScopes(DATA_ACCESS)` mean "any authenticated user except service
accounts." Removes the need for a new `SELF_PROFILE` scope (gap #1)
and unblocks gap #3.

**Test impact:** `SystemRoleScopeMapTest` needs an additional assertion
per role — small.

**No new scopes added. No existing scopes renamed.** This is
backwards-compatible: every previously-passing scope check still
passes.

## 9. Migration sequencing recommendation

With this audit + the broadened `DATA_ACCESS`:

1. **dx-common PR — broaden DATA_ACCESS** (small): updates
   `SystemRoleScopeMap` + tests. Merge first, publish SNAPSHOT.
2. **dx-controlplane — Activity migration** (already planned): proceed
   per `activity-auth-v2-migration-plan.md`, using existing scopes.
3. **dx-controlplane — per-controller migration PRs** in this order
   (smallest blast radius first):
   1. Bookmarks (3 ops, single scope) — 1 day
   2. UserInteractionV2 (9 ops, mostly DATA_ACCESS) — 1 day
   3. AppCredentials (4 ops) — 1 day
   4. Delegation (6 ops) — 1 day
   5. Subscription (5 ops) — 1 day
   6. AccessRequest (8 ops) + AccessReport (2 ops) — 1 day
   7. Policy (4 ops) — 1 day
   8. AssetController (4 ops) + ResourceServer (4 ops) — 1 day
   9. SearchController + ItemController (mostly already custom-handler-based) — 2 days
   10. UserController + AdminController + Credit + KYC — 2 days
   11. Organization (heaviest, 25+ ops) — 3-4 days
   12. Reports/dashboards/leaderboard — decide audience per row, 1-2 days

**Total estimate:** ~3 weeks of single-developer work.

## 10. Open questions for review

1. **Broaden `DATA_ACCESS` to all primary roles** (§8) — agree, or
   prefer adding a separate `SELF_PROFILE` scope?
2. **Bookmarks for admins** (§4.2) — preserve "admins can bookmark" or
   restrict to consumer-class scope?
3. **Resource server scopes** (§4.12 / §7 #2) — reuse asset-management
   scopes, or add dedicated ones?
4. **Open endpoints inventory** (§6) — should each "no role gate today"
   row be triaged in this audit, or in its respective controller-level
   migration PR?
5. **Admin operations without role gates** (`put-auth-v2-user`,
   `delete-auth-v2-user`, etc) — bug or feature? Need a sweep to
   confirm intended audience.

## 11. Out of scope for this audit

- Custom validators (`SubscriptionAuthorizationHandler`,
  `UserAccessHandler`, `ItemOwnershipValidator`, `verifyItemTypeAndRole`)
  — they enforce per-resource access rules and run AFTER scope checks.
  Migration touches the scope check; these handlers stay as-is.
- KYC verification (`KycVerification(isKycRequired)`) — orthogonal
  concern that v2 doesn't displace. May be folded into `DxPrincipal`
  later as a flag, not in this round.
- Auditing handler (`auditingHandler::handleApiAudit`) — runs
  independently of auth.
- gRPC server side — separate audit if/when dataplane integrates v2.