package org.cdpg.dx.aaa.conversation.model;

import org.cdpg.dx.common.model.DxUser;

public record ConversationParticipants(DxUser requester, DxUser approver) {}
