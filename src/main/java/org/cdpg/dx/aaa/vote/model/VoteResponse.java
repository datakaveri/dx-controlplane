package org.cdpg.dx.aaa.vote.model;

import org.cdpg.dx.auditing.enums.EntityType;

import java.util.UUID;

public record VoteResponse(UUID UserId, UUID entityId, EntityType entityType, VoteType voteType) {}
