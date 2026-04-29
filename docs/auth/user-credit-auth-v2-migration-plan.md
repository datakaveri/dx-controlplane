---
title: User + Credit controllers — auth-v2 migration plan
status: draft
related:
  - controlplane-scope-audit.md §4.3, §4.18
  - admin-auth-v2-migration-plan.md (template)
  - activity-auth-v2-migration-plan.md (template)
---

## 1. Goal

Migrate `UserController` (self-service profile + custom-role admin ops)
and `CreditController` (credit requests, credit balance, compute-role
requests) from legacy `AuthorizationHandler.forRoles(...)` to v2
scope-based gating. Remove the duplicated v1 `AccessValidator.validate(...)`
checks inside the three credit handlers.

End-user behaviour is unchanged for ops that already have a v1 gate.
One op (`post-auth-v2-compute-role-request`) currently has no role
gate — see §5.1 for the audience decision.

## 2. Non-goals

- **No dx-common changes.** Existing 13 scopes are sufficient.
- **KYC checks stay.** `AuthorizationHandler.KycVerification(isKycRequired)`
  is a KYC-completion check, not an auth gate. The two routes that use
  it (`post-auth-v2-credit-request`, `post-auth-v2-compute-role-request`,
  `get-auth-v2-user-credit-balance`) keep that handler unchanged.
- **No handler refactor beyond removing the redundant `AccessValidator`
  calls and their imports** (and the stray unused `DxRole.COS_ADMIN`
  static import in `UserHandler`).
- No new tests. Manual smoke list per §7 covers admission cases.

## 3. Endpoint inventory

### 3.1 `UserController` (7 ops)

| Operation ID                            | v1 gate                          | v2 scope                                                                      |
|-----------------------------------------|----------------------------------|-------------------------------------------------------------------------------|
| `post-auth-v2-user-info`                | `forRoles(CONSUMER)`             | `forScopes(DATA_ACCESS)`                                                      |
| `get-auth-v2-user-info`                 | `forRoles(CONSUMER)`             | `forScopes(DATA_ACCESS)`                                                      |
| `patch-auth-v2-user-info`               | `forRoles(CONSUMER)`             | `forScopes(DATA_ACCESS)`                                                      |
| `post-auth-v2-custom-role`              | `forRoles(ORG_ADMIN, COS_ADMIN)` | `forScopesWithContext(platform(ROLE_MANAGEMENT), org(ORG_USER_MANAGEMENT))`   |
| `get-auth-v2-custom-role`               | `forRoles(CONSUMER)`             | `forScopes(DATA_ACCESS)`                                                      |
| `get-auth-v2-custom-role-requester`     | `forRoles(ORG_ADMIN, COS_ADMIN)` | `forScopesWithContext(platform(ROLE_MANAGEMENT), org(ORG_USER_MANAGEMENT))`   |
| `delete-auth-v2-custom-role`            | `forRoles(ORG_ADMIN, COS_ADMIN)` | `forScopesWithContext(platform(ROLE_MANAGEMENT), org(ORG_USER_MANAGEMENT))`   |

### 3.2 `CreditController` (14 ops)

| Operation ID                                    | v1 gate                                                | v1 inline (handler)                                          | v2 scope                                       |
|-------------------------------------------------|--------------------------------------------------------|--------------------------------------------------------------|------------------------------------------------|
| `post-auth-v2-credit-request`                   | `forRoles(COMPUTE)` + `KycVerification`                | none                                                         | `forScopes(COMPUTE_ACCESS)` + KYC unchanged    |
| `get-auth-v2-credit`                            | `forRoles(COS_ADMIN, DELEGATE)`                        | `AccessValidator(COS_ADMIN, CREDIT_MANAGEMENT/COS_ADMIN_ACCESS)` ⚠ | `forScopes(USER_MANAGEMENT)`              |
| `get-auth-v2-user-credit`                       | `forRoles(CONSUMER)`                                   | none                                                         | `forScopes(DATA_ACCESS)`                       |
| `delete-auth-v2-user-credit`                    | `forRoles(CONSUMER)`                                   | none                                                         | `forScopes(DATA_ACCESS)`                       |
| `put-auth-v2-credit-request`                    | `forRoles(COS_ADMIN, DELEGATE)`                        | `AccessValidator(COS_ADMIN, CREDIT_MANAGEMENT/COS_ADMIN_ACCESS)` ⚠ | `forScopes(USER_MANAGEMENT)`              |
| `put-auth-v2-user-credit`                       | `forRoles(COS_ADMIN, DELEGATE)`                        | `AccessValidator(COS_ADMIN, CREDIT_MANAGEMENT/COS_ADMIN_ACCESS)` ⚠ | `forScopes(USER_MANAGEMENT)`              |
| `put-auth-v2-user-credit-add`                   | `forRoles(COS_ADMIN, DELEGATE)`                        | `AccessValidator(COS_ADMIN, CREDIT_MANAGEMENT/COS_ADMIN_ACCESS)` ⚠ | `forScopes(USER_MANAGEMENT)`              |
| `post-auth-v2-compute-role-request`             | `KycVerification` only (no role gate)                  | none                                                         | `forScopes(DATA_ACCESS)` + KYC *(see §5.1)*    |
| `get-auth-v2-compute-role-request`              | `forRoles(COS_ADMIN, DELEGATE)`                        | `AccessValidator(COS_ADMIN, COMPUTE_MANAGEMENT/COS_ADMIN_ACCESS)` ⚠ | `forScopes(USER_MANAGEMENT)`              |
| `get-auth-v2-user-compute-role-request`         | `forRoles(CONSUMER)`                                   | none                                                         | `forScopes(DATA_ACCESS)`                       |
| `delete-auth-v2-user-compute-role-request`      | `forRoles(CONSUMER)`                                   | none                                                         | `forScopes(DATA_ACCESS)`                       |
| `put-auth-v2-compute-role-request`              | `forRoles(COS_ADMIN, DELEGATE)`                        | `AccessValidator(COS_ADMIN, COMPUTE_MANAGEMENT/COS_ADMIN_ACCESS)` ⚠ | `forScopes(USER_MANAGEMENT)`              |
| `get-auth-v2-admin-user-credit-balance`         | `forRoles(COS_ADMIN, DELEGATE)`                        | `AccessValidator(COS_ADMIN, CREDIT_MANAGEMENT/COS_ADMIN_ACCESS)` ⚠ | `forScopes(USER_MANAGEMENT)`              |
| `get-auth-v2-user-credit-balance`               | `forRoles(COMPUTE)` + `KycVerification`                | none                                                         | `forScopes(COMPUTE_ACCESS)` + KYC unchanged    |

⚠ rows: handler-level `AccessValidator.validate(...)` becomes redundant
under v2 and must be removed.

### Scope rationale

- `DATA_ACCESS` — held by every authenticated user via the composite
  `consumer` role on every JWT (see `admin-auth-v2-migration-plan.md`
  §5.1). Used for self-service profile / consumer-self credit ops /
  custom-role read.
- `USER_MANAGEMENT` — held by `COS_ADMIN`. Replaces every
  `forRoles(COS_ADMIN, DELEGATE)` admin gate. Audit §4.18 confirms
  "stretching `USER_MANAGEMENT` for credit/compute admin is
  acceptable — same trust class".
- `forScopesWithContext(platform(ROLE_MANAGEMENT), org(ORG_USER_MANAGEMENT))`
  — admits COS_ADMIN via platform-scope and ORG_ADMIN via org-scope,
  matching `forRoles(ORG_ADMIN, COS_ADMIN)`. Same pattern as
  `ActivityController` admin route, except `ROLE_MANAGEMENT` is the
  natural platform scope for "create/delete custom role" rather than
  `USER_MANAGEMENT`.
- `COMPUTE_ACCESS` — held by `COMPUTE`. Direct 1:1 with
  `forRoles(COMPUTE)`.

## 4. Handler scan — required changes

### 4.1 `UserHandler.java`

- **Line 29:** stray static import `import static
  org.cdpg.dx.auth.authorization.model.DxRole.COS_ADMIN;` — unused
  in the file; remove.
- No `AccessValidator.validate(...)` calls.
- No body changes.

### 4.2 `CreditRequestHandler.java`

| Method                          | `AccessValidator` block | Action  |
|---------------------------------|-------------------------|---------|
| `getCreditRequests`             | L96–101                 | delete  |
| `updateCreditRequestStatus`     | L171–176                | delete  |

Imports to drop: `AccessValidator`, `DxRole`, `DxScope`.

### 4.3 `CreditBalanceHandler.java`

| Method                  | `AccessValidator` block | Action  |
|-------------------------|-------------------------|---------|
| `deductCredits`         | L64–69                  | delete  |
| `addCredits`            | L92–97                  | delete  |
| `getBalanceofUser`      | L131–136                | delete  |

Imports to drop: `AccessValidator`, `DxRole`, `DxScope`.

### 4.4 `ComputeRoleHandler.java`

| Method                       | `AccessValidator` block | Action  |
|------------------------------|-------------------------|---------|
| `getAllComputeRequests`      | L146–151                | delete  |
| `updateComputeRoleStatus`    | L189–194                | delete  |

Imports to drop: `AccessValidator`, `DxRole`, `DxScope`.

`AccessValidator` itself stays — still used by `AssetHandler`,
`ItemController`, `ProviderRoleHandler` (some commented out, not all).

## 5. Open decisions

### 5.1 `post-auth-v2-compute-role-request` — currently KYC-only, no role gate

Audit §6 flags it. Today: any KYC-verified caller can request the
compute role. After migration: `forScopes(DATA_ACCESS)` + KYC.

**Decision: gate with `forScopes(DATA_ACCESS)`** — recommended by audit
§4.18 row 8. Same reasoning as the AdminController self-service ops:
every authenticated user holds `DATA_ACCESS` via composite-stacked
`consumer` role, so this is functionally equivalent to "any KYC-verified
authenticated user" while being explicit. No regression for any role.

If any team wants this to remain reachable without authentication —
flag before merge. The handler keys off `ctx.user().subject()`, so an
unauthenticated call already NPEs in practice.

### 5.2 Custom-role admin ops — `ROLE_MANAGEMENT` vs `USER_MANAGEMENT` for the platform scope

Audit §4.3 pairs `forScopesWithContext(platform(ROLE_MANAGEMENT),
org(ORG_USER_MANAGEMENT))`. `ROLE_MANAGEMENT` is held by COS_ADMIN.
`ORG_USER_MANAGEMENT` is held by ORG_ADMIN. Same admission set as v1
(`forRoles(ORG_ADMIN, COS_ADMIN)`). Following the audit doc; no
deviation proposed.

## 6. Files changed

In **dx-controlplane**:

1. `src/main/java/org/cdpg/dx/aaa/user/controller/UserController.java`
   - Drop v1 imports.
   - Add v2: `AuthenticationHandler`, `AuthorizationHandler`, `Scopes`,
     `ScopeRule`.
   - Constructor takes `AuthenticationHandler` + `AuthorizationHandler`.
   - Pre-resolve two reused handlers: `selfAccess`, `customRoleAdminAccess`.
   - Each route gets `.handler(authenticationV2)` then a scope handler.

2. `src/main/java/org/cdpg/dx/aaa/user/handler/UserHandler.java`
   - Drop unused static import (§4.1).

3. `src/main/java/org/cdpg/dx/aaa/user/factory/UserControllerFactory.java`
   - `create()` takes `AuthHandlersV2`; passes
     `authV2.authentication()` / `authV2.authorization()` to the controller.
   - Drop the unused `Key` import while we're here (cosmetic).

4. `src/main/java/org/cdpg/dx/aaa/credit/Controller/CreditController.java`
   - Drop v1 imports.
   - Add v2: `AuthenticationHandler`, `AuthorizationHandler`, `Scopes`.
   - Constructor takes `AuthenticationHandler` + `AuthorizationHandler`.
   - Pre-resolve three reused handlers: `selfAccess` (DATA_ACCESS),
     `adminAccess` (USER_MANAGEMENT), `computeAccess` (COMPUTE_ACCESS).
   - Each route gets `.handler(authenticationV2)` then a scope handler;
     KYC handler stays unchanged on the two ops that use it plus the
     new compute-role-request gate.

5. `src/main/java/org/cdpg/dx/aaa/credit/handler/CreditRequestHandler.java`
   - Remove two `AccessValidator.validate(...)` blocks.
   - Drop `AccessValidator`, `DxRole`, `DxScope` imports.

6. `src/main/java/org/cdpg/dx/aaa/credit/handler/CreditBalanceHandler.java`
   - Remove three `AccessValidator.validate(...)` blocks.
   - Drop `AccessValidator`, `DxRole`, `DxScope` imports.

7. `src/main/java/org/cdpg/dx/aaa/credit/handler/ComputeRoleHandler.java`
   - Remove two `AccessValidator.validate(...)` blocks.
   - Drop `AccessValidator`, `DxRole`, `DxScope` imports.

8. `src/main/java/org/cdpg/dx/aaa/credit/factory/CreditControllerFactory.java`
   - `create()` takes `AuthHandlersV2`; passes
     `authV2.authentication()` / `authV2.authorization()` to the controller.

9. `src/main/java/org/cdpg/dx/aaa/apiserver/ControllerFactory.java`
   - Pass `authV2` into both `UserControllerFactory.create(...)`
     (L277) and `CreditControllerFactory.create(...)` (L150).

10. `docs/auth/controlplane-scope-audit.md`
    - §4.3 / §4.18 / §6 updated to reflect actual gates and resolve
      the `post-auth-v2-compute-role-request` row in §6.

No `dx-common` changes.

## 7. Build / smoke checks

- `mvn compile -DskipTests` clean (only the pre-existing `Xlint`
  warnings).
- Manual smoke after deploy:
  - `get-auth-v2-user-info` with CONSUMER token → 200.
  - `post-auth-v2-custom-role` with COS_ADMIN token → 200.
  - `post-auth-v2-custom-role` with ORG_ADMIN token → 200 (org-scope path).
  - `post-auth-v2-custom-role` with PROVIDER token → 403.
  - `get-auth-v2-credit` with COS_ADMIN token → 200.
  - `get-auth-v2-user-credit` with CONSUMER token → 200.
  - `post-auth-v2-credit-request` with COMPUTE token (KYC OK) → 200.
  - `post-auth-v2-compute-role-request` with CONSUMER token (KYC OK)
    → 200.
  - `post-auth-v2-compute-role-request` without a JWT → 401.
  - Admin credit op with CONSUMER token → 403.

## 8. Risks

1. **No existing unit tests** for `UserController`, `CreditController`,
   or any of the credit handlers in scope. Mitigate via manual smoke
   list and a careful self-review.
2. **Behaviour change for `post-auth-v2-compute-role-request`**:
   previously KYC-only with no role gate, now `DATA_ACCESS` + KYC.
   Anyone relying on calling it pre-authentication breaks. Likelihood
   low (handler does `ctx.user().subject()`). Flag in PR description.
3. **`AccessValidator` removal across 7 admin ops** silently widens
   admission iff there exists a token holder who has `USER_MANAGEMENT`
   scope but is not COS_ADMIN. Per `SystemRoleScopeMap`,
   `USER_MANAGEMENT` is held only by COS_ADMIN — that set is empty.
   Legacy `CREDIT_MANAGEMENT` / `COMPUTE_MANAGEMENT` /
   `COS_ADMIN_ACCESS` scope claims are not part of the v2 model.
4. **`UserHandler` stray import** — purely cosmetic, but worth doing
   in the same PR to keep the handler clean of v1 references.

## 9. Out-of-scope follow-ups

- Migrate `KYCController` (sister controller — §4.19, also flagged
  in §6).
- Move audit-log writes to the v2 audit pipeline.
- Add unit tests covering scope-gate enforcement on `UserController`
  and `CreditController`.