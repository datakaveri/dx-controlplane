package org.cdpg.dx.aaa.organization.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.UUID;
import org.cdpg.dx.aaa.organization.config.Constants;
import org.cdpg.dx.aaa.organization.dao.OrganizationHierarchyNodeDAO;
import org.cdpg.dx.aaa.organization.models.OrganizationHierarchyNode;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class OrganizationHierarchyNodeDAOImpl extends AbstractBaseDAO<OrganizationHierarchyNode>
    implements OrganizationHierarchyNodeDAO {

  public OrganizationHierarchyNodeDAOImpl(PostgresService postgresService) {
    super(
        postgresService,
        Constants.ORGANIZATION_HIERARCHY_NODE_TABLE,
        "id",
        OrganizationHierarchyNode::fromJson);
  }

  @Override
  public Future<List<OrganizationHierarchyNode>> getByOrganizationId(UUID organizationId) {
    String sql =
        """
            SELECT *
            FROM organization_hierarchy_nodes
            WHERE organization_id = $1
            ORDER BY path
            """;

    JsonArray params = new JsonArray().add(str(organizationId));

    return postgresService
        .executeQuery(sql, params)
        .map(
            result ->
                result.getRows().stream()
                    .map(row -> OrganizationHierarchyNode.fromJson((JsonObject) row))
                    .toList());
  }

  @Override
  public Future<OrganizationHierarchyNode> getByIdAndOrganizationId(
      UUID nodeId, UUID organizationId) {
    String sql =
        """
            SELECT *
            FROM organization_hierarchy_nodes
            WHERE id = $1
              AND organization_id = $2
            """;

    JsonArray params = new JsonArray().add(str(nodeId)).add(str(organizationId));

    return postgresService
        .executeQuery(sql, params)
        .map(
            result -> {
              if (result == null || result.getRows().isEmpty()) {
                return null;
              }

              return OrganizationHierarchyNode.fromJson(result.getRows().getJsonObject(0));
            });
  }

  @Override
  public Future<List<OrganizationHierarchyNode>> getChildren(UUID organizationId, UUID parentId) {
    String sql =
        """
            SELECT *
            FROM organization_hierarchy_nodes
            WHERE organization_id = $1
              AND parent_id IS NOT DISTINCT FROM $2::uuid
            ORDER BY path
            """;

    JsonArray params = new JsonArray().add(str(organizationId)).add(str(parentId));

    return postgresService
        .executeQuery(sql, params)
        .map(
            result ->
                result.getRows().stream()
                    .map(row -> OrganizationHierarchyNode.fromJson((JsonObject) row))
                    .toList());
  }

  @Override
  public Future<List<OrganizationHierarchyNode>> getDescendants(UUID organizationId, String path) {
    String sql =
        """
            SELECT *
            FROM organization_hierarchy_nodes
            WHERE organization_id = $1
              AND path <@ $2::ltree
              AND path <> $2::ltree
            ORDER BY path
            """;

    JsonArray params = new JsonArray().add(str(organizationId)).add(path);

    return postgresService
        .executeQuery(sql, params)
        .map(
            result ->
                result.getRows().stream()
                    .map(row -> OrganizationHierarchyNode.fromJson((JsonObject) row))
                    .toList());
  }

  @Override
  public Future<List<OrganizationHierarchyNode>> insertTree(List<OrganizationHierarchyNode> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return Future.succeededFuture(List.of());
    }

    StringBuilder valuesClause = new StringBuilder();
    JsonArray params = new JsonArray();
    int paramIndex = 1;

    for (int i = 0; i < nodes.size(); i++) {
      OrganizationHierarchyNode node = nodes.get(i);

      if (i > 0) {
        valuesClause.append(", ");
      }

      valuesClause
          .append("($")
          .append(paramIndex++)
          .append(", $")
          .append(paramIndex++)
          .append(", $")
          .append(paramIndex++)
          .append(", $")
          .append(paramIndex++)
          .append(", $")
          .append(paramIndex++)
          .append(", $")
          .append(paramIndex++)
          .append("::ltree)");

      params
          .add(str(node.id()))
          .add(str(node.organizationId()))
          .add(str(node.parentId()))
          .add(node.name())
          .add(node.level())
          .add(node.path());
    }

    String sql =
        "INSERT INTO organization_hierarchy_nodes (id, organization_id, parent_id, name, level, path) "
            + "VALUES "
            + valuesClause
            + " RETURNING *";

    return postgresService
        .executeQuery(sql, params)
        .map(
            result ->
                result.getRows().stream()
                    .map(row -> OrganizationHierarchyNode.fromJson((JsonObject) row))
                    .toList());
  }

  @Override
  public Future<Boolean> existsByOrganizationId(UUID organizationId) {
    String sql =
        """
            SELECT EXISTS (
                SELECT 1
                FROM organization_hierarchy_nodes
                WHERE organization_id = $1
            ) AS exists
            """;

    JsonArray params = new JsonArray().add(str(organizationId));

    return postgresService
        .executeQuery(sql, params)
        .map(result -> result.getRows().getJsonObject(0).getBoolean("exists"));
  }

  private static String str(UUID id) {
    return id != null ? id.toString() : null;
  }
}
