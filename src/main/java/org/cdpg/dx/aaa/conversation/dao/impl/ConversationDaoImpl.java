package org.cdpg.dx.aaa.conversation.dao.impl;

import io.vertx.core.Future;
import org.cdpg.dx.aaa.conversation.dao.ConversationDao;
import org.cdpg.dx.aaa.conversation.model.ConversationMessage;
import org.cdpg.dx.database.postgres.base.dao.AbstractBaseDAO;
import org.cdpg.dx.database.postgres.models.UpsertResult;
import org.cdpg.dx.database.postgres.service.PostgresService;

import java.util.List;

public class ConversationDaoImpl extends AbstractBaseDAO<ConversationMessage> implements ConversationDao {


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
}
