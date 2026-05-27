# Independent Provider Feature — Design & Implementation Document

> **Feature:** Allow platform consumers to request the Provider role directly without joining any organisation.  
> **Approver track:** Org-based → Org Admin approves | Platform → COS Admin approves  
> **Status:** Design / Pre-implementation

---

## Table of Contents

1. [Background & Problem Statement](#1-background--problem-statement)
2. [Proposed Design Decision — One Table vs Two Tables](#2-proposed-design-decision--one-table-vs-two-tables)
3. [Database Changes](#3-database-changes)
4. [Data Models](#4-data-models)
5. [API Changes — New & Modified Endpoints](#5-api-changes--new--modified-endpoints)
6. [Service Layer Changes](#6-service-layer-changes)
7. [Handler Layer Changes](#7-handler-layer-changes)
8. [Controller & Routing Changes](#8-controller--routing-changes)
9. [Email Notification Changes](#9-email-notification-changes)
10. [User Info / Token Changes](#10-user-info--token-changes)
11. [Pre-existing Bug Fixed Alongside](#11-pre-existing-bug-fixed-alongside)
12. [Complete End-to-End Workflows](#12-complete-end-to-end-workflows)
13. [Authorization Matrix](#13-authorization-matrix)
14. [Full Touchpoint Summary Table](#14-full-touchpoint-summary-table)
15. [OpenAPI Spec — New Endpoints](#15-openapi-spec--new-endpoints)

---

## 1. Background & Problem Statement

### Current Behaviour

A user on the platform can only become a Provider if they are already a member of an Organisation:

```
Consumer → joins Organisation → Org Admin approves join → request Provider role → Org Admin approves → PROVIDER role assigned
```

The `provider_requests` table enforces this via two hard constraints:
- `organization_id UUID NOT NULL REFERENCES organizations(id)` — must belong to an org
- `user_id UUID NOT NULL REFERENCES organization_users(user_id)` — must exist in the `organization_users` table

And the handler (`ProviderRoleHandler.createProviderRequest`) explicitly rejects any user whose JWT does not carry an `organisation_id` claim:

```java
if (orgID == null || orgID.isEmpty()) {
    ctx.fail(new DxForbiddenException("User is not part any organisation"));
    return;
}
```

### Desired Behaviour

The platform should support **two parallel provider tracks**:

| Track | Who approves | Org required | Owner |
|---|---|---|---|
| **Org-based** (existing) | Org Admin | Yes — user must be org member | Org Admin |
| **Platform** (new) | COS Admin | No — any platform consumer | COS Admin |

For platform providers:
- Any consumer can request provider role directly without joining an org
- COS Admin reviews and approves/rejects the request
- On approval, `PROVIDER` role is assigned in Keycloak (same mechanism)
- No org is linked to the user; COS Admin is the effective owner

---

## 2. Proposed Design Decision — One Table vs Two Tables

### Option A: Separate table `platform_provider_requests`

**Pros:**
- Clean separation, no nullable FKs
- Simpler queries per track (no filter on `provider_type`)
- Independent schema evolution

**Cons:**
- Duplicate table structure (same ~6 columns)
- All DAO, service, and handler code must be duplicated or abstracted
- Reporting becomes two separate streams
- `hasPendingProviderRole()` must query two tables

### Option B: Extend existing `provider_requests` table (recommended)

**Pros:**
- Single source of truth for all provider requests
- Existing DAO, service, and pagination infrastructure reused
- Single report endpoint covers both tracks
- Minimal new code — only the routing and auth guard differ
- `hasPendingProviderRole()` stays one query (filtered by `provider_type`)

**Cons:**
- `organization_id` becomes nullable (requires migration)
- FK from `user_id → organization_users(user_id)` must be dropped (platform users are not in `organization_users`)
- A `provider_type` discriminator column is needed

### Decision: **Option B — Extend existing table**

Two structural changes to `provider_requests`:
1. Make `organization_id` nullable (platform providers have no org)
2. Drop the FK from `user_id → organization_users(user_id)` (platform users won't be in that table)
3. Add `provider_type VARCHAR CHECK ('org', 'platform')` as a discriminator

**Why `provider_type` over `request_type`:**
- Values `'org'` / `'platform'` map 1:1 to the URL segments (`/organization/...` vs `/platform/...`) and to who manages the provider
- `provider_type` answers "what kind of provider is this?" — the ownership model — not just "what kind of request"
- The existing `AbstractBaseDAO` / `ConditionBuilder` only supports equality filters; `null` values in filter maps are silently skipped, so filtering by `organization_id IS NULL` is **not possible** without DAO changes
- `provider_type = 'platform'` is a plain equality filter that works out of the box with the current infrastructure

No new table is needed.

---

## 3. Database Changes

### New Migration: `V62__platform_provider_support.sql`

```sql
-- Step 1: Make organization_id nullable
-- Platform providers do not belong to any organisation
ALTER TABLE provider_requests
  ALTER COLUMN organization_id DROP NOT NULL;

-- Step 2: Drop the FK that forces user_id to exist in organization_users
-- Platform providers are never in that table
ALTER TABLE provider_requests
  DROP CONSTRAINT provider_requests_user_id_fkey;

-- Step 3: Add discriminator column to tell the two tracks apart
-- Default 'org' so all existing rows remain valid
ALTER TABLE provider_requests
  ADD COLUMN provider_type VARCHAR NOT NULL DEFAULT 'org'
    CHECK (provider_type IN ('org', 'platform'));

-- Step 4: Backfill all existing rows with 'org'
UPDATE provider_requests
  SET provider_type = 'org'
  WHERE organization_id IS NOT NULL;
```

### Final `provider_requests` table schema (after migration)

```sql
CREATE TABLE provider_requests (
    id               UUID    DEFAULT gen_random_uuid() PRIMARY KEY,
    organization_id  UUID    REFERENCES organizations(id) ON DELETE CASCADE,  -- now NULLABLE
    user_id          UUID    NOT NULL,                                         -- FK to org_users dropped
    provider_type    VARCHAR NOT NULL DEFAULT 'org'
                             CHECK (provider_type IN ('org', 'platform')),     -- NEW
    status           VARCHAR NOT NULL CHECK (status IN ('pending', 'granted', 'rejected')),
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP
);
```

### What does NOT change in the DB

- `organization_users` table — untouched
- `organizations` table — untouched
- `organization_join_requests` table — untouched
- `organization_create_requests` table — untouched
- All other tables — untouched

---

## 4. Data Models

### 4.1 `ProviderRoleRequest.java` — Modified

**What changes:**
- `orgId` field becomes nullable (`UUID`, but can be `null` for platform track)
- New field `providerType` (`String`: `"org"` or `"platform"`)
- `fromJson()` — `organization_id` is no longer required; parsed as null if absent
- `toJson()` — skip `organization_id` key if null
- `getTableName()` — **bug fix**: was returning `"organization_create_requests"`, must return `"provider_requests"`

**Before (current):**

```java
public record ProviderRoleRequest(
    UUID id,
    UUID userId,
    UUID orgId,          // mandatory
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
)

public String getTableName() {
    return Constants.ORG_CREATE_REQUEST_TABLE;  // BUG: wrong table name
}
```

**After (new):**

```java
public record ProviderRoleRequest(
    UUID id,
    UUID userId,
    UUID orgId,          // nullable — null for platform providers
    String providerType, // "org" or "platform"
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
)

// fromJson: organization_id is optional
String orgIdStr = json.getString("organization_id");
UUID orgId = (orgIdStr != null && !orgIdStr.isBlank()) ? UUID.fromString(orgIdStr) : null;

// toJson: only include organization_id if present
if (orgId != null) json.put("organization_id", orgId.toString());
json.put("provider_type", providerType);

// getTableName: fixed
public String getTableName() {
    return Constants.PROVIDER_REQUEST_TABLE;   // "provider_requests" — bug fixed
}
```

**Response JSON shape — org-based request:**
```json
{
  "id": "e970320f-ffed-4946-ae68-cb13a9f7d7d2",
  "user_id": "719b5e7c-3a20-4f77-91f9-1f43ac4ef6a7",
  "organization_id": "0706147d-2c77-4165-8550-812804d114be",
  "provider_type": "org",
  "status": "pending",
  "created_at": "2025-08-25 05:28:20.356043",
  "updated_at": "2025-08-25 05:30:14.607398"
}
```

**Response JSON shape — platform request:**
```json
{
  "id": "f881430g-ggfe-5057-bf79-dc25b0281e03",
  "user_id": "829c6491-4b31-5g88-02g0-9ga9e081g420",
  "provider_type": "platform",
  "status": "pending",
  "created_at": "2025-09-01 10:00:00.000000",
  "updated_at": "2025-09-01 10:00:00.000000"
}
```

---

### 4.2 `Constants.java` — Modified

**What changes (additions only, no removals):**

```java
// Fix: correct table name for provider requests (was missing)
public static final String PROVIDER_REQUEST_TABLE = "provider_requests";

// New: discriminator field name and values
public static final String PROVIDER_TYPE          = "provider_type";
public static final String PROVIDER_TYPE_ORG      = "org";
public static final String PROVIDER_TYPE_PLATFORM = "platform";

// New: filter map for platform provider requests (COS Admin view)
public static final Map<String, String> ALLOWED_FILTER_MAP_FOR_PLATFORM_PROVIDER_REQUEST =
    Map.of(
        "status", STATUS,
        "userId", USER_ID
    );

// New: API-to-DB field mapping for platform provider requests
public static final Map<String, String> API_TO_DB_PLATFORM_PROVIDER_REQUEST =
    Map.ofEntries(
        Map.entry("userId",       USER_ID),
        Map.entry("status",       STATUS),
        Map.entry("providerType", PROVIDER_TYPE),
        Map.entry("createdAt",    CREATED_AT),
        Map.entry("updatedAt",    UPDATED_AT)
    );
```

---

### 4.3 `OrganisationAuditOperation.java` — Modified (additions only)

```java
// Existing — unchanged
REQUEST_PROVIDER_ROLE("Request for upgrading to provider role"),
UPDATE_PROVIDER_REQUEST("Update provider role requests"),
GET_PROVIDER_REQS("Get provider requests"),
DELETE_PENDING_PROVIDER_REQUEST("Delete pending provider request"),

// New
REQUEST_PLATFORM_PROVIDER_ROLE("Request for platform provider role"),
GET_PLATFORM_PROVIDER_REQS("Get platform provider role requests"),
UPDATE_PLATFORM_PROVIDER_REQUEST("Update platform provider role request"),
```

---

## 5. API Changes — New & Modified Endpoints

### 5.1 Modified: `POST /iudx/v2/auth/organization/user/provider_role/requests`

**What changes:** Currently rejects users with no org. After the change, it accepts all authenticated consumers regardless of org membership.

**No URL change.** Behaviour branches internally based on whether the user has an `organisation_id` claim in their JWT.

| | Before | After |
|---|---|---|
| User without org | 403 Forbidden | 200 OK — creates platform request |
| User with org | 200 OK — creates org request | 200 OK — creates org request (unchanged) |
| Notification email | Org Admin | Org Admin (org) / COS Admin (platform) |

**Request body:** No change
```json
{}  // empty body — user_id and org_id are taken from the JWT token
```

**Response 200 — org-based (unchanged):**
```json
{
  "type": "urn:dx:ControlPlane:success",
  "title": "Success",
  "detail": "Created Request"
}
```

**Response 200 — platform (new):**
```json
{
  "type": "urn:dx:ControlPlane:success",
  "title": "Success",
  "detail": "Created Request"
}
```

**Error Responses (updated):**

| Code | When |
|---|---|
| 401 | Token invalid/expired |
| 409 | A pending or granted provider role request already exists for this user (both tracks) |

---

### 5.2 Unchanged: `GET /iudx/v2/auth/organization/user/provider_role/requests`

Org Admin sees their org's pending provider requests. No change — still filtered by `organization_id` from JWT + `provider_type = 'org'`.

---

### 5.3 Unchanged: `PUT /iudx/v2/auth/organization/user/provider_role/requests/{id}`

Org Admin approves/rejects an org-based provider request. The core update logic is shared, but the COS Admin route uses a **thin wrapper method** (`updatePlatformProviderRequest`) that calls the same service method and logs `UPDATE_PLATFORM_PROVIDER_REQUEST` instead of `UPDATE_PROVIDER_REQUEST`. See section 7.1.

---

### 5.4 NEW: `GET /iudx/v2/auth/platform/provider-requests`

**Who calls it:** COS Admin only  
**Purpose:** List all pending provider requests from users with no organisation (platform track)  
**Auth:** `ORG_MANAGEMENT` scope (same as other COS Admin routes)

> The `/platform/` prefix already distinguishes this from `/organization/` routes — no `/platform` suffix needed.

**Query Parameters:**

| Param | Type | Description |
|---|---|---|
| `page` | integer | Page number (default: 1) |
| `size` | integer | Items per page (default: 10) |
| `sort` | string | Sort field + direction |
| `status` | string | Filter: `pending`, `granted`, `rejected` |
| `userId` | UUID | Filter by specific user |

**Response 200:**
```json
{
  "type": "urn:dx:ControlPlane:success",
  "title": "Success",
  "result": [
    {
      "id": "f881430g-ggfe-5057-bf79-dc25b0281e03",
      "user_id": "829c6491-4b31-5g88-02g0-9ga9e081g420",
      "provider_type": "platform",
      "status": "pending",
      "created_at": "2025-09-01T10:00:00",
      "updated_at": "2025-09-01T10:00:00",
      "roles": ["consumer"],
      "account_enabled": true
    }
  ],
  "paginationInfo": {
    "page": 1,
    "size": 10,
    "totalCount": 5,
    "totalPages": 1,
    "hasNext": false,
    "hasPrevious": false
  }
}
```

**Error Responses:**

| Code | Condition |
|---|---|
| 401 | Token invalid/expired |
| 403 | Caller does not have `cos_admin` role |

---

### 5.5 NEW: `PUT /iudx/v2/auth/platform/provider-requests/{id}`

**Who calls it:** COS Admin only  
**Purpose:** Approve or reject a platform provider role request  
**Auth:** `ORG_MANAGEMENT` scope

**Path param:** `id` (UUID) — provider request ID

**Request Body:**
```json
{
  "status": "granted"
}
```

**On `status = "granted"` — side effects:**
1. Update `provider_requests.status = 'granted'` and `updated_at = now()`
2. Fetch `user_id` from the request record
3. Call `keycloakUserService.addRoleToUser(userId, DxRole.PROVIDER)` — assigns PROVIDER in Keycloak
4. Send approval email to user

**On `status = "rejected"` — side effects:**
1. Update `provider_requests.status = 'rejected'`
2. Send rejection email to user

**Response 200:**
```json
{
  "type": "urn:dx:ControlPlane:success",
  "title": "Success",
  "detail": "Provider role updated"
}
```

**Error Responses:**

| Code | Condition |
|---|---|
| 400 | Request not found or invalid status value |
| 401 | Unauthorized |
| 403 | Caller does not have `cos_admin` role |

> **Implementation note:** A thin `updatePlatformProviderRequest()` method is added to `ProviderRoleHandler` that calls the same service method (`updateProviderRequestStatus`) but logs `UPDATE_PLATFORM_PROVIDER_REQUEST` in the audit trail. This is needed because the handler has no context about which route invoked it — without a separate method, all COS Admin approvals would be mis-logged as `UPDATE_PROVIDER_REQUEST`.

---

### 5.6 Unchanged: `GET /iudx/v2/auth/organization/user/provider_requests`

User views their own pending provider request. Already queries by `user_id` only (no org filter). Works for both tracks with no code change.

---

### 5.7 Unchanged: `DELETE /iudx/v2/auth/organization/user/provider-requests/{id}`

User deletes their own pending request. Already works by request ID with `user_id` ownership check. Works for both tracks.

---

## 6. Service Layer Changes

### 6.1 `ProviderRoleService.java` (interface) — Modified

**What changes:** Two new methods added. All existing methods unchanged.

```java
// NEW: check if a user has a pending platform provider role request (no org)
Future<Boolean> hasPendingPlatformProviderRole(UUID userId);

// NEW: paginated list of all platform provider requests (for COS Admin)
Future<PaginatedResult<ProviderRoleRequest>> getAllPlatformProviderRequests(PaginatedRequest request);
```

**Existing methods — no changes:**
```java
Future<ProviderRoleRequest> createProviderRequest(ProviderRoleRequest providerRoleRequest);
Future<Boolean> updateProviderRequestStatus(UUID requestId, Status status);
Future<List<ProviderRoleRequest>> getAllPendingProviderRoleRequests(UUID orgId);
Future<PaginatedResult<ProviderRoleRequest>> getAllPendingProviderRoleRequests(PaginatedRequest req);
Future<Boolean> hasPendingProviderRole(UUID userId, UUID orgId);
Future<Boolean> createProviderRole(ProviderRoleRequest providerRoleRequest);
Future<ProviderRoleRequest> getProviderRequestById(UUID requestId);
Future<ProviderRoleRequest> getProviderRoleRequestByUserId(UUID userId);
Future<Boolean> deleteProviderRoleRequest(UUID orgId, UUID userId);
Future<Boolean> deleteProviderRoleRequestById(UUID id);
```

---

### 6.2 `ProviderRoleServiceImpl.java` — Modified

**What changes:**

**a) `createProviderRequest()` — minor change**

Currently filters existing requests by `userId` only. This is already correct (no orgId filter). Only ensure the `providerType` field from the model is persisted to DB. No logic change needed — the model carries the `provider_type` value, the DAO persists it automatically via `toNonEmptyFieldsMap()`.

**b) New method: `hasPendingPlatformProviderRole(UUID userId)`**

```java
@Override
public Future<Boolean> hasPendingPlatformProviderRole(UUID userId) {
    Map<String, Object> filterMap = Map.of(
        STATUS,        Status.PENDING.getStatus(),
        USER_ID,       userId.toString(),
        PROVIDER_TYPE, PROVIDER_TYPE_PLATFORM
    );
    return providerRequestDAO.getAllWithFilters(filterMap)
        .map(list -> !list.isEmpty());
}
```

**c) New method: `getAllPlatformProviderRequests(PaginatedRequest)`**

```java
@Override
public Future<PaginatedResult<ProviderRoleRequest>> getAllPlatformProviderRequests(
        PaginatedRequest paginatedRequest) {
    // paginatedRequest carries additionalFilter: provider_type = 'platform'
    // built in the handler before calling this method
    return providerRequestDAO.getAllWithFilters(paginatedRequest);
}
```

**d) `updateProviderRequestStatus()` — no change**

Already: looks up request by ID → updates status → if GRANTED, fetches userId and calls Keycloak. This works identically for both org-based and platform requests.

**e) `createProviderRole()` — no change**

This is the "direct grant" action used by org admins. COS Admin does not use this method — COS Admin uses `updateProviderRequestStatus()` via the new PUT route.

**f) `hasPendingProviderRole(userId, orgId)` — no change**

This continues to serve the org-based track check.

---

### 6.3 `OrganizationService.java` (interface) — Modified

**What changes:** Two new delegation methods added. No existing methods changed.

```java
// NEW
Future<Boolean> hasPendingPlatformProviderRole(UUID userId);
Future<PaginatedResult<ProviderRoleRequest>> getAllPlatformProviderRequests(PaginatedRequest request);
```

---

### 6.4 `OrganizationServiceImpl.java` — Modified

**What changes:** Implement the two new interface methods by delegating to `providerRoleService`.

```java
@Override
public Future<Boolean> hasPendingPlatformProviderRole(UUID userId) {
    return providerRoleService.hasPendingPlatformProviderRole(userId);
}

@Override
public Future<PaginatedResult<ProviderRoleRequest>> getAllPlatformProviderRequests(PaginatedRequest req) {
    return providerRoleService.getAllPlatformProviderRequests(req);
}
```

---

### 6.5 `UserServiceImpl.getUserInfo()` — Modified

**Current code (shortened):**
```java
if (orgId != null) {
    pendingProvider = organizationService.hasPendingProviderRole(dxUser.sub(), orgId);
}
// if orgId is null, pendingProvider stays Future.succeededFuture(false)
// → platform users never show "provider" in pendingRoles
```

**What changes:** When user has no org, check the platform track instead.

```java
if (orgId != null) {
    // existing: org-based pending provider check
    pendingProvider = organizationService.hasPendingProviderRole(dxUser.sub(), orgId);
} else {
    // new: platform pending provider check
    pendingProvider = organizationService.hasPendingPlatformProviderRole(dxUser.sub());
}
```

**Effect:** The `/user/info` endpoint will now correctly show `"provider"` in `pendingRoles` for platform users who have submitted a request.

---

## 7. Handler Layer Changes

### 7.1 `ProviderRoleHandler.java` — Modified

#### Existing method: `createProviderRequest()` — significant change

**What changes:** Remove the hard 403 guard that rejects users without an org. Branch on whether user has `organisation_id` in JWT and build the request object accordingly.

**Current logic:**
```
if (orgID == null) → 403 Forbidden
else → create org-based request
```

**New logic:**
```
if (orgID == null || empty)
    → build ProviderRoleRequest with providerType = "platform", orgId = null
    → send email to COS Admin
else
    → build ProviderRoleRequest with providerType = "org", orgId = orgID
    → send email to Org Admin   (unchanged)
```

**Detailed new method:**

```
createProviderRequest(ctx):
  1. Extract DxUser from principal
  2. Get userId from dxUser.sub()
  3. Get orgID from dxUser.organisationId()
  4. Determine isPlatform = (orgID == null || orgID.isEmpty())
  5. Build ProviderRoleRequest:
       - user_id         = userId
       - organization_id = orgID (null if platform)
       - provider_type   = isPlatform ? "platform" : "org"
       - status          = PENDING (default)
  6. Call organizationService.createProviderRequest(providerRoleRequest)
  7. On success:
       - Set audit log with REQUEST_PROVIDER_ROLE or REQUEST_PLATFORM_PROVIDER_ROLE
       - Send 200 OK
       - If platform → emailComposer.sendEmailForPlatformProviderRole(request, user)
       - If org-based → emailComposer.sendEmailForProviderRole(request, user)  [unchanged]
  8. On failure → ctx.fail(err)
```

**Resolved design decisions for this method:**

| Decision | Resolution |
|---|---|
| **KYC guard** | Applies to both tracks — URL is unchanged, `kycVerification(isKycRequired)` stays on the route. Platform providers must complete KYC before requesting the role. No code change needed. |
| **Cross-track conflict check** | `createProviderRequest` in `ProviderRoleServiceImpl` filters by `userId` only (no `provider_type` filter). This is **correct and intentional**: a user with a GRANTED org-based request gets 409 (they're already a provider); a user with PENDING on one track gets 409 (can't have two concurrent requests). Only REJECTED requests allow re-application on either track. |

#### Existing methods — no change

| Method | Why no change |
|---|---|
| `updateProviderRequest()` | Org Admin route only. Service call + Keycloak grant are track-agnostic. |
| `getProviderRequest()` | Org Admin view, scoped by `organisation_id` from JWT. Platform requests have `organisation_id = NULL` so they never appear here. |
| `deleteUserProviderRoleRequest()` | Queries by `userId`; works for both tracks. |
| `getProviderRoleRequest()` | User's own request; queries by `userId`. Works for both. |
| `createProviderRole()` | Org Admin direct grant. COS Admin does not use this path. |

#### New method: `updatePlatformProviderRequest()` — thin audit wrapper for COS Admin

The core update logic (service call, email, Keycloak) is identical for both tracks. The only difference is the audit operation tag. Rather than polluting the handler with a track-detection branch, a thin wrapper method is added:

```java
public void updatePlatformProviderRequest(RoutingContext ctx) {
    JsonObject orgRequestJson = ctx.body().asJsonObject();
    UUID reqId = RequestHelper.getPathParamAsUUID(ctx, "id");
    Status status = Status.fromString(orgRequestJson.getString("status"));

    organizationService
        .updateProviderRequestStatus(reqId, status)
        .onSuccess(v -> {
            // Only difference from updateProviderRequest(): audit op
            UserActivityAuditLogBuilder auditLogBuilder =
                OrganizationAuditHelper.buildOrganisationAudit(
                    ctx, new JsonObject().put(ID, reqId.toString()),
                    OrganisationAuditOperation.UPDATE_PLATFORM_PROVIDER_REQUEST);
            CpRoutingContextHelper.setAuditingLogV2(ctx, auditLogBuilder);

            emailComposer
                .sendUserEmailForProviderRoleApproval(reqId, status)
                .onFailure(err -> LOGGER.error("Email send failed", err));

            ResponseBuilder.sendSuccess(ctx, "Provider role updated", urnGenerator);
        })
        .onFailure(ctx::fail);
}
```

#### New method: `getPlatformProviderRequests()` — for COS Admin

```
getPlatformProviderRequests(ctx):
  1. Build PaginatedRequest:
       - allowedFiltersDbMap = ALLOWED_FILTER_MAP_FOR_PLATFORM_PROVIDER_REQUEST
       - apiToDbMap          = API_TO_DB_PLATFORM_PROVIDER_REQUEST
       - additionalFilters   = { "provider_type": "platform" }  ← always filter to platform track
       - defaultSort         = created_at DESC
  2. Call organizationService.getAllPlatformProviderRequests(request)
  3. Compose with userService.enrichWithUserRoles() to add roles + account_enabled
  4. On success:
       - Set audit log with GET_PLATFORM_PROVIDER_REQS
       - Send 200 OK with paginated result
  5. On failure → ctx.fail(err)
```

---

## 8. Controller & Routing Changes

### 8.1 No new controller — use existing `OrganizationController`

**The existing `OrganizationController` is reused. No new controller class is needed.**

Reasons:
- `OrganizationController` already has all three access guards wired up: `selfAccess`, `orgAdminAccess`, `cosAdminAccess`
- `providerRoleHandler` is already injected into it
- Adding two new route bindings is a two-line change — a new controller would just duplicate all the constructor wiring for no benefit
- The `/platform/` URL prefix lives in the OpenAPI spec (operationId), not in the controller class — there is no architectural reason to separate it

---

### 8.2 `OperationIds.java` — Modified (additions only)

```java
// NEW
public static final String OP_GET_PLATFORM_PROVIDER_REQUESTS =
    "get-auth-v2-platform-provider-requests";

public static final String OP_UPDATE_PLATFORM_PROVIDER_REQUEST =
    "put-auth-v2-platform-provider-requests-id";
```

**Existing operation IDs — no change:**
```java
OP_CREATE_PROVIDER_REQUEST       = "post-auth-v2-user-roles"
OP_GET_PROVIDER_REQUEST          = "get-auth-v2-provider-requests"
OP_UPDATE_PROVIDER_REQUEST       = "put-auth-v2-user-roles"
OP_GET_USER_PROVIDER_REQUESTS    = "get-auth-v2-user-provider-requests"
OP_DELETE_USER_PROVIDER_REQUEST  = "delete-auth-v2-user-provider-requests"
OP_CREATE_PROVIDER_ROLE          = "post-auth-v2-organization-user-provider"
```

---

### 8.3 `OrganizationController.java` — Modified (additions only)

The three existing access handlers are already defined in the controller constructor:

```java
var selfAccess     = AuthorizationHandler.forScopes(Scopes.DATA_ACCESS);
var orgAdminAccess = AuthorizationHandler.forScopes(Scopes.ORG_USER_MANAGEMENT);
var cosAdminAccess = AuthorizationHandler.forScopes(Scopes.ORG_MANAGEMENT);   // already exists
```

**Two new route bindings added:**

```java
// NEW: COS Admin lists all platform provider role requests
routerBuilder.operation(OP_GET_PLATFORM_PROVIDER_REQUESTS)
    .handler(auditingHandler::handleApiAudit)
    .handler(cosAdminAccess)
    .handler(providerRoleHandler::getPlatformProviderRequests);

// NEW: COS Admin approves/rejects a platform provider role request
// NOTE: thin wrapper — same service call as updateProviderRequest but logs UPDATE_PLATFORM_PROVIDER_REQUEST
routerBuilder.operation(OP_UPDATE_PLATFORM_PROVIDER_REQUEST)
    .handler(auditingHandler::handleApiAudit)
    .handler(cosAdminAccess)
    .handler(providerRoleHandler::updatePlatformProviderRequest);
```

**Existing route bindings — no change:**

```java
// User submits provider role request (modified internally but route unchanged)
routerBuilder.operation(OP_CREATE_PROVIDER_REQUEST)
    .handler(auditingHandler::handleApiAudit)
    .handler(selfAccess)
    .handler(kycVerification(isKycRequired))
    .handler(providerRoleHandler::createProviderRequest);

// Org Admin lists pending provider requests for their org
routerBuilder.operation(OP_GET_PROVIDER_REQUEST)
    .handler(auditingHandler::handleApiAudit)
    .handler(orgAdminAccess)
    .handler(providerRoleHandler::getProviderRequest);

// Org Admin approves/rejects a provider request
routerBuilder.operation(OP_UPDATE_PROVIDER_REQUEST)
    .handler(auditingHandler::handleApiAudit)
    .handler(orgAdminAccess)
    .handler(providerRoleHandler::updateProviderRequest);

// User views own provider request
routerBuilder.operation(OP_GET_USER_PROVIDER_REQUESTS)
    .handler(auditingHandler::handleApiAudit)
    .handler(selfAccess)
    .handler(providerRoleHandler::getProviderRoleRequest);

// User deletes own pending provider request
routerBuilder.operation(OP_DELETE_USER_PROVIDER_REQUEST)
    .handler(auditingHandler::handleApiAudit)
    .handler(selfAccess)
    .handler(providerRoleHandler::deleteUserProviderRoleRequest);

// Org Admin directly grants provider role
routerBuilder.operation(OP_CREATE_PROVIDER_ROLE)
    .handler(auditingHandler::handleApiAudit)
    .handler(orgAdminAccess)
    .handler(providerRoleHandler::createProviderRole);
```

---

## 9. Email Notification Changes

### 9.1 `EmailComposer.java` — Modified

**What changes:** One new method added. Existing methods unchanged.

**Existing `sendEmailForProviderRole()` — no change**

Still sends to the Org Admin email for org-based requests:
```java
public Future<Void> sendEmailForProviderRole(ProviderRoleRequest request, User user) {
    return getOrgAdminEmail(request.orgId())  // looks up org admin by orgId
        .compose(orgAdminEmail -> newEmail()
            .template("templates/request-provider-role.html")
            .to(orgAdminEmail)              // → Org Admin
            ...
        );
}
```

**New `sendEmailForPlatformProviderRole()` — new method**

Sends directly to `cosAdminEmailId` (already a field in the class, no new config needed):
```java
public Future<Void> sendEmailForPlatformProviderRole(ProviderRoleRequest request, User user) {
    return newEmail()
        .template("templates/request-provider-role.html")  // reuse same template
        .to(cosAdminEmailId)                                 // → COS Admin (no org lookup needed)
        .subject("Platform Provider Role Request")
        .variable("ADMIN_FIRST_NAME", "Admin")
        .variable("ADMIN_LAST_NAME", "")
        .variable("USER_FIRST_NAME", user.principal().getString("name"))
        .variable("USER_EMAIL_ID", user.principal().getString("email"))
        .variable("ADMIN_PORTAL_URL", adminPortalUrl)
        .variable("SENDER_NAME", senderName)
        .variable("DETAILS_MESSAGE", detailsMessage())
        .send();
}
```

**Existing `sendUserEmailForProviderRoleApproval()` — no change**

Already looks up user by `userId` from the request record and sends to user. Works for both tracks.

---

## 10. User Info / Token Changes

### 10.1 Token structure — no change

`DxRole.PROVIDER` is already a global platform-level role in Keycloak (not org-scoped). When assigned via `keycloakUserService.addRoleToUser(userId, DxRole.PROVIDER)`, it appears in `realm_access.roles` in the JWT exactly the same way for both org-based and platform providers.

Platform providers will not have an `organisation_id` in their JWT claims (they were never in an org). This is correct and expected — their PROVIDER role is platform-owned.

### 10.2 `UserServiceImpl.getUserInfo()` — modified

As described in section 6.5, the `pendingRoles` check is updated so that platform users see `"provider"` in their `pending_roles` array when they have a pending platform provider request.

No change to the token-issuing path itself — this only affects the user-info endpoint response.

---

## 11. Pre-existing Bug Fixed Alongside

**File:** `organization/models/ProviderRoleRequest.java`  
**Line:** `getTableName()` method  
**Bug:** Returns `Constants.ORG_CREATE_REQUEST_TABLE` = `"organization_create_requests"` (wrong table)  
**Fix:** Return `Constants.PROVIDER_REQUEST_TABLE` = `"provider_requests"` (correct table)

This bug currently causes every DAO operation on `ProviderRoleRequest` to silently target the wrong table. It should be fixed in the same PR.

---

## 12. Complete End-to-End Workflows

### Workflow A — Platform Provider (NEW)

```
Consumer (no org)
       │
       │  POST /iudx/v2/auth/organization/user/provider_role/requests
       │  Body: {} (empty — user_id derived from JWT, orgId absent from JWT)
       │
       ▼
ProviderRoleHandler.createProviderRequest()
       │
       ├─ Extracts userId from JWT
       ├─ Checks orgId from JWT → NULL
       ├─ Sets providerType = "platform"
       ├─ Builds ProviderRoleRequest { userId, orgId=null, providerType="platform", status="pending" }
       │
       ▼
ProviderRoleServiceImpl.createProviderRequest()
       │
       ├─ Queries: any existing pending/granted request for this userId?
       │    → If YES (pending or granted): 409 Conflict
       │    → If YES (rejected): allow new request
       │    → If NO: proceed
       │
       ├─ providerRequestDAO.create(request)
       │    → INSERT provider_requests(user_id, organization_id=NULL, provider_type='platform', status='pending')
       │
       ▼
Handler
       ├─ Audit log: REQUEST_PLATFORM_PROVIDER_ROLE
       ├─ 200 OK → "Created Request"
       └─ emailComposer.sendEmailForPlatformProviderRole() → email to cosAdminEmailId

─────────────── COS Admin reviews ───────────────

COS Admin
       │
       │  GET /iudx/v2/auth/platform/provider-requests
       │  Auth: ORG_MANAGEMENT scope (cos_admin role)
       │
       ▼
ProviderRoleHandler.getPlatformProviderRequests()
       │
       ├─ Builds PaginatedRequest with additionalFilter: provider_type = 'platform'
       ├─ organizationService.getAllPlatformProviderRequests(request)
       │    → SELECT * FROM provider_requests WHERE provider_type = 'platform' [+ pagination + filters]
       │
       ├─ userService.enrichWithUserRoles() → adds Keycloak roles + account_enabled to each record
       │
       └─ 200 OK → paginated list of platform provider requests

       │
       │  PUT /iudx/v2/auth/platform/provider-requests/{requestId}
       │  Body: { "status": "granted" }
       │  Auth: ORG_MANAGEMENT scope (cos_admin role)
       │
       ▼
ProviderRoleHandler.updatePlatformProviderRequest()    ← thin wrapper: same service call, logs UPDATE_PLATFORM_PROVIDER_REQUEST
       │
       ├─ Reads requestId from path param
       ├─ Reads status from body → GRANTED
       │
       ▼
ProviderRoleServiceImpl.updateProviderRequestStatus(requestId, GRANTED)
       │
       ├─ UPDATE provider_requests SET status='granted', updated_at=now() WHERE id=requestId
       ├─ Fetches request by ID → gets userId
       ├─ keycloakUserService.addRoleToUser(userId, DxRole.PROVIDER)
       │    → PROVIDER role assigned in Keycloak
       │
       ▼
Handler
       ├─ Audit log: UPDATE_PLATFORM_PROVIDER_REQUEST
       ├─ emailComposer.sendUserEmailForProviderRoleApproval(requestId, GRANTED)
       │    → email to user: "Your provider role request has been approved"
       └─ 200 OK → "Provider role updated"

─────────────── User's next token request ───────────────

Consumer's next JWT
       └─ realm_access.roles = ["consumer", "provider"]   ← PROVIDER now present
```

---

### Workflow B — Org-based Provider (EXISTING — unchanged)

```
Org Member
       │
       │  POST /iudx/v2/auth/organization/user/provider_role/requests
       │  Body: {} (user_id from JWT, orgId present in JWT)
       │
       ▼
ProviderRoleHandler.createProviderRequest()
       │
       ├─ Extracts orgId from JWT → PRESENT (e.g., "0706147d-...")
       ├─ Sets providerType = "org"
       ├─ Builds ProviderRoleRequest { userId, orgId, providerType="org", status="pending" }
       │
       ▼
ProviderRoleServiceImpl.createProviderRequest()
       │
       ├─ Same duplicate-check logic
       ├─ providerRequestDAO.create(request)
       │    → INSERT provider_requests(user_id, organization_id, provider_type='org', status='pending')
       │
       ▼
Handler
       ├─ Audit log: REQUEST_PROVIDER_ROLE
       ├─ 200 OK
       └─ emailComposer.sendEmailForProviderRole() → email to Org Admin   ← unchanged

─────────────── Org Admin reviews ───────────────

Org Admin
       │  GET /iudx/v2/auth/organization/user/provider_role/requests   [unchanged route]
       │  PUT /iudx/v2/auth/organization/user/provider_role/requests/{id}  [unchanged route]
       │
       ▼
       (same approval flow as today)
```

---

### Workflow C — User checks their own pending status (both tracks)

```
Any User (with or without org)
       │
       │  GET /iudx/v2/auth/user/info   ← implicit via getUserInfo()
       │
       ▼
UserServiceImpl.getUserInfo()
       │
       ├─ If orgId present in JWT:
       │    pendingProvider = organizationService.hasPendingProviderRole(userId, orgId)
       │                       → queries provider_requests WHERE user_id AND organization_id AND status='pending'
       │
       └─ If orgId absent from JWT (platform track):
            pendingProvider = organizationService.hasPendingPlatformProviderRole(userId)  ← NEW
                               → queries provider_requests WHERE user_id AND provider_type='platform' AND status='pending'

       If pendingProvider = true → pendingRoles includes "provider"

Response:
{
  "pending_roles": ["provider"],
  "roles": ["consumer"]
}
```

---

### Workflow D — User withdraws/deletes their own pending request (both tracks, unchanged)

```
Any User
       │
       │  GET /iudx/v2/auth/organization/user/provider_requests
       │   → getProviderRoleRequestByUserId(userId) → queries by userId only, works for both tracks
       │
       │  DELETE /iudx/v2/auth/organization/user/provider-requests/{id}
       │   → deleteProviderRoleRequestById(id) → deletes by PK, works for both tracks
```

---

## 13. Authorization Matrix

| Operation | Consumer (no org) | Org Member | Org Admin | COS Admin |
|---|---|---|---|---|
| Submit platform provider request | ✓ (new) | ✗ (has org, goes to org track) | ✗ | ✗ |
| Submit org-based provider request | ✗ (no org) | ✓ (existing) | ✗ | ✗ |
| View own provider request | ✓ | ✓ | ✓ | ✓ |
| Delete own pending provider request | ✓ | ✓ | ✓ | ✓ |
| List platform provider requests | ✗ | ✗ | ✗ | ✓ (new) |
| Approve/reject platform request | ✗ | ✗ | ✗ | ✓ (new) |
| List org-based provider requests | ✗ | ✗ | ✓ (existing) | ✗ |
| Approve/reject org-based request | ✗ | ✗ | ✓ (existing) | ✗ |
| Direct-grant provider role (org) | ✗ | ✗ | ✓ (existing) | ✗ |

**Scopes used:**

| Scope | Holder | Purpose |
|---|---|---|
| `DATA_ACCESS` | All authenticated users | Submit and view own requests |
| `ORG_USER_MANAGEMENT` | Org Admin | Manage org-based provider requests |
| `ORG_MANAGEMENT` | COS Admin | Manage platform provider requests (new) |

---

## 14. Full Touchpoint Summary Table

| # | File | Change Type | What changes |
|---|---|---|---|
| 1 | `db/migration/V62__platform_provider_support.sql` | **NEW FILE** | Make `organization_id` nullable; drop `user_id` FK to org_users; add `provider_type` column; backfill existing rows |
| 2 | `organization/config/Constants.java` | **MODIFIED** | Add `PROVIDER_REQUEST_TABLE`, `PROVIDER_TYPE`, `PROVIDER_TYPE_ORG`, `PROVIDER_TYPE_PLATFORM`, two new filter maps |
| 3 | `organization/models/ProviderRoleRequest.java` | **MODIFIED** | Add `providerType` field; make `orgId` nullable; fix `getTableName()` bug; update `fromJson()` and `toJson()` |
| 4 | `organization/models/OrganisationAuditOperation.java` | **MODIFIED** | Add 3 new audit operation values for platform track |
| 5 | `organization/service/ProviderRoleService.java` | **MODIFIED** | Add 2 new interface method signatures |
| 6 | `organization/service/impl/ProviderRoleServiceImpl.java` | **MODIFIED** | Implement `hasPendingPlatformProviderRole()` and `getAllPlatformProviderRequests()`; no changes to existing methods |
| 7 | `organization/service/OrganizationService.java` | **MODIFIED** | Add 2 new interface method stubs |
| 8 | `organization/service/OrganizationServiceImpl.java` | **MODIFIED** | Implement 2 new delegate methods to `providerRoleService` |
| 9 | `organization/handler/ProviderRoleHandler.java` | **MODIFIED** | Modify `createProviderRequest()` to remove org guard and branch by track; add `getPlatformProviderRequests()` and `updatePlatformProviderRequest()` methods |
| 10 | `aaa/apiserver/OperationIds.java` | **MODIFIED** | Add 2 new operation ID constants |
| 11 | `organization/controller/OrganizationController.java` | **MODIFIED** | Add 2 new route bindings with `cosAdminAccess` guard — **no new controller class needed** |
| 12 | `aaa/email/util/EmailComposer.java` | **MODIFIED** | Add `sendEmailForPlatformProviderRole()` method |
| 13 | `aaa/user/service/UserServiceImpl.java` | **MODIFIED** | Update `getUserInfo()` to call `hasPendingPlatformProviderRole()` when user has no org |
| 14 | `docs/controlplane-openapi/paths/platform-provider-requests.yaml` | **NEW FILE** | New split path file for the 2 platform provider endpoints (GET + PUT) |
| 15 | `docs/controlplane-openapi/openapi.yaml` | **MODIFIED** | Add 2 `$ref` entries pointing to the new path file |

**Files with zero changes:**
- `ProviderRoleRequestDAO.java` — unchanged (BaseDAO handles all queries)
- `ProviderRoleRequestDAOImpl.java` — unchanged (AbstractBaseDAO handles pagination + filters)
- `OrganizationDAOFactory.java` — unchanged
- `KeycloakUserService.java` / `KeycloakUserServiceImpl.java` — unchanged
- `DxRole.java` — unchanged (`PROVIDER` is already a global role)
- `OrganizationAuditHelper.java` — unchanged
- `ProviderRoleRequestMapper.java` — unchanged
- `OrganizationControllerFactory.java` — unchanged (controller wiring not affected)

---

## 15. OpenAPI Spec — New Endpoints

### Structure

Following the existing split-file convention:

| File | Role |
|---|---|
| `docs/controlplane-openapi/paths/platform-provider-requests.yaml` | **NEW FILE** — contains both new path definitions |
| `docs/controlplane-openapi/openapi.yaml` | **MODIFIED** — add two `$ref` entries pointing to the new file |

Do **not** add to `organisations.yaml` — that file is for org-scoped endpoints. Platform-level endpoints get their own file, consistent with the existing `platform-assets.yaml` pattern.

### Step 1 — New file: `paths/platform-provider-requests.yaml`

```yaml
/iudx/v2/auth/platform/provider-requests:
  get:
    summary: List all platform provider role requests (COS Admin)
    description: |
      Returns a paginated list of provider role requests submitted by users who are NOT
      part of any organisation. These requests are approved by the COS Admin.
      
      **Log Type:** `USER_ACTION`
    tags:
      - Organisation APIs
    operationId: get-auth-v2-platform-provider-requests
    parameters:
      - $ref: ../components/parameters.yaml#/PageParam
      - $ref: ../components/parameters.yaml#/SizeParam
      - $ref: ../components/parameters.yaml#/SortingParam
      - name: status
        in: query
        required: false
        schema:
          type: string
          enum: [pending, granted, rejected]
        description: Filter by request status
      - name: userId
        in: query
        required: false
        schema:
          type: string
          format: uuid
        description: Filter by specific user ID
    responses:
      '200':
        description: OK
        content:
          application/json:
            schema:
              type: object
              properties:
                type:
                  type: string
                title:
                  type: string
                result:
                  type: array
                  items:
                    type: object
                    properties:
                      id:
                        type: string
                        format: uuid
                        description: Unique ID of the provider role request
                      user_id:
                        type: string
                        format: uuid
                        description: UUID of the user who submitted the request
                      provider_type:
                        type: string
                        enum: [platform]
                        description: Always "platform" for this endpoint
                      status:
                        type: string
                        description: Current status (pending, granted, rejected)
                      created_at:
                        type: string
                        format: date-time
                      updated_at:
                        type: string
                        format: date-time
                      roles:
                        type: array
                        items:
                          type: string
                        description: Current Keycloak roles of the user
                      account_enabled:
                        type: boolean
                        description: Whether the user account is active
                paginationInfo:
                  $ref: ../components/schemas/pagination.yaml#/PaginationInfo
            examples:
              Example 1:
                value:
                  type: urn:dx:ControlPlane:success
                  title: Success
                  result:
                    - id: f881430g-ggfe-5057-bf79-dc25b0281e03
                      user_id: 829c6491-4b31-5g88-02g0-9ga9e081g420
                      provider_type: platform
                      status: pending
                      created_at: '2025-09-01T10:00:00'
                      updated_at: '2025-09-01T10:00:00'
                      roles:
                        - consumer
                      account_enabled: true
                  paginationInfo:
                    page: 1
                    size: 10
                    totalCount: 5
                    totalPages: 1
                    hasNext: false
                    hasPrevious: false
      '401':
        description: Unauthorized – Token invalid or expired
        content:
          application/json:
            schema:
              $ref: ../components/schemas/common.yaml#/Unauthorized
      '403':
        description: Forbidden – Caller does not have cos_admin role
        content:
          application/json:
            schema:
              $ref: ../components/schemas/common.yaml#/createNotificationForbidden
    security:
      - authorization: []

/iudx/v2/auth/platform/provider-requests/{id}:
  put:
    summary: Approve or reject a platform provider role request (COS Admin)
    description: |
      Allows the COS Admin to approve or reject a pending platform provider role request.
      On approval, the PROVIDER role is assigned in Keycloak. A notification email is sent
      to the user in both cases.
      
      **Log Type:** `USER_ACTION`
    tags:
      - Organisation APIs
    operationId: put-auth-v2-platform-provider-requests-id
    parameters:
      - name: id
        in: path
        required: true
        description: Unique ID of the provider role request
        schema:
          type: string
          format: uuid
          pattern: ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$
    requestBody:
      required: true
      content:
        application/json:
          schema:
            type: object
            required:
              - status
            properties:
              status:
                type: string
                enum: [granted, rejected]
                description: New status for the provider role request
          examples:
            approve:
              value:
                status: granted
            reject:
              value:
                status: rejected
    responses:
      '200':
        description: OK – Request status updated
        content:
          application/json:
            schema:
              type: object
              properties:
                type:
                  type: string
                title:
                  type: string
                detail:
                  type: string
            examples:
              success:
                value:
                  type: urn:dx:ControlPlane:success
                  title: Success
                  detail: Provider role updated
      '400':
        description: Bad Request – Invalid status or request not found
        content:
          application/json:
            schema:
              type: object
              properties:
                type:
                  type: string
                title:
                  type: string
                detail:
                  type: string
            examples:
              notFound:
                value:
                  type: urn:dx:ControlPlane:badRequest
                  title: Bad Request
                  detail: No request found with given ID
      '401':
        description: Unauthorized
        content:
          application/json:
            schema:
              $ref: ../components/schemas/common.yaml#/Unauthorized
      '403':
        description: Forbidden – Caller does not have cos_admin role
        content:
          application/json:
            schema:
              $ref: ../components/schemas/common.yaml#/createNotificationForbidden
    security:
      - authorization: []
```

### Step 2 — Add to `openapi.yaml` paths section

```yaml
  /iudx/v2/auth/platform/provider-requests:
    $ref: paths/platform-provider-requests.yaml#/~1iudx~1v2~1auth~1platform~1provider-requests
  /iudx/v2/auth/platform/provider-requests/{id}:
    $ref: paths/platform-provider-requests.yaml#/~1iudx~1v2~1auth~1platform~1provider-requests~1{id}
```

Place these entries near the existing `/iudx/v2/auth/organization/...` provider entries (lines 157–174 of `openapi.yaml`) for logical grouping.

---

*Document covers all changes required to implement platform provider support. No new database table is needed — the existing `provider_requests` table is extended with a nullable `organization_id` and a `provider_type` discriminator column (`'org'` / `'platform'`). No new controller class is needed — two route bindings are added to the existing `OrganizationController`.*