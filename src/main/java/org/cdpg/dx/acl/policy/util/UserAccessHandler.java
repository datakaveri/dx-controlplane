package org.cdpg.dx.acl.policy.util;

import static org.cdpg.dx.common.ResponseUrn.DB_ERROR_URN;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.HttpStatusCode;
import org.cdpg.dx.common.exception.DxRuntimeException;
import org.cdpg.dx.common.model.DxUser;
import org.cdpg.dx.common.util.RoutingContextHelper;
import org.cdpg.dx.database.postgres.models.InsertQuery;
import org.cdpg.dx.database.postgres.models.QueryResult;
import org.cdpg.dx.database.postgres.service.PostgresService;
import org.cdpg.dx.keycloak.service.KeycloakUserService;

public class UserAccessHandler implements Handler<RoutingContext> {
  private static final Logger LOGGER = LogManager.getLogger(UserAccessHandler.class);
  private final PostgresService postgresService;
  private final KeycloakUserService keycloakUserService;

  public UserAccessHandler(PostgresService postgresService, KeycloakUserService keycloakUserService) {
    this.postgresService = postgresService;
    this.keycloakUserService = keycloakUserService;
  }

  @Override
  public void handle(RoutingContext event) {
    User user = event.user();

    UUID userId = UUID.fromString(user.principal().getString("sub"));

    keycloakUserService
        .getUserById(userId)
        .compose(this::insertUserIntoDb)
        .onSuccess(
            result -> {
              LOGGER.debug("User {} successfully inserted/updated in DB", userId);
              RoutingContextHelper.setUser(event, user);
              event.next();
            })
        .onFailure(
            err -> {
              LOGGER.error("Failed to insert user {} in DB", userId, err);
              event.fail(
                  new DxRuntimeException(
                      HttpStatusCode.INTERNAL_SERVER_ERROR.getValue(),
                      String.valueOf(DB_ERROR_URN)));
            });
  }


  /**
   * Inserts or updates user details in DB using DxUser fetched from Keycloak service
   */
  private Future<QueryResult> insertUserIntoDb(DxUser dxUser) {
    LOGGER.debug("inside insert user in DB method");

    List<Object> values = new ArrayList<>();
    values.add(dxUser.sub().toString());         // user ID
    values.add(dxUser.email());                  // email
    values.add(dxUser.givenName());              // first name
    values.add(dxUser.familyName());             // last name

    InsertQuery insertQuery = new InsertQuery()
        .setTable("user_table")
        .setColumns(List.of("_id", "email_id", "first_name", "last_name"))
        .setValues(values)
        .setConflictColumns(List.of("_id"))
        .setUpdateColumns(List.of("email_id", "first_name", "last_name")); //update on conflict
    LOGGER.debug("Insert Query : {}", insertQuery.toSQL());

    return postgresService.insert(insertQuery)
        .onSuccess(result -> LOGGER.debug("User with ID {} inserted successfully: {}",
            dxUser.sub(), result.toString()))
        .onFailure(err -> {
          LOGGER.error("Failure while executing user insertion query: {}", err.getMessage(), err);
          throw new DxRuntimeException(
              HttpStatusCode.getByValue(500).getValue(),
              "db_error");
        });
  }

  /**
   * Fetch user info from auth and return JsonObject principal. Keeps same logic as before but
   * returns a JsonObject we can use to create a Vert.x User.
   */
//  private Future<User> getUserFromAuthAsJson(RoutingContext event) {
//    Promise<User> promise = Promise.promise();
//    DxUser dxUser = RoutingContextHelper.fromPrincipal(event);
//    DxRole role = DxRole.fromRoles(dxUser);
//    boolean isDelegate = role.getRole().equalsIgnoreCase(DELEGATE.getRole());
//    UUID id = UUID.fromString(isDelegate ? dxUser.did() : dxUser.sub().toString());
//
//    LOGGER.info("Setting user info");
//    JsonObject userObj = new JsonObject();
//    userObj.put(org.cdpg.dx.acl.accessRequest.config.Constants.USER_ID, id.toString());
//    userObj.put(USER_ROLE, role.getRole());
//    userObj.put(EMAIL_ID, dxUser.email());
//    userObj.put(FIRST_NAME,dxUser.givenName());
//    userObj.put(LAST_NAME, dxUser.familyName());
//    userObj.put(RS_SERVER_URL, "user.resourceServerUrls");
//
//    boolean checkIfUserInfoIsInvalid =
//        dxUser.email() == null
//            || dxUser.name() == null
//            || dxUser.givenName() == null
//            || dxUser.familyName() == null;
//    if (checkIfUserInfoIsInvalid) {
//      LOGGER.error("Some user info from Auth is null");
//      LOGGER.error("Result from auth is {}", dxUser.toJson().encode());
//      promise.fail("User information is invalid");
//    } else {
//      //                userObj.put(IS_DELEGATE,isDelegate);
//      User user = new User(userObj);
//      promise.complete(user);
//    }
//    });
//  }
}
