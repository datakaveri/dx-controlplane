# Auth v2 — Activity APIs migration plan

**Repo:** `dx-controlplane`
**Branch:** `refact/auth-v2-activity` (based on `refact/auth-v2-wiring`)
**Controllers in scope:** `ActivityController`, `ActivityReportController`
**Status:** Plan — awaiting review before implementation

---

## 1. Goal

Migrate both activity-related controllers from the legacy
`org.cdpg.dx.auth.authorization.handler.AuthorizationHandler` (static
`forRoles` / `forDelegationScopes`) to the v2 stack
(`org.cdpg.dx.auth.v2.handler.AuthenticationHandler` +
`AuthorizationHandler.forScopes` / `forScopesWithContext`).
First real-endpoint migration — proves v2 works on production routes,
not just `/auth/v2/whoami`.

End-user behavior is unchanged: same callers admitted, same response
shapes, same error codes. Internally we move from role-based to
scope-based authorization using **existing dx-common scopes only**.

## 2. Non-goals

- **No dx-common changes.** Use the existing 13 scopes as-is. The
  broader "broaden DATA_ACCESS to all primary roles" / `SELF_PROFILE`
  discussion (see `controlplane-scope-audit.md` §7-§8) is deferred —
  for now `DATA_ACCESS` is the consumer-only scope and that's fine for
  Activity since both consumer-facing routes are CONSUMER-only today.
- **Not changing handler bodies.** `ctx.user()` / `DxUser` reads stay
  as-is — they keep working because the upstream OpenAPI JWT security
  handler still populates `ctx.user()`.
- Not refactoring `LocalAuthV2Factory`'s internal service proxy
  creation. Known duplication with `SharedServices` (Keycloak,
  Postgres, DataBroker) is left for a separate cleanup PR.

## 3. Endpoint inventory

| Controller | Operation ID | v1 auth | v2 scope mapping |
|---|---|---|---|
| `ActivityController` | `OP_GET_ACTIVITY_FOR_CONSUMER` | `forRoles(CONSUMER)` | `authz.forScopes(Scopes.DATA_ACCESS)` |
| `ActivityController` | `OP_GET_ACTIVITY_FOR_ADMIN` | `forRoles(ORG_ADMIN, COS_ADMIN)` | `authz.forScopesWithContext(ScopeRule.platform(Scopes.USER_MANAGEMENT), ScopeRule.org(Scopes.ORG_USER_MANAGEMENT))` |
| `ActivityReportController` | `get-consumer-report` | `forRoles(CONSUMER)` | `authz.forScopes(Scopes.DATA_ACCESS)` |
| `ActivityReportController` | `get-admin-report` | `forRoles(ORG_ADMIN, COS_ADMIN)` | `authz.forScopesWithContext(ScopeRule.platform(Scopes.USER_MANAGEMENT), ScopeRule.org(Scopes.ORG_USER_MANAGEMENT))` |

### Scope rationale
- `DATA_ACCESS` — held by CONSUMER. Consumer activity-log viewing is
  inspecting their own data access trail; same trust class.
- `USER_MANAGEMENT` (held by COS_ADMIN) and `ORG_USER_MANAGEMENT`
  (held by ORG_ADMIN) — admin activity viewing is part of the same
  trust class as managing those users. `forScopesWithContext` exposes
  the platform-vs-org level via `AuthorizationContext`, replacing the
  ad-hoc branching in `Util.getAllowedFilterMapForAdmin`.

## 4. Current state — verified

### 4.1 Controllers today (legacy v1)

- `src/main/java/org/cdpg/dx/aaa/activity/controller/ActivityController.java`
  - Lines 23-24: imports `org.cdpg.dx.auth.authorization.handler.AuthorizationHandler` (v1 static) + v1 `DxRole`.
  - Lines 46-48: builds two `Handler<RoutingContext>` via static `forRoles(...)`.
  - Handler bodies read `context.user()` (line 63) and `RoutingContextHelper.fromPrincipal(context)` → `DxUser` (line 108).

- `src/main/java/org/cdpg/dx/aaa/ActivityReport/controller/ActivityReportController.java`
  - Lines 21-22: same v1 imports.
  - Lines 38-40: same static `forRoles` pattern.
  - Handler bodies read `routingContext.user()` (line 114) and `RoutingContextHelper.fromPrincipal(...)` → `DxUser` (line 62).

### 4.2 Factories

- `ActivityControllerFactory.create(PostgresService, URNGenerator)` — no auth deps.
- `ActivityReportControllerFactory.create(PostgresService, Vertx)` — no auth deps.

### 4.3 Wiring

- `ControllerFactory.createControllers(vertx, config, urnGenerator)` at
  `src/main/java/org/cdpg/dx/aaa/apiserver/ControllerFactory.java:79`.
  - Line 118-120 — creates `ActivityController`.
  - Line 122-124 — creates `ActivityReportController`.

- `ApiServerVerticle.getAuthV2Handler()` at
  `src/main/java/org/cdpg/dx/aaa/apiserver/ApiServerVerticle.java:59`
  calls `LocalAuthV2Factory.build(vertx, config())` and returns just the
  `AuthenticationHandler` (the `/auth/v2/whoami` diagnostic).

- `LocalAuthV2Factory.build(vertx, config)` currently returns only
  `Handler<RoutingContext>` (the authentication handler). No
  authorization handler is exposed.

### 4.4 Tests

- No controller-level tests for either activity controller exist today
  (`find src/test … -name "*Activity*"` finds only
  `UserActivityAuditLogServiceTest.java`, which exercises the service
  layer and does not touch auth).
- Conclusion: no existing tests need auth-migration updates.

## 5. Target state — touch points

Six files touched. Summary first, diffs in §6.

| # | File | Change type |
|---|---|---|
| 1 | `auth/v2/factory/AuthHandlersV2.java` | **New** record holding both v2 handlers |
| 2 | `auth/v2/factory/LocalAuthV2Factory.java` | **Modified** — add `buildPair()`; keep `build()` as a thin delegate for `/whoami` |
| 3 | `aaa/apiserver/ApiServerVerticle.java` | **Modified** — build the pair once in `createControllers`, reuse in `getAuthV2Handler` |
| 4 | `aaa/apiserver/ControllerFactory.java` | **Modified** — accept the pair, pass to both activity factories |
| 5 | `aaa/activity/controller/ActivityController.java` + its factory | **Modified** — accept v2 handlers, swap in v2 auth chain |
| 6 | `aaa/ActivityReport/controller/ActivityReportController.java` + its factory | **Modified** — same pattern |

*(ACL side — `ApdApiServerVerticle` — is not in scope; it uses the v2 handler
for `/auth/v2/whoami` but doesn't host activity controllers. No change there.)*

## 6. File-by-file changes

### 6.1 New — `auth/v2/factory/AuthHandlersV2.java`

Small record holding the pair. Lives in controlplane (not dx-common) for now —
if other consumers (dataplane, acl-apd) adopt the same pattern, promote to
dx-common in a follow-up.

```java
package org.cdpg.dx.auth.v2.factory;

import org.cdpg.dx.auth.v2.handler.AuthenticationHandler;
import org.cdpg.dx.auth.v2.handler.AuthorizationHandler;

public record AuthHandlersV2(
    AuthenticationHandler authentication,
    AuthorizationHandler authorization) {}
```

### 6.2 Modified — `LocalAuthV2Factory.java`

Add `buildPair` that constructs both. Keep `build` as a delegate so the
existing `/auth/v2/whoami` wiring in `ApiServerVerticle.getAuthV2Handler()`
keeps compiling; we'll tidy it up in the verticle diff in §6.3 so there's
only one builder call per process.

```java
public static AuthHandlersV2 buildPair(Vertx vertx, JsonObject config) {
  // …existing lookup wiring unchanged…
  RoleScopeRegistry registry = new InMemoryRoleScopeRegistry();
  return new AuthHandlersV2(
      new AuthenticationHandler(
          new JwtPrincipalResolver(),
          new DelegationResolver(delegationLookup, userLookup),
          new AppCredentialsResolver(appLookup, userLookup)),
      new AuthorizationHandler(registry));
}

public static Handler<RoutingContext> build(Vertx vertx, JsonObject config) {
  return buildPair(vertx, config).authentication();
}
```

Verified against dx-common source: `InMemoryRoleScopeRegistry` has a
default no-arg constructor (it's stateless; flattens
`SystemRoleScopeMap` on each call). `new InMemoryRoleScopeRegistry()`
is correct.

### 6.3 Modified — `ApiServerVerticle.java`

Build the pair once; reuse in both overrides. Avoids running the resolver
stack twice at boot.

```java
private AuthHandlersV2 authV2Pair;

@Override
protected List<ApiController> createControllers(
    Vertx vertx, JsonObject config, URNGenerator urnGenerator) {
  this.authV2Pair = LocalAuthV2Factory.buildPair(vertx, config);
  return ControllerFactory.createControllers(vertx, config, urnGenerator, authV2Pair);
}

@Override
protected Handler<RoutingContext> getAuthV2Handler() {
  return authV2Pair.authentication();
}
```

### 6.4 Modified — `ControllerFactory.java`

New signature. Only activity factories consume the pair for now; other
controllers are unchanged.

```java
public static List<ApiController> createControllers(
    Vertx vertx, JsonObject config, URNGenerator urnGenerator, AuthHandlersV2 authV2) {
  // …unchanged preamble…

  // Activity
  ActivityController activityController =
      ActivityControllerFactory.create(infra.pgService(), urnGenerator, authV2);
  controllers.add(activityController);

  ActivityReportController activityReportController =
      ActivityReportControllerFactory.create(infra.pgService(), vertx, authV2);
  controllers.add(activityReportController);

  // …all other controllers unchanged…
}
```

### 6.5 Modified — `ActivityController.java` + `ActivityControllerFactory.java`

**Controller (imports, constructor, `register()`):**

```java
// Imports: drop v1 AuthorizationHandler + v1 DxRole
import org.cdpg.dx.auth.v2.handler.AuthenticationHandler;
import org.cdpg.dx.auth.v2.handler.AuthorizationHandler;
import org.cdpg.dx.auth.v2.handler.ScopeRule;
import org.cdpg.dx.auth.v2.model.Scopes;

public class ActivityController implements ApiController {
  private final UserActivityAuditLogService userActivityAuditLogService;
  private final URNGenerator urnGenerator;
  private final AuthenticationHandler authenticationV2;
  private final AuthorizationHandler authorizationV2;

  public ActivityController(
      UserActivityAuditLogService userActivityAuditLogService,
      URNGenerator urnGenerator,
      AuthenticationHandler authenticationV2,
      AuthorizationHandler authorizationV2) {
    this.userActivityAuditLogService = userActivityAuditLogService;
    this.urnGenerator = urnGenerator;
    this.authenticationV2 = authenticationV2;
    this.authorizationV2 = authorizationV2;
  }

  @Override
  public void register(RouterBuilder builder) {
    builder
        .operation(OP_GET_ACTIVITY_FOR_CONSUMER)
        .handler(authenticationV2)
        .handler(authorizationV2.forScopes(Scopes.DATA_ACCESS))
        .handler(this::handleGetAllActivityLogsForUser);

    builder
        .operation(OP_GET_ACTIVITY_FOR_ADMIN)
        .handler(authenticationV2)
        .handler(authorizationV2.forScopesWithContext(
            ScopeRule.platform(Scopes.USER_MANAGEMENT),
            ScopeRule.org(Scopes.ORG_USER_MANAGEMENT)))
        .handler(this::handleGetAllActivityLogsForAdmin);
  }

  // handleGetAllActivityLogsForUser — UNCHANGED for this migration.
  //   Reads ctx.user().subject() — still populated by upstream JWT handler.
  //
  // handleGetAllActivityLogsForAdmin — UNCHANGED for this migration.
  //   Continues to use Util.getAllowedFilterMapForAdmin(user) / DxUser.
  //   Future cleanup PR can swap to AuthorizationContext (read .getLevel()
  //   == ORG → filter by .getOrgId(); PLATFORM → no filter).
}
```

**Factory:**

```java
public static ActivityController create(
    PostgresService postgresService, URNGenerator urnGenerator, AuthHandlersV2 authV2) {
  UserActivityLogDao userActivityLogDao = new UserActivityLogDaoImpl(postgresService);
  UserActivityAuditLogService userActivityAuditLogService =
      new UserActivityAuditLogServiceImpl(userActivityLogDao);
  return new ActivityController(
      userActivityAuditLogService, urnGenerator,
      authV2.authentication(), authV2.authorization());
}
```

### 6.6 Modified — `ActivityReportController.java` + `ActivityReportControllerFactory.java`

Identical pattern. Same import swap, constructor extension, `register()` chain
change. Handler bodies (both `handleGenerateCsvForAdmin` and
`handleGenerateCsvForConsumer`) are untouched.

**Register section after migration:**
```java
builder
    .operation("get-admin-report")
    .handler(authenticationV2)
    .handler(authorizationV2.forScopesWithContext(
        ScopeRule.platform(Scopes.USER_MANAGEMENT),
        ScopeRule.org(Scopes.ORG_USER_MANAGEMENT)))
    .handler(this::handleGenerateCsvForAdmin);
builder
    .operation("get-consumer-report")
    .handler(authenticationV2)
    .handler(authorizationV2.forScopes(Scopes.DATA_ACCESS))
    .handler(this::handleGenerateCsvForConsumer);
```

**Factory signature:**
```java
public static ActivityReportController create(
    PostgresService pgService, Vertx vertx, AuthHandlersV2 authV2) { … }
```

## 7. Behavioral considerations

### 7.1 App credentials on activity endpoints

For an app principal, v2's `forScopes` checks the principal's
`directScopes` (which is `app.scopes ∩ flatten(owner.roles)` — the
intersection of granted app scopes and the owner's currently held
scopes).

- **Today (v1):** activity endpoints only accept JWT. App creds were
  never considered for these routes.
- **After migration (v2):** an app whose granted scope set includes
  `DATA_ACCESS` (consumer routes) or `USER_MANAGEMENT` /
  `ORG_USER_MANAGEMENT` (admin routes) would pass — but only if the
  app was explicitly granted those scopes by its owner. Apps not
  granted those scopes are blocked at the scope check.

This is **safer than `forRoles` would have been**: `forRoles` checks
`auditRoles` (the owner's full role bundle) and bypasses the
delegation/app scope cap. `forScopes` respects the cap.

**No additional gate needed.** Apps without the relevant scope are
already blocked. Activity is read-only and the owner would see the
data via their own JWT — no privilege escalation if an app is granted
the scope.

### 7.2 `ctx.user()` populated when v2 auth runs

v2 `JwtPrincipalResolver` reads `ctx.user()` set by the upstream OpenAPI
`authorization` security handler (`MultiIssuerJwtAuthHandler` or
`CombinedAuthHandler`). That upstream handler runs first because it's
attached via `routerBuilder.securityHandler("authorization", authHandler)`
in `AbstractApiServerVerticle.java:292`, and OpenAPI security runs before
operation handlers.

Sanity check during local testing: if v2 auth fails with "No user present"
for a valid Bearer request, this ordering assumption is wrong and needs
investigation.

### 7.3 Handler body `DxUser` reads

`RoutingContextHelper.fromPrincipal(ctx)` and `ctx.user().subject()` will
continue to work because `ctx.user()` is still populated. No breaking
change. A cleanup PR later can switch these to
`ctx.get(AuthorizationHandler.PRINCIPAL_KEY)` → `DxPrincipal`.

## 8. Test plan

### 8.1 Compile

- [ ] `mvn compile` passes after all file changes
- [ ] `mvn test` passes (no controller tests exist, but `UserActivityAuditLogServiceTest` and unrelated tests must still compile and pass)

### 8.2 Local smoke tests (boot controlplane)

Required log line at boot: `v2 auth diagnostic mounted at GET /auth/v2/whoami`
(confirms v2 stack still wired).

For each of the 4 endpoints:

| Path | Auth | Expected |
|---|---|---|
| `GET /user/activity/admin` | `ORG_ADMIN` JWT | 200 + paginated JSON; `AuthorizationContext.level == ORG`, `getOrgId()` populated |
| `GET /user/activity/admin` | `COS_ADMIN` JWT | 200; `AuthorizationContext.level == PLATFORM` |
| `GET /user/activity/admin` | `CONSUMER` JWT | 403 `Insufficient scope` |
| `GET /user/activity/admin` | no auth header | 401 `Missing credentials` (from v2) or 401 from OpenAPI security (whichever runs first) |
| `GET /user/activity/consumer` | `CONSUMER` JWT | 200 |
| `GET /user/activity/consumer` | `ORG_ADMIN` JWT | 403 — ORG_ADMIN does not hold `DATA_ACCESS` |
| `GET /user/activity-report/admin` | `ORG_ADMIN` JWT | 200 + CSV stream |
| `GET /user/activity-report/admin` | `CONSUMER` JWT | 403 |
| `GET /user/activity-report/consumer` | `CONSUMER` JWT | 200 + CSV stream |
| Any activity route | Bearer + `X-App-Id` both set | 400 `Ambiguous credentials` |

(Exact paths above may need verification against the OpenAPI spec — using
OP IDs to identify the endpoints.)

### 8.3 Regression checks on non-migrated controllers

- [ ] Hit one v1-authenticated endpoint (e.g. one in `UserController`) and
      confirm it still returns 200 on a valid JWT. Makes sure we didn't
      break the shared `ControllerFactory.createControllers` signature.

## 9. Commit shape

Three commits on `refact/auth-v2-activity`:

1. `refactor(auth-v2): expose handler pair via AuthHandlersV2` — §6.1 + §6.2 + §6.3
2. `refactor(controller-factory): accept AuthHandlersV2 param` — §6.4
3. `feat(activity): migrate activity APIs to v2 auth` — §6.5 + §6.6

Or a single squashed commit with the full message — team's call.

## 10. Open questions

1. **Commit split vs squash** — three commits per §9, or single
   squashed commit? Team preference.

### Resolved
- Scope strategy — **use existing scopes only** (`DATA_ACCESS`,
  `USER_MANAGEMENT`, `ORG_USER_MANAGEMENT`). No dx-common changes for
  this PR. `SELF_PROFILE` discussion deferred (see
  `controlplane-scope-audit.md` §7-§8).
- App-credential semantics — `forScopes` naturally respects app-scope
  caps. Apps with `DATA_ACCESS` granted will pass; without it, blocked
  at scope check. No extra gate needed.
- DELEGATE role — dropped per v2 design. The scope check covers
  delegated callers transparently.

## 11. What lands in this file

Just this plan — no Java code yet. Review comments go on this file.
Once §10 questions are settled, implementation follows §6 verbatim.
