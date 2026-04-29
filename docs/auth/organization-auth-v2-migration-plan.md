---
title: OrganizationController — auth-v2 migration plan
status: draft
related:
  - controlplane-scope-audit.md §4.5, §6
  - user-credit-auth-v2-migration-plan.md (template)
  - admin-auth-v2-migration-plan.md (template)
---

## 1. Goal

Migrate `OrganizationController` (org create/join requests, org core
CRUD, org users, provider-role requests — 24 ops) from legacy
`AuthorizationHandler.forRoles(...)` + `forDelegationScopes(...)` to v2
scope-based gating, **and** retire the v1 controlplane-side delegation
mechanism (`DelegatorResolver` + `?delegatorId=` query param) in favour
of the framework's `DxPrincipal` (driven by the `X-Delegator-Id`
header that v2's `DelegationResolver` already consumes).

Five ops currently lack a v1 role gate (3 KYC-only "create request"
ops and 2 open read ops) — see §5 for the audience decisions.

This is a **breaking client-side change for delegation**. Old contract:
caller's JWT is the delegator's, with `?delegatorId=<delegate-uuid>`
flagging that a delegate is the actual operator. New contract: caller's
JWT is the **delegate's**, with `X-Delegator-Id: <delegator-sub>`
header flagging who they are acting as.

**Server-side**, the v1 `?delegatorId=` query param is **silently
ignored** after this PR — handlers no longer read it. The OpenAPI spec
is **left advertising the query param** to avoid a synchronized
spec/client/server change in one release; cleanup is a follow-up
(§9). Clients that continue sending the query param without also
sending `X-Delegator-Id` will get a direct (non-delegated) response
— flag prominently in the PR description and release notes.

## 2. Non-goals

- **No dx-common changes.** Existing 13 scopes are sufficient and the
  v2 `DelegationResolver` is already wired in via
  `LocalAuthV2Factory.buildPair(...)`.
- **KYC checks stay.** `AuthorizationHandler.KycVerification(isKycRequired)`
  is a KYC-completion check, not an auth gate. The three routes that
  use it (`OP_CREATE_ORG_REQUEST`, `OP_CREATE_ORG_JOIN_REQUEST`,
  `OP_CREATE_PROVIDER_REQUEST`) keep that handler unchanged.
- **No business-logic changes inside handlers** beyond the identity
  read-source (raw JWT → `DxPrincipal`) and the removal of the v1
  delegate branch + ownership check. `created_by` / `updated_by`
  semantics are preserved (action attributed to the *delegator*, same
  as v1 via `DelegatorResolver.resolveActingUserId`).
- **No other controllers migrate in this PR.** `DelegatorResolver` is
  used only by the four org handlers (verified); after this PR it
  is unused. `OrgOwnershipValidator` is also used only by the org
  module and only inside v1-delegate branches; after this PR it is
  unused.
- **No file deletions in this PR.** `DelegatorResolver` and
  `OrgOwnershipValidator` are left in the tree unused — cleanup is a
  follow-up (§9). Likewise the OpenAPI spec retains the
  `?delegatorId=` query-param parameter; the server simply stops
  consuming it.
- **No `AuditLogHelper.createBaseAudit(ctx)` change.** That helper
  reads `userId` from the raw JWT (`ctx.user().principal().sub`),
  which under v2 delegation already returns the delegate's sub —
  matching the audit rule "audit `sub` is always the primary
  authenticating user, even under delegation". The migration only
  adds `delegatorId` population in the org-audit helper (see §5.4).

## 3. Endpoint inventory — gating

24 operations across one controller. Mapping below replaces both the
v1 `forRoles(...)` admission **and** the implicit
"DELEGATE+forDelegationScopes" branch — the latter is now framework-side
via `DelegationResolver` (see §5.4).

| # | Operation ID                            | v1 gate                                                                           | v2 scope                                                                       |
|---|-----------------------------------------|-----------------------------------------------------------------------------------|--------------------------------------------------------------------------------|
| 1 | `OP_GET_ORG_CREATE_REQUESTS`            | `forRoles(COS_ADMIN, DELEGATE)` + `forDelegationScopes(COS_ADMIN_ACCESS)`         | `forScopes(ORG_MANAGEMENT)`                                                    |
| 2 | `OP_GET_USER_ORG_CREATE_REQUESTS`       | `forRoles(CONSUMER, DELEGATE)`                                                    | `forScopes(DATA_ACCESS)`                                                       |
| 3 | `OP_DELETE_USER_ORG_CREATE_REQUEST`     | `forRoles(CONSUMER, DELEGATE)`                                                    | `forScopes(DATA_ACCESS)`                                                       |
| 4 | `OP_CREATE_ORG_REQUEST`                 | `KycVerification` only                                                            | `forScopes(DATA_ACCESS)` + KYC unchanged *(see §5.1)*                          |
| 5 | `OP_APPROVE_ORG_CREATE_REQUEST`         | `forRoles(COS_ADMIN, DELEGATE)` + `forDelegationScopes(COS_ADMIN_ACCESS)`         | `forScopes(ORG_MANAGEMENT)`                                                    |
| 6 | `OP_CREATE_ORG_JOIN_REQUEST`            | `KycVerification` only                                                            | `forScopes(DATA_ACCESS)` + KYC unchanged *(see §5.1)*                          |
| 7 | `OP_GET_ORG_JOIN_REQUESTS`              | `forRoles(ORG_ADMIN, DELEGATE)` + `forDelegationScopes(USER_MANAGEMENT, ORG_ADMIN_ACCESS)` | `forScopes(ORG_USER_MANAGEMENT)`                                          |
| 8 | `OP_GET_USER_ORG_JOIN_REQUESTS`         | `forRoles(CONSUMER, DELEGATE)`                                                    | `forScopes(DATA_ACCESS)`                                                       |
| 9 | `OP_WITHDRAW_USER_ORG_JOIN_REQUESTS`    | `forRoles(CONSUMER, DELEGATE)`                                                    | `forScopes(DATA_ACCESS)`                                                       |
| 10| `OP_DELETE_USER_ORG_JOIN_REQUEST`       | `forRoles(CONSUMER, DELEGATE)`                                                    | `forScopes(DATA_ACCESS)`                                                       |
| 11| `OP_APPROVE_ORG_JOIN_REQUEST`           | `forRoles(ORG_ADMIN, DELEGATE)` + `forDelegationScopes(USER_MANAGEMENT, ORG_ADMIN_ACCESS)` | `forScopes(ORG_USER_MANAGEMENT)`                                          |
| 12| `OP_LIST_ORGANISATIONS`                 | none                                                                              | `forScopes(DATA_ACCESS)` *(see §5.2)*                                          |
| 13| `OP_GET_ORGANISATION_BY_ID`             | none                                                                              | `forScopes(DATA_ACCESS)` *(see §5.2)*                                          |
| 14| `OP_UPDATE_ORGANISATION_BY_ID`          | `forRoles(COS_ADMIN, DELEGATE)` + `forDelegationScopes(USER_MANAGEMENT, COS_ADMIN_ACCESS)` | `forScopes(ORG_MANAGEMENT)`                                              |
| 15| `OP_DELETE_ORGANISATION_BY_ID`          | `forRoles(COS_ADMIN, ORG_ADMIN, DELEGATE)` + `forDelegationScopes(USER_MANAGEMENT, COS_ADMIN_ACCESS, ORG_ADMIN_ACCESS)` | `forScopesWithContext(platform(ORG_MANAGEMENT), org(ORG_USER_MANAGEMENT))` |
| 16| `OP_GET_ORG_USERS`                      | `forRoles(ORG_ADMIN, DELEGATE)` + `forDelegationScopes(USER_MANAGEMENT, ORG_ADMIN_ACCESS)` | `forScopes(ORG_USER_MANAGEMENT)`                                          |
| 17| `OP_GET_ORG_USER_INFO`                  | same as #16                                                                       | `forScopes(ORG_USER_MANAGEMENT)`                                               |
| 18| `OP_DELETE_ORG_USER`                    | same as #16                                                                       | `forScopes(ORG_USER_MANAGEMENT)`                                               |
| 19| `OP_UPDATE_ORG_USER_ROLE`               | same as #16                                                                       | `forScopes(ORG_USER_MANAGEMENT)`                                               |
| 20| `OP_CREATE_PROVIDER_REQUEST`            | `KycVerification` only (no role gate today, no KYC handler attached either)       | `forScopes(DATA_ACCESS)` + KYC *(see §5.1)*                                    |
| 21| `OP_GET_PROVIDER_REQUEST`               | `forRoles(ORG_ADMIN, DELEGATE)`                                                   | `forScopes(ORG_USER_MANAGEMENT)`                                               |
| 22| `OP_UPDATE_PROVIDER_REQUEST`            | `forRoles(ORG_ADMIN, DELEGATE)` + `forDelegationScopes(USER_MANAGEMENT, ORG_ADMIN_ACCESS)` | `forScopes(ORG_USER_MANAGEMENT)`                                          |
| 23| `OP_GET_USER_PROVIDER_REQUESTS`         | `forRoles(CONSUMER, DELEGATE)`                                                    | `forScopes(DATA_ACCESS)`                                                       |
| 24| `OP_DELETE_USER_PROVIDER_REQUEST`       | `forRoles(CONSUMER, DELEGATE)`                                                    | `forScopes(DATA_ACCESS)`                                                       |
| 25| `OP_CREATE_PROVIDER_ROLE`               | `forRoles(ORG_ADMIN, DELEGATE)` + `forDelegationScopes(USER_MANAGEMENT, ORG_ADMIN_ACCESS)` | `forScopes(ORG_USER_MANAGEMENT)`                                          |

> Note: `OP_CREATE_PROVIDER_REQUEST` (#20) is currently the only "create
> provider request" op without `KycVerification` attached in the handler
> chain. Audit §4.5 expected KYC; we add KYC alongside the new
> `DATA_ACCESS` scope gate to keep parity with the other create-request
> ops. Flag in §5.1.

### Scope rationale

- `DATA_ACCESS` — held by every authenticated user via the composite
  `consumer` role on every JWT.
- `ORG_USER_MANAGEMENT` — held by `ORG_ADMIN`. Replaces every
  `forRoles(ORG_ADMIN, DELEGATE)` admin gate.
- `ORG_MANAGEMENT` — held by `COS_ADMIN`. Replaces every
  `forRoles(COS_ADMIN, DELEGATE)` admin gate.
- `forScopesWithContext(platform(ORG_MANAGEMENT), org(ORG_USER_MANAGEMENT))`
  — matches v1 `forRoles(COS_ADMIN, ORG_ADMIN, DELEGATE)` for
  `OP_DELETE_ORGANISATION_BY_ID`. COS_ADMIN admitted via platform
  scope; ORG_ADMIN admitted via org-context scope (which the framework
  pins to `principal.getOrganisationId()`).

## 4. Handler body changes — switch to `DxPrincipal`

This is the heart of Option B. Every handler currently reads identity
from one of two v1 sources:

- `ctx.user()` / `user.subject()` / `user.principal().getString("organisation_id")`
  — JWT-only path, ignores delegation entirely.
- `DelegatorResolver.getDelegatorId(ctx)` / `resolveActingUserId(ctx)`
  / `resolveOrgId(ctx, userService, …)` — v1 query-param path,
  with handler-time DB lookup of the delegator.

After migration both paths collapse into a single read of `DxPrincipal`,
populated by the framework's `AuthenticationHandler` (regardless of
direct-user or delegation flow):

```java
DxPrincipal principal = ctx.get(AuthorizationHandler.PRINCIPAL_KEY);
UUID actingUserId = UUID.fromString(principal.getSub());          // delegator under delegation
String actingOrgId = principal.getOrganisationId();               // delegator's org under delegation
boolean isDelegated = principal.isDelegation();                   // for audit branching
String authenticatedSub = principal.getAuthenticatedSub();        // actual delegate (audit only)
```

Per-handler changes:

### 4.1 `OrganizationCommandHandler.java`
- L45–46 (`updateOrganisationById`): replace `ctx.user().principal()` /
  `user.subject()` reads with `DxPrincipal` reads.
- L81 (`deleteOrganisationById`): same. The org-context check from
  `forScopesWithContext` already publishes an `AuthorizationContext`
  to `ctx`, but the handler also needs to verify that the URL's
  `orgId` path param matches `principal.getOrganisationId()` for
  ORG_ADMIN admission (COS_ADMIN admission via the PLATFORM rule
  bypasses this check — see §5.3).
- Drop `AccessValidator`, `DxRole`, `DxScope` imports (commented
  blocks already neutralized).

### 4.2 `OrganizationCreateRequestHandler.java`
- L75–76 (`createOrganisationRequest`): `UUID userId =
  UUID.fromString(user.subject())` → `principal.getSub()`. The
  request is created on behalf of the **acting user** (delegator
  under delegation), preserving v1 semantics.
- L149–150 (`getAllOrganisationRequest`): drop unused `User user`
  read.
- L183–184 (`getUserOrganisationRequest`): replace
  `DelegatorResolver.resolveActingUserId(ctx)` with
  `principal.getSub()`. Behaviour identical for both direct-user
  and delegation cases.
- L232–233 (`deleteOrganizationCreateRequest`): same pattern as
  L183–184.
- L266–267 (`updateOrganisationRequest`): replace `user.principal()`
  with `principal`-derived reads (the handler uses `userJson` as a
  source for `approver_id` — that becomes `principal.getSub()`).
- Drop `DelegatorResolver` and v1 auth imports.

### 4.3 `OrganizationJoinRequestHandler.java`
- L82–83 (`joinOrganisationRequest`): `user.subject()` → `principal.getSub()`.
- L150–174 (`getJoinOrganisationRequests`): replace the
  `DelegatorResolver` + `OrgOwnershipValidator.validateOrgOwnership`
  combo with:
  - `principal.getOrganisationId().equals(orgIdParam)` cross-check.
  - If mismatch → `DxForbiddenException("Org id mismatch")`.
  - The "delegator is admin of this org" check in
    `validateOrgOwnership` is no longer needed: the v2 framework's
    `DelegationResolver` already validates that the delegator
    currently holds `ORG_USER_MANAGEMENT` (i.e. is an ORG_ADMIN),
    via the intersection in `DelegationResolver.buildPrincipal`
    (`dx-common/src/.../resolver/DelegationResolver.java:99-105`).
- L231–232, L265–266 (`getUserJoinOrganisationRequests`,
  `withdrawJoinRequest`): `DelegatorResolver.resolveActingUserId` →
  `principal.getSub()`.
- L317–318, L336–338 (`deleteUserJoinOrganisationRequests`,
  `approveJoinOrganisationRequests`): replace
  `DelegatorResolver.resolveOrgId(...)` with `principal.getOrganisationId()`
  + URL-param cross-check.
- L374–387 (`approveJoinOrganisationRequests` cont.): replace
  `DelegatorResolver.resolveActingUserId(ctx)` and the
  `userService.getUserInfoByID(delegatorId)` block with direct
  reads from `principal`. The DB lookup of the delegator's user info
  is no longer necessary — `principal.getSub()` and
  `principal.getOrganisationId()` are pre-resolved at auth time.
- Drop `DelegatorResolver`, `OrgOwnershipValidator` (constructor
  arg), v1 auth imports.

### 4.4 `OrganizationUserHandler.java`
- L63–64, L96–97 (`getOrganisationUsers`, `getOrganisationUserInfo`):
  replace `user.principal()` reads with `principal.getOrganisationId()`
  for the org-scope cross-check.
- L144 (`deleteOrganisationUserById`): same.
- L185, L189, L196 (`updateOrganisationUserRole`): replace the
  ternary `delegatorId != null ? delegatorId : UUID.fromString(ctx.user().subject())`
  with `principal.getSub()`.
- Drop `DelegatorResolver`, v1 auth imports.

### 4.5 `ProviderRoleHandler.java`
- L74–94 (`createProviderRequest`): replace
  `user.subject()` / `user.principal().getString("organisation_id")`
  with `principal.getSub()` / `principal.getOrganisationId()`.
- L117–144 (`getProviderRequest`): replace `DelegatorResolver.resolveOrgId(...)` +
  the `validateOrgOwnership` branch with `principal.getOrganisationId()` +
  URL-param cross-check (same pattern as §4.3).
- L181–187 (`updateProviderRequest`): same pattern.
- L270, L319–320 (`getProviderRoleRequest`,
  `deleteUserProviderRoleRequest`):
  `DelegatorResolver.resolveActingUserId(ctx)` →
  `principal.getSub()`.
- L362 (`createProviderRole`): replace `user.principal()` reads
  with `principal`-derived reads.
- Drop `DelegatorResolver`, `OrgOwnershipValidator` (constructor
  arg), v1 auth imports.

### 4.6 `OrganizationQueryHandler.java`
- No identity reads today. No changes needed.

### 4.7 Files left unused after this PR

Per the "no deletions in this PR" decision (§2), these stay in the
tree but become orphans:

- `src/main/java/org/cdpg/dx/common/util/DelegatorResolver.java` —
  only callers were the 4 org handlers; after this PR, zero callers.
- `src/main/java/org/cdpg/dx/aaa/delegation/OrgOwnershipValidator.java`
  — only callers were the v1 delegate branches in 2 org handlers;
  after this PR, zero callers (and `OrganizationControllerFactory`
  no longer constructs it).

Cleanup of both is queued in §9. Note in PR description so reviewers
know the dead-code state is intentional and short-lived.

`AccessValidator` itself stays — still used by other controllers
(asset, item, etc.).

> The dead `DelegatorStrategy` / `DelegateStrategy` /
> `NonDelegateStrategy` / `DelegatorStrategyFactory` classes in
> `src/main/java/org/cdpg/dx/common/util/resolver/` have **no callers
> anywhere** (verified — already unused before this PR). Listed in §9
> as a follow-up cleanup.

## 5. Open decisions

### 5.1 Three "create request" ops — KYC-only, no role gate today

`OP_CREATE_ORG_REQUEST`, `OP_CREATE_ORG_JOIN_REQUEST`,
`OP_CREATE_PROVIDER_REQUEST` (#20 currently lacks even the
`KycVerification` handler — see note in §3).

**Decision: gate all three with `forScopes(DATA_ACCESS)`.** Same
reasoning as `post-auth-v2-compute-role-request` (user-credit
migration §5.1): every authenticated user holds `DATA_ACCESS` via
composite-stacked `consumer` role. For `OP_CREATE_PROVIDER_REQUEST`
we **also add** the missing `KycVerification(isKycRequired)` handler
to match the audit's stated intent. If CONSUMER-without-KYC should
still be able to apply for the provider role, flag.

### 5.2 `OP_LIST_ORGANISATIONS` and `OP_GET_ORGANISATION_BY_ID` — currently open

Audit §6 flags both. **Decision: gate with `forScopes(DATA_ACCESS)`** —
tightens "open" to "any authenticated user". Flag if either should
stay public.

### 5.3 `OP_DELETE_ORGANISATION_BY_ID` — `forScopesWithContext` admission set

Audit §4.5 pairs `forScopesWithContext(platform(ORG_MANAGEMENT),
org(ORG_USER_MANAGEMENT))`. COS_ADMIN admitted via platform; ORG_ADMIN
admitted via org-context (framework pins to `principal.getOrganisationId()`).

The handler reads the `AuthorizationContext` published by the rule
to branch on platform-vs-org admission — but in practice the only
side-effect is that org-admins can only delete their *own* org. The
handler's URL-param cross-check below covers that explicitly:

```java
if (authCtx.level() == AuthLevel.ORG &&
    !principal.getOrganisationId().equals(orgIdParam)) {
  ctx.fail(new DxForbiddenException("Cannot delete a different organisation"));
  return;
}
```

### 5.4 Delegation handling — framework-side, v1 path retired

**v1 (today):** caller's JWT is the delegator's. The fact that "this
is actually a delegate acting on behalf of the delegator" is conveyed
by `?delegatorId=<delegate-uuid>` query param. Handlers explicitly call
`DelegatorResolver.resolveActingUserId(ctx)` / `resolveOrgId(...)` to
override the acting user and look up the delegator's org via DB.
`OrgOwnershipValidator` runs to confirm the delegator is the org-admin
of the requested org.

**v2 (after this PR):** caller's JWT is the **delegate's**. The header
`X-Delegator-Id: <delegator-sub>` flags delegation. Framework-side
(`dx-common`):

1. `AuthenticationHandler` dispatches to `DelegationResolver` when
   the header is present (`AuthenticationHandler.java:75-82`).
2. `DelegationResolver` (`DelegationResolver.java:39-115`):
   - Looks up active delegation grant from delegator → delegate in DB
     (one query, at auth time).
   - Loads delegator's current roles via `UserLookup` (one query).
   - Builds `DxPrincipal` whose `getSub()` / `getOrganisationId()`
     return the delegator's identity, and whose `directScopes` is the
     intersection of the delegation's stored scopes with the
     delegator's current role-derived scopes.
3. `AuthorizationHandler.forScopes(...)` admits the delegate **iff**
   the (capped) effective scopes contain the required scope — i.e.,
   iff the delegator currently holds it AND the grant covers it.
4. Handler reads `DxPrincipal` and gets the same identity it would
   for a direct call by the delegator.

Net effect: scope gating, ownership validation, and "did delegation
expire / was it revoked" are all handled framework-side. Handler
bodies are identical for direct-user and delegation calls — they just
read `principal.getSub()` / `getOrganisationId()`.

**Audit semantics — explicit decision:**

> Audit `userId` (the "sub" field) is **always the primary
> authenticating user's sub** — i.e., the JWT subject. Under
> delegation, that's the **delegate's** sub. When (and only when)
> the call is delegated, a separate `delegatorId` field carries the
> delegator's sub.

This differs from v1, where `DelegatorResolver.resolveActingUserId`
returned the delegator and that value flowed into audit. v2 splits
the two:

| Field           | Direct call                | Delegated call                           |
|-----------------|----------------------------|------------------------------------------|
| audit `userId`  | user's sub                 | **delegate's sub** (`getAuthenticatedSub`) |
| audit `delegatorId` | absent                 | delegator's sub (`principal.getSub()`)   |

Implementation:

- `AuditLogHelper.createBaseAudit(ctx)` already reads `userId` from
  `ctx.user().principal().getString("sub")`, which is the **JWT
  subject** — the delegate under delegation. **No change** to
  `AuditLogHelper`.
- `OrganizationAuditHelper.buildOrganisationAudit(ctx, body, op)` is
  extended to read the v2 `DxPrincipal` from
  `ctx.get(AuthorizationHandler.PRINCIPAL_KEY)` and chain
  `.withDelegatorId(UUID.fromString(principal.getSub()))` (and
  optionally `.withDelegatorRole(...)` derived from
  `principal.getAuditRoles()`) when `principal.isDelegation()`.
- The **business-logic** acting-user reads (e.g. `created_by`,
  "show me MY join requests" filters) still use `principal.getSub()`
  — under delegation that returns the **delegator**, matching v1
  business semantics. Only the **audit** path changes.

## 6. Files changed

In **dx-controlplane**:

1. `src/main/java/org/cdpg/dx/aaa/organization/controller/OrganizationController.java`
   - Drop v1 `AuthorizationHandler`, `DxRole`, `DxScope` imports.
   - Add v2: `AuthenticationHandler`, `AuthorizationHandler` (v2),
     `Scopes`, `ScopeRule`.
   - Constructor takes `AuthenticationHandler` + `AuthorizationHandler`.
   - Pre-resolve four reused handlers: `selfAccess` (DATA_ACCESS),
     `orgAdminAccess` (ORG_USER_MANAGEMENT), `cosAdminAccess`
     (ORG_MANAGEMENT), `orgDeleteAccess` (forScopesWithContext for
     `OP_DELETE_ORGANISATION_BY_ID`).
   - Each route gets `.handler(authenticationV2)` then a scope handler.
   - Re-import `KycVerification` static import from the v1
     `AuthorizationHandler` (still the canonical KYC check).

2. `src/main/java/org/cdpg/dx/aaa/organization/factory/OrganizationControllerFactory.java`
   - `create()` takes `AuthHandlersV2`; passes `authV2.authentication()`
     / `authV2.authorization()` to the controller.
   - Drop `OrgOwnershipValidator` parameter (no longer needed).
   - Drop `OrgOwnershipValidator` from `ProviderRoleHandler` /
     `OrganizationJoinRequestHandler` constructor calls.

3. `src/main/java/org/cdpg/dx/aaa/organization/handler/OrganizationCommandHandler.java`
   - Switch identity reads to `DxPrincipal` (§4.1).
   - Drop v1 auth imports.

4. `src/main/java/org/cdpg/dx/aaa/organization/handler/OrganizationCreateRequestHandler.java`
   - Switch identity reads to `DxPrincipal` (§4.2).
   - Drop `DelegatorResolver` + v1 auth imports.

5. `src/main/java/org/cdpg/dx/aaa/organization/handler/OrganizationJoinRequestHandler.java`
   - Switch identity reads to `DxPrincipal` (§4.3).
   - Drop the `validateOrgOwnership` branch; replace with URL-param
     cross-check.
   - Drop `OrgOwnershipValidator` constructor arg.
   - Drop `DelegatorResolver` + v1 auth imports.

6. `src/main/java/org/cdpg/dx/aaa/organization/handler/OrganizationUserHandler.java`
   - Switch identity reads to `DxPrincipal` (§4.4).
   - Drop `DelegatorResolver` + v1 auth imports.

7. `src/main/java/org/cdpg/dx/aaa/organization/handler/ProviderRoleHandler.java`
   - Switch identity reads to `DxPrincipal` (§4.5).
   - Drop the `validateOrgOwnership` branch.
   - Drop `OrgOwnershipValidator` constructor arg.
   - Drop `DelegatorResolver` + v1 auth imports.

8. `src/main/java/org/cdpg/dx/aaa/organization/audit/OrganizationAuditHelper.java`
   - Extend `buildOrganisationAudit(ctx, body, op)` to read the v2
     `DxPrincipal` and chain `.withDelegatorId(...)` (+ optionally
     `.withDelegatorRole(...)`) when `principal.isDelegation()`. See
     §5.4 for the exact rule.

9. `src/main/java/org/cdpg/dx/aaa/apiserver/ControllerFactory.java`
   - Pass `authV2` into `OrganizationControllerFactory.create(...)` (L129).
   - Stop passing `OrgOwnershipValidator` to the org factory (the
     factory's signature drops that parameter).
   - The construction of `orgOwnershipValidator` itself stays in
     this file for now (no deletions per §2); it just isn't passed
     downstream after this PR.

10. `docs/auth/controlplane-scope-audit.md`
    - §6 updated: strike out the `OrganizationController` rows
      (`OP_LIST_ORGANISATIONS`, `OP_GET_ORGANISATION_BY_ID`) as
      resolved to `forScopes(DATA_ACCESS)`. Note the three
      create-request ops as resolved (`forScopes(DATA_ACCESS)` + KYC).

**Not changed in this PR (deferred to follow-up):**

- `docs/controlplane-openapi/**` — the `delegatorId` query param and
  `DelegatorIdParam` component remain. Server stops consuming the
  param; OpenAPI cleanup is queued in §9.
- `src/main/java/org/cdpg/dx/common/util/DelegatorResolver.java` —
  left in tree, zero callers.
- `src/main/java/org/cdpg/dx/aaa/delegation/OrgOwnershipValidator.java`
  — left in tree, zero callers.

No `dx-common` changes.

## 7. Build / smoke checks

- `mvn compile -DskipTests` clean (only the pre-existing `Xlint` warnings).
- Manual smoke after deploy. Direct-user paths:
  - `OP_LIST_ORGANISATIONS` with any token → 200; without JWT → 401.
  - `OP_GET_ORGANISATION_BY_ID` with any token → 200; without JWT → 401.
  - `OP_CREATE_ORG_REQUEST` with CONSUMER (KYC OK) → 200.
  - `OP_GET_USER_ORG_CREATE_REQUESTS` with CONSUMER → 200.
  - `OP_GET_ORG_CREATE_REQUESTS` with COS_ADMIN → 200; CONSUMER → 403.
  - `OP_APPROVE_ORG_CREATE_REQUEST` with COS_ADMIN → 200.
  - `OP_CREATE_ORG_JOIN_REQUEST` with CONSUMER (KYC OK) → 200.
  - `OP_GET_ORG_JOIN_REQUESTS` with ORG_ADMIN → 200; CONSUMER → 403.
  - `OP_APPROVE_ORG_JOIN_REQUEST` with ORG_ADMIN → 200.
  - `OP_UPDATE_ORGANISATION_BY_ID` with COS_ADMIN → 200; ORG_ADMIN → 403.
  - `OP_DELETE_ORGANISATION_BY_ID` with COS_ADMIN → 200; ORG_ADMIN
    (matching org context) → 200; ORG_ADMIN (different org) → 403.
  - `OP_GET_ORG_USERS` / `OP_DELETE_ORG_USER` / `OP_UPDATE_ORG_USER_ROLE`
    with ORG_ADMIN → 200.
  - `OP_CREATE_PROVIDER_REQUEST` with CONSUMER (KYC OK) → 200; without
    KYC → blocked by KYC handler (new behaviour vs today, see §5.1).
  - `OP_CREATE_PROVIDER_ROLE` / `OP_UPDATE_PROVIDER_REQUEST` with
    ORG_ADMIN → 200.
- Delegation paths (delegate's JWT + `X-Delegator-Id: <delegator-sub>`):
  - Delegate-of-COS_ADMIN hits `OP_GET_ORG_CREATE_REQUESTS` → 200.
  - Delegate-of-ORG_ADMIN hits `OP_GET_ORG_JOIN_REQUESTS` (matching
    org context) → 200; (different org) → 403.
  - Delegate-of-ORG_ADMIN hits `OP_APPROVE_ORG_JOIN_REQUEST` → 200;
    audit log records both delegator (`principal.getSub()`) and
    delegate (`principal.getAuthenticatedSub()`).
  - Delegate-with-revoked-grant hits any admin op → 403 with
    "No active delegation".
  - Delegate-of-ex-ORG_ADMIN (delegator lost the role) hits
    `OP_GET_ORG_JOIN_REQUESTS` → 403 (capped by intersection in
    `DelegationResolver.buildPrincipal`).
  - Caller still sending the legacy `?delegatorId=` query param →
    silently ignored; request behaves as a direct call (the param
    was removed from the OpenAPI spec, but old clients will still
    send it harmlessly until they upgrade).

## 8. Risks

1. **Breaking client behaviour change for delegation.** Old contract:
   `JWT=delegator + ?delegatorId=delegate-uuid`. New contract:
   `JWT=delegate + X-Delegator-Id: delegator-sub`. Clients calling
   any of the 9 delegation-aware ops (org create/join/provider
   request approval flows) must update their auth flow:
     - Acquire JWT as the delegate (different user identity at the
       Keycloak token endpoint).
     - Send `X-Delegator-Id` header instead of `?delegatorId=` query.
   The OpenAPI spec **still advertises** the `?delegatorId=` query
   param (per §2) — but the server now ignores it. A client that
   sends only the query param will get a direct (non-delegated)
   response and may be silently broken until they migrate to the
   header. Flag this prominently in the PR description and release
   notes; spec cleanup is a follow-up.
2. **No existing unit tests** for `OrganizationController` or any
   of its handlers. Mitigate via manual smoke list (§7) and a
   careful self-review.
3. **Behaviour change for `OP_LIST_ORGANISATIONS` /
   `OP_GET_ORGANISATION_BY_ID`** (§5.2): tightening from "open" to
   "any authenticated user".
4. **Behaviour change for `OP_CREATE_PROVIDER_REQUEST`** (§5.1):
   adds the `KycVerification` handler. CONSUMER-without-KYC callers
   blocked.
5. **`OrgOwnershipValidator` removal** drops the explicit
   delegator-is-org-admin DB check from the handler path. The v2
   framework's intersection check in `DelegationResolver.buildPrincipal`
   covers this (delegator must currently hold the required scope), but
   the *DB-level* "is admin of *this specific* org" cross-check is
   subtler — it now lives in the handler-side
   `principal.getOrganisationId().equals(urlOrgId)` comparison. Verify
   each affected handler does this comparison before mutating any
   org-scoped data.
6. **`forDelegationScopes(...)` removal across 14 admin/self ops**
   silently widens admission iff there exists a token holder who has
   `ORG_USER_MANAGEMENT` / `ORG_MANAGEMENT` scope but is not
   ORG_ADMIN / COS_ADMIN. Per `SystemRoleScopeMap`, set is empty.

## 9. Out-of-scope follow-ups

- **OpenAPI cleanup PR (immediately after this lands)**: remove the
  `delegatorId` query param from all 8 affected routes, remove the
  `DelegatorIdParam` component, add an `XDelegatorIdHeader` header
  parameter component and reference it on the same routes. Files:
  `docs/controlplane-openapi/components/parameters.yaml`,
  `docs/controlplane-openapi/paths/organisations.yaml`,
  `docs/controlplane-openapi/paths/roles.yaml`,
  and the bundled `docs/openapi.yaml` / `docs/central-openapi.yaml`
  if those are hand-edited.
- **Dead-code cleanup PR**: delete
  `src/main/java/org/cdpg/dx/common/util/DelegatorResolver.java`,
  `src/main/java/org/cdpg/dx/aaa/delegation/OrgOwnershipValidator.java`,
  the construction of `orgOwnershipValidator` in
  `ControllerFactory.java`, and the already-orphaned
  `src/main/java/org/cdpg/dx/common/util/resolver/{DelegatorStrategy,
  DelegateStrategy, NonDelegateStrategy, DelegatorStrategyFactory}.java`.
- Migrate `OrganizationReportController` (sister controller).
- Migrate `KYCController` (still uses v1 auth).
- Centralize the `delegatorId` audit-population in
  `AuditLogHelper.createBaseAudit(ctx)` itself (currently each
  domain audit-helper has to chain it). Once centralized, every
  controller's audit path picks it up automatically.
- Add unit tests covering scope-gate enforcement and delegation
  on `OrganizationController`.
- Once `DelegatorResolver` is gone from this repo, audit other
  controlplane modules for any future re-introduction (lint rule?).