package org.cdpg.dx.aaa.token.model;

import java.util.List;

/** Represents a token generation request payload. Immutable and compact record-based model. */
public record AccessTokenRequest(
    String clientId,
    String clientSecret,
    String delegationId, // optional
    String itemId,
    List<ConsentInfo> consentInfo) {}
