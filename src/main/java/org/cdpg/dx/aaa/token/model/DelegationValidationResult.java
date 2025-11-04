package org.cdpg.dx.aaa.token.model;

import java.util.List;
import java.util.UUID;

public record DelegationValidationResult(UUID delegationId,
                                         UUID delegatorId,
                                         UUID delegateId, // optional
                                         ItemInfo itemInfo) {
}
