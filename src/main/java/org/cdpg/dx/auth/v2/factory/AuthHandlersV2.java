package org.cdpg.dx.auth.v2.factory;

import org.cdpg.dx.auth.v2.handler.AuthenticationHandler;
import org.cdpg.dx.auth.v2.handler.AuthorizationHandler;

public record AuthHandlersV2(
    AuthenticationHandler authentication,
    AuthorizationHandler authorization) {}