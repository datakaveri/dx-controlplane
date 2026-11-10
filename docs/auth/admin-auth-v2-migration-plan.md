---
title: Admin (Keycloak user CRUD) — auth-v2 migration plan
status: draft
related:
  - controlplane-scope-audit.md §4.4, §6
  - activity-auth-v2-migration-plan.md (template)
---

## 1. Goal

Migrate `AdminController` (Keycloak user CRUD endpoints) from the legacy
`org.cdpg.dx.auth.authorization.handler.AuthorizationHandler.forRoles(...)`
gates to the v2 stack
(`org.cdpg.dx.auth.v2.handler.AuthenticationHandler` +
`AuthorizationHandler.forScopes`), and remove the duplicated v1
`AccessValidator.validate(...)` checks inside `AdminHandler` that become
redundant once v2 scope gating is in place.

End-user behaviour is unchanged for ops that already have a v1 gate. For
the 5 ops that currently have *no* gate, this PR adds one — see §5.

## 2. Non-goals

- **No dx-common changes.** Existing 13 scopes are sufficient.
- **No handler refactor beyond removing the redundant `AccessValidator`
  calls and their imports.** Audit-log helpers (`AuditingHelper`,
  `RoutingContextHelper.setAuditingLog`) and the existing service-call
  chains stay as-is.
- **Not migrating** `KYCController`, `CreditController`, or other
  controllers that share `KeycloakUserService` / `UserService`. Each is
  its own per-controller PR per the audit doc's §9 sequencing.
- No new tests beyond what's needed to lock in the scope gates (none
  exist for `AdminController` today — see §8 risk).

## 3. Endpoint inventory

| Operation ID                       | v1 gate (controller)                              | v1 inline check (handler)                                      | v2 scope                          |
|------------------------------------|---------------------------------------------------|----------------------------------------------------------------|-----------------------------------|
| `get-auth-v2-user`                 | none                                              | none                                                           | `forScopes(DATA_ACCESS)` *(self)* |
| `get-auth-v2-user-id-admin`        | `forRoles(COS_ADMIN, DELEGATE)`                   | `AccessValidator.validate(COS_ADMIN, COS_ADMIN_ACCESS)` ⚠      | `forScopes(USER_MANAGEMENT)`      |
| `get-auth-v2-admin-user`           | `forRoles(COS_ADMIN)`                             | `AccessValidator.validate(COS_ADMIN, COS_ADMIN_ACCESS)` ⚠      | `forScopes(USER_MANAGEMENT)`      |
| `get-auth-v2-user-search`          | `forRoles(CONSUMER)`                              | none                                                           | `forScopes(DATA_ACCESS)`          |
| `put-auth-v2-user`                 | none                                              | none                                                           | `forScopes(DATA_ACCESS)` *(self)* |
| `put-auth-v2-user-password`        | none                                              | none                                                           | `forScopes(DATA_ACCESS)` *(self)* |
| `post-auth-v2-user-update`         | none                                              | none                                                           | `forScopes(DATA_ACCESS)` *(self)* |
| `delete-auth-v2-user`              | none                                              | none                                                           | `forScopes(DATA_ACCESS)` *(self)* |
| `post-auth-v2-admin-id-update`     | `forRoles(COS_ADMIN, DELEGATE)`                   | `AccessValidator.validate(COS_ADMIN, COS_ADMIN_ACCESS)` ⚠      | `forScopes(USER_MANAGEMENT)`      |

⚠ rows: handler-level `AccessValidator.validate(...)` becomes redundant
under v2 and must be removed alongside the controller-level swap.
*(self)* rows: each handler keys off `ctx.user().subject()`, so the
caller can only act on themselves regardless of scope. `DATA_ACCESS`
gates them safely — every authenticated user holds it because Keycloak
issues the `consumer` role on every JWT via composite-role inheritance
(see §5.1).

### Scope rationale

- `USER_MANAGEMENT` — held by `COS_ADMIN`. Replaces `forRoles(COS_ADMIN,
  DELEGATE)` (the `DELEGATE` term collapses since v2 has no
  delegation-role gate; an admin holding the scope is admitted directly
  without the v1 delegation-vs-primary branching).
- `DATA_ACCESS` — held by `CONSUMER`. Already used for `UserController`
  self-info ops (audit §4.3). Self-service admin ops here read/write
  `ctx.user().subject()` only and so are functionally the same trust
  class.

## 4. Handler scan — required changes inside `AdminHandler.java`

Three methods contain an inline v1 access check that is duplicated by
the controller-level gate. Once v2 scope gating is wired, these throw
spuriously when the caller holds the v2 scope but the JWT doesn't
include the legacy `COS_ADMIN_ACCESS` scope claim. They must go.

| Method                       | Lines (current)            | Action                                                                |
|------------------------------|----------------------------|-----------------------------------------------------------------------|
| `getDxUserFromKeycloak`      | `AdminHandler.java:79–84`  | delete the `AccessValidator.validate(...)` block (and unused locals)  |
| `getAllDxUsersKeycloak`      | `AdminHandler.java:106–111`| delete the `AccessValidator.validate(...)` block (and unused locals)  |
| `updateDxUserStatusById`     | `AdminHandler.java:410–415`| delete the `AccessValidator.validate(...)` block (and unused locals)  |

Imports to remove from `AdminHandler.java`:

- `org.cdpg.dx.auth.authentication.util.AccessValidator`
- `org.cdpg.dx.auth.authorization.model.DxRole`
- `org.cdpg.dx.auth.authorization.model.DxScope`

`AccessValidator` is still used by other handlers (`AssetHandler`,
`CreditRequestHandler`, `CreditBalanceHandler`, `ComputeRoleHandler`,
`ItemController`) — **leave the class itself in place**. Don't delete
the file.

Other handler internals (audit log writes, service-call chains, response
shaping, email composition) are untouched.

## 5. Open decisions

### 5.1 Audience of the 5 currently-ungated ops

The audit doc (`controlplane-scope-audit.md` §6 row 1) flagged these as
needing an explicit decision:

> `get-auth-v2-user`, `put-auth-v2-user`, `put-auth-v2-user-password`,
> `post-auth-v2-user-update`, `delete-auth-v2-user` — Self-update vs
> admin-only?? Confirm — currently anyone with a JWT can call them.

**Decision: gate with `forScopes(DATA_ACCESS)`.**

Reasoning:

1. Every handler reads `ctx.user().subject()` and operates on that user
   only. There is no `:id` path param or query lookup. Admins targeting
   *another* user go through the dedicated `*-admin-*` ops
   (`-id-admin`, `-admin-user`, `admin-id-update`).
2. **`DATA_ACCESS` admits every authenticated user**, because the
   Keycloak realm is configured so every primary role is a composite
   that includes the `consumer` role:

   | Role on the user | `realm_access.roles` issued | Effective scopes include `DATA_ACCESS`? |
   |---|---|---|
   | `consumer` | `[consumer]` | ✅ |
   | `provider` | `[provider, consumer]` | ✅ |
   | `org_admin` | `[org_admin, provider, consumer]` | ✅ |
   | `cos_admin` | `[cos_admin, consumer]` (NOT `org_admin` / `provider`) | ✅ |
   | `compute` | `[compute, consumer]` | ✅ |

   `InMemoryRoleScopeRegistry` unions `SystemRoleScopeMap.getScopes()`
   over every role in the principal, so the `consumer` term in the
   composite always contributes `DATA_ACCESS`.

3. Therefore `forScopes(DATA_ACCESS)` is functionally equivalent to
   "any authenticated principal" today, while still being declarative
   and audit-friendly at the route level.

### 5.2 `delete-auth-v2-user` allows any user to delete themselves

This is the existing v1 behaviour (and continues under `DATA_ACCESS`).
Worth a quick confirm during review — not changing in this PR.

### 5.3 No dx-common change needed

We considered baking role hierarchy into `SystemRoleScopeMap`
(e.g., `PROVIDER → {OWN_ASSET_MANAGEMENT, DATA_ACCESS}`). **Not
required** — the hierarchy is already realised in Keycloak via composite
roles, and the registry's per-role union is correct. `SystemRoleScopeMap`
remains atomic per role per its class-doc design intent.

## 6. Files changed

In **dx-controlplane**:

1. `src/main/java/org/cdpg/dx/aaa/admin/controller/AdminController.java`
   - Drop `AuthorizationHandler` (v1) and `DxRole` imports.
   - Add `AuthenticationHandler`, `AuthorizationHandler` (v2),
     `Scopes`, and `AuthHandlersV2`.
   - Constructor takes `AuthHandlersV2` (or pre-resolved
     authentication/authorization handlers — match the
     `UserInteractionV2Controller` style for consistency).
   - Each route gets `.handler(authenticationV2)` then a scope handler
     per the table in §3.

2. `src/main/java/org/cdpg/dx/aaa/admin/handler/AdminHandler.java`
   - Remove three `AccessValidator.validate(...)` blocks (§4 table).
   - Drop `AccessValidator`, `DxRole`, `DxScope` imports.
   - No constructor change.

3. `src/main/java/org/cdpg/dx/aaa/apiserver/ControllerFactory.java`
   - Pass `authV2` into the `AdminController` constructor at
     `ControllerFactory.java:175`.

4. `docs/auth/controlplane-scope-audit.md` (§4.4, §6)
   - Update the §4.4 v2-scope column for the 5 currently-ungated rows
     once §5.1 is decided.
   - Strike the AdminController row from §6 once gated.

No `dx-common` changes.

## 7. Build / smoke checks

- `mvn compile -DskipTests` clean (same baseline as the
  user-interaction PR — only the two `Xlint` warnings remain).
- Manual smoke (post-deploy / local run):
  - Self-info op `get-auth-v2-user` with a CONSUMER token → 200.
  - Admin op `get-auth-v2-admin-user` with a COS_ADMIN token → 200.
  - Admin op with a CONSUMER token → 403.
  - Self-delete `delete-auth-v2-user` with a CONSUMER token → 200.
  - Self-delete with no token → 401.

## 8. Risks

1. **No existing unit tests for `AdminController` or `AdminHandler`.**
   Refactor is mechanical, but a typo'd scope name slips silently.
   Mitigate via the manual smoke list in §7 and a careful self-review
   diff. *(Adding tests is out of scope for this PR — call it out as a
   follow-up if the team wants coverage here.)*
2. **Behaviour change for the 5 currently-ungated ops** (§5.1). Anyone
   relying on calling them without authentication breaks. Likelihood
   low (each one does `ctx.user().subject()` so an unauthenticated call
   already NPEs in practice), but worth flagging in the PR description.
3. **`AccessValidator` removal across the 3 admin ops** silently widens
   admission iff there exists a token holder who has `USER_MANAGEMENT`
   scope but is *not* COS_ADMIN. Per `SystemRoleScopeMap`, that set is
   empty — `USER_MANAGEMENT` is held only by COS_ADMIN. The legacy
   `COS_ADMIN_ACCESS` scope claim that v1 `AccessValidator` checked is
   not part of the v2 model and isn't issued separately, so dropping
   it has no admission impact.

## 9. Out-of-scope follow-ups

- Migrate `KYCController` / `CreditController` / other handlers that
  still use `AccessValidator` — separate PRs per controller.
- Move audit-log writes to the v2 audit pipeline (`AuditingHandler` +
  `UserActivityAuditLogBuilder`) — orthogonal cleanup.
- Add unit tests covering scope-gate enforcement on `AdminController`.