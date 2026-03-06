package org.cdpg.dx.testutil;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import jakarta.ws.rs.core.Response;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.cdpg.dx.common.util.BlockingExecutionUtil;
import org.cdpg.dx.keycloak.service.KeycloakUserService;
import org.cdpg.dx.keycloak.service.KeycloakUserServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.userprofile.config.UPConfig;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for Keycloak integration tests.
 *
 * <p>Starts a Keycloak 26 container and programmatically creates a confidential admin service
 * account in the master realm. Creates test users and required realm roles in the master realm.
 * The KeycloakUserServiceImpl is configured to use this admin service account.
 */
@Testcontainers
@ExtendWith(VertxExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class KeycloakTestBase {

  private static final String ADMIN_CLIENT_ID = "test-service-admin";
  private static final String ADMIN_CLIENT_SECRET = "test-service-secret";

  @Container
  protected static final KeycloakContainer KEYCLOAK =
      new KeycloakContainer("quay.io/keycloak/keycloak:26.0");

  protected KeycloakUserService keycloakUserService;
  protected JsonObject keycloakConfig;
  protected Keycloak adminClient;

  // UUIDs of pre-seeded test users, resolved at setup time
  protected UUID testUser1Id;
  protected UUID testUser2Id;

  @BeforeAll
  void setUp(Vertx vertx, VertxTestContext ctx) throws Exception {
    // Reset static singletons from any previous test run
    resetKeycloakClientProvider();
    resetBlockingExecutionUtil();

    // Initialize BlockingExecutionUtil with Vertx
    BlockingExecutionUtil.initialize(vertx);

    // Use the built-in admin client (master realm, admin/admin) to set up infrastructure
    Keycloak masterAdmin = KEYCLOAK.getKeycloakAdminClient();
    setupMasterRealmForTests(masterAdmin);

    // Build config for KeycloakUserServiceImpl
    // Auth and user management both target 'master' realm
    keycloakConfig =
        new JsonObject()
            .put("keycloakUrl", KEYCLOAK.getAuthServerUrl())
            .put("keycloakRealm", "master")
            .put("keycloakAdminClientId", ADMIN_CLIENT_ID)
            .put("keycloakAdminClientSecret", ADMIN_CLIENT_SECRET)
            .put("keycloakClientId", "admin-cli");

    // Create the service under test
    keycloakUserService = new KeycloakUserServiceImpl(keycloakConfig);

    // Use the master admin client for test assertions
    adminClient = masterAdmin;

    // Resolve UUIDs of pre-seeded users
    RealmResource masterRealm = adminClient.realm("master");
    List<UserRepresentation> users1 =
        masterRealm.users().searchByUsername("testuser1", true);
    List<UserRepresentation> users2 =
        masterRealm.users().searchByUsername("testuser2", true);

    if (!users1.isEmpty()) {
      testUser1Id = UUID.fromString(users1.get(0).getId());
    }
    if (!users2.isEmpty()) {
      testUser2Id = UUID.fromString(users2.get(0).getId());
    }

    ctx.completeNow();
    ctx.awaitCompletion(30, TimeUnit.SECONDS);
  }

  /**
   * Set up the master realm for testing:
   * - Create a confidential service account client with admin permissions
   * - Create required realm roles
   * - Create test users
   */
  private void setupMasterRealmForTests(Keycloak masterAdmin) {
    RealmResource masterRealm = masterAdmin.realm("master");

    // Create required realm roles (used by the application)
    for (String roleName :
        List.of("cos_admin", "org_admin", "provider", "consumer", "delegate", "compute")) {
      try {
        RoleRepresentation role = new RoleRepresentation();
        role.setName(roleName);
        masterRealm.roles().create(role);
      } catch (Exception e) {
        // Role may already exist, ignore
      }
    }

    // Enable unmanaged attributes so that the admin client can set custom user attributes.
    // KC 26 enforces User Profile by default and silently drops unknown attributes.
    UPConfig upConfig = masterRealm.users().userProfile().getConfiguration();
    upConfig.setUnmanagedAttributePolicy(UPConfig.UnmanagedAttributePolicy.ADMIN_EDIT);
    masterRealm.users().userProfile().update(upConfig);

    // Create a confidential admin client with service account
    ClientRepresentation adminClientRep = new ClientRepresentation();
    adminClientRep.setClientId(ADMIN_CLIENT_ID);
    adminClientRep.setEnabled(true);
    adminClientRep.setProtocol("openid-connect");
    adminClientRep.setPublicClient(false);
    adminClientRep.setSecret(ADMIN_CLIENT_SECRET);
    adminClientRep.setServiceAccountsEnabled(true);
    adminClientRep.setDirectAccessGrantsEnabled(true);
    adminClientRep.setStandardFlowEnabled(false);
    try {
      masterRealm.clients().create(adminClientRep);
    } catch (Exception e) {
      // May already exist
    }

    // Find the admin client and assign admin role to its service account
    List<ClientRepresentation> clients =
        masterRealm.clients().findByClientId(ADMIN_CLIENT_ID);
    if (!clients.isEmpty()) {
      String clientInternalId = clients.get(0).getId();
      UserRepresentation serviceAccountUser =
          masterRealm.clients().get(clientInternalId).getServiceAccountUser();

      // In master realm, assign the 'admin' realm role to the service account
      try {
        RoleRepresentation adminRole =
            masterRealm.roles().get("admin").toRepresentation();
        masterRealm
            .users()
            .get(serviceAccountUser.getId())
            .roles()
            .realmLevel()
            .add(Collections.singletonList(adminRole));
      } catch (Exception e) {
        // Fallback: try to assign realm-management client roles
        try {
          List<ClientRepresentation> realmMgmtClients =
              masterRealm.clients().findByClientId("master-realm");
          if (!realmMgmtClients.isEmpty()) {
            String realmMgmtId = realmMgmtClients.get(0).getId();
            RoleRepresentation realmAdminRole =
                masterRealm.clients().get(realmMgmtId).roles().get("manage-users")
                    .toRepresentation();
            masterRealm
                .users()
                .get(serviceAccountUser.getId())
                .roles()
                .clientLevel(realmMgmtId)
                .add(Collections.singletonList(realmAdminRole));
          }
        } catch (Exception ex) {
          // Log but continue
          System.err.println("Warning: could not assign admin roles: " + ex.getMessage());
        }
      }
    }

    // Create test user 1 (with consumer role)
    createTestUser(masterRealm, "testuser1", "test1@example.com", "Test", "UserOne", "consumer");

    // Create test user 2 (no special roles)
    createTestUser(masterRealm, "testuser2", "test2@example.com", "Test", "UserTwo", null);
  }

  private void createTestUser(
      RealmResource realm,
      String username,
      String email,
      String firstName,
      String lastName,
      String roleName) {
    UserRepresentation user = new UserRepresentation();
    user.setUsername(username);
    user.setEmail(email);
    user.setEmailVerified(true);
    user.setEnabled(true);
    user.setFirstName(firstName);
    user.setLastName(lastName);

    CredentialRepresentation cred = new CredentialRepresentation();
    cred.setType(CredentialRepresentation.PASSWORD);
    cred.setValue("password");
    cred.setTemporary(false);
    user.setCredentials(List.of(cred));

    Response resp = realm.users().create(user);
    resp.close();

    if (roleName != null) {
      List<UserRepresentation> found = realm.users().searchByUsername(username, true);
      if (!found.isEmpty()) {
        try {
          RoleRepresentation role = realm.roles().get(roleName).toRepresentation();
          realm
              .users()
              .get(found.get(0).getId())
              .roles()
              .realmLevel()
              .add(Collections.singletonList(role));
        } catch (Exception e) {
          System.err.println("Warning: could not assign role " + roleName + ": " + e.getMessage());
        }
      }
    }
  }

  @AfterAll
  void tearDown(Vertx vertx, VertxTestContext ctx) {
    // Don't close adminClient — it's the container's built-in admin
    resetKeycloakClientProvider();
    resetBlockingExecutionUtil();
    ctx.completeNow();
  }

  /** Reset KeycloakClientProvider singleton via reflection (package-private method). */
  private void resetKeycloakClientProvider() {
    try {
      Class<?> clazz = Class.forName("org.cdpg.dx.keycloak.client.KeycloakClientProvider");
      Method resetMethod = clazz.getDeclaredMethod("reset");
      resetMethod.setAccessible(true);
      resetMethod.invoke(null);
    } catch (Exception e) {
      // Ignore — may not have been initialized
    }
  }

  /** Reset BlockingExecutionUtil singleton via reflection (package-private method). */
  private void resetBlockingExecutionUtil() {
    try {
      Class<?> clazz = Class.forName("org.cdpg.dx.common.util.BlockingExecutionUtil");
      Method resetMethod = clazz.getDeclaredMethod("reset");
      resetMethod.setAccessible(true);
      resetMethod.invoke(null);
    } catch (Exception e) {
      // Ignore
    }
  }
}
