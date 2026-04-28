package org.cdpg.dx.aaa.conversation.dao;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.conversation.model.ConversationMessage;
import org.cdpg.dx.database.postgres.base.dao.BaseDAO;
import org.cdpg.dx.database.postgres.models.UpsertResult;

public interface ConversationDao extends BaseDAO<ConversationMessage> {
//  Future<UpsertResult<ConversationMessage>> upsert(ConversationMessage conversationMessage);
}
