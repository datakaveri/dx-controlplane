package org.cdpg.dx.aaa.token.model;

import java.util.UUID;

public record AppTokenRequest(UUID appId, String appSecret) {}
