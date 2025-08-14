package org.cdpg.dx.aaa.clientSecret.controller;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.cdpg.dx.aaa.apiserver.ApiController;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;

public class ClientController implements ApiController {



    @Override
    public void register(RouterBuilder builder) {

        builder
                .operation("post-clientSecret")
                .handler(this::handleGetAllActivityLogsForUser);

    }





}
