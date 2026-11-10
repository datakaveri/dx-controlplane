---
title: ACL controllers (Policy / AccessRequest / AccessReport) — auth-v2 migration plan
status: draft
related:
  - controlplane-scope-audit.md §4.13, §4.14, §4.15
  - organization-auth-v2-migration-plan.md (template)
  - user-credit-auth-v2-migration-plan.md (template)
---

## 1. Goal

Migrate the three ACL controllers (`PolicyController`,
`AccessRequestController`, `AccessReportController` — 14 ops total) from
legacy `AuthorizationHandler.forRoles(...)` to v2 scope-based gating.

This is a **gating-only migration** (Option A, decided 2026-04-27).
Handler bodies, `UserAccessHandler`, and all
`RoutingContextHelper.fromPrincipal(ctx)` /
`RoutingContextHelper.getDxUser(ctx)` reads are **kept as-is**. v1 ACL
controllers never honored delegation in business logic (no
`DelegatorResolver` / no `?delegatorId=` query param), so this PR
preserves that behavior. Full delegation-aware semantics (delegator
becomes the acting identity in business logic) is queued as a Phase 2
follow-up (§9).

## 2. Non-goals

- **No dx-common changes.** Existing 13 scopes are sufficient.
- **No business-logic changes.** Identity reads keep using
  `ctx.user().subject()` / `RoutingContextHelper.fromPrincipal(ctx)` /
  `RoutingContextHelper.getDxUser(ctx)`. Under v2 delegation these
  return the *delegate's* identity (since the JWT is the delegate's),
  same as the v1 direct-user behavior.
- **`UserAccessHandler` stays unchanged.** It still upserts the
  JWT-presenting user into `user_table` after auth; under delegation
  that records the delegate (acceptable — local table tracks who
  actually used the system).
- **No `?delegatorId=` query param removal.** ACL never used it.
- **No KYC checks added.** None of the 14 ops have v1 KYC; audit
  doesn't recommend adding any.
- **No new tests.** Manual smoke list per §7 covers admission cases.

## 3. Endpoint inventory — gating

14 operations across 3 controllers.

### 3.1 `PolicyController` (4 ops)

| Operation ID         | v1 gate                                                | v2 scope                                                                          |
|----------------------|--------------------------------------------------------|-----------------------------------------------------------------------------------|
| `CREATE_POLICY_API`  | `forRoles(PROVIDER, ORG_ADMIN, DELEGATE)`              | `forScopesWithContext(self(OWN_ASSET_MANAGEMENT), org(ORG_ASSET_MANAGEMENT))`     |
| `GET_POLICY_API`     | `forRoles(CONSUMER, PROVIDER, DELEGATE)`               | `forScopes(DATA_ACCESS, OWN_ASSET_MANAGEMENT)`                                    |
| `DELETE_POLICY_API`  | `forRoles(PROVIDER, ORG_ADMIN, DELEGATE)`              | `forScopesWithContext(self(OWN_ASSET_MANAGEMENT), org(ORG_ASSET_MANAGEMENT))`     |
| `VERIFY_API`         | `forRoles(PROVIDER, ORG_ADMIN, CONSUMER)`              | `forScopes(DATA_ACCESS, OWN_ASSET_MANAGEMENT, ORG_ASSET_MANAGEMENT)`              |

### 3.2 `AccessRequestController` (8 ops)

| Operation ID                                  | v1 gate                                | v2 scope                                                                          |
|-----------------------------------------------|----------------------------------------|-----------------------------------------------------------------------------------|
| `CREATE_ACCESS_REQUEST_API`                   | none — service-level check only        | `forScopes(DATA_ACCESS)` *(see §5.1)*                                             |
| `GET_ACCESS_REQUEST_CONSUMER_API`             | none                                   | `forScopes(DATA_ACCESS)` *(see §5.1)*                                             |
| `WITHDRAW_ACCESS_REQUEST_API_FOR_CONSUMER`    | `forRoles(CONSUMER)`                   | `forScopes(DATA_ACCESS)`                                                          |
| `GET_ACCESS_REQUEST_FOR_ORG_ADMIN_API`        | `forRoles(ORG_ADMIN)`                  | `forScopes(ORG_ASSET_MANAGEMENT)`                                                 |
| `GET_ACCESS_REQUEST_FOR_COS_ADMIN_API`        | `forRoles(COS_ADMIN)`                  | `forScopes(ASSET_MANAGEMENT)`                                                     |
| `GET_ACCESS_REQUEST_PROVIDER_API`             | `forRoles(PROVIDER, ORG_ADMIN)`        | `forScopesWithContext(self(OWN_ASSET_MANAGEMENT), org(ORG_ASSET_MANAGEMENT))`     |
| `UPDATE_ACCESS_REQUEST_API`                   | `forRoles(PROVIDER, ORG_ADMIN)`        | `forScopesWithContext(self(OWN_ASSET_MANAGEMENT), org(ORG_ASSET_MANAGEMENT))`     |
| `CHECK_ACCESS_REQUEST_API`                    | none                                   | `forScopes(DATA_ACCESS)` *(see §5.1)*                                             |

`UPDATE_ACCESS_REQUEST_API` keeps its existing `userAccessHandler`
chained between `authenticationV2` and the scope handler (today it
runs *between* the handler and the v1 role gate — we keep the same
relative position, just swap the gate).

### 3.3 `AccessReportController` (2 ops)

| Operation ID                                       | v1 gate                | v2 scope                          |
|----------------------------------------------------|------------------------|-----------------------------------|
| `GET_ACCESS_REQUEST_REPORT_API`                    | `forRoles(PROVIDER)`   | `forScopes(OWN_ASSET_MANAGEMENT)` |
| `GET_ACCESS_REQUEST_REPORT_FOR_ORG_ADMIN_API`      | `forRoles(ORG_ADMIN)`  | `forScopes(ORG_ASSET_MANAGEMENT)` |

### Scope rationale

- `DATA_ACCESS` — held by every authenticated user (composite
  `consumer` role on every JWT). Used for consumer self-service
  and for filling the gap on the three v1-ungated ops (§5.1).
- `OWN_ASSET_MANAGEMENT` — held by `PROVIDER`. 1:1 with
  `forRoles(PROVIDER)`.
- `ORG_ASSET_MANAGEMENT` — held by `ORG_ADMIN` (org-scoped). 1:1
  with `forRoles(ORG_ADMIN)`.
- `ASSET_MANAGEMENT` — held by `COS_ADMIN`. 1:1 with
  `forRoles(COS_ADMIN)`.
- `forScopesWithContext(self(OWN_ASSET_MANAGEMENT), org(ORG_ASSET_MANAGEMENT))`
  — admits PROVIDER via self-scope (no org pinning) and ORG_ADMIN via
  org-scoped match. Same admission set as v1 `forRoles(PROVIDER, ORG_ADMIN)`.

## 4. Handler body changes — none

All `RoutingContextHelper.fromPrincipal(ctx)`,
`RoutingContextHelper.getDxUser(ctx)`, and `ctx.user().subject()` reads
in the three controllers stay **exactly as today**. `UserAccessHandler`
is unchanged.

This means under v2 delegation, every ACL business-logic identity
read continues to surface the **delegate's** sub/org/email (the JWT
presenter), not the delegator's. v1 had the same property (no
delegator awareness at handler level), so this preserves v1
semantics. Phase 2 (§9) will switch these to `DxPrincipal`-based
reads if/when delegation behavior for ACL is product-defined.

`AccessValidator.validate(...)` is **not** present in any ACL
controller or handler — verified. No import drops needed in the
ACL module beyond v1 `AuthorizationHandler` / `DxRole`.

## 5. Open decisions

### 5.1 Three v1-ungated ops — `CREATE_ACCESS_REQUEST_API`, `GET_ACCESS_REQUEST_CONSUMER_API`, `CHECK_ACCESS_REQUEST_API`

Audit §6 flags all three. **Decision: gate all three with
`forScopes(DATA_ACCESS)`.** Same reasoning as the
`post-auth-v2-compute-role-request` decision in the user-credit plan
and the `OP_LIST_ORGANISATIONS` decision in the organization plan: every
authenticated user holds `DATA_ACCESS` via composite-stacked
`consumer` role, so this is functionally equivalent to "any
authenticated user" while being explicit. Tightens admission from
"anyone (including unauthenticated)" to "any authenticated user".

If any of these three need to remain reachable without authentication,
flag before merge — easy to drop the scope handler from one or more
routes.

### 5.2 `forScopesWithContext` rule order — self before org

For `CREATE_POLICY_API`, `DELETE_POLICY_API`,
`GET_ACCESS_REQUEST_PROVIDER_API`, `UPDATE_ACCESS_REQUEST_API`, the
order is `(self(OWN_ASSET_MANAGEMENT), org(ORG_ASSET_MANAGEMENT))` —
i.e., self-tier first. This matches the audit doc's pairing and the
intent that a PROVIDER (who holds `OWN_ASSET_MANAGEMENT` directly)
is admitted via the self rule, while ORG_ADMIN falls through to the
org rule. The `AuthorizationContext` published downstream will have
`level=SELF` for PROVIDER and `level=ORG` for ORG_ADMIN — handler
code currently doesn't branch on this, so no behavior depends on
the order. Following audit; no deviation.

### 5.3 No KYC checks

None of the 14 ACL ops have `KycVerification` in v1, and the audit
doc doesn't recommend adding any. Plan keeps it that way. If
product wants KYC on `CREATE_POLICY_API` or `UPDATE_ACCESS_REQUEST_API`,
flag before merge.

## 6. Files changed

In **dx-controlplane**:

1. `src/main/java/org/cdpg/dx/acl/policy/controller/PolicyController.java`
   - Drop v1 `AuthorizationHandler`, `DxRole` imports.
   - Add v2: `AuthenticationHandler`, `AuthorizationHandler` (v2),
     `Scopes`, `ScopeRule`.
   - Constructor takes `AuthenticationHandler` + `AuthorizationHandler`.
   - Pre-resolve in `register(...)`:
     `selfOrConsumerAccess` (`forScopes(DATA_ACCESS, OWN_ASSET_MANAGEMENT)`),
     `verifyAccess` (`forScopes(DATA_ACCESS, OWN_ASSET_MANAGEMENT, ORG_ASSET_MANAGEMENT)`),
     `policyAdminAccess` (`forScopesWithContext(self(OWN_ASSET_MANAGEMENT), org(ORG_ASSET_MANAGEMENT))`).
   - Each route gets `.handler(authenticationV2)` then a scope handler.
   - `UserAccessHandler` chain position unchanged.
   - Handler bodies unchanged.

2. `src/main/java/org/cdpg/dx/acl/policy/factory/PolicyFactory.java`
   - `createPolicyController(...)` takes `AuthHandlersV2`; passes
     `authV2.authentication()` / `authV2.authorization()` to the controller.

3. `src/main/java/org/cdpg/dx/acl/accessRequest/controller/AccessRequestController.java`
   - Same drop / add / constructor / pre-resolve pattern.
   - Pre-resolve in `register(...)`:
     `selfAccess` (`forScopes(DATA_ACCESS)`),
     `orgAdminAccess` (`forScopes(ORG_ASSET_MANAGEMENT)`),
     `cosAdminAccess` (`forScopes(ASSET_MANAGEMENT)`),
     `providerAdminAccess` (`forScopesWithContext(self(OWN_ASSET_MANAGEMENT), org(ORG_ASSET_MANAGEMENT))`).
   - Add `selfAccess` (DATA_ACCESS) gating to the three v1-ungated
     ops per §5.1.
   - `userAccessHandler` chain position unchanged.
   - Handler bodies unchanged.

4. `src/main/java/org/cdpg/dx/acl/accessRequest/factory/AccessRequestFactory.java`
   - `createAccessRequestController(...)` takes `AuthHandlersV2`;
     passes auth pair to controller.

5. `src/main/java/org/cdpg/dx/acl/accessReport/controller/AccessReportController.java`
   - Same pattern. Constructor takes `AuthenticationHandler` +
     `AuthorizationHandler`. Pre-resolve `providerAccess` (OWN), `orgAdminAccess` (ORG).
   - Handler bodies unchanged.

6. `src/main/java/org/cdpg/dx/acl/accessReport/factory/AccessReportFactory.java`
   - `create(...)` takes `AuthHandlersV2`; passes auth pair to
     controller.

7. `src/main/java/org/cdpg/dx/acl/apiserver/ControllerFactory.java`
   - Build `AuthHandlersV2` once via
     `LocalAuthV2Factory.buildPair(vertx, config)`.
   - Pass it into all three sub-factories.

8. `docs/auth/controlplane-scope-audit.md`
   - §6 updated: strike out the three v1-ungated `AccessRequestController`
     rows (`CREATE_ACCESS_REQUEST_API`, `GET_ACCESS_REQUEST_CONSUMER_API`,
     `CHECK_ACCESS_REQUEST_API`) as resolved to `forScopes(DATA_ACCESS)`.

No `dx-common` changes.

> **Not changed in this PR:**
> - `ApdApiServerVerticle.getAuthV2Handler()` — already returns
>   `LocalAuthV2Factory.build(...)` for the diagnostic
>   `/auth/v2/whoami` route. The actual per-route auth wiring goes
>   through the new `AuthHandlersV2` bundle in the ControllerFactory.
> - All ACL handler bodies (zero changes).
> - `UserAccessHandler`.
> - OpenAPI specs (no `delegatorId` query param exists on any ACL
>   route — verified).

## 7. Build / smoke checks

- `mvn compile -DskipTests` clean (only the pre-existing `Xlint`
  warnings).
- Manual smoke after deploy. Direct-user paths:
  - `CREATE_POLICY_API` with PROVIDER → 200; ORG_ADMIN → 200; CONSUMER → 403.
  - `GET_POLICY_API` with CONSUMER → 200; PROVIDER → 200; without JWT → 401.
  - `DELETE_POLICY_API` with PROVIDER → 200; ORG_ADMIN → 200; CONSUMER → 403.
  - `VERIFY_API` with PROVIDER, ORG_ADMIN, CONSUMER → 200; without JWT → 401.
  - `CREATE_ACCESS_REQUEST_API` with CONSUMER → 200; without JWT → 401 (was open).
  - `GET_ACCESS_REQUEST_CONSUMER_API` with CONSUMER → 200; without JWT → 401 (was open).
  - `WITHDRAW_ACCESS_REQUEST_API_FOR_CONSUMER` with CONSUMER → 200.
  - `GET_ACCESS_REQUEST_FOR_ORG_ADMIN_API` with ORG_ADMIN → 200; PROVIDER → 403.
  - `GET_ACCESS_REQUEST_FOR_COS_ADMIN_API` with COS_ADMIN → 200; ORG_ADMIN → 403.
  - `GET_ACCESS_REQUEST_PROVIDER_API` with PROVIDER → 200; ORG_ADMIN → 200; CONSUMER → 403.
  - `UPDATE_ACCESS_REQUEST_API` with PROVIDER → 200; ORG_ADMIN → 200; CONSUMER → 403.
  - `CHECK_ACCESS_REQUEST_API` with CONSUMER → 200; without JWT → 401 (was open).
  - `GET_ACCESS_REQUEST_REPORT_API` (CSV stream) with PROVIDER → 200.
  - `GET_ACCESS_REQUEST_REPORT_FOR_ORG_ADMIN_API` (CSV) with ORG_ADMIN → 200.
- Delegation paths (`Authorization: Bearer <delegate-jwt>` +
  `X-Delegator-Id: <delegator-sub>`):
  - Delegate-of-PROVIDER hits `CREATE_POLICY_API` → admitted by
    framework via the delegator's `OWN_ASSET_MANAGEMENT` scope; the
    request's `consumer/owner` fields are taken from the **delegate's**
    JWT (per §4 — no DxPrincipal swap-in this PR). Verify behavior
    matches product expectation; if not, flag for Phase 2.
  - Delegate-of-ORG_ADMIN hits `GET_ACCESS_REQUEST_FOR_ORG_ADMIN_API`
    → admitted; org-scoped query uses `ctx.user().principal()`
    (delegate's org). Same caveat as above.

## 8. Risks

1. **Behavior change for the three v1-ungated ops** (§5.1): tightening
   from "open / no auth required" to "any authenticated user
   (`DATA_ACCESS`)". Anyone calling these pre-authentication breaks.
   Likelihood low — these are aaa-server endpoints behind the v2
   router, but worth flagging in the PR description.
2. **No existing unit tests** for `PolicyController`,
   `AccessRequestController`, or `AccessReportController`. Mitigate
   via manual smoke list and self-review.
3. **`forDelegationScopes(...)` removal**: ACL routes that admitted
   `DELEGATE` via v1 `forRoles(...)` now rely on v2 framework-side
   delegation. Per `SystemRoleScopeMap`, `OWN_ASSET_MANAGEMENT`,
   `ORG_ASSET_MANAGEMENT`, and `ASSET_MANAGEMENT` are held only by
   PROVIDER, ORG_ADMIN, and COS_ADMIN respectively — no widening of
   the admission set.
4. **Delegation-aware business logic is *intentionally* deferred.** A
   delegate-of-PROVIDER calling `CREATE_POLICY_API` will create a
   policy with the **delegate** as the owner (per current handler
   bodies reading `ctx.user().principal()`), not the delegator. v1
   had the same property. If product requires delegator-as-owner,
   that's Phase 2.

## 9. Out-of-scope follow-ups

- **Phase 2: full `DxPrincipal` migration in ACL handlers.** Switch
  `RoutingContextHelper.fromPrincipal(ctx)` /
  `ctx.user().subject()` reads to read from `DxPrincipal` so under
  delegation, the delegator is the acting identity (matches the
  organization-controller pattern). Audit-log `delegatorId`
  population can also land here.
- **Centralize `delegatorId` in `AuditLogHelper.createBaseAudit`**
  (also queued from the org migration) — once done, ACL audit logs
  pick it up automatically.
- **Migrate `KYCController`** (still v1 — flagged in audit §4.19).
- **Add unit tests** for ACL scope-gate enforcement.