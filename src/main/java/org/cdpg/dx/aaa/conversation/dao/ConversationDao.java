package org.cdpg.dx.aaa.conversation.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.conversation.model.ConversationMessage;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;
import org.cdpg.dx.database.postgres.models.UpsertResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ConversationDao extends BaseDAO<ConversationMessage> {

  // Fetch first `limit` replies per parent — used for threaded response
  Future<List<ConversationMessage>> getRepliesByParentIds(List<UUID> parentIds, int limit);

  // Fetch ALL replies for given parents — used for existing inline reply attachment
  Future<List<ConversationMessage>> getRepliesByParentIds(List<UUID> parentIds);

  // Increment reply_count on parent when a reply is created
  Future<Object> incrementReplyCount(UUID messageId);

  // Decrement reply_count on parent when a reply is deleted
  Future<Object> decrementReplyCount(UUID messageId);

  Future<Void> deleteAllReplies(UUID parentMsgId);
}
