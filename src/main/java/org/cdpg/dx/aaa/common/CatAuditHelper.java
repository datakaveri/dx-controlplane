package org.cdpg.dx.aaa.common;

import static org.cdpg.dx.aaa.common.Constants.*;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.auth.authorization.model.DxRole;

public class CatAuditHelper {

  public static AuditLog createAuditLog(
      JsonObject item, User user, String method, String apiEndpoint) {
    UUID id = UUID.randomUUID();
    return new CatAuditLog(
        id,
        item.getString("name"),
        parseUUID(item.getString("id")),
        getItemType(item.getJsonArray("type")),
        getOperation(method),
        LocalDateTime.now().toString(),
        apiEndpoint,
        method,
        0L,
        getUserRole(user),
        parseUUID(user != null ? user.subject() : null),
        "Catalogue",
        getOrgnizationId(user),
        getOrgnizationName(user),
        true,
        item.getString("shortDescription"));
  }

  private static String getUserRole(User user) {

    if (user == null || user.principal() == null) {
      return "unknown";
    }
    JsonArray roles =
        user.principal()
            .getJsonObject("realm_access", new JsonObject())
            .getJsonArray("roles", new JsonArray());

    if (roles.contains(DxRole.COS_ADMIN.getRole())) return "cos_admin";
    if (roles.contains(DxRole.ORG_ADMIN.getRole())) return "org_admin";
    if (roles.contains(DxRole.PROVIDER.getRole())) return "provider";
    if (roles.contains(DxRole.CONSUMER.getRole())) return "consumer";

    return "unknown";
  }

  public static String getOperation(String httpMethod) {
    if (httpMethod == null) return VIEW;
    return switch (httpMethod.toUpperCase()) {
      case REQUEST_POST -> CREATE;
      case REQUEST_PUT -> UPDATE;
      case REQUEST_DELETE -> DELETE;
      default -> VIEW;
    };
  }

  private static UUID parseUUID(String id) {
    try {
      return id != null ? UUID.fromString(id) : UUID.randomUUID();
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static String getItemType(JsonArray itemType) {
    try {
      if (itemType == null || itemType.isEmpty()) {
        return null;
      }

      // Convert JsonArray to Set<String>
      Set<String> types =
          itemType.stream()
              .filter(Objects::nonNull)
              .map(Object::toString)
              .collect(Collectors.toSet());

      // Retain only allowed ITEM_TYPES
      types.retainAll(ITEM_TYPES);

      if (types.isEmpty()) {
        return null;
      }

      return String.join(",", types);
    } catch (Exception e) {
      return null;
    }
  }

  private static String getOrgnizationId(User user) {
    if (user == null || user.principal() == null) {
      return null;
    }
    return user.principal().getString("organisation_id");
  }

  private static String getOrgnizationName(User user) {
    if (user == null || user.principal() == null) {
      return null;
    }
    return user.principal().getString("organisation_name");
  }
}
