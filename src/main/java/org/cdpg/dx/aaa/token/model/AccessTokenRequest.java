package org.cdpg.dx.aaa.token.model;

import java.util.List;
import org.cdpg.dx.common.model.DxUser;

/** Represents a token generation request payload. Immutable and compact record-based model. */
public record AccessTokenRequest(
    String clientId,
    String clientSecret,
    DxUser authenticatedUser, // resolved from a verified Authorization bearer token, if present
    String delegationId, // optional
    String itemId,
    List<ConsentInfo> consentInfo) {}
