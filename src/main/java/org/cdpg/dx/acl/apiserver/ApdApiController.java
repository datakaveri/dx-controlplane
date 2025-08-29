package org.cdpg.dx.acl.apiserver;

import io.vertx.ext.web.openapi.RouterBuilder;

public interface ApdApiController {
  void register(RouterBuilder builder);
}
