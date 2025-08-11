package org.cdpg.dx.aaa.common;

import static org.cdpg.dx.aaa.common.Constants.*;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.auth.User;
import java.util.Set;
import java.util.UUID;
import org.cdpg.dx.aaa.item.model.Item;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.auth.authorization.model.DxRole;

public class CatAuditHelper {

  public static AuditLog createAuditLog(Item item, User user, String method, String apiEndpoint) {
    UUID id = UUID.randomUUID();
    return new CatAuditLog(
        id,
        item.getName(),
        parseUUID(item.getId()),
        getItemType(item),
        getOperation(method),
        item.getItemCreatedAt(),
        apiEndpoint,
        method,
        0L,
        getUserRole(user),
        parseUUID(user != null ? user.subject() : null),
        "Catalogue",
        item.getOrganizationId(),
        item.getDepartment(),
        true,
        item.getShortDescription());
  }

  private static String getUserRole(User user) {
    if (user == null || user.principal() == null) {
      return "unknown";
    }
    JsonArray roles =
        user.principal()
            .getJsonObject("realm_access", new io.vertx.core.json.JsonObject())
            .getJsonArray("roles", new JsonArray());

    if (roles.contains(DxRole.ORG_ADMIN)) return "org_admin";
    if (roles.contains(DxRole.COS_ADMIN)) return "cos_admin";
    if (roles.contains(DxRole.CONSUMER)) return "consumer";
    if (roles.contains(DxRole.PROVIDER)) return "provider";
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

  private static String getItemType(Item item) {
    try {
      Set<String> types = (Set<String>) item.getType();
      if (types == null || types.isEmpty()) return null;
      types.retainAll(ITEM_TYPES);
      if (types.isEmpty()) return null;
      return String.join(",", types);
    } catch (Exception e) {
      return null;
    }
  }
}
