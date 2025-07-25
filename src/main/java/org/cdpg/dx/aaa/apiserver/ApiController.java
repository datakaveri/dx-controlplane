package org.cdpg.dx.aaa.apiserver;

import io.vertx.ext.web.openapi.RouterBuilder;

public interface ApiController {
  void register(RouterBuilder builder);
}
