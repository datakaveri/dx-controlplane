package org.cdpg.dx.aaa.vote.model;

import java.util.UUID;
import org.cdpg.dx.auditing.enums.EntityType;

public record VoteRequest(UUID entityId, EntityType entityType, VoteType voteType) {}
