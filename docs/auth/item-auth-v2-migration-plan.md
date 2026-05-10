---
title: ItemController — auth-v2 migration plan
status: draft
related:
  - controlplane-scope-audit.md §4.11
  - acl-auth-v2-migration-plan.md (Option A template)
  - organization-auth-v2-migration-plan.md
---

## 1. Goal

Migrate `ItemController` (catalogue item CRUD — 8 ops) from the
mixed-v1 gating (route-level `forRoles`, in-handler
`AccessValidator.validate(...)`, plus two custom handlers
`VerifyItemTypeAndRole` and `ItemOwnershipValidator`) to v2 scope-based
route-level gating, **preserving the two custom handlers verbatim**.

This is a **gating-only migration** (Option A). Handler bodies are
unchanged except for removing two `AccessValidator.validate(...)`
blocks that become redundant under v2 — see §4. No `DxPrincipal`
swap-in. Identity reads continue to use
`ctx.user().subject()` / `RoutingContextHelper.fromPrincipal(ctx)`,
matching v1 semantics. Phase 2 (delegation-aware business logic) is
queued as a follow-up across the asset-domain controllers (§9).

## 2. Non-goals

- **No dx-common changes.** Existing 13 scopes are sufficient.
- **`VerifyItemTypeAndRole` stays.** Custom handler that maps role
  → allowed `itemType` values. Runs after the new scope gate on
  `CREATE_ITEM` / `UPDATE_ITEM`. Untouched.
- **`ItemOwnershipValidator` stays.** Constructor-injected validator
  used inside `handleDeleteItem` / `handlePatchItem` for "this user
  owns this item" checks. Untouched.
- **No business-logic changes.** Identity reads, role-based
  branching inside `handlePatchItem`, item-type validation, etc. all
  preserved.
- **`AccessValidator` itself is not deleted.** Other controllers
  still use it (asset module). Just dropped from `ItemController`'s
  imports + the two in-handler calls.

## 3. Endpoint inventory — gating

8 operations across one controller.

| # | Operation ID                   | v1 gate                                                                                  | v2 scope                                                                          |
|---|--------------------------------|------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------|
| 1 | `CREATE_ITEM`                  | none at route + `VerifyItemTypeAndRole` + in-handler `AccessValidator(PROVIDER, COS_ADMIN; ASSET_MANAGEMENT, COS_ADMIN_ACCESS, ORG_ADMIN_ACCESS)` | `forScopes(OWN_ASSET_MANAGEMENT, ORG_ASSET_MANAGEMENT, ASSET_MANAGEMENT)` + `VerifyItemTypeAndRole` (unchanged) |
| 2 | `GET_ITEM`                     | none                                                                                     | leave open *(see §5.1)*                                                           |
| 3 | `DELETE_ITEM`                  | none at route + `ItemOwnershipValidator` inside handler                                  | `forScopes(OWN_ASSET_MANAGEMENT, ORG_ASSET_MANAGEMENT, ASSET_MANAGEMENT)` + `ItemOwnershipValidator` (unchanged) |
| 4 | `UPDATE_ITEM`                  | none at route + `VerifyItemTypeAndRole` + in-handler `AccessValidator(...)`              | same as `CREATE_ITEM`                                                              |
| 5 | `PATCH_ITEM`                   | `forRoles(COS_ADMIN, ORG_ADMIN, PROVIDER)` + in-handler `AccessValidator(...)`           | `forScopes(ASSET_MANAGEMENT, ORG_ASSET_MANAGEMENT, OWN_ASSET_MANAGEMENT)`          |
| 6 | `GET_ITEM_WITH_ACCESS`         | none — service-level access check                                                        | leave open *(see §5.1)*                                                            |
| 7 | `CHECK_ITEM_NAME_AVAILABILITY` | none                                                                                     | leave open *(see §5.1)*                                                            |
| 8 | `DOWNLOAD_SCRIPT`              | none                                                                                     | `forScopes(OWN_ASSET_MANAGEMENT)` *(see §5.2)*                                     |

### Scope rationale

- `forScopes(OWN_ASSET_MANAGEMENT, ORG_ASSET_MANAGEMENT, ASSET_MANAGEMENT)`
  — admits PROVIDER (own assets), ORG_ADMIN (org assets), COS_ADMIN
  (platform assets). Same admission set as the v1 in-handler
  `AccessValidator.validate(...)` call (which admitted PROVIDER,
  COS_ADMIN, plus delegation-scope-claim holders for
  `COS_ADMIN_ACCESS` / `ORG_ADMIN_ACCESS`). The v1 delegation-scope
  claims aren't in the v2 model — admitted users are the same
  set under `SystemRoleScopeMap`.
- `forScopes(OWN_ASSET_MANAGEMENT)` for `DOWNLOAD_SCRIPT` —
  PROVIDER-only, matching the audit's recommendation. If COS_ADMIN
  / ORG_ADMIN should also download scripts, broaden to all three
  asset scopes (cheap to change).

## 4. Handler body changes — remove redundant `AccessValidator` blocks

Two active `AccessValidator.validate(...)` blocks become redundant
once the v2 scope gate runs at the route level. They check the same
admission set as the new `forScopes(...)`:

| Handler method                  | Lines (current)  | Action  |
|---------------------------------|------------------|---------|
| `handleCreateOrUpdateItem`      | L214–221         | delete  |
| `handlePatchItem`               | L274–281         | delete  |

Imports to drop: `AccessValidator`, `DxScope.COS_ADMIN_ACCESS`,
`DxScope.ORG_ADMIN_ACCESS`. `DxRole` and `DxScope` themselves stay
— still used by the role-based branching at L308–313 in
`handlePatchItem` (business logic for which patch operations a role
can perform — preserved).

Other identity reads (`ctx.user()`, `ctx.user().subject()`,
`ctx.user().principal()`, `RoutingContextHelper.fromPrincipal(ctx)`)
are **untouched**. Under v2 delegation these surface the
delegate's identity (matches v1 behavior — Item never honored
delegation at handler level).

## 5. Open decisions

### 5.1 Three "no auth needed" ops — `GET_ITEM`, `GET_ITEM_WITH_ACCESS`, `CHECK_ITEM_NAME_AVAILABILITY`

Audit §4.11 marks all three as "leave open (public catalogue read)
or `forScopes(DATA_ACCESS)`".

`GET_ITEM_WITH_ACCESS` is service-level access-checked (consumer
sees only items they have access to via policy) — but the access
check requires `ctx.user()` non-null. If the route is left
fully open with no `authenticationV2` handler, a no-JWT call
NPEs in the service.

**Decision: gate all three with `authenticationV2` only (no scope
check)** — makes JWT required but admits any authenticated user,
matching the audit's intent. The scope handler is dropped on these
three. Same pattern as left-open admin ops in prior migrations.

If product wants any of the three to remain reachable without a JWT
(e.g. truly public catalogue browse for `GET_ITEM`), flag before
merge — easy to drop the `authenticationV2` handler on individual
routes.

### 5.2 `DOWNLOAD_SCRIPT` — `OWN_ASSET_MANAGEMENT` vs broader

Audit §4.11 says "leave or `forScopes(OWN_ASSET_MANAGEMENT)`".
Plan goes with `OWN_ASSET_MANAGEMENT` — PROVIDER-only. If COS_ADMIN
or ORG_ADMIN need to download scripts (e.g. troubleshooting), flag
before merge.

### 5.3 `CREATE_ITEM` / `UPDATE_ITEM` / `DELETE_ITEM` admission widening

v1 in-handler `AccessValidator.validate(...)` admitted:
- PROVIDER role, OR
- COS_ADMIN role, OR
- token holding `ASSET_MANAGEMENT` / `COS_ADMIN_ACCESS` /
  `ORG_ADMIN_ACCESS` *delegation* scope claim.

v2 `forScopes(OWN_ASSET_MANAGEMENT, ORG_ASSET_MANAGEMENT, ASSET_MANAGEMENT)`
admits PROVIDER, ORG_ADMIN, COS_ADMIN (plus delegates of those via
the framework `DelegationResolver`).

**Net effect**: ORG_ADMIN gains direct (non-delegated) admission,
which v1 didn't grant by primary-role alone. Per
`SystemRoleScopeMap`, `ORG_ASSET_MANAGEMENT` is held only by
ORG_ADMIN — and the audit recommendation explicitly lists this scope
as one of the three for these routes, so this widening is intentional
(audit treats org-asset management as a first-class capability).
`VerifyItemTypeAndRole` then constrains *which itemTypes*
each admitted role can create/update — that custom handler stays in
place and continues to enforce per-type role rules.

If the widening is undesired (i.e. ORG_ADMIN should still be blocked
from CREATE/UPDATE/DELETE except via delegation), narrow the gate to
`forScopes(OWN_ASSET_MANAGEMENT, ASSET_MANAGEMENT)` and flag.

## 6. Files changed

In **dx-controlplane**:

1. `src/main/java/org/cdpg/dx/aaa/item/controller/ItemController.java`
   - Drop v1 `AuthorizationHandler`, `DxScope.COS_ADMIN_ACCESS`,
     `DxScope.ORG_ADMIN_ACCESS`, `AccessValidator` imports.
   - Add v2 `AuthenticationHandler`, `AuthorizationHandler`,
     `Scopes`.
   - Constructor takes `AuthenticationHandler` +
     `AuthorizationHandler`.
   - Drop the L93–94 `patchItemAccessHandler` field (replaced by a
     pre-resolved scope handler in `register(...)`).
   - Pre-resolve in `register(...)`:
     `assetManagementAccess` (`forScopes(OWN_ASSET_MANAGEMENT, ORG_ASSET_MANAGEMENT, ASSET_MANAGEMENT)`),
     `providerScriptAccess` (`forScopes(OWN_ASSET_MANAGEMENT)`).
   - Each route gets the appropriate handler chain per §3.
   - Remove the two in-handler `AccessValidator.validate(...)`
     blocks per §4.
   - `VerifyItemTypeAndRole`, `ItemOwnershipValidator`, all
     handler-body identity reads, role-based patch branching —
     untouched.

2. `src/main/java/org/cdpg/dx/aaa/item/factory/ItemControllerFactory.java`
   - `createCrudController(...)` takes `AuthHandlersV2` as last
     parameter; passes `authV2.authentication()` /
     `authV2.authorization()` to the controller constructor.

3. `src/main/java/org/cdpg/dx/aaa/apiserver/ControllerFactory.java`
   - Pass existing `authV2` field into
     `ItemControllerFactory.createCrudController(...)` at L228.

4. `docs/auth/controlplane-scope-audit.md`
   - §6 — no `ItemController` rows there today; nothing to strike
     out. The audit §4.11 table itself is already accurate; no doc
     edit needed.

No `dx-common` changes.

## 7. Build / smoke checks

- `mvn compile -DskipTests` clean (only the pre-existing `Xlint`
  warnings).
- Manual smoke after deploy:
  - `CREATE_ITEM` with PROVIDER → 200; ORG_ADMIN → 200 (new admission per §5.3); COS_ADMIN → 200; CONSUMER → 403.
  - `UPDATE_ITEM` same as `CREATE_ITEM`.
  - `DELETE_ITEM` with PROVIDER (own item) → 200; PROVIDER (other's item) → 403 via `ItemOwnershipValidator`.
  - `PATCH_ITEM` with PROVIDER → 200; ORG_ADMIN → 200; COS_ADMIN → 200; CONSUMER → 403.
  - `GET_ITEM` with any token → 200; without JWT → 401 (was open — see §5.1 caveat).
  - `GET_ITEM_WITH_ACCESS` with any token → 200; without JWT → 401.
  - `CHECK_ITEM_NAME_AVAILABILITY` with any token → 200; without JWT → 401.
  - `DOWNLOAD_SCRIPT` with PROVIDER → 200; CONSUMER → 403; ORG_ADMIN → 403.
  - `CREATE_ITEM` of an itemType not allowed for the caller's role → 403 via `VerifyItemTypeAndRole`.

## 8. Risks

1. **Behavior change for the three `none → JWT-required` ops**
   (§5.1): tightens admission. Any caller hitting `GET_ITEM`,
   `GET_ITEM_WITH_ACCESS`, `CHECK_ITEM_NAME_AVAILABILITY` without
   a JWT will now get 401. Likelihood depends on whether external
   tools (UI, CLI) currently call these unauthenticated.
2. **ORG_ADMIN gains direct CREATE/UPDATE/DELETE_ITEM admission**
   (§5.3): documented and matches audit recommendation, but verify
   product expectation before merge.
3. **`DOWNLOAD_SCRIPT` tightening from open → PROVIDER-only**
   (§5.2): same flag-before-merge note.
4. **No existing unit tests** for `ItemController`. Manual smoke
   list (§7) and self-review.
5. **`forDelegationScopes(...)`-equivalent admission removal**: v1
   in-handler `AccessValidator` admitted users holding the legacy
   `COS_ADMIN_ACCESS` / `ORG_ADMIN_ACCESS` *delegation* scope
   claims — that claim path is not part of the v2 model. Per
   `SystemRoleScopeMap`, the three v2 scopes
   (`OWN_ASSET_MANAGEMENT`, `ORG_ASSET_MANAGEMENT`,
   `ASSET_MANAGEMENT`) are held only by PROVIDER, ORG_ADMIN,
   COS_ADMIN respectively — no widening.

## 9. Out-of-scope follow-ups

- **Phase 2 — DxPrincipal swap-in across asset-domain controllers**:
  unify Item, Asset, Search, Subscription, ResourceServer,
  Delegation, ACL handler bodies on `DxPrincipal` so delegation
  flows through to business logic.
- **Migrate `AssetController`, `SearchController`,
  `SubscriptionController`** (planned PR1).
- **Migrate `ResourceServerController` and `DelegationController`**
  (planned PR3 — depends on Gap #1 / Gap #2 decisions).
- **Add unit tests** for `ItemController` scope-gate enforcement
  and the `VerifyItemTypeAndRole` / `ItemOwnershipValidator`
  interaction.