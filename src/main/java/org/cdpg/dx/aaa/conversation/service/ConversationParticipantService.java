package org.cdpg.dx.aaa.conversation.service;

import io.vertx.core.Future;
import java.util.UUID;
import org.cdpg.dx.aaa.conversation.model.ConversationParticipants;
import org.cdpg.dx.common.model.DxUser;

public interface ConversationParticipantService {

  Future<ConversationParticipants> getParticipants(String requestType, UUID requestTypeId);
}