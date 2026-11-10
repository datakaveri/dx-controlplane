package org.cdpg.dx.aaa.conversation.dao.impl;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.aaa.conversation.dao.ConversationDao;
import org.cdpg.dx.aaa.conversation.model.ConversationMessage;
import org.cdpg.dx.common.exception.BaseDxException;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.models.*;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ConversationDaoImpl extends AbstractBaseDAO<ConversationMessage> implements ConversationDao {

  private static final Logger LOGGER = LogManager.getLogger(ConversationDaoImpl.class);

  public ConversationDaoImpl(PostgresService postgresService) {
    super(postgresService, "request_messages", "id", ConversationMessage::fromJson);
  }

//  @Override
//  public Future<UpsertResult<ConversationMessage>> upsert(ConversationMessage conversationMessage) {
//    return super.upsertNew(
//      conversationMessage,
//      List.of("request_type_id", "sender_id", "content"), // ✅ conflict on unique constraint
//      List.of("metadata")  // update only metadata on conflict
//    );
//  }

    // ── Used by existing getAllMessages (no limit — fetches all replies) ───────
  @Override
  public Future<List<ConversationMessage>> getRepliesByParentIds(List<UUID> parentIds) {
    if (parentIds == null || parentIds.isEmpty()) {
      return Future.succeededFuture(List.of());
    }

    List<Object> idStrings = parentIds.stream()
      .map(UUID::toString)
      .collect(Collectors.toList());

    Condition condition = new Condition(
      "parent_msg_id",
      Condition.Operator.IN,
      idStrings
    );

    SelectQuery query = new SelectQuery(
      tableName,
      List.of("*"),
      condition,
      null,
      null,
      null,
      null
    );

    return postgresService.select(query, false)
      .map(result ->
        result.getRows().stream()
          .map(r -> ConversationMessage.fromJson((JsonObject) r))
          .collect(Collectors.toList())
      )
      .recover(err -> {
        LOGGER.error("Error fetching replies by parentIds: {}", err.getMessage(), err);
        return Future.failedFuture(BaseDxException.from(err));
      });
  }

    // ── Used by getThreadedMessages (limit = 3 per parent) ────────────────────
    @Override
    public Future<List<ConversationMessage>> getRepliesByParentIds(List<UUID> parentIds, int limit) {

      if (parentIds == null || parentIds.isEmpty()) {
        return Future.succeededFuture(List.of());
      }

      // Build $1, $2, ... $N for the IN clause
      String inClause = IntStream.rangeClosed(1, parentIds.size())
        .mapToObj(i -> "$" + i)
        .collect(Collectors.joining(", "));

      // The limit parameter comes after all UUIDs
      int limitParamIndex = parentIds.size() + 1;

      String sql =
        "SELECT * FROM (" +
          " SELECT *, ROW_NUMBER() OVER (" +
          "   PARTITION BY parent_msg_id ORDER BY created_at ASC" +
          " ) AS rn " +
          " FROM " + tableName +
          " WHERE parent_msg_id IN (" + inClause + ")" +
          ") ranked WHERE rn <= $" + limitParamIndex;

      JsonArray params = new JsonArray();
      parentIds.forEach(id -> params.add(id.toString()));
      params.add(limit);

      return postgresService.executeQuery(sql, params)
        .map(result ->
          result.getRows().stream()
            .map(r -> ConversationMessage.fromJson((JsonObject) r))
            .collect(Collectors.toList())
        )
        .recover(err -> {
          LOGGER.error("Error fetching limited replies: {}", err.getMessage(), err);
          return Future.failedFuture(BaseDxException.from(err));
        });
    }

    // ── Increment reply_count on parent when reply is inserted ────────────────
    @Override
    public Future<Object> incrementReplyCount(UUID messageId) {

      String sql =
        "UPDATE " + tableName +
          " SET reply_count = reply_count + 1" +
          " WHERE id = $1";

      JsonArray params = new JsonArray().add(messageId.toString());

      return postgresService.executeQuery(sql, params)
        .map(res -> null)   // ✅ convert QueryResult → Void
        .recover(err -> {
          LOGGER.error("Error incrementing reply_count for {}: {}", messageId, err.getMessage(), err);
          return Future.failedFuture(err);
        });
    }

  @Override
  public Future<Object> decrementReplyCount(UUID messageId) {
    String sql =
      "UPDATE " + tableName +
        " SET reply_count = GREATEST(reply_count - 1, 0)" +
        " WHERE id = $1";

    JsonArray params = new JsonArray().add(messageId.toString());

    return postgresService.executeQuery(sql, params)
      .mapEmpty()
      .recover(err -> {
        LOGGER.error("Error decrementing reply_count for {}: {}", messageId, err.getMessage());
        return Future.failedFuture(BaseDxException.from(err));
      });
  }

  public Future<Void> deleteAllReplies(UUID parentMsgId) {

    Condition condition =
          new Condition("parent_msg_id", Condition.Operator.EQUALS, List.of(parentMsgId.toString()));

    DeleteQuery deleteQuery = new DeleteQuery("request_messages",condition,null,null);

    return postgresService.delete(deleteQuery).mapEmpty();
  }

  }

