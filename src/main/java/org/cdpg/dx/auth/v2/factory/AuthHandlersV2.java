package org.cdpg.dx.auth.v2.factory;

import org.cdpg.dx.auth.v2.handler.AuthenticationHandlerV2;
import org.cdpg.dx.auth.v2.handler.AuthorizationHandler;

public record AuthHandlersV2(
    AuthenticationHandlerV2 authentication, AuthorizationHandler authorization) {}
