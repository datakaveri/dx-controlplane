package org.cdpg.dx.aaa.shareAssets.dao.impl;

import static org.cdpg.dx.aaa.shareAssets.util.Constants.*;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.shareAssets.dao.VisibilityDao;
import org.cdpg.dx.aaa.shareAssets.dao.model.VisibilityEntity;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.models.Condition;
import org.cdpg.dx.database.postgres.models.InsertQuery;
import org.cdpg.dx.database.postgres.models.QueryResult;
import org.cdpg.dx.database.postgres.models.SelectQuery;
import org.cdpg.dx.database.postgres.models.UpdateQuery;
import org.cdpg.dx.database.postgres.service.PostgresService;

public class VisibilityDaoImpl extends AbstractBaseDAO<VisibilityEntity> implements VisibilityDao {

  private static final Logger LOGGER = LogManager.getLogger(VisibilityDaoImpl.class);

  public VisibilityDaoImpl(PostgresService postgresService) {
    super(postgresService, SHARE_TABLE, DB_ID, VisibilityEntity::new);
  }

  @Override
  public Future<Void> shareWithUsers(UUID itemId, UUID sharedBy, List<UUID> userIds) {
    LOGGER.debug("Inside share with users...");

    List<Future<QueryResult>> futures = new ArrayList<>();

    for (UUID userId : userIds) {

      InsertQuery query =
          new InsertQuery()
              .setTable(SHARE_TABLE)
              .setColumns(List.of(DB_ITEM_ID, DB_SHARE_TYPE, DB_USER_ID, DB_SHARED_BY, DB_STATUS))
              .setValues(
                  List.of(itemId.toString(), USER, userId.toString(), sharedBy.toString(), ACTIVE));

      futures.add(postgresService.insert(query));
    }

    return Future.all(new ArrayList<>(futures)).mapEmpty();
  }

  @Override
  public Future<Void> shareWithOrganizations(UUID itemId, UUID sharedBy, List<UUID> orgIds) {

    List<Future<QueryResult>> futures = new ArrayList<>();

    for (UUID orgId : orgIds) {

      InsertQuery query =
          new InsertQuery()
              .setTable(SHARE_TABLE)
              .setColumns(List.of(DB_ITEM_ID, DB_SHARE_TYPE, DB_ORG_ID, DB_SHARED_BY, DB_STATUS))
              .setValues(
                  List.of(
                      itemId.toString(),
                      ORGANIZATION,
                      orgId.toString(),
                      sharedBy.toString(),
                      ACTIVE));

      futures.add(postgresService.insert(query));
    }

    return Future.all(new ArrayList<>(futures)).mapEmpty();
  }

  @Override
  public Future<List<VisibilityEntity>> getAssetsSharedWithMe(UUID userId, String orgId) {

    Condition userCondition =
        new Condition(DB_USER_ID, Condition.Operator.EQUALS, List.of(userId.toString()));

    Condition statusCondition =
        new Condition(DB_STATUS, Condition.Operator.EQUALS, List.of(ACTIVE));

    Condition accessCondition;

    if (orgId != null && !orgId.isBlank()) {

      Condition orgCondition = new Condition(DB_ORG_ID, Condition.Operator.EQUALS, List.of(orgId));

      accessCondition =
          new Condition(List.of(userCondition, orgCondition), Condition.LogicalOperator.OR);

    } else {

      accessCondition = userCondition;
    }

    Condition finalCondition =
        new Condition(List.of(accessCondition, statusCondition), Condition.LogicalOperator.AND);

    SelectQuery query =
        new SelectQuery()
            .setTable(SHARE_TABLE)
            .setCondition(finalCondition)
            .setColumns(List.of("*"));

    return postgresService
        .select(query, false)
        .map(
            result ->
                result.getRows().stream()
                    .map(obj -> new VisibilityEntity((JsonObject) obj))
                    .toList());
  }

  @Override
  public Future<Void> revokeUserShare(UUID itemId, List<UUID> userIds) {

    Condition condition =
        new Condition(
            List.of(
                new Condition(DB_ITEM_ID, Condition.Operator.EQUALS, List.of(itemId.toString())),
                new Condition(
                    DB_USER_ID,
                    Condition.Operator.IN,
                    userIds.stream().map(UUID::toString).collect(Collectors.toList())),
                new Condition(DB_STATUS, Condition.Operator.EQUALS, List.of(ACTIVE))),
            Condition.LogicalOperator.AND);

    UpdateQuery query =
        new UpdateQuery()
            .setTable(SHARE_TABLE)
            .setColumns(List.of(DB_STATUS))
            .setValues(List.of(DELETED))
            .setCondition(condition);

    return postgresService.update(query).mapEmpty();
  }

  @Override
  public Future<Void> revokeOrganizationShare(UUID itemId, List<UUID> orgIds) {

    Condition condition =
        new Condition(
            List.of(
                new Condition(DB_ITEM_ID, Condition.Operator.EQUALS, List.of(itemId.toString())),
                new Condition(
                    DB_ORG_ID,
                    Condition.Operator.IN,
                    orgIds.stream().map(UUID::toString).collect(Collectors.toList())),
                new Condition(DB_STATUS, Condition.Operator.EQUALS, List.of(ACTIVE))),
            Condition.LogicalOperator.AND);

    UpdateQuery query =
        new UpdateQuery()
            .setTable(SHARE_TABLE)
            .setColumns(List.of(DB_STATUS))
            .setValues(List.of(DELETED))
            .setCondition(condition);

    return postgresService.update(query).mapEmpty();
  }

  @Override
  public Future<List<VisibilityEntity>> getVisibilityDetails(UUID itemId) {

    Condition condition =
        new Condition(
            List.of(
                new Condition(DB_ITEM_ID, Condition.Operator.EQUALS, List.of(itemId.toString())),
                new Condition(DB_STATUS, Condition.Operator.EQUALS, List.of(ACTIVE))),
            Condition.LogicalOperator.AND);

    SelectQuery query =
        new SelectQuery().setTable(SHARE_TABLE).setColumns(List.of("*")).setCondition(condition);

    LOGGER.debug("Fetching visibility details for itemId: {}", itemId);

    return postgresService
        .select(query, false)
        .map(
            result ->
                result.getRows().stream()
                    .map(obj -> new VisibilityEntity((JsonObject) obj))
                    .collect(Collectors.toList()));
  }

  @Override
  public Future<Boolean> hasActiveUserShare(UUID itemId, UUID userId) {

    Condition condition =
        new Condition(
            List.of(
                new Condition(DB_ITEM_ID, Condition.Operator.EQUALS, List.of(itemId.toString())),
                new Condition(DB_USER_ID, Condition.Operator.EQUALS, List.of(userId.toString())),
                new Condition(DB_STATUS, Condition.Operator.EQUALS, List.of(ACTIVE))),
            Condition.LogicalOperator.AND);

    SelectQuery query =
        new SelectQuery().setTable(SHARE_TABLE).setColumns(List.of(DB_ID)).setCondition(condition);

    return postgresService.select(query, false).map(result -> !result.getRows().isEmpty());
  }

  @Override
  public Future<Boolean> hasActiveOrganizationShare(UUID itemId, UUID orgId) {

    Condition condition =
        new Condition(
            List.of(
                new Condition(DB_ITEM_ID, Condition.Operator.EQUALS, List.of(itemId.toString())),
                new Condition(DB_ORG_ID, Condition.Operator.EQUALS, List.of(orgId.toString())),
                new Condition(DB_STATUS, Condition.Operator.EQUALS, List.of(ACTIVE))),
            Condition.LogicalOperator.AND);

    SelectQuery query =
        new SelectQuery().setTable(SHARE_TABLE).setColumns(List.of(DB_ID)).setCondition(condition);

    return postgresService.select(query, false).map(result -> !result.getRows().isEmpty());
  }
}
